package com.jonasbergler.xposed.nikerun;

/**
 * Created on 04/01/14 by Krupal Desai (code@krupal.in).
 */

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.PreferenceFragment;

import java.io.File;

public class SettingsActivity extends Activity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState == null)
            getFragmentManager().beginTransaction().replace(android.R.id.content, new PrefFragment()).commit();
    }

    public static class PrefFragment extends PreferenceFragment
            implements SharedPreferences.OnSharedPreferenceChangeListener {

        private boolean initLoggingMode = false;

        @Override
        public void onCreate(final Bundle savedInstanceState)
        {
            super.onCreate(savedInstanceState);

            getPreferenceManager().setSharedPreferencesMode(MODE_WORLD_READABLE);
            addPreferencesFromResource(R.layout.preferences);
            updateListSummary("pref_unit");
        }

        @Override
        public void onResume() {
            super.onResume();
            getPreferenceScreen().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);

            // Save initial logging mode
            initLoggingMode = ((CheckBoxPreference) findPreference("pref_enableLogging")).isChecked();
        }

        @Override
        public void onPause() {
            super.onPause();
            getPreferenceScreen().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);

            /**
             * If logging mode is changed then create/delete debug flag file and
             * force exit process to reload with new change
             */
            boolean newLoggingMode = ((CheckBoxPreference) findPreference("pref_enableLogging")).isChecked();
            if (initLoggingMode != newLoggingMode) {

                try {
                    File f = (new File("/data/data/" + NikeRun.MY_PACKAGE + "/.debug"));

                    if (newLoggingMode && !f.exists()) {
                        f.createNewFile();
                    }
                    else if (!newLoggingMode && f.exists()) {
                        f.delete();
                    }

                    // Wait till activity is hidden
                    Thread.sleep(1000);

                } catch (Throwable throwable) {
                    throwable.printStackTrace();
                }

                // Force exist process
                System.exit(0);
            }
        }

        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            if (key.equals("pref_unit")) {
                updateListSummary(key);
            }
        }

        private void updateListSummary(String key) {
            ListPreference savePref = (ListPreference) findPreference(key);
            // Set summary to be the user-description for the selected value
            savePref.setSummary(savePref.getEntry());
        }
    }

}
