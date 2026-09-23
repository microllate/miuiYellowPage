package com.microllate.miuyellowpage;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public final class HierarchyCapabilityDiagnosticHook implements IXposedHookLoadPackage {
    private static final String PKG = "com.miui.aod";
    private static final String DEVICE_UTIL = "com.miui.keyguard.editor.utils.DeviceUtil";
    private static final String CLOCK_VIEW = "com.miui.keyguard.editor.edit.base.BaseClockView";
    private static final String TAG = "HIERARCHY CAPABILITY";

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        if (!PKG.equals(lpparam.packageName)) return;
        try {
            Class<?> du = XposedHelpers.findClass(DEVICE_UTIL, lpparam.classLoader);
            hook(du, "isSupportHierarchyDevices");
        } catch (Throwable e) {
            log("DeviceUtil hook failed: " + e.getMessage());
        }
        try {
            Class<?> cv = XposedHelpers.findClass(CLOCK_VIEW, lpparam.classLoader);
            hook(cv, "supportHierarchy");
        } catch (Throwable e) {
            log("BaseClockView hook failed: " + e.getMessage());
        }
    }

    private static void hook(Class<?> cls, String method) {
        XposedHelpers.findAndHookMethod(cls, method, new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam param) {
                log(cls.getSimpleName() + "." + method + "() -> " + param.getResult());
            }
        });
        log("hooked " + cls.getName() + "." + method + "()");
    }

    private static void log(String msg) {
        try { XposedBridge.log(TAG + ": " + msg); } catch (Throwable ignored) {}
    }
}
