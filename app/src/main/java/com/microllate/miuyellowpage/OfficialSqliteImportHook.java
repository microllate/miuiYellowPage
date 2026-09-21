package com.microllate.miuyellowpage;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public final class OfficialSqliteImportHook implements IXposedHookLoadPackage {
    private static final String PACKAGE = "com.miui.yellowpage";
    private static final String TAG = "miu-iYellowPage";
    private final AtomicBoolean importing = new AtomicBoolean(false);

    private void log(String s) {
        String msg = TAG + ": OFFICIAL IMPORT: " + s;
        try {
            XposedBridge.log(msg);
        } catch (Throwable ignored) {
        }
    }

    private void importOfficial(Context context, String reason) {
        if (context == null) return;
        Context app = context.getApplicationContext();
        if (app == null) app = context;

        if (!importPresetData(app)) return;

        java.io.File dataFile = new java.io.File(app.getFilesDir(), "yellowpage/yellow_pages.dat");
        if (!dataFile.isFile() || dataFile.length() <= 0) {
            log("preset file missing: " + dataFile.getAbsolutePath());
            return;
        }
        if (!importing.compareAndSet(false, true)) return;

        try {
            ClassLoader cl = app.getClassLoader();
            Class<?> helperClass = Class.forName(
                    "com.miui.yellowpage.providers.yellowpage.YellowPageDatabaseHelper", false, cl);
            Method getInstance = helperClass.getDeclaredMethod("E", Context.class);
            getInstance.setAccessible(true);
            Object helper = getInstance.invoke(null, app);
            Method getWritable = helperClass.getMethod("getWritableDatabase");
            SQLiteDatabase db = (SQLiteDatabase) getWritable.invoke(helper);

            long beforeYellow = count(db, "yellow_page");
            long beforePhone = count(db, "phone_lookup");

            Method n = helperClass.getDeclaredMethod("N", Context.class, SQLiteDatabase.class);
            n.setAccessible(true);
            n.invoke(helper, app, db);

            long afterYellow = count(db, "yellow_page");
            long afterPhone = count(db, "phone_lookup");
            log("import " + reason + ": yellow_page " + beforeYellow + " -> " + afterYellow
                    + ", phone_lookup " + beforePhone + " -> " + afterPhone);
        } catch (Throwable e) {
            Throwable t = e.getCause() != null ? e.getCause() : e;
            log("N() failed: " + t.getClass().getSimpleName() + ": " + t.getMessage());
        } finally {
            importing.set(false);
        }
    }

    private boolean importPresetData(Context context) {
        try {
            ClassLoader cl = context.getClassLoader();
            Class<?> presetClass = Class.forName("s0.c", false, cl);
            Method singleton = presetClass.getDeclaredMethod("s");
            singleton.setAccessible(true);
            Object preset = singleton.invoke(null);

            Method k = presetClass.getMethod("k");
            int resId = ((Number) k.invoke(preset)).intValue();
            Method p = presetClass.getMethod("p", Context.class);
            Object result = p.invoke(preset, context);
            boolean ok = Boolean.TRUE.equals(result);

            Method d = presetClass.getMethod("d");
            Method f = presetClass.getMethod("f");
            java.io.File data = new java.io.File(
                    new java.io.File(context.getFilesDir(), String.valueOf(f.invoke(preset))),
                    String.valueOf(d.invoke(preset)));

            log("preset p(): resId=" + resId + ", ok=" + ok
                    + ", data=" + data.exists() + "/" + data.length());
            return ok;
        } catch (Throwable e) {
            Throwable t = e.getCause() != null ? e.getCause() : e;
            log("preset p() failed: " + t.getClass().getSimpleName() + ": " + t.getMessage());
            return false;
        }
    }

    private long count(SQLiteDatabase db, String table) {
        android.database.Cursor c = null;
        try {
            c = db.rawQuery("SELECT COUNT(*) FROM " + table, null);
            return c.moveToFirst() ? c.getLong(0) : -1L;
        } finally {
            if (c != null) c.close();
        }
    }

    private void hookChinaEnvironment(ClassLoader cl) {
        try {
            Class<?> build = Class.forName("miui.os.Build", false, cl);
            try {
                XposedHelpers.setStaticBooleanField(build, "IS_INTERNATIONAL_BUILD", false);
            } catch (Throwable ignored) {}

            try {
                Method getRegion = build.getDeclaredMethod("getRegion");
                XposedBridge.hookMethod(getRegion, new XC_MethodHook() {
                    @Override protected void afterHookedMethod(MethodHookParam p) {
                        if (!p.hasThrowable()) p.setResult("CN");
                    }
                });
            } catch (Throwable ignored) {}

            try {
                Method checkRegion = build.getDeclaredMethod("checkRegion", String.class);
                XposedBridge.hookMethod(checkRegion, new XC_MethodHook() {
                    @Override protected void afterHookedMethod(MethodHookParam p) {
                        if (!p.hasThrowable() && p.args.length > 0 && p.args[0] instanceof String) {
                            p.setResult("CN".equalsIgnoreCase((String) p.args[0]));
                        }
                    }
                });
            } catch (Throwable ignored) {}
        } catch (Throwable ignored) {}

        hookSystemProperties(ClassLoader.getSystemClassLoader(), "android.os.SystemProperties");
        hookSystemProperties(cl, "miuix.core.util.SystemProperties");
        hookSystemProperties(cl, "miui.cloud.os.SystemProperties");
    }

    private void hookSystemProperties(ClassLoader loader, String className) {
        try {
            Class<?> sp = Class.forName(className, false, loader);
            XposedHelpers.findAndHookMethod(sp, "get", String.class, new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    if (!p.hasThrowable()) {
                        String value = cnProperty((String) p.args[0], (String) p.getResult());
                        if (value != null) p.setResult(value);
                    }
                }
            });
            XposedHelpers.findAndHookMethod(sp, "get", String.class, String.class,
                    new XC_MethodHook() {
                        @Override protected void afterHookedMethod(MethodHookParam p) {
                            if (!p.hasThrowable()) {
                                String value = cnProperty((String) p.args[0], (String) p.getResult());
                                if (value != null) p.setResult(value);
                            }
                        }
                    });
            XposedHelpers.findAndHookMethod(sp, "getBoolean", String.class, boolean.class,
                    new XC_MethodHook() {
                        @Override protected void afterHookedMethod(MethodHookParam p) {
                            if (!p.hasThrowable() && isChinaBooleanKey((String) p.args[0])) {
                                p.setResult(false);
                            }
                        }
                    });
        } catch (Throwable ignored) {}
    }

    private boolean isChinaBooleanKey(String key) {
        return "ro.miui.is_international_build".equals(key)
                || "ro.miui.is_global_build".equals(key);
    }

    private String cnProperty(String key, String original) {
        if (key == null) return null;
        if ("ro.miui.region".equals(key)
                || "ro.product.locale.region".equals(key)
                || "ro.miui.build.region".equals(key)) return "CN";
        if ("ro.product.locale".equals(key)) return "zh-CN";
        if ("ro.product.mod_device".equals(key) && original != null) {
            return original.replaceFirst("(?i)_global$", "")
                    .replaceFirst("(?i)_eea$", "");
        }
        return null;
    }

    private void hookProvider(ClassLoader cl) {
        try {
            Class<?> provider = Class.forName(
                    "com.miui.yellowpage.providers.yellowpage.YellowPageProvider", false, cl);
            XposedHelpers.findAndHookMethod(provider, "onCreate", new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    try {
                        Context c = (Context) XposedHelpers.callMethod(p.thisObject, "getContext");
                        importOfficial(c, "provider");
                    } catch (Throwable e) {
                        log("provider hook failed: " + e.getClass().getSimpleName());
                    }
                }
            });
        } catch (Throwable ignored) {}
    }

    private void hookDownload(ClassLoader cl) {
        try {
            Class<?> pull = Class.forName("p0.d", false, cl);
            Method n = pull.getDeclaredMethod("n", Context.class, org.json.JSONObject.class);
            XposedBridge.hookMethod(n, new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    Context c = p.args != null && p.args.length > 0 && p.args[0] instanceof Context
                            ? (Context) p.args[0] : null;
                    importOfficial(c, "download");
                }
            });
        } catch (Throwable ignored) {}
    }

    private void hookReleaseTask(ClassLoader cl) {
        try {
            Class<?> release = Class.forName("i0.c", false, cl);
            Method b = release.getDeclaredMethod("b", Context.class, SQLiteDatabase.class);
            XposedBridge.hookMethod(b, new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    Context c = p.args != null && p.args.length > 0 && p.args[0] instanceof Context
                            ? (Context) p.args[0] : null;
                    importOfficial(c, "release");
                }
            });
        } catch (Throwable ignored) {}
    }

    @Override public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        if (!PACKAGE.equals(lpparam.packageName)) return;
        hookChinaEnvironment(lpparam.classLoader);
        hookProvider(lpparam.classLoader);
        hookDownload(lpparam.classLoader);
        hookReleaseTask(lpparam.classLoader);
    }
}
