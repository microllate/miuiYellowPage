package com.microllate.miuyellowpage;

import java.lang.reflect.Method;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * Standalone Xiaomi Music EEA online-service compatibility hook.
 *
 * Target package: com.miui.player
 *
 * Existing project files are intentionally not modified.
 */
public final class MusicOnlineHook implements IXposedHookLoadPackage {

    private static final String PACKAGE = "com.miui.player";
    private static final String TAG = "miu-iYellowPage-Music";

    private static void log(String message) {
        try {
            XposedBridge.log(TAG + ": " + message);
        } catch (Throwable ignored) {
        }
    }

    private static void hookBooleanMethod(Class<?> clazz, String methodName) {
        try {
            Method target = clazz.getDeclaredMethod(methodName);
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
                        log("RegionUtil." + methodName + "(): " + original + " -> true");
                    }
                }
            });

            log("Music online hook installed: RegionUtil." + methodName + "()");
        } catch (Throwable e) {
            log("RegionUtil." + methodName + "() hook failed: "
                    + e.getClass().getName() + ": " + String.valueOf(e.getMessage()));
        }
    }

    private static void hookSingaporeRegion(Class<?> clazz) {
        try {
            Method target = clazz.getDeclaredMethod("l", boolean.class);
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
                        log("RegionUtil.l(boolean): " + original + " -> true (Singapore/NCT endpoint)");
                    }
                }
            });

            log("Music Singapore endpoint hook installed: RegionUtil.l(boolean)");
        } catch (Throwable e) {
            log("RegionUtil.l(boolean) hook failed: "
                    + e.getClass().getName() + ": " + String.valueOf(e.getMessage()));
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

            // p(): general online-service availability gate.
            hookBooleanMethod(regionUtil, "p");

            // i(): ASM/EEA online-mode region gate.
            hookBooleanMethod(regionUtil, "i");

            // l(boolean): force NCT/Singapore endpoint selection.
            hookSingaporeRegion(regionUtil);

        } catch (Throwable e) {
            log("Music online hook failed: "
                    + e.getClass().getName() + ": " + String.valueOf(e.getMessage()));
        }
    }
}
