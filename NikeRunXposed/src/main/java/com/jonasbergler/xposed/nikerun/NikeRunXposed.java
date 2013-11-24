package com.jonasbergler.xposed.nikerun;

/**
 * Created on 23/11/13 by Jonas Bergler (jonas@bergler.name).
 */

import android.content.Context;
import android.content.Intent;

import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

import static de.robv.android.xposed.XposedHelpers.callMethod;
import static de.robv.android.xposed.XposedHelpers.getObjectField;
import static de.robv.android.xposed.XposedHelpers.getFloatField;
import static de.robv.android.xposed.XposedHelpers.getDoubleField;
import static de.robv.android.xposed.XposedHelpers.getBooleanField;
import static de.robv.android.xposed.XposedHelpers.findAndHookMethod;

public class NikeRunXposed implements IXposedHookLoadPackage {

    private long lastUpdated = 0;

    public void handleLoadPackage(LoadPackageParam lpparam) throws Throwable {
        if (!lpparam.packageName.equals("com.nike.plusgps"))
            return;
        else
            XposedBridge.log("NikeRun: NikeRunning load detected.");


        findAndHookMethod("com.nike.plusgps.RunActivity", lpparam.classLoader, "startRun",new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {

                Context context = (Context) callMethod(param.thisObject, "getApplicationContext");
                Intent intent = new Intent();
                intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
                intent.setAction(NikeRun.INTENT_START);
                context.sendBroadcast(intent);

                XposedBridge.log("NikeRun: Run Started");
            }
        });

        findAndHookMethod("com.nike.plusgps.RunActivity", lpparam.classLoader, "endRun",new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {

                Context context = (Context) callMethod(param.thisObject, "getApplicationContext");
                Intent intent = new Intent();
                intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
                intent.setAction(NikeRun.INTENT_STOP);
                context.sendBroadcast(intent);

                XposedBridge.log("NikeRun: Run Finished");
            }
        });

        findAndHookMethod("com.nike.plusgps.RunActivity", lpparam.classLoader, "cancelRun",new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {

                Context context = (Context) callMethod(param.thisObject, "getApplicationContext");
                Intent intent = new Intent();
                intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
                intent.setAction(NikeRun.INTENT_STOP);
                context.sendBroadcast(intent);

                XposedBridge.log("NikeRun: Run Cancelled");
            }
        });

        findAndHookMethod("com.nike.plusgps.RunActivity", lpparam.classLoader, "pauseRun",new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {

                boolean paused = (boolean) getBooleanField((Object) param.thisObject, "isRunPaused");

                Context context = (Context) callMethod(param.thisObject, "getApplicationContext");
                Intent intent = new Intent();
                intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
                intent.setAction(NikeRun.INTENT_PAUSE);
                context.sendBroadcast(intent);

                XposedBridge.log("NikeRun: Run Paused => " + paused);
            }
        });

        findAndHookMethod("com.nike.plusgps.RunActivity", lpparam.classLoader, "resumeRun",new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {

                Context context = (Context) callMethod(param.thisObject, "getApplicationContext");
                Intent intent = new Intent();
                intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
                intent.setAction(NikeRun.INTENT_RESUME);
                context.sendBroadcast(intent);

                XposedBridge.log("NikeRun: Run Resumed");
            }
        });

        findAndHookMethod("com.nike.plusgps.RunActivity", lpparam.classLoader, "updateScreenInfo", double.class, float.class, float.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {

                Object run = (Object) getObjectField(param.thisObject, "currentRun");
                float distanceRaw = (float) getFloatField(((Object) callMethod(run, "getDistanceUnitValue")), "value");
                float durationRaw = (float) getFloatField(((Object) callMethod(run, "getDurationUnitValue")), "value");
                double paceRaw = (double) getDoubleField(run, "currentPace");

                String distance = String.format("%.2f", distanceRaw);
                String duration = String.format("%.0f", durationRaw / 1000);
                String pace = String.format("%.2f", paceRaw);

//                XposedBridge.log("NikeRun: updateScreenInfo()");
//                XposedBridge.log("NikeRun: distance=" + distanceRaw + " / " + distance);
//                XposedBridge.log("NikeRun: duration=" + durationRaw + " / " + duration);
//                XposedBridge.log("NikeRun: pace=" + paceRaw + " / " + pace);

                Context context = (Context) callMethod(param.thisObject, "getApplicationContext");
                Intent intent = new Intent();
                intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
                intent.setAction(NikeRun.INTENT_UPDATE);
                intent.putExtra(NikeRun.DATA_DURATION, duration);
                intent.putExtra(NikeRun.DATA_DISTANCE, distance);
                intent.putExtra(NikeRun.DATA_PACE, pace);
                context.sendBroadcast(intent);

                lastUpdated = System.currentTimeMillis();

                XposedBridge.log("NikeRun: Data Update");
            }
        });


    } // END handleLoadPackage
}
