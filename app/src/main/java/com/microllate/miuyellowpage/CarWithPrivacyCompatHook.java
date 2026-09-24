package com.microllate.miuyellowpage;

import android.content.Context;
import android.os.Build;
import android.util.Log;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Locale;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/**
 * Experimental CarWith privacy compatibility hook.
 *
 * Scope: com.miui.carlink only.
 *
 * The CarWith 3.7.3 APK bundles two AMap privacy implementations:
 *   com.amap.api.col.s.ca
 *   com.loc.u
 *
 * Their gate methods return a PrivacyError object when privacyShow/privacyAgree
 * is not satisfied. For the EEA experiment we force only the gate result to
 * SuccessCode and leave the rest of the ROM untouched.
 */
public final class CarWithPrivacyCompatHook {
    private static final String PACKAGE = "com.miui.carlink";
    private static final String TAG = "miu-iYellowPage";

    private CarWithPrivacyCompatHook() {
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
            Class<?> contextClass = Context.class;
            Class<?> sdkInfo = Class.forName("r.o", false, cl);
            Method method = gate.getDeclaredMethod("a", contextClass, sdkInfo);
            method.setAccessible(true);

            XposedBridge.hookMethod(method, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    if (param.hasThrowable()) {
                        log("CARWITH PRIVACY TEST: AMap gate threw "
                                + param.getThrowable().getClass().getName());
                        return;
                    }
                    Object old = param.getResult();
                    String oldCode = describePrivacyError(old);
                    if (old == null || !"SuccessCode".equals(oldCode)) {
                        Object success = buildSuccessResult(
                                old,
                                "com.amap.api.col.s.h",
                                "com.amap.api.col.s.ca$c",
                                param.args.length > 1 ? param.args[1] : null);
                        if (success != null) {
                            param.setResult(success);
                            log("CARWITH PRIVACY TEST: AMap ca.a -> SuccessCode, old=" + oldCode);
                        } else {
                            log("CARWITH PRIVACY TEST: AMap result construction failed, old=" + oldCode);
                        }
                    }
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
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    if (param.hasThrowable()) {
                        log("CARWITH PRIVACY TEST: LOC gate threw "
                                + param.getThrowable().getClass().getName());
                        return;
                    }
                    Object old = param.getResult();
                    String oldCode = describePrivacyError(old);
                    if (old == null || !"SuccessCode".equals(oldCode)) {
                        Object success = buildSuccessResult(
                                old,
                                "com.loc.j",
                                "com.loc.u$c",
                                param.args.length > 1 ? param.args[1] : null);
                        if (success != null) {
                            param.setResult(success);
                            log("CARWITH PRIVACY TEST: LOC u.a -> SuccessCode, old=" + oldCode);
                        } else {
                            log("CARWITH PRIVACY TEST: LOC result construction failed, old=" + oldCode);
                        }
                    }
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

    private static Object buildSuccessResult(
            Object old,
            String errorClassName,
            String enumClassName,
            Object sdkInfo) {
        try {
            ClassLoader cl = old != null
                    ? old.getClass().getClassLoader()
                    : CarWithPrivacyCompatHook.class.getClassLoader();

            Class<?> errorClass = Class.forName(errorClassName, false, cl);
            Class<?> enumClass = Class.forName(enumClassName, false, cl);
            Object successCode = Enum.valueOf(
                    (Class<? extends Enum>) enumClass.asSubclass(Enum.class),
                    "SuccessCode");

            for (Constructor<?> ctor : errorClass.getDeclaredConstructors()) {
                Class<?>[] p = ctor.getParameterTypes();
                if (p.length == 2
                        && p[0].isAssignableFrom(enumClass)
                        && (sdkInfo == null || p[1].isInstance(sdkInfo))) {
                    ctor.setAccessible(true);
                    return ctor.newInstance(successCode, sdkInfo);
                }
            }
        } catch (Throwable e) {
            log("CARWITH PRIVACY TEST: build success failed: "
                    + e.getClass().getName() + ": " + e.getMessage());
        }
        return null;
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
                + ", brand=" + Build.BRAND
                + ", manufacturer=" + Build.MANUFACTURER
                + ", language=" + Locale.getDefault().getLanguage()
                + ", country=" + Locale.getDefault().getCountry()
                + ", mod_device=" + getSystemProperty("ro.product.mod_device")
                + ", miui_region=" + getSystemProperty("ro.miui.region")
                + ", miui_build_region=" + getSystemProperty("ro.miui.build.region")
                + ", miui_version=" + getSystemProperty("ro.miui.ui.version.name"));
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
