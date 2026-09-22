package com.microllate.miuyellowpage;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * Makes only SecurityCenter see a CN Xiaomi/MIUI environment.
 * Hardware model identity is preserved; only regional/build classification is changed.
 */
public final class SecurityCenterChinaEnvironmentHook implements IXposedHookLoadPackage {
    private static final String PACKAGE = "com.miui.securitycenter";
    private static final String TAG = "miu-iYellowPage";

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        if (!PACKAGE.equals(lpparam.packageName)) return;

        try {
            hookMiuiBuild(lpparam.classLoader);
            hookSystemProperties();
            log("installed");
        } catch (Throwable e) {
            log("install failed: " + e.getClass().getSimpleName()
                    + ": " + e.getMessage());
        }
    }

    private static void hookMiuiBuild(ClassLoader cl) {
        try {
            Class<?> build = Class.forName("miui.os.Build", false, cl);

            setStaticBoolean(build, "IS_INTERNATIONAL_BUILD", false);
            setStaticBoolean(build, "IS_GLOBAL_BUILD", false);
            setStaticBoolean(build, "IS_CM_CUSTOMIZATION", false);
            setStaticBoolean(build, "IS_CU_CUSTOMIZATION_TEST", false);

            try {
                XposedHelpers.findAndHookMethod(build, "getRegion",
                        new XC_MethodHook() {
                            @Override
                            protected void afterHookedMethod(MethodHookParam param) {
                                if (!param.hasThrowable()) param.setResult("CN");
                            }
                        });
            } catch (Throwable e) {
                log("getRegion hook failed: " + e.getClass().getSimpleName());
            }

            try {
                XposedHelpers.findAndHookMethod(build, "checkRegion",
                        String.class, new XC_MethodHook() {
                            @Override
                            protected void afterHookedMethod(MethodHookParam param) {
                                if (param.hasThrowable()) return;
                                Object arg = param.args != null && param.args.length > 0
                                        ? param.args[0] : null;
                                param.setResult(arg instanceof String
                                        && "CN".equalsIgnoreCase((String) arg));
                            }
                        });
            } catch (Throwable e) {
                log("checkRegion hook failed: " + e.getClass().getSimpleName());
            }

            log("MIUI Build flags -> INTERNATIONAL=false GLOBAL=false");
        } catch (Throwable e) {
            log("miui.os.Build hook failed: " + e.getClass().getSimpleName());
        }
    }

    private static void setStaticBoolean(Class<?> cls, String field, boolean value) {
        try {
            XposedHelpers.setStaticBooleanField(cls, field, value);
        } catch (Throwable e) {
            log(field + " -> " + value + " failed: "
                    + e.getClass().getSimpleName());
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

        hookGet(sp, String.class);
        hookGet(sp, String.class, String.class);
        hookGetBoolean(sp);
    }

    private static void hookGet(Class<?> sp, Class<?>... parameterTypes) {
        try {
            XposedHelpers.findAndHookMethod(sp, "get", parameterTypes,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            if (param.hasThrowable() || param.args == null
                                    || param.args.length == 0
                                    || !(param.args[0] instanceof String)) return;

                            String value = cnProperty((String) param.args[0]);
                            if (value != null) param.setResult(value);
                        }
                    });
        } catch (Throwable e) {
            log("SystemProperties.get hook failed: "
                    + e.getClass().getSimpleName());
        }
    }

    private static void hookGetBoolean(Class<?> sp) {
        try {
            XposedHelpers.findAndHookMethod(sp, "getBoolean",
                    String.class, boolean.class, new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            if (param.hasThrowable() || param.args == null
                                    || param.args.length == 0) return;

                            String key = String.valueOf(param.args[0]);
                            if ("ro.miui.is_international_build".equals(key)
                                    || "ro.miui.is_global_build".equals(key)) {
                                param.setResult(false);
                            }
                        }
                    });
        } catch (Throwable e) {
            log("SystemProperties.getBoolean hook failed: "
                    + e.getClass().getSimpleName());
        }
    }

    private static String cnProperty(String key) {
        switch (key) {
            case "ro.miui.region":
            case "ro.miui.build.region":
            case "ro.miui.customized.region":
            case "ro.product.locale.region":
            case "ro.product.country.region":
                return "CN";
            case "ro.product.locale":
                return "zh-CN";
            case "ro.miui.is_international_build":
            case "ro.miui.is_global_build":
                return "0";
            default:
                return null;
        }
    }

    private static void log(String message) {
        try {
            XposedBridge.log(TAG + ": SECURITYCENTER CN ENV: " + message);
        } catch (Throwable ignored) {
        }
    }
}
