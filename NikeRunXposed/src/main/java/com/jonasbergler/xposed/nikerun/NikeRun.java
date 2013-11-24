package com.jonasbergler.xposed.nikerun;

/**
 * Created on 23/11/13 by Jonas Bergler (jonas@bergler.name).
 */


import android.app.Application;
import android.content.Intent;

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

}
