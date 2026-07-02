package com.bluesleep.module;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

import java.lang.reflect.Method;

public class XposedEntry implements IXposedHookLoadPackage {

    private static final String TAG = "BlueSleep";
    private static final String PKG = "com.bluesleep.module";

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (!"android".equals(lpparam.packageName)) return;

        XposedBridge.log(TAG + ": Module loaded in android framework");
        setActiveProperty();

        hookForceStop(lpparam.classLoader);
        hookStoppedState(lpparam.classLoader);
        hookSystemReady(lpparam.classLoader);
    }

    private void setActiveProperty() {
        try {
            Runtime.getRuntime().exec(new String[]{
                    "/system/bin/setprop", "debug.bluesleep.active", "1"
            });
        } catch (Throwable ignored) {}
    }

    private void hookForceStop(ClassLoader cl) {
        int hooked = 0;

        // Hook all overloads of forceStopPackage in ActivityManagerService
        try {
            Class<?> ams = XposedHelpers.findClass(
                    "com.android.server.am.ActivityManagerService", cl);
            for (Method m : ams.getDeclaredMethods()) {
                if ("forceStopPackage".equals(m.getName()) && m.getParameterTypes().length >= 1
                        && m.getParameterTypes()[0] == String.class) {
                    XposedBridge.hookMethod(m, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            if (PKG.equals(param.args[0])) {
                                XposedBridge.log(TAG + ": Blocked forceStopPackage(" +
                                        m.getParameterTypes().length + " params)");
                                param.setResult(null);
                            }
                        }
                    });
                    hooked++;
                }
            }
            XposedBridge.log(TAG + ": Hooked " + hooked + " forceStopPackage overloads");
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": forceStopPackage hook failed: " + t.getMessage());
        }

        // Also hook forceStopPackageAsUser if exists (some ROMs)
        try {
            Class<?> ams = XposedHelpers.findClass(
                    "com.android.server.am.ActivityManagerService", cl);
            for (Method m : ams.getDeclaredMethods()) {
                if ("forceStopPackageAsUser".equals(m.getName()) && m.getParameterTypes().length >= 1
                        && m.getParameterTypes()[0] == String.class) {
                    XposedBridge.hookMethod(m, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            if (PKG.equals(param.args[0])) {
                                XposedBridge.log(TAG + ": Blocked forceStopPackageAsUser");
                                param.setResult(null);
                            }
                        }
                    });
                    hooked++;
                }
            }
        } catch (Throwable ignored) {}
    }

    // Prevent Android from marking BlueSleep as "stopped"
    // Apps in stopped state won't receive implicit broadcasts (BOOT_COMPLETED, etc.)
    private void hookStoppedState(ClassLoader cl) {
        // Try PackageManagerService.setPackageStoppedState
        try {
            Class<?> pms = XposedHelpers.findClass(
                    "com.android.server.pm.PackageManagerService", cl);
            for (Method m : pms.getDeclaredMethods()) {
                if ("setPackageStoppedState".equals(m.getName()) && m.getParameterTypes().length >= 2
                        && m.getParameterTypes()[0] == String.class) {
                    XposedBridge.hookMethod(m, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            String pkg = (String) param.args[0];
                            if (PKG.equals(pkg)) {
                                // Check if trying to set stopped=true
                                for (int i = 1; i < param.args.length; i++) {
                                    if (param.args[i] instanceof Boolean && (Boolean) param.args[i]) {
                                        XposedBridge.log(TAG + ": Blocked setPackageStoppedState(stopped=true)");
                                        param.setResult(null);
                                        return;
                                    }
                                }
                            }
                        }
                    });
                    XposedBridge.log(TAG + ": Hooked setPackageStoppedState");
                    return;
                }
            }
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": setPackageStoppedState hook failed: " + t.getMessage());
        }

        // Fallback: try Settings variant (some ROMs)
        try {
            Class<?> settings = XposedHelpers.findClass(
                    "com.android.server.pm.Settings", cl);
            for (Method m : settings.getDeclaredMethods()) {
                if ("setPackageStopped".equals(m.getName()) || "setPackageStoppedState".equals(m.getName())) {
                    XposedBridge.hookMethod(m, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            for (Object arg : param.args) {
                                if (PKG.equals(arg)) {
                                    XposedBridge.log(TAG + ": Blocked pm.Settings stopped state");
                                    param.setResult(null);
                                    return;
                                }
                            }
                        }
                    });
                    XposedBridge.log(TAG + ": Hooked pm.Settings stopped state");
                    break;
                }
            }
        } catch (Throwable ignored) {}
    }

    // Start BlueSleep service after system boot completes
    private void hookSystemReady(ClassLoader cl) {
        try {
            XposedHelpers.findAndHookMethod(
                    "com.android.server.am.ActivityManagerService", cl,
                    "systemReady", Runnable.class, new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            XposedBridge.log(TAG + ": systemReady fired, scheduling service start");
                            Context ctx = (Context) XposedHelpers.getObjectField(param.thisObject, "mContext");
                            if (ctx != null) {
                                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                                    startBlueSleepService(ctx);
                                }, 15_000);
                            }
                        }
                    }
            );
            XposedBridge.log(TAG + ": Hooked systemReady for boot start");
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": systemReady hook failed: " + t.getMessage());
            // Fallback: hook finishBooting
            try {
                XposedHelpers.findAndHookMethod(
                        "com.android.server.am.ActivityManagerService", cl,
                        "finishBooting", new XC_MethodHook() {
                            @Override
                            protected void afterHookedMethod(MethodHookParam param) {
                                Context ctx = (Context) XposedHelpers.getObjectField(param.thisObject, "mContext");
                                if (ctx != null) {
                                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                                        startBlueSleepService(ctx);
                                    }, 15_000);
                                }
                            }
                        }
                );
                XposedBridge.log(TAG + ": Hooked finishBooting for boot start");
            } catch (Throwable t2) {
                XposedBridge.log(TAG + ": finishBooting hook also failed: " + t2.getMessage());
            }
        }
    }

    private void startBlueSleepService(Context context) {
        try {
            Intent intent = new Intent();
            intent.setClassName(PKG, PKG + ".AudioMonitorService");
            context.startForegroundService(intent);
            XposedBridge.log(TAG + ": Started BlueSleep service from system");
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": Failed to start service: " + t.getMessage());
        }
    }
}
