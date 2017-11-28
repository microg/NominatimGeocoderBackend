/*
 * Copyright (C) 2013-2017 microG Project Team
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.microg.nlp.backend.nominatim;

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.location.Address;
import android.os.Build;
import android.os.Parcel;
import android.os.PowerManager;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.microg.address.Formatter;
import org.microg.nlp.api.GeocoderBackendService;

import static android.os.Build.VERSION.RELEASE;
import static org.microg.nlp.backend.nominatim.BuildConfig.VERSION_NAME;
import static org.microg.nlp.backend.nominatim.ReverseGeocodingCacheContract.LocationAddressCache;
import static org.microg.nlp.backend.nominatim.LogToFile.appendLog;

public class BackendService extends GeocoderBackendService {
    private static final String TAG = "NominatimGeocoder";

    private static final String SERVICE_URL_MAPQUEST = "https://open.mapquestapi.com/nominatim/v1";
    private static final String SERVICE_URL_OSM = "https://nominatim.openstreetmap.org";

    private static final String REVERSE_GEOCODE_URL =
            "%s/reverse?%sformat=json&accept-language=%s&lat=%f&lon=%f";
    private static final String SEARCH_GEOCODE_URL =
            "%s/search?%sformat=json&accept-language=%s&addressdetails=1&bounded=1&q=%s&limit=%d";
    private static final String SEARCH_GEOCODE_WITH_BOX_URL =
            SEARCH_GEOCODE_URL + "&viewbox=%f,%f,%f,%f";

    private static final String WIRE_LATITUDE = "lat";
    private static final String WIRE_LONGITUDE = "lon";
    private static final String WIRE_ADDRESS = "address";
    private static final String WIRE_THOROUGHFARE = "road";
    private static final String WIRE_SUBLOCALITY = "suburb";
    private static final String WIRE_POSTALCODE = "postcode";
    private static final String WIRE_LOCALITY_CITY = "city";
    private static final String WIRE_LOCALITY_TOWN = "town";
    private static final String WIRE_LOCALITY_VILLAGE = "village";
    private static final String WIRE_SUBADMINAREA = "county";
    private static final String WIRE_ADMINAREA = "state";
    private static final String WIRE_COUNTRYNAME = "country";
    private static final String WIRE_COUNTRYCODE = "country_code";

    private ReverseGeocodingCacheDbHelper mDbHelper;

    private PowerManager powerManager;
    private PowerManager.WakeLock wakeLock;

    private Formatter formatter;

    private String mApiUrl;
    private String mAPIKey;

    @Override
    public void onCreate() {
        super.onCreate();

        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);

        LogToFile.logFilePathname = sharedPreferences.getString(SettingsActivity.KEY_DEBUG_FILE,"");
        LogToFile.logToFileEnabled = sharedPreferences.getBoolean(SettingsActivity.KEY_DEBUG_TO_FILE, false);
        LogToFile.logFileHoursOfLasting = Integer.valueOf(sharedPreferences.getString(SettingsActivity.KEY_DEBUG_FILE_LASTING_HOURS, "24"));

        if (!sharedPreferences.contains(SettingsActivity.LOCATION_CACHE_ENABLED)) {
            sharedPreferences.edit().putBoolean(SettingsActivity.LOCATION_CACHE_ENABLED, true).apply();
        }

        try {
            formatter = new Formatter();
        } catch (IOException e) {
            Log.w(TAG, "Could not initialize address formatter", e);
            appendLog(getBaseContext(), TAG, e.getMessage(), e);
        }
        readPrefs();
        powerManager = ((PowerManager) this.getSystemService(Context.POWER_SERVICE));
    }

    @Override
    protected void onOpen() {
        super.onOpen();
        readPrefs();
        powerManager = ((PowerManager) this.getSystemService(Context.POWER_SERVICE));
    }

    private void readPrefs() {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());

        if (sp.getString(SettingsActivity.API_CHOICE_TOKEN, "OSM").equals("OSM")) {
            mApiUrl = SERVICE_URL_OSM;
            // No API key for OSM
            mAPIKey = "";
        } else {
            mApiUrl = SERVICE_URL_MAPQUEST;
            mAPIKey = "key=" + sp.getString(SettingsActivity.MAP_QUEST_API_KEY_TOKEN, "NA")
                    + "&";
        }
    }

    @Override
    protected List<Address> getFromLocation(double latitude,
                                            double longitude,
                                            int maxResults,
                                            String locale) {

        appendLog(getBaseContext(), TAG, "getFromLocation:" + latitude + ", " + longitude + ", " + locale);
        if (mDbHelper == null) {
            mDbHelper = new ReverseGeocodingCacheDbHelper(getApplicationContext());
        }

        List<Address> addressesFromCache = retrieveLocationFromCache(latitude, longitude, locale);
        if (addressesFromCache != null) {
            return addressesFromCache;
        }

        wakeUp();

        String url = String.format(Locale.US, REVERSE_GEOCODE_URL, mApiUrl, mAPIKey,
                locale.split("_")[0], latitude, longitude);
        appendLog(getBaseContext(), TAG, "Constructed URL " + url);
        try {
            JSONObject result = new JSONObject(new AsyncGetRequest(this,
                    url).asyncStart().retrieveString());
            appendLog(getBaseContext(), TAG, "result from nominatim server:" + result);

            Address address = parseResponse(localeFromLocaleString(locale), result);
            if (address != null) {
                List<Address> addresses = new ArrayList<>();
                addresses.add(address);
                storeAddressToCache(latitude, longitude, locale, address);
                return addresses;
            }
        } catch (Exception e) {
            Log.w(TAG, e);
            appendLog(getBaseContext(), TAG, e.getMessage(), e);
        } finally {
            if (wakeLock != null) {
                try {
                    wakeLock.release();
                } catch (Throwable th) {
                    // ignoring this exception, probably wakeLock was already released
                }
            }
        }
        return null;
    }

    private void wakeUp() {
        appendLog(getBaseContext(), TAG, "powerManager:" + powerManager);

        String wakeUpStrategy = PreferenceManager.getDefaultSharedPreferences(this).getString(SettingsActivity.KEY_WAKE_UP_STRATEGY, "nowakeup");

        appendLog(getBaseContext(), TAG, "wakeLock:wakeUpStrategy:" + wakeUpStrategy);

        if (wakeLock != null) {
            try {
                wakeLock.release();
            } catch (Throwable th) {
                // ignoring this exception, probably wakeLock was already released
            }
        }

        if ("nowakeup".equals(wakeUpStrategy)) {
            return;
        }

        int powerLockID;

        if ("wakeupfull".equals(wakeUpStrategy)) {
            powerLockID = PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP;
        } else {
            powerLockID = PowerManager.PARTIAL_WAKE_LOCK;
        }

        appendLog(getBaseContext(), TAG, "wakeLock:powerLockID:" + powerLockID);

        boolean isInUse;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH) {
            isInUse = powerManager.isInteractive();
        } else {
            isInUse = powerManager.isScreenOn();
        }

        if (!isInUse) {
            wakeLock = powerManager.newWakeLock(powerLockID, TAG);
            appendLog(getBaseContext(), TAG, "wakeLock:" + wakeLock + ":" + wakeLock.isHeld());
            if (!wakeLock.isHeld()) {
                wakeLock.acquire();
            }
            appendLog(getBaseContext(), TAG, "wakeLock acquired");
        }
    }

    private static Locale localeFromLocaleString(String localeString) {
        String[] split = localeString.split("_");
        if (split.length == 1) {
            return new Locale(split[0]);
        } else if (split.length == 2) {
            return new Locale(split[0], split[1]);
        } else if (split.length == 3) {
            return new Locale(split[0], split[1], split[2]);
        }
        throw new RuntimeException("That's not a locale: " + localeString);
    }

    @Override
    protected List<Address> getFromLocationName(String locationName, int maxResults,
                                                double lowerLeftLatitude, double lowerLeftLongitude, double upperRightLatitude,
                                                double upperRightLongitude, String locale) {
        String query = Uri.encode(locationName);
        String url;
        if (lowerLeftLatitude == 0 && lowerLeftLongitude == 0 && upperRightLatitude == 0 &&
                upperRightLongitude == 0) {
            url = String.format(Locale.US, SEARCH_GEOCODE_URL, mApiUrl, mAPIKey,
                    locale.split("_")[0], query, maxResults);
        } else {
            url = String.format(Locale.US, SEARCH_GEOCODE_WITH_BOX_URL, mApiUrl, mAPIKey,
                    locale.split("_")[0], query, maxResults, lowerLeftLongitude,
                    upperRightLatitude, upperRightLongitude, lowerLeftLatitude);
        }
        try {
            JSONArray result = new JSONArray(new AsyncGetRequest(this,
                    url).asyncStart().retrieveString());
            List<Address> addresses = new ArrayList<>();
            for (int i = 0; i < result.length(); i++) {
                Address address = parseResponse(localeFromLocaleString(locale),
                        result.getJSONObject(i));
                if (address != null)
                    addresses.add(address);
            }
            if (!addresses.isEmpty()) return addresses;
        } catch (Exception e) {
            Log.w(TAG, e);
            appendLog(getBaseContext(), TAG, e.getMessage(), e);
        }
        return null;
    }

    private Address parseResponse(Locale locale, JSONObject result) throws JSONException {
        if (!result.has(WIRE_LATITUDE) || !result.has(WIRE_LONGITUDE) ||
                !result.has(WIRE_ADDRESS)) {
            return null;
        }
        Address address = new Address(locale);
        address.setLatitude(result.getDouble(WIRE_LATITUDE));
        address.setLongitude(result.getDouble(WIRE_LONGITUDE));

        JSONObject a = result.getJSONObject(WIRE_ADDRESS);

        address.setThoroughfare(a.optString(WIRE_THOROUGHFARE));
        address.setSubLocality(a.optString(WIRE_SUBLOCALITY));
        address.setPostalCode(a.optString(WIRE_POSTALCODE));
        address.setSubAdminArea(a.optString(WIRE_SUBADMINAREA));
        address.setAdminArea(a.optString(WIRE_ADMINAREA));
        address.setCountryName(a.optString(WIRE_COUNTRYNAME));
        address.setCountryCode(a.optString(WIRE_COUNTRYCODE));

        if (a.has(WIRE_LOCALITY_CITY)) {
            address.setLocality(a.getString(WIRE_LOCALITY_CITY));
        } else if (a.has(WIRE_LOCALITY_TOWN)) {
            address.setLocality(a.getString(WIRE_LOCALITY_TOWN));
        } else if (a.has(WIRE_LOCALITY_VILLAGE)) {
            address.setLocality(a.getString(WIRE_LOCALITY_VILLAGE));
        }

        if (formatter != null) {
            Map<String, String> components = new HashMap<>();
            for (String s : new IterableIterator<>(a.keys())) {
                components.put(s, String.valueOf(a.get(s)));
            }
            String[] split = formatter.formatAddress(components).split("\n");
            for (int i = 0; i < split.length; i++) {
                Log.d(TAG, split[i]);
                address.setAddressLine(i, split[i]);
            }

            address.setFeatureName(formatter.guessName(components));
        }

        return address;
    }

    private class IterableIterator<T> implements Iterable<T> {
        Iterator<T> i;

        public IterableIterator(Iterator<T> i) {
            this.i = i;
        }

        @Override
        public Iterator<T> iterator() {
            return i;
        }
    }

    private class AsyncGetRequest extends Thread {
        static final String USER_AGENT = "User-Agent";
        static final String USER_AGENT_TEMPLATE = "UnifiedNlp/%s (Linux; Android %s)";
        private final AtomicBoolean done = new AtomicBoolean(false);
        private final Context context;
        private final String url;
        private byte[] result;

        private AsyncGetRequest(Context context, String url) {
            this.context = context;
            this.url = url;
        }

        @Override
        public void run() {
            appendLog(getBaseContext(), TAG, "Sync key (done)" + done);
            synchronized (done) {
                try {
                    Log.d(TAG, "Requesting " + url);
                    appendLog(getBaseContext(), TAG, "Requesting " + url);
                    HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
                    appendLog(getBaseContext(), TAG, "Connection opened");
                    connection.setRequestProperty(USER_AGENT, String.format(USER_AGENT_TEMPLATE, VERSION_NAME, RELEASE));
                    connection.setConnectTimeout(30000);
                    connection.setReadTimeout(30000);
                    connection.setDoInput(true);
                    appendLog(getBaseContext(), TAG, "Getting input stream");
                    InputStream inputStream = connection.getInputStream();
                    appendLog(getBaseContext(), TAG, "Reading input stream");
                    result = readStreamToEnd(inputStream);
                    appendLog(getBaseContext(), TAG, "Input stream read");
                } catch (Exception e) {
                    Log.w(TAG, e);
                    appendLog(getBaseContext(), TAG, e.getMessage(), e);
                }
                done.set(true);
                done.notifyAll();
            }
        }

        AsyncGetRequest asyncStart() {
            start();
            return this;
        }

        byte[] retrieveAllBytes() {
            if (!done.get()) {
                synchronized (done) {
                    while (!done.get()) {
                        try {
                            done.wait();
                        } catch (InterruptedException e) {
                            break;
                        }
                    }
                }
            }
            return result;
        }

        String retrieveString() {
            return new String(retrieveAllBytes());
        }

        private byte[] readStreamToEnd(InputStream is) throws IOException {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            if (is != null) {
                byte[] buff = new byte[1024];
                while (true) {
                    int nb = is.read(buff);
                    if (nb < 0) {
                        break;
                    }
                    bos.write(buff, 0, nb);
                }
                is.close();
            }
            return bos.toByteArray();
        }
    }

    private List<Address> retrieveLocationFromCache(double latitude, double longitude, String locale) {
        boolean useCache = PreferenceManager.getDefaultSharedPreferences(this).getBoolean(SettingsActivity.LOCATION_CACHE_ENABLED, true);

        if (!useCache) {
            return null;
        }

        Address addressFromCache = getResultFromCache(latitude, longitude, locale);
        appendLog(getBaseContext(), TAG, "address retrieved from cache:" + addressFromCache);
        if (addressFromCache == null) {
            return null;
        }
        List<Address> addresses = new ArrayList<>();
        addresses.add(addressFromCache);
        return addresses;
    }

    private void storeAddressToCache(double latitude, double longitude, String locale, Address address) {

        boolean useCache = PreferenceManager.getDefaultSharedPreferences(this).getBoolean(SettingsActivity.LOCATION_CACHE_ENABLED, true);

        if (!useCache) {
            return;
        }

        // Gets the data repository in write mode
        SQLiteDatabase db = mDbHelper.getWritableDatabase();

        // Create a new map of values, where column names are the keys
        ContentValues values = new ContentValues();
        values.put(LocationAddressCache.COLUMN_NAME_ADDRESS, getAddressAsBytes(address));
        values.put(LocationAddressCache.COLUMN_NAME_LONGITUDE, longitude);
        values.put(LocationAddressCache.COLUMN_NAME_LATITUDE, latitude);
        values.put(LocationAddressCache.COLUMN_NAME_LOCALE, locale);
        values.put(LocationAddressCache.COLUMN_NAME_CREATED, new Date().getTime());

        long newLocationRowId = db.insert(LocationAddressCache.TABLE_NAME, null, values);

        appendLog(getBaseContext(), TAG, "storedAddress:" + latitude + ", " + longitude + ", " + newLocationRowId + ", " + address);
    }

    private Address getResultFromCache(double latitude, double longitude, String locale) {

        new DeleteOldRows().start();

        SQLiteDatabase db = mDbHelper.getReadableDatabase();

        String[] projection = {
            LocationAddressCache.COLUMN_NAME_ADDRESS
        };

        double latitudeLow = latitude - 0.0001;
        double latitudeHigh = latitude + 0.0001;
        double longitudeLow = longitude - 0.0001;
        double longitudeHigh = longitude + 0.0001;

        String selection = LocationAddressCache.COLUMN_NAME_LONGITUDE + " <= ? and " +
                           LocationAddressCache.COLUMN_NAME_LONGITUDE + " >= ? and " +
                           LocationAddressCache.COLUMN_NAME_LATITUDE + " <= ? and " +
                           LocationAddressCache.COLUMN_NAME_LATITUDE + " >= ? and " +
                           LocationAddressCache.COLUMN_NAME_LOCALE + " = ? ";
        String[] selectionArgs = { String.valueOf(longitudeHigh),
                                   String.valueOf(longitudeLow),
                                   String.valueOf(latitudeHigh),
                                   String.valueOf(latitudeLow),
                                   locale };

        Cursor cursor = db.query(
            LocationAddressCache.TABLE_NAME,
            projection,
            selection,
            selectionArgs,
            null,
            null,
            null
            );

        if (!cursor.moveToNext()) {
            cursor.close();
            return null;
        }

        byte[] cachedAddressBytes = cursor.getBlob(
                                      cursor.getColumnIndexOrThrow(LocationAddressCache.COLUMN_NAME_ADDRESS));
        cursor.close();

        return ReverseGeocodingCacheDbHelper.getAddressFromBytes(cachedAddressBytes);
    }

    private boolean recordDateIsNotValidOrIsTooOld(long recordCreatedinMilis) {
        Calendar now = Calendar.getInstance();
        Calendar calendarRecordCreated = Calendar.getInstance();
        calendarRecordCreated.setTimeInMillis(recordCreatedinMilis);

        int timeToLiveRecordsInCacheInHours = Integer.parseInt(
                        PreferenceManager.getDefaultSharedPreferences(this).getString(SettingsActivity.LOCATION_CACHE_LASTING_HOURS, "720"));

        calendarRecordCreated.add(Calendar.HOUR_OF_DAY, timeToLiveRecordsInCacheInHours);
        return calendarRecordCreated.before(now);
    }

    private byte[] getAddressAsBytes(Address address) {
        final Parcel parcel = Parcel.obtain();
        address.writeToParcel(parcel, 0);
        byte[] addressBytes = parcel.marshall();
        parcel.recycle();
        return addressBytes;
    }

    private class DeleteOldRows extends Thread {

        @Override
        public void run() {
            SQLiteDatabase db = mDbHelper.getWritableDatabase();

            String[] projection = {
                LocationAddressCache.COLUMN_NAME_CREATED,
                LocationAddressCache._ID
            };

            Cursor cursor = db.query(
                LocationAddressCache.TABLE_NAME,
                projection,
                null,
                null,
                null,
                null,
                null
                );

            while (cursor.moveToNext()) {
                Integer recordId = cursor.getInt(
                                 cursor.getColumnIndexOrThrow(LocationAddressCache._ID));

                long recordCreatedInMilis = cursor.getLong(
                                            cursor.getColumnIndexOrThrow(LocationAddressCache.COLUMN_NAME_CREATED));

                if (recordDateIsNotValidOrIsTooOld(recordCreatedInMilis)) {
                    mDbHelper.deleteRecordFromTable(recordId);
                }
            }
            cursor.close();
        }
    }
}
