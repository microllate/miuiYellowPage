package com.microllate.miuyellowpage;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
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
            hookSystemProperties();
            hookMiuiBuild(cl);
            hookLocale();
            hookCarWithPermissionChecks();
            hookYouTubeMusicSupport(cl);
            log("CARWITH COMPAT: hooks active (CN env + locale + permissions)");
        } catch (Throwable e) {
            log("CARWITH CN ENV: install failed: "
                    + e.getClass().getName() + ": " + e.getMessage());
        }
    }

    private static void hookSystemProperties() {
        try {
            Class<?> sp = Class.forName("android.os.SystemProperties");
            XC_MethodHook hook = new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam p) {
                    if (p.args == null || p.args.length == 0 || !(p.args[0] instanceof String)) return;
                    String key = (String) p.args[0];
                    String value = cnProperty(key);
                    if (value != null) {
                        p.setResult(value);
                        // Deliberately quiet: SystemProperties.get() is called very frequently by CarWith.
                    }
                }
            };

            XposedBridge.hookMethod(sp.getDeclaredMethod("get", String.class), hook);
            XposedBridge.hookMethod(sp.getDeclaredMethod("get", String.class, String.class), hook);

        } catch (Throwable e) {
            log("CARWITH CN ENV: SystemProperties hook failed: " + e);
        }
    }

    private static String cnProperty(String key) {
        switch (key) {
            case "ro.product.mod_device": return "mondrian";
            case "ro.miui.region": return "CN";
            case "ro.miui.build.region": return "CN";
            case "ro.product.locale": return "zh-CN";
            case "ro.product.locale.language": return "zh";
            case "ro.product.locale.region": return "CN";
            case "persist.sys.locale": return "zh-CN";
            case "persist.sys.language": return "zh";
            case "persist.sys.country": return "CN";
            case "ro.miui.cust_variant": return "cn";
            case "ro.miui.customized.region": return "CN";
            case "ro.miui.region.region": return "CN";
            default: return null;
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
                }
            });
        } catch (Throwable e) {
            log("CARWITH CN ENV: miui.os.Build.getRegion hook failed: " + e);
        }
    }

    private static void hookMiuiBuildFlags(ClassLoader cl) {
        try {
            Class<?> build = Class.forName("miui.os.Build", false, cl);
            int changed = 0;

            for (Field f : build.getDeclaredFields()) {
                int mods = f.getModifiers();
                if (!Modifier.isStatic(mods)) continue;

                String n = f.getName().toUpperCase(Locale.ROOT);
                if (!(n.contains("INTERNATIONAL")
                        || n.contains("GLOBAL")
                        || n.contains("OVERSEAS")
                        || n.contains("REGION"))) {
                    continue;
                }

                f.setAccessible(true);
                Object old = null;
                try {
                    old = f.get(null);
                } catch (Throwable ignored) {
                }

                if (f.getType() == boolean.class || f.getType() == Boolean.class) {
                    XposedHelpers.setStaticBooleanField(build, f.getName(), false);
                    log("CARWITH CN ENV: miui.os.Build." + f.getName()
                            + " " + old + " -> false");
                    changed++;
                } else if (f.getType() == String.class) {
                    String value = "CN";
                    XposedHelpers.setStaticObjectField(build, f.getName(), value);
                    log("CARWITH CN ENV: miui.os.Build." + f.getName()
                            + " " + old + " -> " + value);
                    changed++;
                }
            }

            log("CARWITH CN ENV: Build flag fields changed=" + changed);
        } catch (Throwable e) {
            log("CARWITH CN ENV: Build flag hook failed: "
                    + e.getClass().getName() + ": " + e.getMessage());
        }
    }

    private static void hookYouTubeMusicSupport(ClassLoader cl) {
        try {
            Class<?> mgr = Class.forName("com.carwith.common.utils.w", false, cl);
            XposedHelpers.findAndHookMethod(
                    mgr,
                    "o",
                    String.class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam p) {
                            if (p.args == null || p.args.length == 0
                                    || !(p.args[0] instanceof String)) {
                                return;
                            }
                            if ("com.google.android.apps.youtube.music".equals(p.args[0])) {
                                p.setResult(1);
                            }
                        }
                    });
            log("CARWITH MUSIC: YouTube Music -> supported MediaSession app");
        } catch (Throwable e) {
            log("CARWITH MUSIC: hook failed: "
                    + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private static void hookCarWithPermissionChecks() {
        try {
            XposedHelpers.findAndHookMethod(
                    ContextWrapper.class,
                    "checkSelfPermission",
                    String.class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam p) {
                            if (p.args == null || p.args.length == 0
                                    || !(p.args[0] instanceof String)) {
                                return;
                            }

                            String permission = (String) p.args[0];
                            if ("android.permission.BLUETOOTH_SCAN".equals(permission)
                                    || "android.permission.ACCESS_BACKGROUND_LOCATION".equals(permission)) {
                                p.setResult(PackageManager.PERMISSION_GRANTED);
                            }
                        }
                    });
        } catch (Throwable e) {
            log("CARWITH PERMISSION: hook failed: "
                    + e.getClass().getSimpleName() + ": " + e.getMessage());
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
