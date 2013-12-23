package com.jonasbergler.xposed.nikerun;

/**
 * Created on 23/11/13 by Jonas Bergler (jonas@bergler.name).
 */

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import timber.log.Timber;

public class UpdateReceiver extends BroadcastReceiver {
    public void onReceive(Context context, Intent intent) {
        String intentExtras = (intent.getExtras() != null) ? intent.getExtras().toString() : "";
        Timber.d("Processing intent: '" + intent.getAction() + "' {" + intentExtras  + "}");

        NikeRun appContext = (NikeRun) context.getApplicationContext();
        assert appContext != null;

        String action = intent.getAction();
        assert action != null;

        if (action.equals(NikeRun.INTENT_START)) {
            appContext.setRunning();
        }
        else if (action.equals(NikeRun.INTENT_STOP)) {
            appContext.setStopped();
        }
        else if (action.equals(NikeRun.INTENT_PAUSE)) {
            appContext.setPaused();
        }
        else if (action.equals(NikeRun.INTENT_RESUME)) {
            appContext.setRunning();
        }
        else if (action.equals(NikeRun.INTENT_UPDATE)) {
            appContext.setData(NikeRun.DATA_DISTANCE, intent.getStringExtra(NikeRun.DATA_DISTANCE) + " km");
            appContext.setData(NikeRun.DATA_DURATION,formatSeconds(intent.getStringExtra(NikeRun.DATA_DURATION)));
            appContext.setData(NikeRun.DATA_PACE, intent.getStringExtra(NikeRun.DATA_PACE));
        }
        else {
            Timber.d("Unknown intent: " + intent.getAction());
        }

        // Ensure that we send out our updated version straight away.
        appContext.sendUpdatedMetadata();
    }

    public String formatSeconds(String raw) {
        int x = Integer.parseInt(raw);
        if (Integer.toString(x).equals(raw)) return formatSeconds(x);
        return "";
    }

    public String formatSeconds(int raw) {
        raw *= 1000;

        int seconds = (int) (raw / 1000) % 60 ;
        int minutes = (int) ((raw / (1000*60)) % 60);
        int hours   = (int) ((raw / (1000*60*60)) % 24);

        String time = "";
        if (hours > 0) time += hours + "h";
        if (hours > 0 || minutes > 0) time += " " + minutes + "m";
        time += " " + seconds + "s";

        return time.trim();
    }
}