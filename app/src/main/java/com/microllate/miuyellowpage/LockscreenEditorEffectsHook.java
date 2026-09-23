package com.microllate.miuyellowpage;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * Restores the depth/effect controls in the MIUI lockscreen wallpaper editor.
 *
 * MIUIAod treats the editor as an international wallpaper-gallery editor and
 * keeps the hierarchy/effect path disabled. Keep the compatibility change
 * local to BaseTemplateView; do not spoof the global MIUI build flags.
 */
public final class LockscreenEditorEffectsHook implements IXposedHookLoadPackage {
    private static final String PKG = "com.miui.aod";
    private static final String BASE_TEMPLATE_VIEW =
            "com.miui.keyguard.editor.edit.base.BaseTemplateView";
    private static final String TAG = "LOCKSCREEN EDITOR";

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        if (!PKG.equals(lpparam.packageName)) {
            return;
        }

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
                                log("international gallery check: true -> false");
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
                                log("gallery state: true -> false");
                            }
                        }
                    });

            log("installed: gallery compatibility enabled");
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
