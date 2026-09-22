package com.microllate.miuyellowpage;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * Makes the CN Contacts/Dialer APK see the current EEA HyperOS environment
 * as a China-region MIUI build, without changing the global system properties.
 *
 * Scope: com.android.contacts process only.
 */
public final class ContactsChinaEnvironmentHook implements IXposedHookLoadPackage {
    private static final String PACKAGE = "com.android.contacts";
    private static final String TAG = "miu-iYellowPage";

    private static void log(String message) {
        try {
            XposedBridge.log(TAG + ": CONTACTS CN ENV: " + message);
        } catch (Throwable ignored) {
        }
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        if (!PACKAGE.equals(lpparam.packageName)) return;

        try {
            hookMiuiBuild(lpparam.classLoader);
            hookAndroidBuild();
            hookSystemProperties();
            log("installed");
        } catch (Throwable e) {
            log("install failed: " + e.getClass().getName() + ": " + e.getMessage());
        }
    }

    private static void hookMiuiBuild(ClassLoader cl) throws Throwable {
        Class<?> build = Class.forName("miui.os.Build", false, cl);

        // The CN Contacts APK uses this flag through SystemUtil.T().
        setStaticBoolean(build, "IS_INTERNATIONAL_BUILD", false);

        // Keep the current hardware/device identity; only make the region/build
        // classification CN. This avoids pretending to be a different phone model.
        try {
            setStaticBoolean(build, "IS_CM_CUSTOMIZATION_TEST", false);
        } catch (Throwable ignored) {
        }

        try {
            final Method getRegion = build.getDeclaredMethod("getRegion");
            XposedBridge.hookMethod(getRegion, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    if (!param.hasThrowable()) param.setResult("CN");
                }
            });
            log("hooked miui.os.Build.getRegion -> CN");
        } catch (Throwable e) {
            log("getRegion hook failed: " + e.getClass().getSimpleName());
        }

        try {
            final Method checkRegion = build.getDeclaredMethod("checkRegion", String.class);
            XposedBridge.hookMethod(checkRegion, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    if (param.hasThrowable()) return;
                    if (param.args != null && param.args.length > 0
                            && param.args[0] instanceof String) {
                        param.setResult("CN".equalsIgnoreCase((String) param.args[0]));
                    }
                }
            });
            log("hooked miui.os.Build.checkRegion");
        } catch (Throwable e) {
            log("checkRegion hook failed: " + e.getClass().getSimpleName());
        }
    }

    private static void setStaticBoolean(Class<?> cls, String fieldName, boolean value) {
        try {
            XposedHelpers.setStaticBooleanField(cls, fieldName, value);
            log(fieldName + " -> " + value);
        } catch (Throwable e) {
            log(fieldName + " set failed: " + e.getClass().getSimpleName());
        }
    }

    /**
     * Present the CN mondrian/K60 device identity to Contacts without changing
     * the actual system-wide Build values. We deliberately keep Android/HyperOS
     * version fields untouched.
     */
    private static void hookAndroidBuild() {
        try {
            Class<?> build = Class.forName("android.os.Build", false,
                    ClassLoader.getSystemClassLoader());
            setStaticString(build, "MODEL", "23013RK75C");
            setStaticString(build, "MANUFACTURER", "Xiaomi");
            setStaticString(build, "BRAND", "Redmi");
            setStaticString(build, "DEVICE", "mondrian");
            setStaticString(build, "PRODUCT", "mondrian");
            log("android.os.Build identity -> Redmi K60 / 23013RK75C / mondrian");
        } catch (Throwable e) {
            log("android.os.Build hook failed: " + e.getClass().getSimpleName());
        }
    }

    private static void setStaticString(Class<?> cls, String fieldName, String value) {
        try {
            XposedHelpers.setStaticObjectField(cls, fieldName, value);
            log(fieldName + " -> " + value);
        } catch (Throwable e) {
            log(fieldName + " set failed: " + e.getClass().getSimpleName());
        }
    }

    private static void hookSystemProperties() {
        final Class<?> sp;
        try {
            sp = Class.forName("android.os.SystemProperties", false,
                    ClassLoader.getSystemClassLoader());
        } catch (Throwable e) {
            log("SystemProperties class failed: " + e.getClass().getSimpleName());
            return;
        }

        hookGet(sp, "get");
        hookGetWithDefault(sp, "get");
        hookGetBoolean(sp, "getBoolean");
    }

    private static void hookGet(Class<?> sp, String methodName) {
        try {
            XposedHelpers.findAndHookMethod(sp, methodName, String.class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            if (param.hasThrowable()) return;
                            String value = cnProperty((String) param.args[0],
                                    (String) param.getResult());
                            if (value != null) param.setResult(value);
                        }
                    });
            log("hooked android.os.SystemProperties.get(String)");
        } catch (Throwable e) {
            log("get(String) hook failed: " + e.getClass().getSimpleName());
        }
    }

    private static void hookGetWithDefault(Class<?> sp, String methodName) {
        try {
            XposedHelpers.findAndHookMethod(sp, methodName, String.class, String.class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            if (param.hasThrowable()) return;
                            String value = cnProperty((String) param.args[0],
                                    (String) param.getResult());
                            if (value != null) param.setResult(value);
                        }
                    });
            log("hooked android.os.SystemProperties.get(String,String)");
        } catch (Throwable e) {
            log("get(String,String) hook failed: " + e.getClass().getSimpleName());
        }
    }

    private static void hookGetBoolean(Class<?> sp, String methodName) {
        try {
            XposedHelpers.findAndHookMethod(sp, methodName, String.class, boolean.class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            if (param.hasThrowable()) return;
                            String key = (String) param.args[0];
                            if ("ro.miui.is_international_build".equals(key)
                                    || "ro.miui.is_global_build".equals(key)) {
                                param.setResult(false);
                            }
                        }
                    });
            log("hooked android.os.SystemProperties.getBoolean");
        } catch (Throwable e) {
            log("getBoolean hook failed: " + e.getClass().getSimpleName());
        }
    }

    private static String cnProperty(String key, String original) {
        if (key == null) return null;

        switch (key) {
            case "ro.miui.region":
            case "ro.product.locale.region":
            case "ro.miui.build.region":
            case "ro.miui.customized.region":
                return "CN";

            case "ro.product.locale":
                return "zh-CN";

            case "ro.product.model":
            case "ro.product.odm.model":
            case "ro.product.odm.cert":
                return "23013RK75C";

            case "ro.product.marketname":
                return "Redmi K60";

            case "ro.product.name":
                return "mondrian";

            case "ro.product.mod_device":
                return "mondrian";

            case "ro.product.brand":
                return "Redmi";

            case "ro.build.fingerprint":
            case "ro.odm.build.fingerprint":
                return "Redmi/mondrian/mondrian:12/SKQ1.230401.001/OS3.0.4.0.VMNCNXM:user/release-keys";

            case "ro.build.description":
                return "missi-user 15 AQ3A.250226.002 OS3.0.4.0.VMNCNXM release-keys";

            case "ro.miui.is_international_build":
            case "ro.miui.is_global_build":
                return "0";

            default:
                return null;
        }
    }
}
