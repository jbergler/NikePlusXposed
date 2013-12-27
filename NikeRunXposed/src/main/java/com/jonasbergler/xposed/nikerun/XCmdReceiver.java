package com.jonasbergler.xposed.nikerun;

/**
 * Created on 27/12/13 by Krupal Desai (code@krupal.in)
 */

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.getpebble.android.kit.Constants;

import de.robv.android.xposed.XposedBridge;

import static de.robv.android.xposed.XposedHelpers.callMethod;

public class XCmdReceiver extends BroadcastReceiver{

    private Object runControlInstance = null;

    public XCmdReceiver(){
        super();
    }

    public XCmdReceiver(Object runInst) {
        runControlInstance = runInst;
        XposedBridge.log("Run instance set");
    }

    public void onReceive(Context context, Intent intent) {
        String intentExtras = (intent.getExtras() != null) ? intent.getExtras().toString() : "";
        String xcmd = intent.getAction();
        assert xcmd !=null;

        XposedBridge.log("Processing XCMD: '" + xcmd + "' {" + intentExtras  + "}");

        if (xcmd.equals(NikeRun.INTENT_XCMD)) {
            int newState = intent.getIntExtra("sportsstate", Constants.SPORTS_STATE_INIT);
            setRunState(newState);
        }
        else {
            XposedBridge.log("Unknown XCMD received: " + xcmd);
        }
    }

    public void setRunState(int pebbleSportsState) {

        if (runControlInstance == null) {
            XposedBridge.log("No run instance found, hence cannot set run state: " + pebbleSportsState);
            return;
        }

        switch (pebbleSportsState){
            case Constants.SPORTS_STATE_RUNNING:
                callMethod(runControlInstance, "resumeRun");
                XposedBridge.log("State 'Running' sent to Nike+");
                break;
            case Constants.SPORTS_STATE_PAUSED:
                callMethod(runControlInstance, "pauseRun");
                XposedBridge.log("State 'Paused' sent to Nike+");
                break;
            default:
                XposedBridge.log("State '" + pebbleSportsState + "' not handled");
        }

    }

}
