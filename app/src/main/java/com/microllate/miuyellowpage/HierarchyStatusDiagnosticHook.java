package com.microllate.miuyellowpage;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public final class HierarchyStatusDiagnosticHook implements IXposedHookLoadPackage {
    private static final String PKG = "com.miui.aod";
    private static final String CLASS_NAME =
            "com.miui.keyguard.editor.edit.base.EffectsTemplateView";
    private static final String TAG = "HIERARCHY STATUS";

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        if (!PKG.equals(lpparam.packageName)) return;
        try {
            Class<?> cls = XposedHelpers.findClass(CLASS_NAME, lpparam.classLoader);
            XposedHelpers.findAndHookMethod(
                    cls,
                    "updateHierarchyButton",
                    int.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            log("updateHierarchyButton(status=" + param.args[0] + ")");
                        }
                    });
            log("hooked EffectsTemplateView.updateHierarchyButton(int)");
        } catch (Throwable e) {
            log("install failed: " + e.getClass().getSimpleName()
                    + ": " + e.getMessage());
        }
    }

    private static void log(String msg) {
        try { XposedBridge.log(TAG + ": " + msg); } catch (Throwable ignored) {}
    }
}
