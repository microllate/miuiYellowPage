package com.microllate.miuyellowpage;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import org.json.JSONObject;

import java.io.File;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * Calls the real YellowPageDatabaseHelper.N() after bypassing the preset gate.
 *
 * This APK has multiple generations of the obfuscated r0.a base class:
 * older decompilations expose l(Context), while the current EEA APK exposes
 * k(Context). Therefore hook every matching boolean Context gate on r0.a and
 * report the exact method names found, rather than assuming l().
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

    private List<Method> findContextBooleanMethods(ClassLoader cl) throws ClassNotFoundException {
        Class<?> base = Class.forName("r0.a", false, cl);
        List<Method> result = new ArrayList<>();
        for (Method method : base.getDeclaredMethods()) {
            if (method.getReturnType() == Boolean.TYPE
                    && method.getParameterTypes().length == 1
                    && method.getParameterTypes()[0] == Context.class) {
                method.setAccessible(true);
                result.add(method);
            }
        }
        return result;
    }

    private void forceOfficialPresetGate(ClassLoader cl) {
        try {
            List<Method> gates = findContextBooleanMethods(cl);
            if (gates.isEmpty()) {
                log("PRESET GATE FAILED: r0.a has no boolean(Context) method");
                return;
            }

            for (final Method gate : gates) {
                XposedBridge.hookMethod(gate, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        if (!param.hasThrowable()) {
                            param.setResult(true);
                        }
                    }
                });
                log("PRESET GATE HOOKED: r0.a."
                        + gate.getName() + "(Context) -> true");
            }
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

            forceOfficialPresetGate(cl);

            Class<?> helperClass = Class.forName(
                    "com.miui.yellowpage.providers.yellowpage.YellowPageDatabaseHelper",
                    false, cl);

            Method getInstance = helperClass.getDeclaredMethod("E", Context.class);
            getInstance.setAccessible(true);
            Object helper = getInstance.invoke(null, app);
            if (helper == null) {
                log("FAILED: YellowPageDatabaseHelper.E() returned null");
                return;
            }

            Method getWritable = helperClass.getMethod("getWritableDatabase");
            getWritable.setAccessible(true);
            SQLiteDatabase db = (SQLiteDatabase) getWritable.invoke(helper);
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
