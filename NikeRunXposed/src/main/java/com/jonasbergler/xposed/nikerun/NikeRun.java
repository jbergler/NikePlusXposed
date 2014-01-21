package com.jonasbergler.xposed.nikerun;

/**
 * Created on 23/11/13 by Jonas Bergler (jonas@bergler.name).
 */


import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.preference.PreferenceManager;

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

    public enum UnitType {KILOMETERS, MILES}

    private boolean changed = false;
    private String state = STATE_STOPPED;

    private Map<String, String> data;
    private Map<String, Map<String, String>> formats;

    private PebbleKit.PebbleDataReceiver sportsDataHandler = null;
    private int updateCounter;

    private boolean prefBCMusic = true;
    private boolean prefBCSportsApp = true;
    private boolean prefRestartSportsApp = true;
    private boolean prefBindToMusic = true;
    private boolean prefEnableLogging = false;
    private boolean pauseFlag = true;
    private boolean runCreated = false;
    public UnitType prefUnitType = UnitType.KILOMETERS;

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
        runCreated = false;

        // Load saved configurations
        loadSavedPreferences();

        // Setup Debug
        if (prefEnableLogging)
            Timber.plant(new DebugTree());

        Timber.d("NikeRunXposed process created");
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

    public String replaceTokens(String text, Map<String, String> replacements) {
        Pattern pattern = Pattern.compile("\\#(.+?)\\#");
        Matcher matcher = pattern.matcher(text);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String replacement = replacements.get(matcher.group(1));

            if (matcher.group(1).equals(DATA_DISTANCE)) {
                if (prefUnitType == UnitType.KILOMETERS)
                    replacement += "km";
                else
                    replacement += "mi";
            }

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

        // Ignore updates if run is not yet created
        if (!runCreated) return;

        // Send broadcast for sports app
        if(prefBCSportsApp) {
            //Send data to watch
            updateWatchApp();
        }

        if (!this.changed) return;

        // Send broadcast for music app
        if(prefBCMusic) {
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
        }

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
        PebbleKit.startAppOnPebble(this, Constants.SPORTS_UUID);
    }

    /**
     * Send a broadcast to close Sports App on the connected Pebble
     */
    public void stopWatchApp() {
        Timber.d("Stopping Watch App");
        PebbleKit.closeAppOnPebble(this, Constants.SPORTS_UUID);
    }

    /**
     * Set customized data for Sports App
     */
    public void customizeWatchApp() {
        Timber.d("Customizing watch Sports App");
        final String customAppName = "Nike+ Running";
        final Bitmap customIcon = BitmapFactory.decodeResource(getResources(), R.drawable.watch);

        PebbleKit.customizeWatchApp(
                this, Constants.PebbleAppType.SPORTS, customAppName, customIcon);
    }

    /**
     * Push (distance, time, pace) data to be displayed on Pebble's Sports App.
     */
    public void updateWatchApp() {

        /**
         * Start watch Sports App after every 30 seconds
         * There seems to be a bug in current PebbleSDK2 Beta4
         * Sports app disappears from menu after some time (if not active)
         */
        if (prefRestartSportsApp) {
            if(updateCounter > 30) {
                startWatchApp();
                updateCounter = 0;
            }
            else {
                updateCounter++;
            }
        }

        String time = getData(NikeRun.DATA_DURATION);
        String distance = getData(NikeRun.DATA_DISTANCE);
        String pace = getData(NikeRun.DATA_PACE);

        PebbleDictionary data = new PebbleDictionary();

        //Differentiate not running state
        if (this.state.equals(STATE_PAUSED)) {
            if (pauseFlag) {
                time = "-";
            }
            pauseFlag = !pauseFlag;
            //time = ":" + time + ":";
            data.addUint16(Constants.SPORTS_STATE_KEY, (short) Constants.SPORTS_STATE_PAUSED);
        }
        else if (this.state.equals(STATE_STOPPED)) {

            //De-register Sports App handler
            if (sportsDataHandler != null) {
                unregisterReceiver(sportsDataHandler);
                sportsDataHandler = null;
            }

            pace = "----";
            data.addUint16(Constants.SPORTS_STATE_KEY, (short) Constants.SPORTS_STATE_END);
        }
        else {
            data.addUint16(Constants.SPORTS_STATE_KEY, (short) Constants.SPORTS_STATE_RUNNING);
        }

        Timber.d("Updating Watch App with values: Dur='" + time + "' Dist='" + distance + "' Pace='" + pace + "'");

        data.addString(Constants.SPORTS_TIME_KEY, time);
        data.addString(Constants.SPORTS_DISTANCE_KEY, distance);
        data.addString(Constants.SPORTS_DATA_KEY, pace);
        data.addUint8(Constants.SPORTS_LABEL_KEY, (byte)Constants.SPORTS_DATA_PACE); // Set pace label

        // Send configured unit type to pebble
        if (prefUnitType == UnitType.KILOMETERS)
            data.addUint8(Constants.SPORTS_UNITS_KEY, (byte) Constants.SPORTS_UNITS_METRIC);
        else
            data.addUint8(Constants.SPORTS_UNITS_KEY, (byte) Constants.SPORTS_UNITS_IMPERIAL);

        PebbleKit.sendDataToPebble(this, Constants.SPORTS_UUID, data);
    }

    /**
     * Handle Nike+ run created
     */
    public void runCreated() {

        Timber.d("New run created");

        // Set flag
        runCreated = true;

        // Load preferences for every new run
        loadSavedPreferences();

        // Ignore launching sports app if not configured
        if (!prefBCSportsApp)
            return;

        // Register handler to receive button events from pebble
        sportsDataHandler = new PebbleKit.PebbleDataReceiver(Constants.SPORTS_UUID) {
            @Override
            public void receiveData(final Context context, final int transactionId, final PebbleDictionary data) {

                int newState = data.getUnsignedInteger(Constants.SPORTS_STATE_KEY).intValue();

                PebbleKit.sendAckToPebble(context, transactionId);

                // Broadcast intent to NikeRunXPosed for changing Nike+ running state
                Intent intent = new Intent();
                intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
                intent.setAction(NikeRun.INTENT_XCMD);
                intent.putExtra("sportsstate", newState);

                //Send intent
                context.sendBroadcast(intent);

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

        // Reset flag
        runCreated = false;

        // De-register Sports App handler
        if (sportsDataHandler != null) {
            unregisterReceiver(sportsDataHandler);
            sportsDataHandler = null;
        }

        // Stop watch Sports App
        stopWatchApp();

        // Force stop process
        System.exit(0);
    }

    /**
     * Load saved preferences
     */
    private void loadSavedPreferences() {

        // Load saved preferences
        try {
            SharedPreferences savedPref = PreferenceManager.getDefaultSharedPreferences(this);
            prefBCMusic = savedPref.getBoolean("pref_sendBCMusic", true);
            prefBCSportsApp = savedPref.getBoolean("pref_sendBCSportsApp", true);
            prefRestartSportsApp = savedPref.getBoolean("pref_restartSportsApp", true);
            prefBindToMusic = savedPref.getBoolean("pref_bindToMusicPlayState", true);
            prefEnableLogging = savedPref.getBoolean("pref_enableLogging", false);
            prefUnitType = UnitType.values()[Integer.parseInt(savedPref.getString("pref_unit", "0"))];
        } catch (Exception ex) {
            Timber.e("Error while loading saved preferences: " + ex.getMessage());
        }

        // Log loaded prefs
        Timber.d("Preferences loaded: BCMusic=" + prefBCMusic + ", BCSports=" + prefBCSportsApp +
                ", RestartSports=" + prefRestartSportsApp + ", UnitType=" + prefUnitType + ", BindToMusic=" + prefBindToMusic);
    }

    /**
     * Handle music play state change
     * @param isPlaying
     */
    public void musicPlayStateChanged(boolean isPlaying) {

        if (prefBindToMusic == false) return;

        Timber.d("Changing run state due to music play state change: isPlaying=" + isPlaying);

        int newState = Constants.SPORTS_STATE_PAUSED;

        if (isPlaying)
            newState = Constants.SPORTS_STATE_RUNNING;

        // Broadcast intent to NikeRunXPosed for changing Nike+ running state
        Intent intent = new Intent();
        intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
        intent.setAction(NikeRun.INTENT_XCMD);
        intent.putExtra("sportsstate", newState);

        //Send intent
        this.sendBroadcast(intent);

    }
}