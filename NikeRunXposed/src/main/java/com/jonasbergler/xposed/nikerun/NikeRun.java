package com.jonasbergler.xposed.nikerun;

/**
 * Created on 23/11/13 by Jonas Bergler (jonas@bergler.name).
 */


import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import com.getpebble.android.kit.Constants;
import com.getpebble.android.kit.PebbleKit;
import com.getpebble.android.kit.util.PebbleDictionary;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import timber.log.Timber;

import static timber.log.Timber.DebugTree;

/**
 * Created by jbergler on 23/11/2013.
 */
public class NikeRun extends Application {
    public static final String INTENT_FROM_SELF = "com.jonasbergler.xposed.nikerun.fromself";
    public static final String INTENT_UPDATE = "com.jonasbergler.xposed.nikerun.update";
    public static final String INTENT_START = "com.jonasbergler.xposed.nikerun.start";
    public static final String INTENT_PAUSE = "com.jonasbergler.xposed.nikerun.pause";
    public static final String INTENT_RESUME = "com.jonasbergler.xposed.nikerun.resume";
    public static final String INTENT_STOP = "com.jonasbergler.xposed.nikerun.stop";
    public static final String INTENT_CREATE = "com.jonasbergler.xposed.nikerun.create";
    public static final String INTENT_DESTROY = "com.jonasbergler.xposed.nikerun.destroy";
    public static final String INTENT_XCMD = "com.jonasbergler.xposed.nikerun.xcmd";

    public static final String MY_PACKAGE = "com.jonasbergler.xposed.nikerun";

    public static final String DATA_DURATION = "duration";
    public static final String DATA_DISTANCE = "distance";
    public static final String DATA_PACE = "pace";
    public static final String DATA_TRACK = "track";
    public static final String DATA_ARTIST = "artist";
    public static final String DATA_ALBUM = "album";
    public static final String[] DATA = {DATA_DURATION, DATA_DISTANCE, DATA_PACE, DATA_TRACK, DATA_ARTIST, DATA_ALBUM};

    public static final String STATE_STOPPED = "stopped";
    public static final String STATE_PAUSED = "paused";
    public static final String STATE_RUNNING = "running";

    public static final String FIELD_TRACK = "track";
    public static final String FIELD_ARTIST = "artist";
    public static final String FIELD_ALBUM = "album";
    public static final String[] FIELDS = {FIELD_TRACK, FIELD_ARTIST, FIELD_ALBUM};

    private boolean changed = false;
    private String state = STATE_STOPPED;

    private Map<String, String> data;
    private Map<String, Map<String, String>> formats;

    private PebbleKit.PebbleDataReceiver sportsDataHandler = null;
    private int updateCounter;

    public static Object RunCtrlInstance = null;

    @Override
    public void onCreate() {
        super.onCreate();

        // Initialize data store
        this.data = new HashMap();
        this.formats = new HashMap();

        this.formats.put(STATE_RUNNING, new HashMap<String, String>(){{
            put(FIELD_TRACK, "#distance#\n#duration#");
            put(FIELD_ARTIST, "#track#");
            put(FIELD_ALBUM, "#artist#");
        }});

        this.formats.put(STATE_PAUSED, new HashMap<String, String>(){{
            put(FIELD_TRACK, "[P] #distance#\n#duration#");
            put(FIELD_ARTIST, "#track#");
            put(FIELD_ALBUM, "#artist#");
        }});

        this.formats.put(STATE_STOPPED, new HashMap<String, String>(){{
            put(FIELD_TRACK, "#track#");
            put(FIELD_ARTIST, "#artist#");
            put(FIELD_ALBUM, "#album#");
        }});

        // Initialise data
        for (String field : DATA) setData(field, "");
        updateCounter = 0;

        // Setup Debugging
        Timber.plant(new DebugTree());
    }

    /**
     * Set the metadata for current music info.
     * If nothing is playing this should be set to null.
     * @param track Info for the 'track' data
     * @param artist Info for the 'artist' data
     * @param album Info for the 'album' data
     */
    public void setMusicMetadata(String track, String artist, String album) {
        setData(DATA_TRACK, track);
        setData(DATA_ARTIST, artist);
        setData(DATA_ALBUM, album);
    }

    public Map<String, String> getFormattedMetadata() {
        Map<String, String> result = new HashMap();
        Map<String, String> format = formats.get(state);

        for (String field : FIELDS) {
            result.put(field, replaceTokens(format.get(field), data));
        }
        return result;
    }

