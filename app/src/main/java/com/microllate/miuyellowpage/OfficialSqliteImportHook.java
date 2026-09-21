package com.microllate.miuyellowpage;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import org.json.JSONObject;

import java.io.File;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * Uses the YellowPage APK's own importer instead of reimplementing its
 * yellow_pages.dat parser.
 *
 * Official flow:
 *   downloaded yellow_pages.dat
 *       -> YellowPageReleaseTask / p0.d.n()
 *       -> YellowPageDatabaseHelper.N()
 *       -> YellowPage.fromJson()
 *       -> yellow_page
 *       -> g0.g.f()
 *       -> phone_lookup
 *
 * EEA normally does not schedule/execute the release task reliably, so this
 * hook calls the exact official N() method after a successful data download
 * and once at Provider startup when the file is already present.
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
            Method n = pull.getDeclaredMethod(
                    "n", Context.class, JSONObject.class);

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

                    // n() returns only after q() has committed the downloaded
                    // file to files/yellowpage/yellow_pages.dat.
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
