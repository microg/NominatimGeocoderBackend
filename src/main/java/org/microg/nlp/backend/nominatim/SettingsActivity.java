package org.microg.nlp.backend.nominatim;

import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.location.Address;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.preference.ListPreference;
import android.support.v7.preference.Preference;

import com.obsez.android.lib.filechooser.ChooserDialog;
import com.takisoft.fix.support.v7.preference.PreferenceFragmentCompat;

import java.io.File;

public class SettingsActivity extends AppCompatActivity {

    public static final String KEY_DEBUG_FILE = "debug.log.file";
    public static final String KEY_DEBUG_TO_FILE = "debug.to.file";
    public static final String KEY_DEBUG_FILE_LASTING_HOURS = "debug.file.lasting.hours";
    public static final String KEY_WAKE_UP_STRATEGY = "wake.up.strategy";
    public static final String LOCATION_CACHE_ENABLED = "location.cache.enabled";
    public static final String LOCATION_CACHE_LASTING_HOURS = "location.cache.lasting";
    public static final String CAT_API_KEY_TOKEN = "cat_api_preference";
    public static final String API_CHOICE_TOKEN = "api_server_choice";
    public static final String MAP_QUEST_API_KEY_TOKEN = "api_preference";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(R.string.app_name);
            getSupportActionBar().show();
        }

        // Display the fragment as the main content.
        getSupportFragmentManager()
                .beginTransaction()
                .replace(android.R.id.content, new PrefsFragment())
                .commit();
    }

    public static class PrefsFragment extends PreferenceFragmentCompat {

        private SharedPreferences shPref;
        private SharedPreferences.OnSharedPreferenceChangeListener listener;

        private Preference mApiChoicePref;
        private Preference mCatAPIKeyPref;
        private Preference mMapQuestApiKeyPref;

        @Override
        public void onCreatePreferencesFix(Bundle savedInstanceState, String rootKey) {
            shPref = getPreferenceManager().getSharedPreferences();

            // Load the preferences from an XML resource
            addPreferencesFromResource(R.xml.preferences);

            mApiChoicePref = findPreference(API_CHOICE_TOKEN);
            mCatAPIKeyPref = findPreference(CAT_API_KEY_TOKEN);
            mMapQuestApiKeyPref = findPreference(MAP_QUEST_API_KEY_TOKEN);

            initLogFileChooser();
            initLogFileLasting();
            initWakeUpStrategy();
            initLocationCache();

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

            mApiChoicePref.setSummary(shPref.getString(API_CHOICE_TOKEN, "OSM"));
            mMapQuestApiKeyPref.setSummary(shPref.getString(MAP_QUEST_API_KEY_TOKEN, ""));
        }

        private void refreshPrefs() {
            String apiServer = shPref.getString(API_CHOICE_TOKEN, "OSM");
            if (apiServer.equals("OSM")) {
                getPreferenceScreen().removePreference(mCatAPIKeyPref);
            } else {
                getPreferenceScreen().addPreference(mCatAPIKeyPref);
            }
        }

        private void updatePreference(Preference preference, String key) {

            if (!(CAT_API_KEY_TOKEN.equals(key) ||
                  API_CHOICE_TOKEN.equals(key) ||
                  MAP_QUEST_API_KEY_TOKEN.equals(key))) {
                return;
            }

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

        private void initLocationCache() {

            Preference locationCacheEnabled = findPreference(LOCATION_CACHE_ENABLED);
            locationCacheEnabled.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {

                @Override
                public boolean onPreferenceChange(Preference preference, Object value) {
                    boolean enabled = (Boolean) value;
                    SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getContext());
                    preferences.edit().putBoolean(LOCATION_CACHE_ENABLED, enabled).apply();
                    return true;
                }
            });

            Preference locationLasting = findPreference(LOCATION_CACHE_LASTING_HOURS);
            locationLasting.setSummary(
                    getLocationLastingLabel(Integer.parseInt(
                            PreferenceManager.getDefaultSharedPreferences(getContext()).getString(LOCATION_CACHE_LASTING_HOURS, "720"))
                    )
            );
            locationLasting.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference locationLasting, Object value) {
                    String locationRowLastingHoursTxt = (String) value;
                    Integer locationRowLastingHours = Integer.valueOf(locationRowLastingHoursTxt);
                    SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getContext());
                    preferences.edit().putString(LOCATION_CACHE_LASTING_HOURS, locationRowLastingHoursTxt).apply();
                    locationLasting.setSummary(getString(getLocationLastingLabel(locationRowLastingHours)));
                    return true;
                }
            });


            Preference button = findPreference("clear_cache_button");
            button.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    ReverseGeocodingCacheDbHelper mDbHelper = new ReverseGeocodingCacheDbHelper(preference.getContext());
                    SQLiteDatabase db = mDbHelper.getWritableDatabase();
                    mDbHelper.onUpgrade(db, 0, 0);
                    return true;
                }
            });

            Preference dbInfo = findPreference("db_info");
            dbInfo.setSummary(getDataFromCacheDB());
        }

        private void initLogFileChooser() {

            Preference logToFileCheckbox = findPreference(KEY_DEBUG_TO_FILE);
            logToFileCheckbox.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(final Preference preference, Object value) {
                    boolean logToFile = (Boolean) value;
                    SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getContext());
                    preferences.edit().putBoolean(KEY_DEBUG_TO_FILE, logToFile).apply();
                    LogToFile.logToFileEnabled = logToFile;
                    return true;
                }
            });

            Preference buttonFileLog = findPreference(KEY_DEBUG_FILE);
            buttonFileLog.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(final Preference preference) {
                    new ChooserDialog().with(getContext())
                            .withFilter(true, false)
                            .withStartFile("/mnt")
                            .withChosenListener(new ChooserDialog.Result() {
                                @Override
                                public void onChoosePath(String path, File pathFile) {
                                    String logFileName = path + "/log-nominatim.txt";
                                    LogToFile.logFilePathname = logFileName;
                                    SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getContext());
                                    preferences.edit().putString(KEY_DEBUG_FILE, logFileName).apply();
                                    preference.setSummary(preferences.getString(KEY_DEBUG_FILE,""));
                                }
                            })
                            .build()
                            .show();
                    return true;
                }
            });
        }

        private void initLogFileLasting() {
            Preference logFileLasting = findPreference(KEY_DEBUG_FILE_LASTING_HOURS);
            logFileLasting.setSummary(
                    getLogFileLastingLabel(Integer.parseInt(
                            PreferenceManager.getDefaultSharedPreferences(getContext()).getString(KEY_DEBUG_FILE_LASTING_HOURS, "24"))
                    )
            );
            logFileLasting.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference logFileLasting, Object value) {
                    String logFileLastingHoursTxt = (String) value;
                    Integer logFileLastingHours = Integer.valueOf(logFileLastingHoursTxt);
                    SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getContext());
                    preferences.edit().putString(KEY_DEBUG_FILE_LASTING_HOURS, logFileLastingHoursTxt).apply();
                    logFileLasting.setSummary(getString(getLogFileLastingLabel(logFileLastingHours)));
                    LogToFile.logFileHoursOfLasting = logFileLastingHours;
                    return true;
                }
            });
        }

        private String getDataFromCacheDB() {

            ReverseGeocodingCacheDbHelper mDbHelper = new ReverseGeocodingCacheDbHelper(getContext());
            SQLiteDatabase db = mDbHelper.getReadableDatabase();
            long numberOfRowsInAddress = DatabaseUtils.queryNumEntries(db, ReverseGeocodingCacheContract.LocationAddressCache.TABLE_NAME);

            StringBuilder lastRowsFromDB = new StringBuilder();

            lastRowsFromDB.append("There are ");
            lastRowsFromDB.append(numberOfRowsInAddress);
            lastRowsFromDB.append(" of rows in cache.\n\n");

            String[] projection = {
                    ReverseGeocodingCacheContract.LocationAddressCache.COLUMN_NAME_ADDRESS,
                    ReverseGeocodingCacheContract.LocationAddressCache.COLUMN_NAME_CREATED,
                    ReverseGeocodingCacheContract.LocationAddressCache._ID
            };

            String sortOrder = ReverseGeocodingCacheContract.LocationAddressCache.COLUMN_NAME_CREATED + " DESC";

            Cursor cursor = db.query(
                    ReverseGeocodingCacheContract.LocationAddressCache.TABLE_NAME,
                    projection,
                    null,
                    null,
                    null,
                    null,
                    sortOrder
            );

            int rowsCounter = 0;

            while(cursor.moveToNext()) {

                if (!cursor.isFirst()) {
                    lastRowsFromDB.append("\n");
                }

                byte[] cachedAddressBytes = cursor.getBlob(
                        cursor.getColumnIndexOrThrow(ReverseGeocodingCacheContract.LocationAddressCache.COLUMN_NAME_ADDRESS));
                Address address = ReverseGeocodingCacheDbHelper.getAddressFromBytes(cachedAddressBytes);

                String recordCreatedTxt = cursor.getString(cursor.getColumnIndexOrThrow(ReverseGeocodingCacheContract.LocationAddressCache.COLUMN_NAME_CREATED));

                int itemId = cursor.getInt(cursor.getColumnIndexOrThrow(ReverseGeocodingCacheContract.LocationAddressCache._ID));

                lastRowsFromDB.append(itemId);
                lastRowsFromDB.append(" : ");
                lastRowsFromDB.append(recordCreatedTxt);
                lastRowsFromDB.append(" : ");
                lastRowsFromDB.append(address.getLocality());
                if (!address.getLocality().equals(address.getSubLocality())) {
                    lastRowsFromDB.append(" - ");
                    lastRowsFromDB.append(address.getSubLocality());
                }

                rowsCounter++;
                if (rowsCounter > 7) {
                    break;
                }
            }
            cursor.close();

            return lastRowsFromDB.toString();
        }

        private void initWakeUpStrategy() {
            Preference wakeUpStrategy = findPreference(KEY_WAKE_UP_STRATEGY);
            wakeUpStrategy.setSummary(
                    getWakeUpStrategyLabel(
                            PreferenceManager.getDefaultSharedPreferences(getContext()).getString(KEY_WAKE_UP_STRATEGY, "nowakeup")
                    )
            );
            wakeUpStrategy.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference wakeUpStrategy, Object value) {
                    String wakeUpStrategyValue = (String) value;
                    wakeUpStrategy.setSummary(getString(getWakeUpStrategyLabel(wakeUpStrategyValue)));
                    return true;
                }
            });
        }

        private int getWakeUpStrategyLabel(String wakeUpStrategyValue) {
            int wakeUpStrategyId;
            switch (wakeUpStrategyValue) {
                case "wakeuppartial":
                    wakeUpStrategyId = R.string.wakeuppartial_label;
                    break;
                case "wakeupfull":
                    wakeUpStrategyId = R.string.wakeupfull_label;
                    break;
                case "nowakeup":
                default:
                    wakeUpStrategyId = R.string.nowakeup_label;
                    break;
            }
            return wakeUpStrategyId;
        }

        private int getLogFileLastingLabel(int logFileLastingValue) {
            int logFileLastingId;
            switch (logFileLastingValue) {
                case 12:
                    logFileLastingId = R.string.log_file_12_label;
                    break;
                case 48:
                    logFileLastingId = R.string.log_file_48_label;
                    break;
                case 72:
                    logFileLastingId = R.string.log_file_72_label;
                    break;
                case 168:
                    logFileLastingId = R.string.log_file_168_label;
                    break;
                case 720:
                    logFileLastingId = R.string.log_file_720_label;
                    break;
                case 24:
                default:
                    logFileLastingId = R.string.log_file_24_label;
                    break;
            }
            return logFileLastingId;
        }

        private int getLocationLastingLabel(int locationLastingValue) {
            int locationLastingLastingId;
            switch (locationLastingValue) {
                case 12:
                    locationLastingLastingId = R.string.location_cache_12_label;
                    break;
                case 24:
                    locationLastingLastingId = R.string.location_cache_24_label;
                    break;
                case 168:
                    locationLastingLastingId = R.string.location_cache_168_label;
                    break;
                case 2190:
                    locationLastingLastingId = R.string.location_cache_2190_label;
                    break;
                case 4380:
                    locationLastingLastingId = R.string.location_cache_4380_label;
                    break;
                case 8760:
                    locationLastingLastingId = R.string.location_cache_8760_label;
                    break;
                case 88888:
                    locationLastingLastingId = R.string.location_cache_88888_label;
                    break;
                case 720:
                default:
                    locationLastingLastingId = R.string.location_cache_720_label;
                    break;
            }
            return locationLastingLastingId;
        }
    }
}