    public static String replaceTokens(String text, Map<String, String> replacements) {
        Pattern pattern = Pattern.compile("\\#(.+?)\\#");
        Matcher matcher = pattern.matcher(text);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String replacement = replacements.get(matcher.group(1));
            if (replacement != null) {
                matcher.appendReplacement(buffer, "");
                buffer.append(replacement);
            }
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    /**
     * Trigger the updated metadata to be sent via a broadcast intent.
     */
    public void sendUpdatedMetadata() {
        if (!this.changed) return;

        // Setup the intent
        Intent intent = new Intent();
        intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
        intent.setAction("com.android.music.metachanged");
        intent.putExtra(NikeRun.INTENT_FROM_SELF, "true");

        //Add the data
        Map<String, String> values = getFormattedMetadata();

        for (String key : values.keySet()) {
            intent.putExtra(key, values.get(key));
        }

        String intentExtras = (intent.getExtras() != null) ? intent.getExtras().toString() : "";
        Timber.d("Sent intent '" + intent.getAction() + "' {" + intentExtras + "}");

        //Send intent
        this.sendBroadcast(intent);

        //Send data to watch
        updateWatchApp();

        resetChanged();
    }

    public void setStopped() {

        this.state = STATE_STOPPED;
        this.setChanged(true);
    }

    public void setRunning() {

        this.state = STATE_RUNNING;
        this.setChanged(true);
    }

    public void setPaused() {
        this.state = STATE_PAUSED;
        this.setChanged(true);
    }

    public boolean isRunning() {
        return !this.state.equals(STATE_STOPPED);
    }

    public String getState() {
        return this.state;
    }

    public String getData(String key) {
        return data.get(key);
    }

    public void setData(String key, String value) {
        String oldValue = data.put(key, value);
        setChanged(!(oldValue == null ? value == null : oldValue.equals(value)));
    }

    public void setChanged(boolean b){
        if (b) changed = true;
    }

    public void resetChanged() {
        changed = false;
    }

    /**
     * Send a broadcast to launch the Sports App on the connected Pebble
     */
    public void startWatchApp() {
        Timber.d("Starting Watch App");

        //Trigger Sports App
        PebbleKit.startAppOnPebble(getApplicationContext(), Constants.SPORTS_UUID);
    }

    /**
     * Send a broadcast to close Sports App on the connected Pebble
     */
    public void stopWatchApp() {
        Timber.d("Stopping Watch App");
        PebbleKit.closeAppOnPebble(getApplicationContext(), Constants.SPORTS_UUID);
    }

    /**
     * Set customized data for Sports App
     */
    public void customizeWatchApp() {
        Timber.d("Customizing watch Sports App");
        final String customAppName = "Nike+ Running";
        final Bitmap customIcon = BitmapFactory.decodeResource(getResources(), R.drawable.watch);

        PebbleKit.customizeWatchApp(
                getApplicationContext(), Constants.PebbleAppType.SPORTS, customAppName, customIcon);
    }

    /**
     * Push (distance, time, pace) data to be displayed on Pebble's Sports App.
     */
    public void updateWatchApp() {

        /**
         * Start watch Sports App after every 15 seconds
         * There seems to be a bug in current PebbleSDK2 Beta4
         * Sports app disappears from menu after some time (if not active)
         */
        if(updateCounter > 15) {
            startWatchApp();
            updateCounter = 0;
        }
        else {
            updateCounter++;
        }

        String time = getData(NikeRun.DATA_DURATION);
        String distance = getData(NikeRun.DATA_DISTANCE);
        String pace = getData(NikeRun.DATA_PACE);

        PebbleDictionary data = new PebbleDictionary();

        //Differentiate not running state
        if(this.state != STATE_RUNNING){
            time = ":" + time + ":";
        }

        Timber.d("Updating Watch App with values: Dur='" + time + "' Dist='" + distance + "' Pace='" + pace + "'");

        data.addString(Constants.SPORTS_TIME_KEY, time);
        data.addString(Constants.SPORTS_DISTANCE_KEY, distance);
        data.addString(Constants.SPORTS_DATA_KEY, pace);
        data.addUint8(Constants.SPORTS_LABEL_KEY, (byte)Constants.SPORTS_DATA_PACE); // Set pace label
        data.addUint8(Constants.SPORTS_UNITS_KEY, (byte) Constants.SPORTS_UNITS_METRIC); // Unit metrics

        PebbleKit.sendDataToPebble(getApplicationContext(), Constants.SPORTS_UUID, data);
    }

    /**
     * Handle Nike+ run created
     */
    public void runCreated() {

        Timber.d("New run created");

        // Register handler to receive button events from pebble
        sportsDataHandler = new PebbleKit.PebbleDataReceiver(Constants.SPORTS_UUID) {
            @Override
            public void receiveData(final Context context, final int transactionId, final PebbleDictionary data) {
                int newState = data.getUnsignedInteger(Constants.SPORTS_STATE_KEY).intValue();

                // Broadcast intent to NikeRunXPosed for changing Nike+ running state
                Intent intent = new Intent();
                intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
                intent.setAction(NikeRun.INTENT_XCMD);
                intent.putExtra("sportsstate", newState);

                //Send intent
                context.sendBroadcast(intent);

                PebbleKit.sendAckToPebble(context, transactionId);

                Timber.d("Sports App state changed: %d", newState);
            }
        };
        PebbleKit.registerReceivedDataHandler(this, sportsDataHandler);

        // Customize pebble watch app
        customizeWatchApp();

        // Start watch Sports App
        startWatchApp();
    }

    /**
     * Handle Nike+ run destroyed
     */
    public void runDestroyed() {

        Timber.d("Run destroyed");

        //De-register Sports App handler
        if (sportsDataHandler != null) {
            unregisterReceiver(sportsDataHandler);
            sportsDataHandler = null;
        }

        //Stop watch Sports App
        stopWatchApp();
    }

}