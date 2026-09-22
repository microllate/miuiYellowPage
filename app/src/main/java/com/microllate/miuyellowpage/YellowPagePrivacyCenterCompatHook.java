package com.microllate.miuyellowpage;

import android.content.ContentResolver;
import android.net.Uri;
import android.os.Bundle;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * Provides the small CN privacy-center compatibility surface expected by Yellow Page.
 * The EEA ROM does not expose com.miui.permcenter.privacycenter.
 */
public final class YellowPagePrivacyCenterCompatHook implements IXposedHookLoadPackage {
    private static final String PACKAGE = "com.miui.yellowpage";
    private static final String AUTHORITY = "com.miui.permcenter.privacycenter";
    private static final String QUERY_CTA_STATUS = "queryCTAStatus";
    private static final String SET_CTA_STATUS = "setCTAStatus";
    private static final String TAG = "miu-iYellowPage";

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        if (!PACKAGE.equals(lpparam.packageName)) return;

        try {
            XposedHelpers.findAndHookMethod(
                    ContentResolver.class,
                    "call",
                    Uri.class,
                    String.class,
                    String.class,
                    Bundle.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            if (param.args == null || param.args.length < 2) return;

                            Uri uri = param.args[0] instanceof Uri
                                    ? (Uri) param.args[0] : null;
                            String method = param.args[1] instanceof String
                                    ? (String) param.args[1] : null;

                            if (uri == null || method == null
                                    || !AUTHORITY.equals(uri.getAuthority())) {
                                return;
                            }

                            if (QUERY_CTA_STATUS.equals(method)) {
                                Bundle result = new Bundle();
                                result.putBoolean("is_agreed", true);
                                param.setResult(result);
                            } else if (SET_CTA_STATUS.equals(method)) {
                                param.setResult(new Bundle());
                            }
                        }
                    });
            log("installed");
        } catch (Throwable e) {
            log("install failed: " + e.getClass().getSimpleName()
                    + ": " + e.getMessage());
        }
    }

    private static void log(String message) {
        try {
            XposedBridge.log(TAG + ": PRIVACY CENTER COMPAT: " + message);
        } catch (Throwable ignored) {
        }
    }
}
