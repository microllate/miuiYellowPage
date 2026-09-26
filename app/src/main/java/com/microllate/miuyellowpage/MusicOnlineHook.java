package com.microllate.miuyellowpage;

import java.lang.reflect.Method;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * Xiaomi Music EEA online-service compatibility hook.
 *
 * Scope:
 * - Only touches com.xiaomi.music.
 * - Does not modify the existing YellowPage hooks.
 * - Only overrides RegionUtil.p() so the Music app does not disable
 *   its online-service layer because of the current EEA region.
 *
 * Important:
 * This file is intentionally standalone. xposed_init is NOT modified here,
 * per the requirement to leave existing files untouched.
 */
public final class MusicOnlineHook implements IXposedHookLoadPackage {

    private static final String PACKAGE = "com.xiaomi.music";
    private static final String TAG = "miu-iYellowPage-Music";

    private static void log(String message) {
        try {
            XposedBridge.log(TAG + ": " + message);
        } catch (Throwable ignored) {
        }
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        if (!PACKAGE.equals(lpparam.packageName)) {
            return;
        }

        try {
            Class<?> regionUtil = Class.forName(
                    "com.xiaomi.music.util.RegionUtil",
                    false,
                    lpparam.classLoader
            );

            Method target = regionUtil.getDeclaredMethod("p");
            target.setAccessible(true);

            XposedBridge.hookMethod(target, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    if (param.hasThrowable()) {
                        return;
                    }

                    Object original = param.getResult();
                    if (!Boolean.TRUE.equals(original)) {
                        param.setResult(true);
                        log("RegionUtil.p(): " + original + " -> true");
                    }
                }
            });

            log("Music online hook installed: RegionUtil.p()");
        } catch (Throwable e) {
            log("Music online hook failed: "
                    + e.getClass().getName() + ": " + String.valueOf(e.getMessage()));
        }
    }
}
