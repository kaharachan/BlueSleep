package com.bluesleep.module;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;

public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(action)
                || "android.intent.action.QUICKBOOT_POWERON".equals(action)) {

            SharedPreferences prefs = context.getSharedPreferences("bluesleep_prefs", Context.MODE_PRIVATE);
            boolean enabled = prefs.getBoolean("enabled", true);
            boolean bootStart = prefs.getBoolean("start_on_boot", true);

            if (enabled && bootStart) {
                Log.d("BlueSleep", "Starting service on boot");
                try {
                    Intent serviceIntent = new Intent(context, AudioMonitorService.class);
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(serviceIntent);
                    } else {
                        context.startService(serviceIntent);
                    }
                } catch (Exception e) {
                    Log.e("BlueSleep", "Boot start failed: " + e.getMessage());
                }
            }
        }
    }
}
