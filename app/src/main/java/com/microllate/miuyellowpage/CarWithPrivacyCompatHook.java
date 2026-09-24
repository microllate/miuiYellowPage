package com.microllate.miuyellowpage;

import android.content.Context;
import android.os.Build;
import android.util.Log;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Locale;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public final class CarWithPrivacyCompatHook implements IXposedHookLoadPackage {
    private static final String PACKAGE = "com.miui.carlink";
    private static final String TAG = "miu-iYellowPage";

    private CarWithPrivacyCompatHook() {
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        if (!PACKAGE.equals(lpparam.packageName)) return;
        install(lpparam.classLoader);
    }

    public static void install(ClassLoader cl) {
        try {
            logEnvironment();
            int found = 0;
            found += hookAmapGate(cl);
            found += hookLocGate(cl);
            log("CARWITH PRIVACY TEST: installed=" + found);
        } catch (Throwable e) {
            log("CARWITH PRIVACY TEST: install failed: "
                    + e.getClass().getName() + ": " + e.getMessage());
        }
    }

    private static int hookAmapGate(ClassLoader cl) {
        try {
            Class<?> gate = Class.forName("com.amap.api.col.s.ca", false, cl);
            Class<?> sdkInfo = Class.forName("r.o", false, cl);
            Method method = gate.getDeclaredMethod("a", Context.class, sdkInfo);
            method.setAccessible(true);
            XposedBridge.hookMethod(method, new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam param) {
                    log("CARWITH PRIVACY TEST: AMap ca.a invoked, result="
                            + describePrivacyError(param.getResult()));
                }
            });
            log("CARWITH PRIVACY TEST: hooked com.amap.api.col.s.ca.a(Context,r.o)");
            return 1;
        } catch (Throwable e) {
            log("CARWITH PRIVACY TEST: AMap hook failed: "
                    + e.getClass().getName() + ": " + e.getMessage());
            return 0;
        }
    }

    private static int hookLocGate(ClassLoader cl) {
        try {
            Class<?> gate = Class.forName("com.loc.u", false, cl);
            Class<?> sdkInfo = Class.forName("a9.a5", false, cl);
            Method method = gate.getDeclaredMethod("a", Context.class, sdkInfo);
            method.setAccessible(true);
            XposedBridge.hookMethod(method, new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam param) {
                    log("CARWITH PRIVACY TEST: LOC u.a invoked, result="
                            + describePrivacyError(param.getResult()));
                }
            });
            log("CARWITH PRIVACY TEST: hooked com.loc.u.a(Context,a9.a5)");
            return 1;
        } catch (Throwable e) {
            log("CARWITH PRIVACY TEST: LOC hook failed: "
                    + e.getClass().getName() + ": " + e.getMessage());
            return 0;
        }
    }

    private static String describePrivacyError(Object result) {
        if (result == null) return "null";
        try {
            Object code = XposedHelpers.getObjectField(result, "f3485a");
            if (code != null) return String.valueOf(code);
        } catch (Throwable ignored) {
        }
        try {
            Object code = XposedHelpers.getObjectField(result, "f12264a");
            if (code != null) return String.valueOf(code);
        } catch (Throwable ignored) {
        }
        return result.getClass().getName();
    }

    private static void logEnvironment() {
        log("CARWITH PRIVACY TEST: model=" + Build.MODEL
                + ", device=" + Build.DEVICE
                + ", mod_device=" + getSystemProperty("ro.product.mod_device")
                + ", miui_region=" + getSystemProperty("ro.miui.region")
                + ", miui_build_region=" + getSystemProperty("ro.miui.build.region"));
    }

    private static String getSystemProperty(String key) {
        try {
            Class<?> cls = Class.forName("android.os.SystemProperties");
            Method get = cls.getDeclaredMethod("get", String.class, String.class);
            get.setAccessible(true);
            return String.valueOf(get.invoke(null, key, ""));
        } catch (Throwable e) {
            return "?";
        }
    }

    private static void log(String message) {
        try {
            XposedBridge.log(TAG + ": " + message);
        } catch (Throwable ignored) {
        }
        try {
            Log.i(TAG, message);
        } catch (Throwable ignored) {
        }
    }
}