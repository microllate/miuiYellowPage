package com.microllate.miuyellowpage;

import android.view.View;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * Hides the Clear All (X) button at the bottom of MIUI/POCO recent tasks.
 *
 * Target is intentionally limited to the current Global Launcher package.
 * The underlying cleanInRecents() action is not modified; only the button
 * view is hidden after RecentsContainer finishes inflating.
 */
public final class RecentTasksClearButtonHook implements IXposedHookLoadPackage {
    private static final String PKG = "com.mi.android.globallauncher";
    private static final String RECENTS_CONTAINER =
            "com.miui.home.recents.views.RecentsContainer";
    private static final String R_ID =
            "com.miui.home.app.R$id";
    private static final String TAG = "RECENTS CLEAR BUTTON";

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        if (!PKG.equals(lpparam.packageName)) {
            return;
        }

        try {
            Class<?> recentsContainer =
                    XposedHelpers.findClass(RECENTS_CONTAINER, lpparam.classLoader);

            XposedHelpers.findAndHookMethod(
                    recentsContainer,
                    "onFinishInflate",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            hideClearButton(param, lpparam.classLoader);
                        }
                    });

            log("hooked RecentsContainer.onFinishInflate()");
        } catch (Throwable e) {
            log("install failed: " + e.getClass().getSimpleName()
                    + ": " + e.getMessage());
        }
    }

    private static void hideClearButton(
            XC_MethodHook.MethodHookParam param, ClassLoader classLoader) {
        try {
            Class<?> ids = XposedHelpers.findClass(R_ID, classLoader);
            int clearAnimViewId =
                    XposedHelpers.getStaticIntField(ids, "clearAnimView");

            View clearButton = (View) XposedHelpers.callMethod(
                    param.thisObject, "findViewById", clearAnimViewId);

            if (clearButton != null) {
                clearButton.setVisibility(View.GONE);
                log("clearAnimView -> GONE");
            } else {
                log("clearAnimView not found");
            }
        } catch (Throwable e) {
            log("hide failed: " + e.getClass().getSimpleName()
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
