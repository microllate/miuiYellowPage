package com.microllate.miuyellowpage;

import java.util.ArrayList;
import java.util.List;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * Keeps Game Turbo on the CN/normal Q6.k.U() path.
 *
 * The normal path can return plain model.n instances with NORMAL/NONE states,
 * while Z3.J expects every non-HOT entry to be model.m (COMPOSITE) and calls
 * m.w() on it.  This adapter groups the non-HOT plain n entries into one m
 * without changing IS_INTERNATIONAL_BUILD or redirecting U() to Z2.g.i().
 */
public final class GameTurboNormalModelCompatHook implements IXposedHookLoadPackage {
    private static final String PACKAGE = "com.miui.securitycenter";
    private static final String TAG = "miu-iYellowPage";

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        if (!PACKAGE.equals(lpparam.packageName)) return;

        try {
            install(lpparam.classLoader);
            log("GameTurbo normal-model compatibility installed");
        } catch (Throwable e) {
            log("GameTurbo normal-model compatibility failed: "
                    + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private static void install(ClassLoader cl) {
        final Class<?> kClass = XposedHelpers.findClass("Q6.k", cl);
        final Class<?> nClass = XposedHelpers.findClass(
                "com.miui.gamebooster.model.n", cl);
        final Class<?> mClass = XposedHelpers.findClass(
                "com.miui.gamebooster.model.m", cl);

        XposedHelpers.findAndHookMethod(
                kClass,
                "U",
                String.class,
                int.class,
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        if (param.hasThrowable()) return;

                        Object result = param.getResult();
                        if (!(result instanceof List)) return;

                        List<?> source = (List<?>) result;
                        if (source.isEmpty()) return;

                        ArrayList<Object> nonHot = new ArrayList<>();
                        int firstNonHot = -1;
                        Object existingComposite = null;

                        for (int i = 0; i < source.size(); i++) {
                            Object item = source.get(i);
                            if (!nClass.isInstance(item)) continue;

                            Object state;
                            try {
                                state = XposedHelpers.callMethod(item, "j");
                            } catch (Throwable ignored) {
                                continue;
                            }

                            if ("HOT".equals(String.valueOf(state))) continue;

                            if (mClass.isInstance(item)) {
                                if (existingComposite == null) {
                                    existingComposite = item;
                                    if (firstNonHot < 0) firstNonHot = i;
                                }
                                continue;
                            }

                            if (firstNonHot < 0) firstNonHot = i;
                            nonHot.add(item);
                        }

                        if (nonHot.isEmpty()) return;

                        try {
                            Object composite = existingComposite;
                            if (composite == null) {
                                composite = XposedHelpers.newInstance(mClass);
                            }

                            for (Object item : nonHot) {
                                XposedHelpers.callMethod(composite, "v", item);
                            }

                            ArrayList<Object> adapted = new ArrayList<>();
                            boolean inserted = false;

                            for (Object item : source) {
                                if (nonHot.contains(item)) {
                                    if (!inserted) {
                                        adapted.add(composite);
                                        inserted = true;
                                    }
                                } else {
                                    adapted.add(item);
                                }
                            }

                            if (!inserted && firstNonHot >= 0) {
                                int index = Math.min(firstNonHot, adapted.size());
                                adapted.add(index, composite);
                            }

                            param.setResult(adapted);

                            log("U() normal-model adapter: source="
                                    + source.size()
                                    + " nonHot=" + nonHot.size()
                                    + " result=" + adapted.size()
                                    + " compositeChildren=" + nonHot.size());
                        } catch (Throwable e) {
                            log("U() normal-model adapter failed: "
                                    + e.getClass().getSimpleName() + ": "
                                    + e.getMessage());
                        }
                    }
                });
    }

    private static void log(String message) {
        try {
            XposedBridge.log(TAG + ": GAME TURBO CN MODEL: " + message);
        } catch (Throwable ignored) {
        }
    }
}
