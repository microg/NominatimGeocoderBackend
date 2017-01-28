package org.microg.nlp.backend.nominatim;

import android.content.SharedPreferences;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceFragment;
import android.os.Bundle;

import java.util.Objects;

public class SettingsActivity extends PreferenceActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Display the fragment as the main content.
        getFragmentManager().beginTransaction()
                .replace(android.R.id.content, new PrefsFragment()).commit();

    }

    public static class PrefsFragment extends PreferenceFragment {
        public static final String catApiKeyToken = "cat_api_preference";
        public static final String apiChoiceToken = "api_server_choice";
        public static final String mapQuestApiKeyToken = "api_preference";

        private SharedPreferences shPref;
        private SharedPreferences.OnSharedPreferenceChangeListener listener;

        private Preference mApiChoicePref;
        private Preference mCatAPIKeyPref;
        private Preference mMapQuestApiKeyPref;

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            shPref = getPreferenceManager().getSharedPreferences();

            // Load the preferences from an XML resource
            addPreferencesFromResource(R.xml.preferences);

            mApiChoicePref = findPreference(apiChoiceToken);
            mCatAPIKeyPref = findPreference(catApiKeyToken);
            mMapQuestApiKeyPref = findPreference(mapQuestApiKeyToken);

            refreshPrefs();

            // Need explicit reference.
            // See :
            // http://stackoverflow.com/a/3104265
            listener = new SharedPreferences.OnSharedPreferenceChangeListener() {
                @Override
                public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
                    updatePreference(findPreference(key), key);
                }
            };
            shPref.registerOnSharedPreferenceChangeListener(listener);

            mApiChoicePref.setSummary(shPref.getString(apiChoiceToken, "OSM"));
            mMapQuestApiKeyPref.setSummary(shPref.getString(mapQuestApiKeyToken, ""));
        }

        private void refreshPrefs() {
            String apiServer = shPref.getString(apiChoiceToken, "OSM");
            if (apiServer.equals("OSM")) {
                getPreferenceScreen().removePreference(mCatAPIKeyPref);
            } else {
                getPreferenceScreen().addPreference(mCatAPIKeyPref);
            }
        }

        private void updatePreference(Preference preference, String key) {
            refreshPrefs();

            if (preference == null) {
                return;
            }

            if (preference instanceof ListPreference) {
                ListPreference listPreference = (ListPreference) preference;
                listPreference.setSummary(listPreference.getEntry());
                return;
            }

            preference.setSummary(shPref.getString(key, "Default"));
        }
    }
}
