package com.microllate.miuyellowpage;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * Restores the depth/effect controls in the MIUI lockscreen wallpaper editor.
 *
 * MIUIAod and the MIUI wallpaper service both treat the international/theme
 * carousel state as a reason to disable or clear wallpaper effects. Keep the
 * compatibility changes local to the lockscreen editor/wallpaper service.
 */
public final class LockscreenEditorEffectsHook implements IXposedHookLoadPackage {
    private static final String AOD_PKG = "com.miui.aod";
    private static final String WALLPAPER_PKG = "com.miui.miwallpaper";

    private static final String BASE_TEMPLATE_VIEW =
            "com.miui.keyguard.editor.edit.base.BaseTemplateView";
    private static final String WALLPAPER_AUTHORITY_UTIL =
            "com.miui.miwallpaper.utils.WallpaperAuthorityUtil";

    private static final String TAG = "LOCKSCREEN EDITOR";

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        if (AOD_PKG.equals(lpparam.packageName)) {
            hookAod(lpparam);
        } else if (WALLPAPER_PKG.equals(lpparam.packageName)) {
            hookWallpaperService(lpparam);
        }
    }

    private static void hookAod(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> cls = XposedHelpers.findClass(BASE_TEMPLATE_VIEW, lpparam.classLoader);

            XposedHelpers.findAndHookMethod(
                    cls,
                    "isInternationalGalleryOpen",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            if (Boolean.TRUE.equals(param.getResult())) {
                                param.setResult(false);
                                log("AOD: international gallery check true -> false");
                            }
                        }
                    });

            XposedHelpers.findAndHookMethod(
                    cls,
                    "setGalleryOpened",
                    boolean.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            if (Boolean.TRUE.equals(param.args[0])) {
                                param.args[0] = false;
                                log("AOD: gallery state true -> false");
                            }
                        }
                    });

            log("AOD: editor compatibility enabled");
        } catch (Throwable e) {
            log("AOD: install failed: " + e.getClass().getSimpleName()
                    + ": " + e.getMessage());
        }
    }

    private static void hookWallpaperService(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> cls = XposedHelpers.findClass(
                    WALLPAPER_AUTHORITY_UTIL,
                    lpparam.classLoader);

            XposedHelpers.findAndHookMethod(
                    cls,
                    "isThemeCarousalOpened",
                    android.content.Context.class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            if (Boolean.TRUE.equals(param.getResult())) {
                                param.setResult(false);
                                log("Wallpaper: theme carousel check true -> false");
                            }
                        }
                    });

            log("Wallpaper: theme carousel compatibility enabled");
        } catch (Throwable e) {
            log("Wallpaper: install failed: " + e.getClass().getSimpleName()
                    + ": " + e.getMessage());
        }
    }

    private static void log(String msg) {
        try {
            XposedBridge.log(TAG + ": " + msg);
        } catch (Throwable ignored) {
        }
    }
}
