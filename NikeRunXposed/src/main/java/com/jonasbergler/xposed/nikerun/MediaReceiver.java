package com.jonasbergler.xposed.nikerun;

/**
 * Created on 23/11/13 by Jonas Bergler (jonas@bergler.name).
 */


import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import timber.log.Timber;

public class MediaReceiver extends BroadcastReceiver {
    public void onReceive(Context context, Intent intent) {
        String intentExtras = (intent.getExtras() != null) ? intent.getExtras().toString() : "";
        Timber.d("Processing intent: '" + intent.getAction() + "' {" + intentExtras  + "}");

        NikeRun appContext = (NikeRun) context.getApplicationContext();
        assert appContext != null;

        // Make sure we're not copying our own info back into the metadata.
        if (intent.hasExtra(NikeRun.INTENT_FROM_SELF)) return;

        appContext.setMusicMetadata(
            intent.getStringExtra("track"),
            intent.getStringExtra("artist"),
            intent.getStringExtra("album")
        );

        // Handle music play/pause to control run activity
        if (intent.getAction().equals("com.android.music.playstatechanged") && intent.hasExtra("playing")) {
                appContext.musicPlayStateChanged(intent.getBooleanExtra("playing", false));
        }

        // Ensure that we send out our updated version straight away.
        if (appContext.isRunning()) appContext.sendUpdatedMetadata();
    }
}
