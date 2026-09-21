package com.microllate.miuyellowpage;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import org.json.JSONObject;

import java.io.File;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.concurrent.atomic.AtomicBoolean;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * Calls the real YellowPageDatabaseHelper.N() after opening the EEA preset gate.
 *
 * N() first checks r0.c.n().l(context). On the international/EEA build this
 * returns false, so N() exits before it ever reads yellow_pages.dat.
 */
public final class OfficialSqliteImportHook implements IXposedHookLoadPackage {
    private static final String PACKAGE = "com.miui.yellowpage";
    private static final String TAG = "miu-iYellowPage";
    private final AtomicBoolean importing = new AtomicBoolean(false);

    private void log(String message) {
        Log.i(TAG, "OFFICIAL IMPORT FIX: " + message);
        try {
            XposedBridge.log(TAG + ": OFFICIAL IMPORT FIX: " + message);
        } catch (Throwable ignored) {
        }
    }

    /**
     * Do not use XposedHelpers.callStaticMethod("n") here.
     * On this obfuscated build that lookup can throw NoSuchMethodError even
     * though the zero-argument static method is present. Resolve it directly
     * from the loaded class and invoke it reflectively.
     */
    private Method findZeroArgStatic(Class<?> start, String name) {
        Class<?> owner = start;
        while (owner != null && owner != Object.class) {
            for (Method m : owner.getDeclaredMethods()) {
                if (name.equals(m.getName())
                        && m.getParameterTypes().length == 0
                        && Modifier.isStatic(m.getModifiers())) {
                    m.setAccessible(true);
                    return m;
                }
            }
            owner = owner.getSuperclass();
        }
        return null;
    }

    private Method findContextMethod(Class<?> start, String name) {
        Class<?> owner = start;
        while (owner != null && owner != Object.class) {
            for (Method m : owner.getDeclaredMethods()) {
                Class<?>[] p = m.getParameterTypes();
                if (name.equals(m.getName())
                        && p.length == 1
                        && Context.class.isAssignableFrom(p[0])) {
                    m.setAccessible(true);
                    return m;
                }
            }
            owner = owner.getSuperclass();
        }
        return null;
    }

    private void forceOfficialPresetGate(ClassLoader cl, Context context) {
        try {
            Class<?> preset = Class.forName("r0.c", false, cl);

            Method singletonFactory = findZeroArgStatic(preset, "n");
            if (singletonFactory == null) {
                log("PRESET GATE: r0.c zero-arg static n() not found; methods="
                        + preset.getDeclaredMethods().length);
                return;
            }

            Object singleton = singletonFactory.invoke(null);
            if (singleton == null) {
                log("PRESET GATE: r0.c.n() returned null");
                return;
            }

            Method gate = findContextMethod(preset, "l");
            if (gate == null) {
                log("PRESET GATE: r0.c.n().l(Context) not found");
                return;
            }

            XposedBridge.hookMethod(gate, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    if (!param.hasThrowable()) {
                        param.setResult(true);
                    }
                }
            });

            log("PRESET GATE HOOKED: " + gate.getDeclaringClass().getName()
                    + "." + gate.getName() + "(Context) -> true");

