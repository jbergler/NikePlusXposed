package com.jonasbergler.xposed.nikerun;

/**
 * Created on 23/11/13 by Jonas Bergler (jonas@bergler.name).
 */

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

import com.getpebble.android.kit.Constants;

import java.math.BigDecimal;

import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

import static de.robv.android.xposed.XposedHelpers.callMethod;
import static de.robv.android.xposed.XposedHelpers.getDoubleField;
import static de.robv.android.xposed.XposedHelpers.getObjectField;
import static de.robv.android.xposed.XposedHelpers.getFloatField;
import static de.robv.android.xposed.XposedHelpers.getBooleanField;
import static de.robv.android.xposed.XposedHelpers.findAndHookMethod;

public class NikeRunXposed implements IXposedHookLoadPackage {

    private final int PACE_MAGIC_NUM = 960;

    private static Object runControlInstance = null;

    private long lastUpdated = 0;
    private BroadcastReceiver mBroadcastReceiver = null;
    private boolean prefEnableLogging = true;

    private void log(String msg) {
        if (prefEnableLogging)
            XposedBridge.log(msg);
    }

    public void handleLoadPackage(LoadPackageParam lpparam) throws Throwable {
        if (!lpparam.packageName.equals("com.nike.plusgps")) {
            return;
        } else {
            XSharedPreferences savedPref = new XSharedPreferences(NikeRun.MY_PACKAGE);
            prefEnableLogging = savedPref.getBoolean("pref_enableLogging", true);
            log("NikeRun: NikeRunning load detected.");
        }

        findAndHookMethod("com.nike.plusgps.RunActivity", lpparam.classLoader, "startRun",new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {

                Context context = (Context) callMethod(param.thisObject, "getApplicationContext");
                Intent intent = new Intent();
                intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
                intent.setAction(NikeRun.INTENT_START);
                context.sendBroadcast(intent);

                log("NikeRun: Run Started");

                // Load saved preferences
                XSharedPreferences savedPref = new XSharedPreferences(NikeRun.MY_PACKAGE);
                boolean pauseOnLaunch = savedPref.getBoolean("pref_pauseRunOnStart", false);
                prefEnableLogging = savedPref.getBoolean("pref_enableLogging", true);
                log("Preferences loaded: PauseOnLaunch=" + pauseOnLaunch + ", EnableLogging=" + prefEnableLogging);

                // If run is to be paused on start then call pauseRun method
                if (pauseOnLaunch == true) {
                    Object runAct = (Object) getObjectField(param.thisObject, "runInfoAndControlView");
                    callMethod(runAct, "pauseRun");
                }
            }
        });

        findAndHookMethod("com.nike.plusgps.RunActivity", lpparam.classLoader, "onCreate", android.os.Bundle.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {

                // Register broadcast receiver in NikeRunXposed process to pause/resume run
                Object runAct = (Object) getObjectField(param.thisObject, "runInfoAndControlView");
                runControlInstance = runAct;
                Context context = (Context) callMethod(param.thisObject, "getApplicationContext");
                mBroadcastReceiver = new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context context, Intent intent) {

                        String intentExtras = (intent.getExtras() != null) ? intent.getExtras().toString() : "";
                        String xcmd = intent.getAction();
                        assert xcmd !=null;

                        log("Processing XCMD: '" + xcmd + "' {" + intentExtras  + "}");

                        if (xcmd.equals(NikeRun.INTENT_XCMD)) {
                            int newState = intent.getIntExtra("sportsstate", Constants.SPORTS_STATE_INIT);

                            if (runControlInstance == null) {
                                log("No run instance found, hence cannot set run state: " + newState);
                                return;
                            }

                            switch (newState){
                                case Constants.SPORTS_STATE_RUNNING:
                                    callMethod(runControlInstance, "resumeRun");
                                    log("State 'Running' sent to Nike+");
                                    break;
                                case Constants.SPORTS_STATE_PAUSED:
                                    callMethod(runControlInstance, "pauseRun");
                                    log("State 'Paused' sent to Nike+");
                                    break;
                                default:
                                    log("State '" + newState + "' not handled");
                            }

                        }
                        else {
                            log("Unknown XCMD received: " + xcmd);
                        }

                    }
                };
                context.registerReceiver(mBroadcastReceiver,
                        new IntentFilter(NikeRun.INTENT_XCMD), null, null);

                Intent intent = new Intent();
                intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
                intent.setAction(NikeRun.INTENT_CREATE);
                context.sendBroadcast(intent);

                log("NikeRun: Run Create");
            }
        });

        findAndHookMethod("com.nike.plusgps.RunActivity", lpparam.classLoader, "onDestroy",new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {

                Context context = (Context) callMethod(param.thisObject, "getApplicationContext");

                // Reset run control instance on activity destroy
                if (mBroadcastReceiver != null)
                {
                    log("NikeRun: broadcast receiver is not null, so unregister");
                    context.unregisterReceiver(mBroadcastReceiver);
                    mBroadcastReceiver = null;
                }

                // Nullify the objects that are no longer required
                runControlInstance = null;

                Intent intent = new Intent();
                intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
                intent.setAction(NikeRun.INTENT_DESTROY);
                context.sendBroadcast(intent);

                log("NikeRun: Run Destroy");
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

                log("NikeRun: Run Finished");
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

                log("NikeRun: Run Cancelled");
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

                log("NikeRun: Run Paused => " + paused);
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

                log("NikeRun: Run Resumed");
            }
        });

        findAndHookMethod("com.nike.plusgps.RunActivity", lpparam.classLoader, "updateScreenInfo", double.class, float.class, float.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {

                Object run = (Object) getObjectField(param.thisObject, "currentRun");
                float distanceRaw = (float) getFloatField(((Object) callMethod(run, "getDistanceUnitValue")), "value");
                float durationRaw = (float) getFloatField(((Object) callMethod(run, "getDurationUnitValue")), "value");
                double paceRaw = (double) getDoubleField(run, "currentPace");

                // Convert current pace value to seconds/km
                if (paceRaw != 0) {
                    paceRaw = PACE_MAGIC_NUM / paceRaw;
                }

                // Fix distance on watch sometimes being little ahead of nike+ app
                BigDecimal bd = new BigDecimal(Float.toString(distanceRaw));
                bd = bd.setScale(2, BigDecimal.ROUND_DOWN);
                String distance = bd.toString();
                String duration = String.format("%.0f", durationRaw / 1000);
                String pace = String.format("%d", (int) paceRaw);

//                log("NikeRun: updateScreenInfo()");
//                log("NikeRun: distance=" + distanceRaw + " / " + distance);
//                log("NikeRun: duration=" + durationRaw + " / " + duration);
//                log("NikeRun: pace=" + paceRaw + " / " + pace);

                Context context = (Context) callMethod(param.thisObject, "getApplicationContext");
                Intent intent = new Intent();
                intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
                intent.setAction(NikeRun.INTENT_UPDATE);
                intent.putExtra(NikeRun.DATA_DURATION, duration);
                intent.putExtra(NikeRun.DATA_DISTANCE, distance);
                intent.putExtra(NikeRun.DATA_PACE, pace);
                context.sendBroadcast(intent);

                lastUpdated = System.currentTimeMillis();

                log("NikeRun: Data Update");
            }
        });

    } // END handleLoadPackage

}
