package com.bluesleep.module;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

public class ServiceRestartWorker extends Worker {

    public ServiceRestartWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context context = getApplicationContext();
        SharedPreferences prefs = context.getSharedPreferences("bluesleep_prefs", Context.MODE_PRIVATE);
        boolean enabled = prefs.getBoolean("enabled", true);

        if (enabled) {
            Log.d("BlueSleep", "WorkManager: ensuring service is running");
            try {
                Intent intent = new Intent(context, AudioMonitorService.class);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent);
                } else {
                    context.startService(intent);
                }
            } catch (Exception e) {
                Log.e("BlueSleep", "WorkManager restart failed: " + e.getMessage());
            }
        }
        return Result.success();
    }
}