            Object result = gate.invoke(singleton, context);
            log("PRESET GATE VERIFY: r0.c.n().l(Context)=" + String.valueOf(result));
        } catch (Throwable e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            log("PRESET GATE FAILED: " + cause.getClass().getName()
                    + ": " + String.valueOf(cause.getMessage()));
        }
    }

    private void importOfficial(Context context, String reason) {
        if (context == null) return;
        Context app = context.getApplicationContext();
        if (app == null) app = context;

        File dataFile = new File(app.getFilesDir(), "yellowpage/yellow_pages.dat");
        if (!dataFile.isFile() || dataFile.length() <= 0) {
            log("skip: yellow_pages.dat missing/empty, reason=" + reason
                    + " path=" + dataFile.getAbsolutePath());
            return;
        }

        if (!importing.compareAndSet(false, true)) {
            log("skip: import already running, reason=" + reason);
            return;
        }

        try {
            ClassLoader cl = app.getClassLoader();

            forceOfficialPresetGate(cl, app);

            Class<?> helperClass = Class.forName(
                    "com.miui.yellowpage.providers.yellowpage.YellowPageDatabaseHelper",
                    false, cl);

            Object helper = XposedHelpers.callStaticMethod(helperClass, "E", app);
            if (helper == null) {
                log("FAILED: YellowPageDatabaseHelper.E() returned null");
                return;
            }

            SQLiteDatabase db = (SQLiteDatabase) XposedHelpers.callMethod(
                    helper, "getWritableDatabase");
            if (db == null) {
                log("FAILED: getWritableDatabase() returned null");
                return;
            }

            long beforeYellow = count(db, "yellow_page");
            long beforePhone = count(db, "phone_lookup");

            log("ENTER N(): reason=" + reason
                    + " file=" + dataFile.getAbsolutePath()
                    + " bytes=" + dataFile.length()
                    + " before yellow_page=" + beforeYellow
                    + " phone_lookup=" + beforePhone);

            Method n = helperClass.getDeclaredMethod(
                    "N", Context.class, SQLiteDatabase.class);
            n.setAccessible(true);
            n.invoke(helper, app, db);

            long afterYellow = count(db, "yellow_page");
            long afterPhone = count(db, "phone_lookup");

            log("EXIT N(): yellow_page=" + afterYellow
                    + " phone_lookup=" + afterPhone
                    + " deltaYellow=" + (afterYellow - beforeYellow)
                    + " deltaPhone=" + (afterPhone - beforePhone));
        } catch (Throwable e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            log("FAILED N(): " + cause.getClass().getName()
                    + ": " + String.valueOf(cause.getMessage()));
        } finally {
            importing.set(false);
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

    private void hookProvider(ClassLoader cl) {
        try {
            Class<?> provider = Class.forName(
                    "com.miui.yellowpage.providers.yellowpage.YellowPageProvider",
                    false, cl);

            XposedHelpers.findAndHookMethod(provider, "onCreate",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            try {
                                Context context = (Context) XposedHelpers.callMethod(
                                        param.thisObject, "getContext");
                                log("Provider.onCreate -> checking official data import");
                                importOfficial(context, "provider_onCreate");
                            } catch (Throwable e) {
                                log("Provider.onCreate hook failed: "
                                        + e.getClass().getName() + ": "
                                        + String.valueOf(e.getMessage()));
                            }
                        }
                    });

            log("hooked YellowPageProvider.onCreate()");
        } catch (Throwable e) {
            log("Provider hook failed: " + e.getClass().getName()
                    + ": " + String.valueOf(e.getMessage()));
        }
    }

    private void hookDownload(ClassLoader cl) {
        try {
            Class<?> pull = Class.forName("p0.d", false, cl);
            Method n = pull.getDeclaredMethod("n", Context.class, JSONObject.class);

            XposedBridge.hookMethod(n, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    if (param.hasThrowable()) {
                        Throwable t = param.getThrowable();
                        log("p0.d.n THROW: " + t.getClass().getName()
                                + ": " + String.valueOf(t.getMessage()));
                        return;
                    }

                    Context context = param.args != null
                            && param.args.length > 0
                            && param.args[0] instanceof Context
                            ? (Context) param.args[0] : null;
                    importOfficial(context, "p0.d.n_after_download");
                }
            });

            log("hooked p0.d.n(Context,JSONObject) -> official N()");
        } catch (Throwable e) {
            log("p0.d.n hook failed: " + e.getClass().getName()
                    + ": " + String.valueOf(e.getMessage()));
        }
    }

    private void hookReleaseTask(ClassLoader cl) {
        try {
            Class<?> release = Class.forName("i0.c", false, cl);
            Method b = release.getDeclaredMethod(
                    "b", Context.class, SQLiteDatabase.class);

            XposedBridge.hookMethod(b, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    Context context = param.args != null
                            && param.args.length > 0
                            && param.args[0] instanceof Context
                            ? (Context) param.args[0] : null;
                    importOfficial(context, "YellowPageReleaseTask_after");
                }
            });

            log("hooked i0.c.b(Context,SQLiteDatabase)");
        } catch (Throwable e) {
            log("release task hook failed: " + e.getClass().getName()
                    + ": " + String.valueOf(e.getMessage()));
        }
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        if (!PACKAGE.equals(lpparam.packageName)) return;

        log("loaded");
        hookProvider(lpparam.classLoader);
        hookDownload(lpparam.classLoader);
        hookReleaseTask(lpparam.classLoader);
    }
}
