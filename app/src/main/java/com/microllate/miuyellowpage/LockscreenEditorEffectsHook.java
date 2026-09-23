package com.microllate.miuyellowpage;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * Restores the depth/effect buttons in the MIUI lockscreen wallpaper editor.
 *
 * MIUIAod skips the depth/effect path while the editor is treated as being
 * opened from the wallpaper carousel (Gallery). Keep the change local to
 * BaseTemplateView instead of spoofing the global international-build flag.
 */
public final class LockscreenEditorEffectsHook implements IXposedHookLoadPackage {
    private static final String PKG = "com.miui.aod";
    private static final String BASE_TEMPLATE_VIEW =
            "com.miui.keyguard.editor.edit.base.BaseTemplateView";
    private static final String TAG = "LOCKSCREEN EDITOR EFFECTS";

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        if (!PKG.equals(lpparam.packageName)) {
            return;
        }

        try {
            Class<?> baseTemplateView =
                    XposedHelpers.findClass(BASE_TEMPLATE_VIEW, lpparam.classLoader);

            XposedHelpers.findAndHookMethod(
                    baseTemplateView,
                    "isInternationalGalleryOpen",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            Object original = param.getResult();
                            if (Boolean.TRUE.equals(original)) {
                                param.setResult(false);
                                log("isInternationalGalleryOpen: true -> false");
                            }
                        }
                    });

            XposedHelpers.findAndHookMethod(
                    baseTemplateView,
                    "setGalleryOpened",
                    boolean.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            if (Boolean.TRUE.equals(param.args[0])) {
                                param.args[0] = false;
                                log("setGalleryOpened: true -> false");
                            }
                        }
                    });

            log("hooked BaseTemplateView.isInternationalGalleryOpen()");
            log("hooked BaseTemplateView.setGalleryOpened(boolean)");
        } catch (Throwable e) {
            log("install failed: " + e.getClass().getSimpleName()
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
