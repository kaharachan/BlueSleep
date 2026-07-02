package com.bluesleep.module;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;

public class HeartbeatReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        SharedPreferences prefs = context.getSharedPreferences("bluesleep_prefs", Context.MODE_PRIVATE);
        boolean enabled = prefs.getBoolean("enabled", true);

        if (enabled) {
            Log.d("BlueSleep", "Heartbeat: ensuring service is running");
            try {
                Intent serviceIntent = new Intent(context, AudioMonitorService.class);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent);
                } else {
                    context.startService(serviceIntent);
                }
            } catch (Exception e) {
                Log.e("BlueSleep", "Heartbeat restart failed: " + e.getMessage());
            }
        }
    }
}
