package com.bluesleep.module;

import android.Manifest;
import android.animation.ValueAnimator;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.materialswitch.MaterialSwitch;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private SharedPreferences prefs;
    private boolean isDarkMode;

    private TextView timerValue, btValue, audioValue;
    private TextView statusLabel;
    private View statusDot;
    private ValueAnimator pulseAnimator;

    private TextView xposedStatusText, rootStatusText;
    private View xposedDot, rootDot;
    private TextView tempStatusTv;
    private View tempDotView;

    private TextView timeoutValueText;
    private SeekBar timeoutSeekBar;

    private TextView logText;

    private int cBg, cBgGlow1, cCard, cCardBorder, cText, cMuted, cMuted2, cAccent, cAccentSoft, cTeal, cTealSoft, cDanger, cTrack, cDivider;

    private final ActivityResultLauncher<String[]> permissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                if (prefs.getBoolean("enabled", true) && hasRequiredPermissions()) {
                    startMonitorService();
                }
            });

    private final BroadcastReceiver statusReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            boolean isPlaying = intent.getBooleanExtra(AudioMonitorService.EXTRA_IS_AUDIO_PLAYING, false);
            long remaining = intent.getLongExtra(AudioMonitorService.EXTRA_SECONDS_REMAINING, 0);
            boolean isMonitoring = intent.getBooleanExtra(AudioMonitorService.EXTRA_IS_MONITORING, false);
            boolean btConnected = intent.getBooleanExtra(AudioMonitorService.EXTRA_BT_CONNECTED, false);
            updateStatusUI(isMonitoring, isPlaying, remaining, btConnected);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("bluesleep_prefs", MODE_PRIVATE);
        isDarkMode = prefs.getBoolean("dark_mode", false);
        applyColors();
        applySystemBars();
        setContentView(buildUI());
        loadSettings();
        checkAndRequestRoot();
        requestPermissionsAndStart();
    }

    @Override
    protected void onResume() {
        super.onResume();
        IntentFilter filter = new IntentFilter(AudioMonitorService.ACTION_STATUS_UPDATE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(statusReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(statusReceiver, filter);
        }
        refreshXposedStatus();
        refreshRootStatus();
        refreshLog();
    }

    @Override
    protected void onPause() {
        super.onPause();
        try { unregisterReceiver(statusReceiver); } catch (Exception ignored) {}
    }

    @Override
    protected void onDestroy() {
        if (pulseAnimator != null) pulseAnimator.cancel();
        super.onDestroy();
    }

    // ── Permissions ──

    private boolean hasRequiredPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                    != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    private void requestPermissionsAndStart() {
        List<String> needed = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                    != PackageManager.PERMISSION_GRANTED) {
                needed.add(Manifest.permission.BLUETOOTH_CONNECT);
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                needed.add(Manifest.permission.POST_NOTIFICATIONS);
            }
        }
        if (!needed.isEmpty()) {
            permissionLauncher.launch(needed.toArray(new String[0]));
        } else if (prefs.getBoolean("enabled", true)) {
            startMonitorService();
        }
    }

    // ── Colors ──

    private void applyColors() {
        if (isDarkMode) {
            cBg = 0xFF0A0F1C;
            cBgGlow1 = 0xFF16244A;
            cCard = 0xAA121A2C;
            cCardBorder = 0x18FFFFFF;
            cText = 0xFFEAEEFA;
            cMuted = 0xFF7C8AA8;
            cMuted2 = 0xFF55627E;
            cAccent = 0xFF5B8CFF;
            cAccentSoft = 0x225B8CFF;
            cTeal = 0xFF4FE0C4;
            cTealSoft = 0x224FE0C4;
            cDanger = 0xFFFF7A8A;
            cTrack = 0xFF232F4C;
            cDivider = 0x12FFFFFF;
        } else {
            cBg = 0xFFE8EDF9;
            cBgGlow1 = 0xFFDBE4FF;
            cCard = 0xCCF2F4FA;
            cCardBorder = 0x14141E3C;
            cText = 0xFF16203A;
            cMuted = 0xFF6C7896;
            cMuted2 = 0xFF97A1B8;
            cAccent = 0xFF3D6DFF;
            cAccentSoft = 0x143D6DFF;
            cTeal = 0xFF0FBE9C;
            cTealSoft = 0x160FBE9C;
            cDanger = 0xFFE5566B;
            cTrack = 0xFFDCE3F5;
            cDivider = 0x14141E3C;
        }
    }

    private void applySystemBars() {
        getWindow().setStatusBarColor(cBg);
        getWindow().setNavigationBarColor(cBg);
        View decor = getWindow().getDecorView();
        if (isDarkMode) {
            decor.setSystemUiVisibility(decor.getSystemUiVisibility() & ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        } else {
            decor.setSystemUiVisibility(decor.getSystemUiVisibility() | View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        }
    }

    private void rebuildUI() {
        prefs.edit().putBoolean("dark_mode", isDarkMode).apply();
        applyColors();
        applySystemBars();
        if (pulseAnimator != null) pulseAnimator.cancel();
        setContentView(buildUI());
        loadSettings();
        refreshXposedStatus();
        refreshRootStatus();
        refreshLog();
    }

    // ── Main layout ──

    private View buildUI() {
        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(cBg);
        sv.setFillViewport(true);
        sv.setOverScrollMode(View.OVER_SCROLL_NEVER);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(16), dp(18), dp(36));

        root.addView(buildThemeToggle());
        root.addView(buildHero());
        root.addView(buildStatusCard());
        root.addView(buildSectionTitle("Cài đặt"));
        root.addView(buildSettingsCard());
        root.addView(buildSectionTitle("Thời gian chờ"));
        root.addView(buildTimeoutCard());
        root.addView(buildSectionTitle("Trạng thái Module"));
        root.addView(buildModuleCard());
        root.addView(buildSectionTitle("Lịch sử hoạt động"));
        root.addView(buildLogCard());

        sv.addView(root);
        return sv;
    }

    // ── Theme toggle ──

    private View buildThemeToggle() {
        FrameLayout wrap = new FrameLayout(this);
        LinearLayout.LayoutParams wrapLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(40));
        wrap.setLayoutParams(wrapLp);

        FrameLayout btn = new FrameLayout(this);
        FrameLayout.LayoutParams btnLp = new FrameLayout.LayoutParams(dp(38), dp(38));
        btnLp.gravity = Gravity.END | Gravity.CENTER_VERTICAL;
        btn.setLayoutParams(btnLp);

        GradientDrawable btnBg = new GradientDrawable();
        btnBg.setShape(GradientDrawable.OVAL);
        btnBg.setColor(cCard);
        btnBg.setStroke(1, cCardBorder);
        btn.setBackground(btnBg);
        btn.setElevation(dp(4));

        TextView icon = new TextView(this);
        icon.setText(isDarkMode ? "🌙" : "☀️");
        icon.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        icon.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams iconLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        icon.setLayoutParams(iconLp);
        btn.addView(icon);

        btn.setOnClickListener(v -> {
            isDarkMode = !isDarkMode;
            rebuildUI();
        });

        wrap.addView(btn);
        return wrap;
    }

    // ── Hero ──

    private View buildHero() {
        LinearLayout hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.VERTICAL);
        hero.setGravity(Gravity.CENTER_HORIZONTAL);
        hero.setPadding(0, 0, 0, dp(22));

        FrameLayout iconWrap = new FrameLayout(this);
        LinearLayout.LayoutParams iconWrapLp = new LinearLayout.LayoutParams(dp(68), dp(68));
        iconWrapLp.gravity = Gravity.CENTER_HORIZONTAL;
        iconWrapLp.bottomMargin = dp(14);
        iconWrap.setLayoutParams(iconWrapLp);

        View pulseRing = new View(this);
        FrameLayout.LayoutParams pulseLp = new FrameLayout.LayoutParams(dp(82), dp(82));
        pulseLp.gravity = Gravity.CENTER;
        pulseRing.setLayoutParams(pulseLp);
        GradientDrawable pulseDrawable = new GradientDrawable();
        pulseDrawable.setShape(GradientDrawable.RECTANGLE);
        pulseDrawable.setCornerRadius(dp(24));
        pulseDrawable.setStroke(dp(2), cAccent);
        pulseDrawable.setColor(Color.TRANSPARENT);
        pulseRing.setBackground(pulseDrawable);
        pulseRing.setAlpha(0f);
        iconWrap.addView(pulseRing);

        pulseAnimator = ValueAnimator.ofFloat(0f, 1f);
        pulseAnimator.setDuration(2600);
        pulseAnimator.setRepeatCount(ValueAnimator.INFINITE);
        pulseAnimator.setInterpolator(new AccelerateDecelerateInterpolator());
        pulseAnimator.addUpdateListener(anim -> {
            float v = (float) anim.getAnimatedValue();
            float scale = 0.85f + v * 0.4f;
            pulseRing.setScaleX(scale);
            pulseRing.setScaleY(scale);
            pulseRing.setAlpha(v < 0.8f ? 0.55f * (1f - v / 0.8f) : 0f);
        });
        pulseAnimator.start();

        View iconBg = new View(this);
        FrameLayout.LayoutParams ibgLp = new FrameLayout.LayoutParams(dp(64), dp(64));
        ibgLp.gravity = Gravity.CENTER;
        iconBg.setLayoutParams(ibgLp);
        GradientDrawable ibgD = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{cAccentSoft, cTealSoft});
        ibgD.setCornerRadius(dp(20));
        iconBg.setBackground(ibgD);
        iconWrap.addView(iconBg);

        ImageView btIcon = new ImageView(this);
        btIcon.setImageResource(android.R.drawable.stat_sys_data_bluetooth);
        btIcon.setColorFilter(cAccent);
        FrameLayout.LayoutParams btLp = new FrameLayout.LayoutParams(dp(28), dp(28));
        btLp.gravity = Gravity.CENTER;
        btIcon.setLayoutParams(btLp);
        iconWrap.addView(btIcon);

        hero.addView(iconWrap);

        TextView title = new TextView(this);
        title.setText("BlueSleep");
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 26);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        title.setTextColor(cText);
        title.setGravity(Gravity.CENTER);
        title.setLetterSpacing(-0.01f);
        hero.addView(title);

        TextView sub = new TextView(this);
        sub.setText("Tự động tắt Bluetooth khi bạn chìm vào giấc ngủ");
        sub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f);
        sub.setTextColor(cMuted);
        sub.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        subLp.topMargin = dp(4);
        sub.setLayoutParams(subLp);
        hero.addView(sub);

        return hero;
    }

    // ── Status Card ──

    private View buildStatusCard() {
        LinearLayout card = createCard();

        LinearLayout topRow = new LinearLayout(this);
        topRow.setGravity(Gravity.CENTER_VERTICAL);
        topRow.setPadding(0, 0, 0, dp(14));

        statusDot = new View(this);
        GradientDrawable dotD = new GradientDrawable();
        dotD.setShape(GradientDrawable.OVAL);
        dotD.setColor(cMuted2);
        statusDot.setBackground(dotD);
        LinearLayout.LayoutParams dotLp = new LinearLayout.LayoutParams(dp(9), dp(9));
        dotLp.rightMargin = dp(10);
        statusDot.setLayoutParams(dotLp);
        topRow.addView(statusDot);

        statusLabel = new TextView(this);
        statusLabel.setText("Đang chờ kết nối BT");
        statusLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        statusLabel.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        statusLabel.setTextColor(cText);
        topRow.addView(statusLabel);

        card.addView(topRow);

        LinearLayout grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.HORIZONTAL);
        grid.setWeightSum(3f);

        timerValue = buildGridItem(grid, "⏱", "Timer", "--:--");
        btValue = buildGridItem(grid, "📡", "Bluetooth", "Đang kiểm tra");
        audioValue = buildGridItem(grid, "♪", "Âm thanh", "--");

        card.addView(grid);
        return wrapCard(card);
    }

    private TextView buildGridItem(LinearLayout parent, String icon, String label, String value) {
        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams itemLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        itemLp.leftMargin = dp(4);
        itemLp.rightMargin = dp(4);
        item.setLayoutParams(itemLp);
        item.setPadding(dp(6), dp(10), dp(6), dp(10));

        GradientDrawable itemBg = new GradientDrawable();
        itemBg.setCornerRadius(dp(14));
        itemBg.setColor(isDarkMode ? 0x10FFFFFF : 0x0A000000);
        item.setBackground(itemBg);

        TextView iconTv = new TextView(this);
        iconTv.setText(icon);
        iconTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        iconTv.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams iLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        iLp.bottomMargin = dp(4);
        iconTv.setLayoutParams(iLp);
        item.addView(iconTv);

        TextView labelTv = new TextView(this);
        labelTv.setText(label);
        labelTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f);
        labelTv.setTextColor(cMuted);
        labelTv.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams lLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lLp.bottomMargin = dp(4);
        labelTv.setLayoutParams(lLp);
        item.addView(labelTv);

        TextView valueTv = new TextView(this);
        valueTv.setText(value);
        valueTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f);
        valueTv.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        valueTv.setTextColor(cText);
        valueTv.setGravity(Gravity.CENTER);
        item.addView(valueTv);

        parent.addView(item);
        return valueTv;
    }

    // ── Section title ──

    private View buildSectionTitle(String text) {
        TextView tv = new TextView(this);
        tv.setText(text.toUpperCase());
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f);
        tv.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        tv.setTextColor(cMuted);
        tv.setLetterSpacing(0.09f);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = dp(4);
        lp.topMargin = dp(4);
        lp.bottomMargin = dp(10);
        tv.setLayoutParams(lp);
        return tv;
    }

    // ── Settings Card ──

    private View buildSettingsCard() {
        LinearLayout card = createCard();
        card.setPadding(dp(18), dp(6), dp(18), dp(6));

        card.addView(buildSettingRow("📡", "Kích hoạt BlueSleep", "Bật để bắt đầu theo dõi",
                "enabled", true, isChecked -> {
                    if (isChecked) {
                        if (hasRequiredPermissions()) {
                            startMonitorService();
                        } else {
                            requestPermissionsAndStart();
                        }
                    } else {
                        stopMonitorService();
                    }
                }));

        View divider = new View(this);
        divider.setBackgroundColor(cDivider);
        divider.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1));
        card.addView(divider);

        card.addView(buildSettingRow("✓", "Khởi động cùng máy", "Tự chạy nền sau khi mở máy",
                "start_on_boot", true, null));

        return wrapCard(card);
    }

    private View buildSettingRow(String icon, String title, String sub, String prefKey,
                                 boolean defVal, SwitchCallback cb) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(14), 0, dp(14));

        FrameLayout iconBox = new FrameLayout(this);
        LinearLayout.LayoutParams ibLp = new LinearLayout.LayoutParams(dp(36), dp(36));
        ibLp.rightMargin = dp(12);
        iconBox.setLayoutParams(ibLp);
        GradientDrawable ibBg = new GradientDrawable();
        ibBg.setCornerRadius(dp(11));
        ibBg.setColor(cAccentSoft);
        iconBox.setBackground(ibBg);

        TextView iconTv = new TextView(this);
        iconTv.setText(icon);
        iconTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        iconTv.setGravity(Gravity.CENTER);
        iconTv.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        iconBox.addView(iconTv);
        row.addView(iconBox);

        LinearLayout textCol = new LinearLayout(this);
        textCol.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams tcLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        textCol.setLayoutParams(tcLp);

        TextView titleTv = new TextView(this);
        titleTv.setText(title);
        titleTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14.5f);
        titleTv.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        titleTv.setTextColor(cText);
        textCol.addView(titleTv);

        TextView subTv = new TextView(this);
        subTv.setText(sub);
        subTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f);
        subTv.setTextColor(cMuted);
        LinearLayout.LayoutParams sLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sLp.topMargin = dp(1);
        subTv.setLayoutParams(sLp);
        textCol.addView(subTv);

        row.addView(textCol);

        MaterialSwitch toggle = new MaterialSwitch(this);
        toggle.setChecked(prefs.getBoolean(prefKey, defVal));
        toggle.setOnCheckedChangeListener((bv, checked) -> {
            prefs.edit().putBoolean(prefKey, checked).apply();
            if (cb != null) cb.onChanged(checked);
        });
        row.addView(toggle);

        return row;
    }

    // ── Timeout Card ──

    private View buildTimeoutCard() {
        LinearLayout card = createCard();

        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = new TextView(this);
        title.setText("Ngưỡng im lặng");
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        title.setTextColor(cText);
        head.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        timeoutValueText = new TextView(this);
        timeoutValueText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        timeoutValueText.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        timeoutValueText.setTextColor(cAccent);
        head.addView(timeoutValueText);

        card.addView(head);

        TextView desc = new TextView(this);
        desc.setText("Tắt Bluetooth sau khoảng thời gian không có âm thanh phát ra");
        desc.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        desc.setTextColor(cMuted);
        desc.setLineSpacing(dp(2), 1f);
        LinearLayout.LayoutParams dLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        dLp.topMargin = dp(4);
        dLp.bottomMargin = dp(16);
        desc.setLayoutParams(dLp);
        card.addView(desc);

        timeoutSeekBar = new SeekBar(this);
        timeoutSeekBar.setMax(59);
        timeoutSeekBar.setProgressTintList(android.content.res.ColorStateList.valueOf(cAccent));
        timeoutSeekBar.setThumbTintList(android.content.res.ColorStateList.valueOf(cAccent));
        timeoutSeekBar.getProgressDrawable().setColorFilter(null);
        card.addView(timeoutSeekBar);

        LinearLayout labels = new LinearLayout(this);
        LinearLayout.LayoutParams labLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        labLp.topMargin = dp(6);
        labels.setLayoutParams(labLp);

        TextView minL = new TextView(this);
        minL.setText("1 phút");
        minL.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        minL.setTextColor(cMuted2);
        minL.setTypeface(Typeface.MONOSPACE);
        labels.addView(minL, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView maxL = new TextView(this);
        maxL.setText("60 phút");
        maxL.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        maxL.setTextColor(cMuted2);
        maxL.setTypeface(Typeface.MONOSPACE);
        maxL.setGravity(Gravity.END);
        labels.addView(maxL, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        card.addView(labels);

        timeoutSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                int mins = progress + 1;
                timeoutValueText.setText(mins + " phút");
                if (fromUser) prefs.edit().putInt("timeout_minutes", mins).apply();
            }
            @Override public void onStartTrackingTouch(SeekBar sb) {}
            @Override public void onStopTrackingTouch(SeekBar sb) {}
        });

        return wrapCard(card);
    }

    // ── Module Card ──

    private View buildModuleCard() {
        LinearLayout card = createCard();

        card.addView(buildModuleRow("LSPosed Module"));
        xposedStatusText = tempStatusTv;
        xposedDot = tempDotView;

        View div = new View(this);
        div.setBackgroundColor(cDivider);
        LinearLayout.LayoutParams divLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 1);
        divLp.topMargin = dp(14);
        divLp.bottomMargin = dp(14);
        div.setLayoutParams(divLp);
        card.addView(div);

        card.addView(buildModuleRow("Quyền Root"));
        rootStatusText = tempStatusTv;
        rootDot = tempDotView;

        return wrapCard(card);
    }

    private LinearLayout buildModuleRow(String title) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);

        FrameLayout dotWrap = new FrameLayout(this);
        LinearLayout.LayoutParams dwLp = new LinearLayout.LayoutParams(dp(24), dp(24));
        dwLp.rightMargin = dp(12);
        dotWrap.setLayoutParams(dwLp);

        GradientDrawable dotBg = new GradientDrawable();
        dotBg.setShape(GradientDrawable.OVAL);
        dotBg.setColor(cMuted2);
        dotWrap.setBackground(dotBg);

        TextView checkIcon = new TextView(this);
        checkIcon.setText("✓");
        checkIcon.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        checkIcon.setTextColor(Color.WHITE);
        checkIcon.setGravity(Gravity.CENTER);
        checkIcon.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        dotWrap.addView(checkIcon);

        row.addView(dotWrap);

        LinearLayout textCol = new LinearLayout(this);
        textCol.setOrientation(LinearLayout.VERTICAL);
        textCol.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView titleTv = new TextView(this);
        titleTv.setText(title);
        titleTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f);
        titleTv.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        titleTv.setTextColor(cText);
        textCol.addView(titleTv);

        TextView statusTv = new TextView(this);
        statusTv.setText("Đang kiểm tra...");
        statusTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f);
        statusTv.setTextColor(cMuted);
        LinearLayout.LayoutParams stLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        stLp.topMargin = dp(2);
        statusTv.setLayoutParams(stLp);
        textCol.addView(statusTv);

        row.addView(textCol);

        tempStatusTv = statusTv;
        tempDotView = dotWrap;

        return row;
    }

    // ── Log Card ──

    private View buildLogCard() {
        LinearLayout card = createCard();

        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams hLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hLp.bottomMargin = dp(10);
        head.setLayoutParams(hLp);

        TextView title = new TextView(this);
        title.setText("Gần đây");
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        title.setTextColor(cText);
        head.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView clearBtn = new TextView(this);
        clearBtn.setText("Xóa");
        clearBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f);
        clearBtn.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        clearBtn.setTextColor(cDanger);
        clearBtn.setPadding(dp(8), dp(4), dp(8), dp(4));
        clearBtn.setOnClickListener(v -> {
            prefs.edit().putString("log", "").apply();
            logText.setText("Chưa có hoạt động nào");
        });
        head.addView(clearBtn);

        card.addView(head);

        logText = new TextView(this);
        logText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        logText.setTextColor(cMuted2);
        logText.setTypeface(Typeface.MONOSPACE);
        logText.setText("Chưa có hoạt động nào");
        logText.setGravity(Gravity.CENTER);
        logText.setPadding(0, dp(12), 0, dp(6));
        card.addView(logText);

        return wrapCard(card);
    }

    // ── Card helpers ──

    private LinearLayout createCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18), dp(18), dp(18), dp(18));

        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(20));
        bg.setColor(cCard);
        bg.setStroke(1, cCardBorder);
        card.setBackground(bg);
        card.setElevation(dp(2));

        return card;
    }

    private View wrapCard(LinearLayout card) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(14);
        card.setLayoutParams(lp);
        return card;
    }

    private int dp(int v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
    }

    // ── Logic ──

    private void loadSettings() {
        int timeout = prefs.getInt("timeout_minutes", 10);
        timeoutSeekBar.setProgress(timeout - 1);
        timeoutValueText.setText(timeout + " phút");
        refreshBluetoothStatus();
    }

    private void refreshXposedStatus() {
        if (xposedStatusText == null) return;
        boolean active = isXposedModuleActive();
        if (active) {
            xposedStatusText.setText("Đã kích hoạt · Scope: android");
            xposedStatusText.setTextColor(cTeal);
            ((GradientDrawable) ((FrameLayout) xposedDot).getBackground()).setColor(cTeal);
        } else {
            xposedStatusText.setText("Chưa kích hoạt · Bật trong LSPosed Manager");
            xposedStatusText.setTextColor(0xFFFBBF24);
            ((GradientDrawable) ((FrameLayout) xposedDot).getBackground()).setColor(0xFFFBBF24);
        }
    }

    private void refreshRootStatus() {
        if (rootStatusText == null) return;
        boolean hasRoot = prefs.getBoolean("root_granted", false);
        if (hasRoot) {
            rootStatusText.setText("Đã cấp quyền root");
            rootStatusText.setTextColor(cTeal);
            ((GradientDrawable) ((FrameLayout) rootDot).getBackground()).setColor(cTeal);
        } else {
            rootStatusText.setText("Chưa cấp · Cấp quyền trong trình quản lý root");
            rootStatusText.setTextColor(cDanger);
            ((GradientDrawable) ((FrameLayout) rootDot).getBackground()).setColor(cDanger);
        }
    }

    private void refreshBluetoothStatus() {
        BluetoothManager btm = (BluetoothManager) getSystemService(BLUETOOTH_SERVICE);
        BluetoothAdapter adapter = btm != null ? btm.getAdapter() : null;
        if (adapter == null) {
            btValue.setText("N/A");
        } else if (!adapter.isEnabled()) {
            btValue.setText("Đã tắt");
            btValue.setTextColor(cMuted);
        } else {
            btValue.setText("Đang bật");
            btValue.setTextColor(cTeal);
        }
    }

    private void refreshLog() {
        String log = prefs.getString("log", "");
        if (log.isEmpty()) {
            logText.setText("Chưa có hoạt động nào");
            logText.setGravity(Gravity.CENTER);
        } else {
            logText.setText(log.trim());
            logText.setGravity(Gravity.START);
        }
    }

    private void updateStatusUI(boolean isMonitoring, boolean isPlaying, long remainingSec, boolean btConnected) {
        GradientDrawable dot = (GradientDrawable) statusDot.getBackground();

        if (btConnected) {
            btValue.setText("Kết nối");
            btValue.setTextColor(cTeal);
        } else {
            refreshBluetoothStatus();
        }

        if (isMonitoring) {
            if (isPlaying) {
                statusLabel.setText("Đang phát nhạc");
                dot.setColor(cTeal);
                audioValue.setText("Đang phát");
                audioValue.setTextColor(cTeal);
                timerValue.setText("Reset");
                timerValue.setTextColor(cTeal);
            } else {
                statusLabel.setText("Đang theo dõi");
                dot.setColor(0xFFF59E0B);
                audioValue.setText("Im lặng");
                audioValue.setTextColor(0xFFFBBF24);
                long min = remainingSec / 60;
                long sec = remainingSec % 60;
                timerValue.setText(String.format("%d:%02d", min, sec));
                timerValue.setTextColor(remainingSec < 60 ? cDanger : 0xFFFBBF24);
            }
        } else {
            statusLabel.setText(btConnected ? "Chờ bật BlueSleep..." : "Đang chờ kết nối BT");
            dot.setColor(cMuted2);
            timerValue.setText("--:--");
            timerValue.setTextColor(cText);
            audioValue.setText("--");
            audioValue.setTextColor(cMuted);
        }

        refreshLog();
    }

    private void checkAndRequestRoot() {
        new Thread(() -> {
            boolean hasRoot = false;
            try {
                Process p = Runtime.getRuntime().exec(new String[]{"su", "-c", "id"});
                java.io.BufferedReader reader = new java.io.BufferedReader(
                        new java.io.InputStreamReader(p.getInputStream()));
                String line = reader.readLine();
                reader.close();
                p.waitFor();
                hasRoot = (line != null && line.contains("uid=0"));
            } catch (Exception ignored) {}
            boolean finalHasRoot = hasRoot;
            runOnUiThread(() -> {
                prefs.edit().putBoolean("root_granted", finalHasRoot).apply();
                refreshRootStatus();
            });
        }).start();
    }

    private boolean isXposedModuleActive() {
        try {
            Process p = Runtime.getRuntime().exec(new String[]{
                    "/system/bin/getprop", "debug.bluesleep.active"});
            java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(p.getInputStream()));
            String value = reader.readLine();
            reader.close();
            p.waitFor();
            return "1".equals(value != null ? value.trim() : "");
        } catch (Exception e) {
            return false;
        }
    }

    private void startMonitorService() {
        Intent intent = new Intent(this, AudioMonitorService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
    }

    private void stopMonitorService() {
        stopService(new Intent(this, AudioMonitorService.class));
    }

    interface SwitchCallback {
        void onChanged(boolean isChecked);
    }
}
