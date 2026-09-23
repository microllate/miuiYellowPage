package com.microllate.miuyellowpage;

import android.os.Build;

import java.lang.reflect.Method;
import java.util.Locale;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * Experimental CN environment hook for Xiaomi Global Smart Assistant.
 *
 * Scope is intentionally limited to com.mi.globalminusscreen.
 * This first stage only changes local environment probes; it does not
 * replace network endpoints or modify arbitrary applications.
 */
public final class SmartAssistantChinaEnvironmentHook implements IXposedHookLoadPackage {
    private static final String PKG = "com.mi.globalminusscreen";
    private static final String TAG = "SMART ASSISTANT CN";

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        if (!PKG.equals(lpparam.packageName)) {
            return;
        }
        install(lpparam.classLoader);
    }

    private static void install(ClassLoader cl) {
        try {
            hookBuild();
            hookSystemProperties(cl);
            hookMiuiBuildRegion(cl);
            hookLocale();
            log("installed: region=CN locale=zh-CN device=mondrian model=23013PC75C");
        } catch (Throwable e) {
            log("install failed: " + e.getClass().getName() + ": " + e.getMessage());
        }
    }

    private static void hookBuild() {
        setBuild("DEVICE", "mondrian");
        setBuild("PRODUCT", "mondrian");
        setBuild("MODEL", "23013PC75C");
        setBuild("BRAND", "Redmi");
        setBuild("MANUFACTURER", "Xiaomi");
    }

    private static void setBuild(String field, String value) {
        try {
            XposedHelpers.setStaticObjectField(Build.class, field, value);
            log("Build." + field + " -> " + value);
        } catch (Throwable e) {
            log("Build." + field + " failed: " + e.getClass().getSimpleName());
        }
    }

    private static void hookSystemProperties(ClassLoader cl) {
        Class<?> sp = XposedHelpers.findClass("android.os.SystemProperties", cl);

        XposedHelpers.findAndHookMethod(
                sp,
                "get",
                String.class,
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam p) {
                        String key = String.valueOf(p.args[0]);
                        String value = mappedProperty(key);
                        if (value != null) {
                            p.setResult(value);
                            log("SystemProperties.get(" + key + ") -> " + value);
                        }
                    }
                });

        XposedHelpers.findAndHookMethod(
                sp,
                "get",
                String.class,
                String.class,
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam p) {
                        String key = String.valueOf(p.args[0]);
                        String value = mappedProperty(key);
                        if (value != null) {
                            p.setResult(value);
                            log("SystemProperties.get(" + key + ",*) -> " + value);
                        }
                    }
                });

        log("SystemProperties hooks installed");
    }

    private static String mappedProperty(String key) {
        switch (key) {
            case "ro.miui.region":
                return "CN";
            case "ro.miui.build.region":
                return "cn";
            case "ro.miui.cust_device":
                return "mondrian";
            case "ro.product.mod_device":
                return "23013PC75C";
            case "persist.sys.locale":
                return "zh-CN";
            case "ro.product.device":
                return "mondrian";
            case "ro.product.name":
                return "mondrian";
            case "ro.product.model":
                return "23013PC75C";
            case "ro.product.brand":
                return "Redmi";
            case "ro.product.manufacturer":
                return "Xiaomi";
            default:
                return null;
        }
    }

    private static void hookMiuiBuildRegion(ClassLoader cl) {
        try {
            Class<?> build = XposedHelpers.findClass("miui.os.Build", cl);
            Method method = build.getDeclaredMethod("getRegion");
            method.setAccessible(true);
            XposedBridge.hookMethod(method, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam p) {
                    p.setResult("CN");
                }
            });
            log("miui.os.Build.getRegion() -> CN");
        } catch (Throwable e) {
            log("miui.os.Build.getRegion hook skipped: "
                    + e.getClass().getSimpleName());
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
            log("Locale.getDefault() -> zh-CN");
        } catch (Throwable e) {
            log("Locale hook skipped: " + e.getClass().getSimpleName());
        }
    }

    private static void log(String msg) {
        try {
            XposedBridge.log(TAG + ": " + msg);
        } catch (Throwable ignored) {
        }
    }
}
