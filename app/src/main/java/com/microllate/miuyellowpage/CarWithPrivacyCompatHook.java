package com.microllate.miuyellowpage;

import android.content.Context;
import android.os.Build;
import android.util.Log;

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

    public CarWithPrivacyCompatHook() {
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        if (!PACKAGE.equals(lpparam.packageName)) return;
        install(lpparam.classLoader);
    }

    public static void install(ClassLoader cl) {
        try {
            log("CARWITH CN ENV: ENTRY LOADED");
            hookSystemProperties();
            hookMiuiBuild(cl);
            hookLocale();
            logEnvironment();
        } catch (Throwable e) {
            log("CARWITH CN ENV: install failed: "
                    + e.getClass().getName() + ": " + e.getMessage());
        }
    }

    private static void hookSystemProperties() {
        try {
            Class<?> sp = Class.forName("android.os.SystemProperties");
            Method get1 = sp.getDeclaredMethod("get", String.class);
            Method get2 = sp.getDeclaredMethod("get", String.class, String.class);
            Method getInt = sp.getDeclaredMethod("getInt", String.class, int.class);

            XC_MethodHook hook = new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam p) {
                    if (p.args == null || p.args.length == 0 || !(p.args[0] instanceof String)) return;
                    String key = (String) p.args[0];
                    String value = cnProperty(key);
                    if (value != null) {
                        p.setResult(value);
                        log("CARWITH CN ENV: SystemProperties " + key + " -> " + value);
                    }
                }
            };

            XposedBridge.hookMethod(get1, hook);
            XposedBridge.hookMethod(get2, hook);

            XposedBridge.hookMethod(getInt, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam p) {
                    if (p.args == null || p.args.length < 1 || !(p.args[0] instanceof String)) return;
                    String key = (String) p.args[0];
                    if ("ro.miui.region".equals(key) || "ro.miui.build.region".equals(key)) {
                        p.setResult(1);
                        log("CARWITH CN ENV: SystemProperties.getInt " + key + " -> 1");
                    }
                }
            });

            log("CARWITH CN ENV: SystemProperties hooks installed");
        } catch (Throwable e) {
            log("CARWITH CN ENV: SystemProperties hook failed: " + e);
        }
    }

    private static String cnProperty(String key) {
        switch (key) {
            case "ro.product.mod_device":
                return "mondrian";
            case "ro.miui.region":
                return "CN";
            case "ro.miui.build.region":
                return "CN";
            case "ro.product.locale":
                return "zh-CN";
            case "ro.product.locale.language":
                return "zh";
            case "ro.product.locale.region":
                return "CN";
            case "persist.sys.locale":
                return "zh-CN";
            case "persist.sys.language":
                return "zh";
            case "persist.sys.country":
                return "CN";
            case "ro.miui.cust_variant":
                return "cn";
            case "ro.miui.customized.region":
                return "CN";
            case "ro.miui.region.region":
                return "CN";
            default:
                return null;
        }
    }

    private static void hookMiuiBuild(ClassLoader cl) {
        try {
            Class<?> build = Class.forName("miui.os.Build", false, cl);

            Method getRegion = build.getDeclaredMethod("getRegion");
            getRegion.setAccessible(true);
            XposedBridge.hookMethod(getRegion, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam p) {
                    p.setResult("CN");
                    log("CARWITH CN ENV: miui.os.Build.getRegion() -> CN");
                }
            });

            log("CARWITH CN ENV: miui.os.Build.getRegion hooked");
        } catch (Throwable e) {
            log("CARWITH CN ENV: miui.os.Build hook failed: " + e);
        }
    }

    private static void hookLocale() {
        try {
            XposedHelpers.findAndHookMethod(
                    Locale.class,
                    "getDefault",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam p) {
                            p.setResult(Locale.SIMPLIFIED_CHINESE);
                        }
                    });
            log("CARWITH CN ENV: Locale.getDefault -> zh-CN");
        } catch (Throwable e) {
            log("CARWITH CN ENV: Locale hook failed: " + e);
        }
    }

    private static void logEnvironment() {
        log("CARWITH CN ENV: model=" + Build.MODEL
                + ", device=" + Build.DEVICE
                + ", mod_device=" + getSystemProperty("ro.product.mod_device")
                + ", miui_region=" + getSystemProperty("ro.miui.region")
                + ", miui_build_region=" + getSystemProperty("ro.miui.build.region")
                + ", locale=" + Locale.getDefault());
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
