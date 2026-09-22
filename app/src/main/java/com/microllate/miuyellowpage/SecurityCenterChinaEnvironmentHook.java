package com.microllate.miuyellowpage;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * Enables the Yellow Page API path inside SecurityCenter without spoofing the
 * whole device/ROM region.
 *
 * SecurityCenter contains Game Turbo, AntiSpam and other global/CN-specific
 * code paths. Spoofing miui.os.Build.IS_INTERNATIONAL_BUILD, Locale and
 * SystemProperties globally makes those unrelated components take CN-only
 * branches on an EEA ROM. The Yellow Page integration itself only needs the
 * YellowPageUtils availability/enable checks to be true.
 */
public final class SecurityCenterChinaEnvironmentHook implements IXposedHookLoadPackage {
    private static final String PACKAGE = "com.miui.securitycenter";
    private static final String YELLOW_PAGE_UTILS = "miui.yellowpage.YellowPageUtils";
    private static final String TAG = "miu-iYellowPage";

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        if (!PACKAGE.equals(lpparam.packageName)) return;

        try {
            hookYellowPageUtils(lpparam.classLoader);
            log("installed (Yellow Page only; ROM region preserved)");
        } catch (Throwable e) {
            log("install failed: " + e.getClass().getSimpleName()
                    + ": " + e.getMessage());
        }
    }

    private static void hookYellowPageUtils(ClassLoader cl) {
        try {
            Class<?> utils = XposedHelpers.findClass(YELLOW_PAGE_UTILS, cl);
            hookBooleanMethod(utils, "isYellowPageAvailable", true);
            hookBooleanMethod(utils, "isYellowPageEnable", true);
        } catch (Throwable e) {
            log("YellowPageUtils hook failed: " + e.getClass().getSimpleName());
        }
    }

    private static void hookBooleanMethod(Class<?> cls, String method, final boolean result) {
        try {
            XposedBridge.hookAllMethods(cls, method, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    if (!param.hasThrowable()) param.setResult(result);
                }
            });
        } catch (Throwable e) {
            log(method + " hook failed: " + e.getClass().getSimpleName());
        }
    }

    private static void log(String message) {
        try {
            XposedBridge.log(TAG + ": SECURITYCENTER CN ENV: " + message);
        } catch (Throwable ignored) {
        }
    }
}
