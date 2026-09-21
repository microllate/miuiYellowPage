package com.microllate.miuyellowpage;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import java.lang.reflect.Method;
import java.util.List;
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
        Log.i(TAG, "OFFICIAL IMPORT FIX: " + s);
        try { XposedBridge.log(TAG + ": OFFICIAL IMPORT FIX: " + s); } catch (Throwable ignored) {}
    }

    private void importOfficial(Context context, String reason) {
        if (context == null) return;
        Context app = context.getApplicationContext();
        if (app == null) app = context;

        java.io.File dataFile = new java.io.File(app.getFilesDir(), "yellowpage/yellow_pages.dat");
        if (!dataFile.isFile() || dataFile.length() <= 0) {
            log("skip: preset missing/empty reason=" + reason + " path=" + dataFile.getAbsolutePath());
            return;
        }
        if (!importing.compareAndSet(false, true)) {
            log("skip: import already running reason=" + reason);
            return;
        }

        try {
            ClassLoader cl = app.getClassLoader();

            // Current APK: N() uses s0.c.s() -> s0.a.q(Context) -> File.exists().
            // Hook the actual gate from the currently supplied APK, not r0.a.
            try {
                Class<?> gateClass = Class.forName("s0.a", false, cl);
                Method q = gateClass.getDeclaredMethod("q", Context.class);
                XposedBridge.hookMethod(q, new XC_MethodHook() {
                    @Override protected void afterHookedMethod(MethodHookParam p) {
                        if (!p.hasThrowable()) {
                            boolean old = Boolean.TRUE.equals(p.getResult());
                            log("PRESET GATE s0.a.q(Context): " + old + " -> true");
                            p.setResult(true);
                        }
                    }
                });
                log("PRESET GATE HOOKED: s0.a.q(Context)");
            } catch (Throwable e) {
                log("PRESET GATE HOOK FAILED: " + e.getClass().getName() + ": " + e.getMessage());
            }

            hookParser(cl);
            hookInsertHelper(cl);
            hookYellowPageInsert(cl);

            Class<?> helperClass = Class.forName(
                    "com.miui.yellowpage.providers.yellowpage.YellowPageDatabaseHelper", false, cl);
            Method getInstance = helperClass.getDeclaredMethod("E", Context.class);
            getInstance.setAccessible(true);
            Object helper = getInstance.invoke(null, app);
            if (helper == null) {
                log("FAILED: helper E() returned null");
                return;
            }

            Method getWritable = helperClass.getMethod("getWritableDatabase");
            SQLiteDatabase db = (SQLiteDatabase) getWritable.invoke(helper);
            long beforeYellow = count(db, "yellow_page");
            long beforePhone = count(db, "phone_lookup");

            log("ENTER N(): reason=" + reason + " file=" + dataFile.getAbsolutePath()
                    + " bytes=" + dataFile.length()
                    + " before yellow_page=" + beforeYellow + " phone_lookup=" + beforePhone);

            Method n = helperClass.getDeclaredMethod("N", Context.class, SQLiteDatabase.class);
            n.setAccessible(true);
            n.invoke(helper, app, db);

            long afterYellow = count(db, "yellow_page");
            long afterPhone = count(db, "phone_lookup");
            log("EXIT N(): yellow_page=" + afterYellow + " phone_lookup=" + afterPhone
                    + " deltaYellow=" + (afterYellow - beforeYellow)
                    + " deltaPhone=" + (afterPhone - beforePhone));
        } catch (Throwable e) {
            Throwable c = e.getCause() != null ? e.getCause() : e;
            log("FAILED N(): " + c.getClass().getName() + ": " + c.getMessage());
        } finally {
            importing.set(false);
        }
    }

    private void hookParser(ClassLoader cl) {
        try {
            Class<?> yp = Class.forName("miui.yellowpage.YellowPage", false, cl);
            Method m = yp.getDeclaredMethod("fromJson", String.class);
            XposedBridge.hookMethod(m, new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) {
                    String s = p.args != null && p.args.length > 0 && p.args[0] instanceof String
                            ? (String)p.args[0] : null;
                    if (s == null || s.length() == 0) {
                        log("fromJson INPUT empty");
                    } else {
                        String sample = s.length() > 180 ? s.substring(0, 180) : s;
                        log("fromJson INPUT len=" + s.length() + " sample=" + sample);
                    }
                }
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    if (p.hasThrowable()) {
                        Throwable t = p.getThrowable();
                        log("fromJson THROW: " + t.getClass().getName() + ": " + t.getMessage());
                    } else {
                        log("fromJson RESULT: " + (p.getResult() == null ? "null" : "object"));
                    }
                }
            });
            log("hooked miui.yellowpage.YellowPage.fromJson(String)");
        } catch (Throwable e) {
            log("fromJson hook failed: " + e.getClass().getName() + ": " + e.getMessage());
        }
    }

    private void hookInsertHelper(ClassLoader cl) {
        try {
            Class<?> yp = Class.forName("miui.yellowpage.YellowPage", false, cl);
            Class<?> g = Class.forName("g0.g", false, cl);
            Method m = g.getDeclaredMethod("f", SQLiteDatabase.class, yp);
            XposedBridge.hookMethod(m, new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    if (p.hasThrowable()) {
                        Throwable t = p.getThrowable();
                        log("g0.g.f THROW: " + t.getClass().getName() + ": " + t.getMessage());
                    } else {
                        Object r = p.getResult();
                        log("g0.g.f RESULT: " + (r instanceof List ? "listSize=" + ((List<?>)r).size() : String.valueOf(r)));
                    }
                }
            });
            log("hooked g0.g.f(SQLiteDatabase,YellowPage)");
        } catch (Throwable e) {
            log("g0.g.f hook failed: " + e.getClass().getName() + ": " + e.getMessage());
        }
    }

    private void hookYellowPageInsert(ClassLoader cl) {
        try {
            XposedHelpers.findAndHookMethod(SQLiteDatabase.class, "insertWithOnConflict",
                    String.class, String.class, android.content.ContentValues.class, int.class,
                    new XC_MethodHook() {
                        @Override protected void beforeHookedMethod(MethodHookParam p) {
                            if (p.args != null && p.args.length >= 3 && "yellow_page".equals(p.args[0])) {
                                android.content.ContentValues v = (android.content.ContentValues)p.args[2];
                                log("DB INSERT yellow_page values=" + String.valueOf(v));
                            }
                        }
                        @Override protected void afterHookedMethod(MethodHookParam p) {
                            if (p.args != null && p.args.length >= 1 && "yellow_page".equals(p.args[0])) {
                                log("DB INSERT yellow_page RESULT=" + p.getResult());
                            }
                        }
                    });
            log("hooked SQLiteDatabase.insertWithOnConflict(yellow_page)");
        } catch (Throwable e) {
            log("SQLite insert hook failed: " + e.getClass().getName() + ": " + e.getMessage());
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


    /**
     * Make the YellowPage process see a mainland-CN MIUI environment.
     *
     * Important: do this before any YellowPage classes that cache Build/SystemProperties
     * values are initialized. We only spoof region/build-flavor properties; Android
     * SDK/device identity is left untouched so normal platform compatibility checks
     * keep using the real device.
     */
    private void hookChinaEnvironment(ClassLoader cl) {
        // miui.os.Build.IS_INTERNATIONAL_BUILD is a static final field used directly
        // by many YellowPage classes, so changing only getRegion() is insufficient.
        try {
            Class<?> build = Class.forName("miui.os.Build", false, cl);
            try {
                XposedHelpers.setStaticBooleanField(build, "IS_INTERNATIONAL_BUILD", false);
                log("CN ENV: miui.os.Build.IS_INTERNATIONAL_BUILD -> false");
            } catch (Throwable e) {
                log("CN ENV: set IS_INTERNATIONAL_BUILD failed: " + e.getClass().getName()
                        + ": " + e.getMessage());
            }

            try {
                Method getRegion = build.getDeclaredMethod("getRegion");
                XposedBridge.hookMethod(getRegion, new XC_MethodHook() {
                    @Override protected void afterHookedMethod(MethodHookParam p) {
                        if (!p.hasThrowable()) p.setResult("CN");
                    }
                });
                log("CN ENV: hooked miui.os.Build.getRegion() -> CN");
            } catch (Throwable e) {
                log("CN ENV: getRegion hook failed: " + e.getClass().getName()
                        + ": " + e.getMessage());
            }

            try {
                Method checkRegion = build.getDeclaredMethod("checkRegion", String.class);
                XposedBridge.hookMethod(checkRegion, new XC_MethodHook() {
                    @Override protected void afterHookedMethod(MethodHookParam p) {
                        if (p.hasThrowable()) return;
                        Object arg = p.args != null && p.args.length > 0 ? p.args[0] : null;
                        if (arg instanceof String) {
                            p.setResult("CN".equalsIgnoreCase((String) arg));
                        }
                    }
                });
                log("CN ENV: hooked miui.os.Build.checkRegion(String)");
            } catch (Throwable e) {
                log("CN ENV: checkRegion hook failed: " + e.getClass().getName()
                        + ": " + e.getMessage());
            }
        } catch (Throwable e) {
            log("CN ENV: miui.os.Build unavailable: " + e.getClass().getName()
                    + ": " + e.getMessage());
        }

        // K60 CN identity. On global firmware the same hardware is commonly exposed as
        // POCO F5 Pro; YellowPage may branch on Build.* identity as well as region.
        try {
            Class<?> androidBuild = Class.forName("android.os.Build", false, ClassLoader.getSystemClassLoader());
            setStaticString(androidBuild, "MODEL", "23013PC75C");
            setStaticString(androidBuild, "DEVICE", "mondrian");
            setStaticString(androidBuild, "PRODUCT", "mondrian");
            setStaticString(androidBuild, "BRAND", "Redmi");
            setStaticString(androidBuild, "MANUFACTURER", "Xiaomi");
            setStaticString(androidBuild, "DISPLAY", "V14.0.0.0.TMNCNXM");
            log("CN ENV: Android Build identity -> Redmi K60 / mondrian / 23013PC75C");
        } catch (Throwable e) {
            log("CN ENV: android.os.Build identity hook failed: " + e.getClass().getName()
                    + ": " + e.getMessage());
        }

        hookSystemProperties(ClassLoader.getSystemClassLoader(), "android.os.SystemProperties");
        hookSystemProperties(cl, "miuix.core.util.SystemProperties");
        hookSystemProperties(cl, "miui.cloud.os.SystemProperties");
    }

    private void setStaticString(Class<?> cls, String field, String value) {
        try {
            XposedHelpers.setStaticObjectField(cls, field, value);
            log("CN ENV: Build." + field + " -> " + value);
        } catch (Throwable e) {
            log("CN ENV: Build." + field + " failed: " + e.getClass().getName());
        }
    }

    private void hookSystemProperties(ClassLoader loader, String className) {
        try {
            Class<?> sp = Class.forName(className, false, loader);

            try {
                XposedHelpers.findAndHookMethod(sp, "get", String.class, new XC_MethodHook() {
                    @Override protected void afterHookedMethod(MethodHookParam p) {
                        if (!p.hasThrowable()) {
                            String key = (String) p.args[0];
                            String value = cnProperty(key, (String) p.getResult());
                            if (value != null) p.setResult(value);
                        }
                    }
                });
            } catch (Throwable ignored) {}

            try {
                XposedHelpers.findAndHookMethod(sp, "get", String.class, String.class,
                        new XC_MethodHook() {
                            @Override protected void afterHookedMethod(MethodHookParam p) {
                                if (!p.hasThrowable()) {
                                    String key = (String) p.args[0];
                                    String value = cnProperty(key, (String) p.getResult());
                                    if (value != null) p.setResult(value);
                                }
                            }
                        });
            } catch (Throwable ignored) {}

            try {
                XposedHelpers.findAndHookMethod(sp, "getBoolean", String.class, boolean.class,
                        new XC_MethodHook() {
                            @Override protected void afterHookedMethod(MethodHookParam p) {
                                if (!p.hasThrowable() && isChinaBooleanKey((String) p.args[0])) {
                                    p.setResult(false);
                                }
                            }
                        });
            } catch (Throwable ignored) {}

            try {
                XposedHelpers.findAndHookMethod(sp, "getInt", String.class, int.class,
                        new XC_MethodHook() {
                            @Override protected void afterHookedMethod(MethodHookParam p) {
                                // ro.miui.ui.version.code is deliberately left unchanged.
                                // It is an OS-version compatibility value, not a region flag.
                            }
                        });
            } catch (Throwable ignored) {}

            log("CN ENV: hooked " + className);
        } catch (Throwable e) {
            log("CN ENV: " + className + " unavailable: " + e.getClass().getName());
        }
    }

    private boolean isChinaBooleanKey(String key) {
        return "ro.miui.is_international_build".equals(key)
                || "ro.miui.is_global_build".equals(key);
    }

    private String cnProperty(String key, String original) {
        if (key == null) return null;
        if ("ro.miui.region".equals(key)
                || "ro.product.locale.region".equals(key)
                || "ro.miui.build.region".equals(key)) {
            return "CN";
        }
        if ("ro.product.locale".equals(key)) {
            return "zh-CN";
        }
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
                        Context c = (Context)XposedHelpers.callMethod(p.thisObject, "getContext");
                        log("Provider.onCreate -> checking official data import");
                        importOfficial(c, "provider_onCreate");
                    } catch (Throwable e) {
                        log("Provider.onCreate hook failed: " + e.getClass().getName() + ": " + e.getMessage());
                    }
                }
            });
            log("hooked YellowPageProvider.onCreate()");
        } catch (Throwable e) {
            log("Provider hook failed: " + e.getClass().getName() + ": " + e.getMessage());
        }
    }

    private void hookDownload(ClassLoader cl) {
        try {
            Class<?> pull = Class.forName("p0.d", false, cl);
            Method n = pull.getDeclaredMethod("n", Context.class, org.json.JSONObject.class);
            XposedBridge.hookMethod(n, new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    if (p.hasThrowable()) {
                        Throwable t = p.getThrowable();
                        log("p0.d.n THROW: " + t.getClass().getName() + ": " + t.getMessage());
                        return;
                    }
                    Context c = p.args != null && p.args.length > 0 && p.args[0] instanceof Context
                            ? (Context)p.args[0] : null;
                    importOfficial(c, "p0.d.n_after_download");
                }
            });
            log("hooked p0.d.n(Context,JSONObject) -> official N()");
        } catch (Throwable e) {
            log("p0.d.n hook failed: " + e.getClass().getName() + ": " + e.getMessage());
        }
    }

    private void hookReleaseTask(ClassLoader cl) {
        try {
            Class<?> release = Class.forName("i0.c", false, cl);
            Method b = release.getDeclaredMethod("b", Context.class, SQLiteDatabase.class);
            XposedBridge.hookMethod(b, new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    Context c = p.args != null && p.args.length > 0 && p.args[0] instanceof Context
                            ? (Context)p.args[0] : null;
                    importOfficial(c, "YellowPageReleaseTask_after");
                }
            });
            log("hooked i0.c.b(Context,SQLiteDatabase)");
        } catch (Throwable e) {
            log("release task hook failed: " + e.getClass().getName() + ": " + e.getMessage());
        }
    }

    @Override public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        if (!PACKAGE.equals(lpparam.packageName)) return;
        log("loaded");
        hookChinaEnvironment(lpparam.classLoader);
        hookProvider(lpparam.classLoader);
        hookDownload(lpparam.classLoader);
        hookReleaseTask(lpparam.classLoader);
    }
}
