package com.bluesleep.module;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class AudioMonitorService extends Service {

    private static final String TAG = "BlueSleep";
    private static final String CHANNEL_ID = "bluesleep_monitor";
    private static final int NOTIFICATION_ID = 1001;
    private static final int CHECK_INTERVAL_MS = 1_000;
    private static final long WAKELOCK_TIMEOUT_MS = 60 * 60 * 1000L;
    private static final long ROOT_CMD_TIMEOUT_SEC = 10;

    public static final String ACTION_STATUS_UPDATE = "com.bluesleep.STATUS_UPDATE";
    public static final String ACTION_HEARTBEAT = "com.bluesleep.HEARTBEAT";
    private static final long HEARTBEAT_INTERVAL_MS = 5 * 60 * 1000L;
    public static final String EXTRA_IS_AUDIO_PLAYING = "is_audio_playing";
    public static final String EXTRA_SECONDS_REMAINING = "seconds_remaining";
    public static final String EXTRA_IS_MONITORING = "is_monitoring";
    public static final String EXTRA_BT_CONNECTED = "bt_connected";

    private static final int BT_CHECK_INTERVAL = 30;

    private HandlerThread handlerThread;
    private Handler handler;
    private AudioManager audioManager;
    private SharedPreferences prefs;
    private PowerManager.WakeLock wakeLock;

    private long lastAudioTime;
    private boolean isMonitoring = false;
    private boolean lastKnownBtConnected = false;
    private int tickCounter = 0;
    private boolean btDisabledByUs = false;
    private String lastNotificationText = "";

    private final BroadcastReceiver bluetoothReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (BluetoothDevice.ACTION_ACL_CONNECTED.equals(action)) {
                Log.d(TAG, "BT device connected (broadcast)");
                if (btDisabledByUs) {
                    Log.d(TAG, "Ignoring ACL_CONNECTED - BT was disabled by us");
                    return;
                }
                lastKnownBtConnected = true;
                if (!isMonitoring) startMonitoring();
            } else if (BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(action)) {
                Log.d(TAG, "BT device disconnected (broadcast)");
                handler.postDelayed(() -> {
                    if (!checkBluetoothConnectedRoot()) {
                        lastKnownBtConnected = false;
                        stopMonitoring();
                    }
                }, 1500);
            } else if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(action)) {
                int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);
                if (state == BluetoothAdapter.STATE_OFF) {
                    lastKnownBtConnected = false;
                    stopMonitoring();
                } else if (state == BluetoothAdapter.STATE_ON && btDisabledByUs) {
                    Log.d(TAG, "BT re-enabled externally, clearing btDisabledByUs");
                    btDisabledByUs = false;
                }
            }
        }
    };

    private final Runnable mainLoopRunnable = new Runnable() {
        @Override
        public void run() {
            renewWakeLockIfNeeded();

            if (!prefs.getBoolean("enabled", true)) {
                stopMonitoring();
                updateNotification("BlueSleep tạm tắt");
                broadcastStatus(false, 0, false);
                handler.postDelayed(this, 3000);
                return;
            }

            tickCounter++;
            if (tickCounter >= BT_CHECK_INTERVAL) {
                tickCounter = 0;
                if (btDisabledByUs) {
                    if (isBluetoothOn()) {
                        Log.d(TAG, "BT re-enabled externally, clearing btDisabledByUs");
                        btDisabledByUs = false;
                    } else {
                        lastKnownBtConnected = false;
                    }
                }
                if (!btDisabledByUs) {
                    boolean btConnected = checkBluetoothConnectedRoot();
                    lastKnownBtConnected = btConnected;

                    if (btConnected && !isMonitoring) {
                        startMonitoring();
                    } else if (!btConnected && isMonitoring) {
                        stopMonitoring();
                    }
                }
            }

            if (isMonitoring) {
                checkAudioState();
            } else {
                broadcastStatus(false, 0, lastKnownBtConnected);
                if (isBluetoothOn()) {
                    updateNotification("Đang chờ kết nối Bluetooth...");
                } else {
                    updateNotification("Bluetooth đã tắt");
                }
            }

            handler.postDelayed(this, CHECK_INTERVAL_MS);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        handlerThread = new HandlerThread("BlueSleep-Monitor");
        handlerThread.start();
        handler = new Handler(handlerThread.getLooper());
        audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        prefs = getSharedPreferences("bluesleep_prefs", MODE_PRIVATE);

        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "BlueSleep::Monitor");

        createNotificationChannel();
        registerBluetoothReceiver();
        scheduleHeartbeat();
        scheduleWorkManager();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(NOTIFICATION_ID, buildNotification("Đang khởi động..."));

        if (!wakeLock.isHeld()) {
            wakeLock.acquire(WAKELOCK_TIMEOUT_MS);
        }

        handler.removeCallbacks(mainLoopRunnable);
        handler.post(mainLoopRunnable);

        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacks(mainLoopRunnable);
        isMonitoring = false;
        try {
            unregisterReceiver(bluetoothReceiver);
        } catch (Exception ignored) {}
        if (wakeLock.isHeld()) {
            wakeLock.release();
        }
        handlerThread.quitSafely();
        // If service was killed but user wants it running, heartbeat alarm will restart it
        super.onDestroy();
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        super.onTaskRemoved(rootIntent);
        Log.d(TAG, "Task removed (swiped from recents), scheduling restart");
        scheduleImmediateRestart();
    }

    private void scheduleImmediateRestart() {
        try {
            AlarmManager am = (AlarmManager) getSystemService(ALARM_SERVICE);
            if (am == null) return;
            Intent intent = new Intent(this, HeartbeatReceiver.class);
            intent.setAction(ACTION_HEARTBEAT);
            PendingIntent pi = PendingIntent.getBroadcast(this, 1, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,
                        System.currentTimeMillis() + 3000, pi);
            } else {
                am.setExact(AlarmManager.RTC_WAKEUP,
                        System.currentTimeMillis() + 3000, pi);
            }
            Log.d(TAG, "Scheduled immediate restart via alarm in 3s");
        } catch (Exception e) {
            Log.e(TAG, "Failed to schedule restart: " + e.getMessage());
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void renewWakeLockIfNeeded() {
        if (!wakeLock.isHeld()) {
            wakeLock.acquire(WAKELOCK_TIMEOUT_MS);
        }
    }

    private void startMonitoring() {
        if (isMonitoring) return;
        isMonitoring = true;
        lastAudioTime = System.currentTimeMillis();
        renewWakeLockIfNeeded();
        Log.d(TAG, "Started monitoring audio");
    }

    private void stopMonitoring() {
        if (!isMonitoring) return;
        isMonitoring = false;
        Log.d(TAG, "Stopped monitoring");
    }

    private void checkAudioState() {
        boolean isPlaying = audioManager != null && audioManager.isMusicActive();

        if (isPlaying) {
            lastAudioTime = System.currentTimeMillis();
        }

        long elapsed = System.currentTimeMillis() - lastAudioTime;
        long timeoutMs = getTimeoutMs();
        long remaining = timeoutMs - elapsed;

        if (remaining <= 0) {
            Log.d(TAG, "Timeout reached, disabling Bluetooth");
            boolean success = disableBluetoothRoot();
            if (success) {
                btDisabledByUs = true;
                addLog("Đã tắt Bluetooth sau " + getTimeoutMinutes() + " phút không có âm thanh");
                isMonitoring = false;
                lastKnownBtConnected = false;
                updateNotification("Đã tắt Bluetooth ✓");
                broadcastStatus(false, 0, false);
            } else {
                addLog("Tắt Bluetooth thất bại — kiểm tra quyền root");
                updateNotification("⚠ Tắt BT thất bại — thử lại...");
                lastAudioTime = System.currentTimeMillis();
            }
            return;
        }

        long remainingSec = remaining / 1000;

        String status;
        if (isPlaying) {
            status = "🎵 Đang phát nhạc - Timer đã reset";
        } else {
            int min = (int) (remainingSec / 60);
            int sec = (int) (remainingSec % 60);
            status = String.format(Locale.getDefault(),
                    "🔇 Tắt BT sau %d:%02d", min, sec);
        }

        updateNotification(status);
        broadcastStatus(isPlaying, remainingSec, true);
    }

    // --- Bluetooth via root ---

    private boolean checkBluetoothConnectedRoot() {
        if (!isBluetoothOn()) return false;
        try {
            Process p = Runtime.getRuntime().exec(new String[]{
                    "su", "-c", "dumpsys bluetooth_manager | grep -ci 'ConnectionState: STATE_CONNECTED'"
            });
            BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line = reader.readLine();
            reader.close();
            boolean finished = p.waitFor(ROOT_CMD_TIMEOUT_SEC, TimeUnit.SECONDS);
            if (!finished) {
                p.destroyForcibly();
                Log.w(TAG, "BT check timed out, falling back to API");
                return checkBluetoothConnectedApi();
            }
            int count = 0;
            if (line != null) {
                try { count = Integer.parseInt(line.trim()); } catch (NumberFormatException ignored) {}
            }
            if (p.exitValue() != 0 && count == 0) {
                return checkBluetoothConnectedApi();
            }
            return count > 0;
        } catch (Exception e) {
            return checkBluetoothConnectedApi();
        }
    }

    private boolean checkBluetoothConnectedApi() {
        BluetoothManager btManager = (BluetoothManager) getSystemService(BLUETOOTH_SERVICE);
        if (btManager == null) return false;
        BluetoothAdapter adapter = btManager.getAdapter();
        if (adapter == null || !adapter.isEnabled()) return false;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                    ContextCompat.checkSelfPermission(this, android.Manifest.permission.BLUETOOTH_CONNECT)
                            != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
            return adapter.getProfileConnectionState(android.bluetooth.BluetoothProfile.A2DP)
                    == android.bluetooth.BluetoothProfile.STATE_CONNECTED
                    || adapter.getProfileConnectionState(android.bluetooth.BluetoothProfile.HEADSET)
                    == android.bluetooth.BluetoothProfile.STATE_CONNECTED;
        } catch (SecurityException e) {
            return false;
        }
    }

    private boolean isBluetoothOn() {
        BluetoothManager btManager = (BluetoothManager) getSystemService(BLUETOOTH_SERVICE);
        if (btManager == null) return false;
        BluetoothAdapter adapter = btManager.getAdapter();
        return adapter != null && adapter.isEnabled();
    }

    private boolean disableBluetoothRoot() {
        try {
            Process su = Runtime.getRuntime().exec("su");
            DataOutputStream os = new DataOutputStream(su.getOutputStream());
            os.writeBytes("settings put global ble_scan_always_enabled 0\n");
            os.writeBytes("svc bluetooth disable\n");
            os.writeBytes("settings put global bluetooth_on 0\n");
            os.writeBytes("sleep 1\n");
            os.writeBytes("am force-stop com.android.bluetooth\n");
            os.writeBytes("exit\n");
            os.flush();
            boolean finished = su.waitFor(ROOT_CMD_TIMEOUT_SEC, TimeUnit.SECONDS);
            if (!finished) {
                su.destroyForcibly();
                Log.e(TAG, "Root BT disable timed out");
                return false;
            }
            Log.d(TAG, "Root BT disable exit=" + su.exitValue());
            Thread.sleep(500);
            boolean btOff = !isBluetoothOn();
            if (!btOff) {
                Log.w(TAG, "BT still on after root disable");
            }
            return btOff;
        } catch (Exception e) {
            Log.e(TAG, "Root BT disable failed: " + e.getMessage());
            return false;
        }
    }

    // --- Helpers ---

    private int getTimeoutMinutes() {
        return prefs.getInt("timeout_minutes", 10);
    }

    private long getTimeoutMs() {
        return getTimeoutMinutes() * 60 * 1000L;
    }

    private void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "BlueSleep Monitor",
                NotificationManager.IMPORTANCE_LOW
        );
        channel.setDescription("Thông báo theo dõi âm thanh và Bluetooth");
        channel.setShowBadge(false);

        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null) {
            nm.createNotificationChannel(channel);
        }
    }

    private Notification buildNotification(String text) {
        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("BlueSleep")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
                .setContentIntent(pi)
                .setOngoing(true)
                .setSilent(true)
                .build();
    }

    private void updateNotification(String text) {
        if (text.equals(lastNotificationText)) return;
        lastNotificationText = text;
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null) {
            nm.notify(NOTIFICATION_ID, buildNotification(text));
        }
    }

    private void broadcastStatus(boolean isPlaying, long remainingSec, boolean btConnected) {
        Intent intent = new Intent(ACTION_STATUS_UPDATE);
        intent.putExtra(EXTRA_IS_AUDIO_PLAYING, isPlaying);
        intent.putExtra(EXTRA_SECONDS_REMAINING, remainingSec);
        intent.putExtra(EXTRA_IS_MONITORING, isMonitoring);
        intent.putExtra(EXTRA_BT_CONNECTED, btConnected);
        intent.setPackage(getPackageName());
        sendBroadcast(intent);
    }

    private void addLog(String message) {
        String time = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());
        String log = prefs.getString("log", "");
        String entry = time + " - " + message + "\n";
        if (log.length() > 5000) {
            log = log.substring(0, 2500);
        }
        prefs.edit().putString("log", entry + log).apply();
    }

    private void scheduleHeartbeat() {
        AlarmManager am = (AlarmManager) getSystemService(ALARM_SERVICE);
        if (am == null) return;
        Intent intent = new Intent(this, HeartbeatReceiver.class);
        intent.setAction(ACTION_HEARTBEAT);
        PendingIntent pi = PendingIntent.getBroadcast(this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        am.setRepeating(AlarmManager.RTC_WAKEUP,
                System.currentTimeMillis() + HEARTBEAT_INTERVAL_MS,
                HEARTBEAT_INTERVAL_MS, pi);
        Log.d(TAG, "Heartbeat alarm scheduled every 5 min");
    }

    private void scheduleWorkManager() {
        try {
            PeriodicWorkRequest workRequest = new PeriodicWorkRequest.Builder(
                    ServiceRestartWorker.class, 15, TimeUnit.MINUTES)
                    .build();
            WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                    "bluesleep_restart",
                    ExistingPeriodicWorkPolicy.KEEP,
                    workRequest);
            Log.d(TAG, "WorkManager periodic restart scheduled");
        } catch (Exception e) {
            Log.e(TAG, "WorkManager scheduling failed: " + e.getMessage());
        }
    }

    private void registerBluetoothReceiver() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);
        filter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
        filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(bluetoothReceiver, filter, Context.RECEIVER_EXPORTED);
        } else {
            registerReceiver(bluetoothReceiver, filter);
        }
    }
}
