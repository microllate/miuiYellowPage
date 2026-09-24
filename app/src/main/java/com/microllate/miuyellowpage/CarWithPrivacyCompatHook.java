package com.microllate.miuyellowpage;

import android.content.ContextWrapper;
import android.content.pm.PackageManager;
import android.util.Log;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public final class CarWithPrivacyCompatHook implements IXposedHookLoadPackage {
    private static final String PACKAGE = "com.miui.carlink";
    private static final String TAG = "miu-iYellowPage";
    private static final String YOUTUBE_MUSIC = "com.google.android.apps.youtube.music";

    public CarWithPrivacyCompatHook() {}

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
            hookYouTubeMusicWhitelist(cl);
            log("CARWITH COMPAT: hooks active (CN env + locale + permissions)");
        } catch (Throwable e) {
            log("CARWITH CN ENV: install failed: " + e.getClass().getName() + ": " + e.getMessage());
        }
    }

    private static void hookSystemProperties() {
        try {
            Class<?> sp = Class.forName("android.os.SystemProperties");
            XC_MethodHook hook = new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam p) {
                    if (p.args == null || p.args.length == 0 || !(p.args[0] instanceof String)) return;
                    String value = cnProperty((String) p.args[0]);
                    if (value != null) p.setResult(value);
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

    private static void hookCarWithPermissionChecks() {
        try {
            XposedHelpers.findAndHookMethod(ContextWrapper.class, "checkSelfPermission", String.class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam p) {
                            if (p.args == null || p.args.length == 0 || !(p.args[0] instanceof String)) return;
                            String permission = (String) p.args[0];
                            if ("android.permission.BLUETOOTH_SCAN".equals(permission)
                                    || "android.permission.ACCESS_BACKGROUND_LOCATION".equals(permission)) {
                                p.setResult(PackageManager.PERMISSION_GRANTED);
                            }
                        }
                    });
        } catch (Throwable e) {
            log("CARWITH PERMISSION: hook failed: " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private static void hookLocale() {
        try {
            XposedHelpers.findAndHookMethod(Locale.class, "getDefault", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam p) {
                    p.setResult(Locale.SIMPLIFIED_CHINESE);
                }
            });
        } catch (Throwable e) {
            log("CARWITH CN ENV: Locale hook failed: " + e);
        }
    }

    private static void hookYouTubeMusicWhitelist(ClassLoader cl) {
        try {
            Class<?> mgr = Class.forName("com.carwith.common.utils.w", false, cl);
            Class<?> itemClass = Class.forName("com.carwith.common.bean.AppWhiteItem", false, cl);

            XposedHelpers.findAndHookMethod(mgr, "G", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam p) {
                    Object result = p.getResult();
                    if (!(result instanceof List)) return;

                    List<?> original = (List<?>) result;
                    for (Object item : original) {
                        if (item == null) continue;
                        try {
                            Method getPackageName = itemClass.getMethod("getPackageName");
                            if (YOUTUBE_MUSIC.equals(getPackageName.invoke(item))) return;
                        } catch (Throwable ignored) {
                        }
                    }

                    try {
                        Object item = itemClass.getDeclaredConstructor().newInstance();
                        itemClass.getMethod("setPackageName", String.class).invoke(item, YOUTUBE_MUSIC);
                        itemClass.getMethod("setVersionCode", long.class).invoke(item, 0L);
                        itemClass.getMethod("setApplicationType", String.class).invoke(item, "music");
                        itemClass.getMethod("setCastType", int.class).invoke(item, 1);
                        itemClass.getMethod("setAppName", String.class).invoke(item, "YouTube Music");
                        itemClass.getMethod("setAppMode", int.class).invoke(item, 1);
                        itemClass.getMethod("setAppCategory", int.class).invoke(item, 0);
                        itemClass.getMethod("setAppId", int.class).invoke(item, 0);

                        List<Object> updated = new ArrayList<>(original);
                        updated.add(item);
                        p.setResult(updated);
                        log("CARWITH MUSIC: YouTube Music added to runtime whitelist");
                    } catch (Throwable e) {
                        log("CARWITH MUSIC: whitelist item failed: "
                                + e.getClass().getSimpleName() + ": " + e.getMessage());
                    }
                }
            });

            log("CARWITH MUSIC: whitelist hook installed");
        } catch (Throwable e) {
            log("CARWITH MUSIC: hook failed: "
                    + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private static void log(String message) {
        try { XposedBridge.log(TAG + ": " + message); } catch (Throwable ignored) {}
        try { Log.i(TAG, message); } catch (Throwable ignored) {}
    }
}
