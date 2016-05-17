package org.microg.nlp.backend.nominatim;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Address;
import android.net.Uri;
import android.os.Build;
import android.preference.PreferenceManager;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.microg.nlp.api.GeocoderBackendService;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

public class BackendService extends GeocoderBackendService {
    private static final String TAG = "NominatimGeocoderBackend";
    private static final String SERVICE_URL_MAPQUEST = "http://open.mapquestapi.com/nominatim/v1/";
    private static final String SERVICE_URL_OSM = " http://nominatim.openstreetmap.org/";
    private static final String REVERSE_GEOCODE_URL =
            "%sreverse?format=json&key=%s&accept-language=%s&lat=%f&lon=%f";
    private static final String SEARCH_GEOCODE_URL =
            "%ssearch?format=json&key=%s&accept-language=%s&addressdetails=1&bounded=1&q=%s&limit=%d";
    private static final String SEARCH_GEOCODE_WITH_BOX_URL =
            "%ssearch?format=json&key=%s&accept-language=%s&addressdetails=1&bounded=1&q=%s&limit=%d" +
                    "&viewbox=%f,%f,%f,%f";
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

    @Override
    protected List<Address> getFromLocation(double latitude, double longitude, int maxResults,
            String locale) {

        SharedPreferences SP = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
        String mapquestApiKey = SP.getString("api_preference", "NA");

        String url = String.format(Locale.US, REVERSE_GEOCODE_URL, SERVICE_URL_MAPQUEST,
                mapquestApiKey, locale.split("_")[0], latitude, longitude);
        try {
            JSONObject result = new JSONObject(new AsyncGetRequest(this,
                    url).asyncStart().retrieveString());
            Address address = parseResponse(localeFromLocaleString(locale), result);
            if (address != null) {
                List<Address> addresses = new ArrayList<>();
                addresses.add(address);
                return addresses;
            }
        } catch (Exception e) {
            Log.w(TAG, e);
        }
        return null;
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

        SharedPreferences SP = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
        String mapquestApiKey = SP.getString("api_preference", "NA");

        String query = Uri.encode(locationName);
        String url;
        if (lowerLeftLatitude == 0 && lowerLeftLongitude == 0 && upperRightLatitude == 0 &&
                upperRightLongitude == 0) {
            url = String.format(Locale.US, SEARCH_GEOCODE_URL, SERVICE_URL_MAPQUEST,
                    mapquestApiKey, locale.split("_")[0], query, maxResults);
        } else {
            url = String.format(Locale.US, SEARCH_GEOCODE_WITH_BOX_URL, SERVICE_URL_MAPQUEST,
                    mapquestApiKey, locale.split("_")[0], query, maxResults, lowerLeftLongitude,
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

        int line = 0;
        JSONObject a = result.getJSONObject(WIRE_ADDRESS);

        if (a.has(WIRE_THOROUGHFARE)) {
            address.setAddressLine(line++, a.getString(WIRE_THOROUGHFARE));
            address.setThoroughfare(a.getString(WIRE_THOROUGHFARE));
        }
        if (a.has(WIRE_SUBLOCALITY)) {
            address.setSubLocality(a.getString(WIRE_SUBLOCALITY));
        }
        if (a.has(WIRE_POSTALCODE)) {
            address.setAddressLine(line++, a.getString(WIRE_POSTALCODE));
            address.setPostalCode(a.getString(WIRE_POSTALCODE));
        }
        if (a.has(WIRE_LOCALITY_CITY)) {
            address.setAddressLine(line++, a.getString(WIRE_LOCALITY_CITY));
            address.setLocality(a.getString(WIRE_LOCALITY_CITY));
        } else if (a.has(WIRE_LOCALITY_TOWN)) {
            address.setAddressLine(line++, a.getString(WIRE_LOCALITY_TOWN));
            address.setLocality(a.getString(WIRE_LOCALITY_TOWN));
        } else if (a.has(WIRE_LOCALITY_VILLAGE)) {
            address.setAddressLine(line++, a.getString(WIRE_LOCALITY_VILLAGE));
            address.setLocality(a.getString(WIRE_LOCALITY_VILLAGE));
        }
        if (a.has(WIRE_SUBADMINAREA)) {
            address.setAddressLine(line++, a.getString(WIRE_SUBADMINAREA));
            address.setSubAdminArea(a.getString(WIRE_SUBADMINAREA));
        }
        if (a.has(WIRE_ADMINAREA)) {
            address.setAddressLine(line++, a.getString(WIRE_ADMINAREA));
            address.setAdminArea(a.getString(WIRE_ADMINAREA));
        }
        if (a.has(WIRE_COUNTRYNAME)) {
            address.setAddressLine(line++, a.getString(WIRE_COUNTRYNAME));
            address.setCountryName(a.getString(WIRE_COUNTRYNAME));
        }
        if (a.has(WIRE_COUNTRYCODE)) {
            address.setCountryCode(a.getString(WIRE_COUNTRYCODE));
        }

        return address;
    }

    private class AsyncGetRequest extends Thread {
        public static final String USER_AGENT = "User-Agent";
        public static final String USER_AGENT_TEMPLATE = "UnifiedNlp/%s (Linux; Android %s)";
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
            synchronized (done) {
                try {
                    Log.d(TAG, "Requesting " + url);
                    HttpURLConnection connection = (HttpURLConnection) new URL(url)
                            .openConnection();
                    setUserAgentOnConnection(connection);
                    connection.setDoInput(true);
                    InputStream inputStream = connection.getInputStream();
                    result = readStreamToEnd(inputStream);
                } catch (Exception e) {
                    Log.w(TAG, e);
                }
                done.set(true);
                done.notifyAll();
            }
        }

        public AsyncGetRequest asyncStart() {
            start();
            return this;
        }

        public byte[] retrieveAllBytes() {
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

        public String retrieveString() {
            return new String(retrieveAllBytes());
        }

        private void setUserAgentOnConnection(URLConnection connection) {
            try {
                connection.setRequestProperty(USER_AGENT, String.format(USER_AGENT_TEMPLATE,
                        context.getPackageManager().getPackageInfo(context.getPackageName(),
                                0).versionName, Build.VERSION.RELEASE));
            } catch (PackageManager.NameNotFoundException e) {
                connection.setRequestProperty(USER_AGENT, String.format(USER_AGENT_TEMPLATE, 0,
                        Build.VERSION.RELEASE));
            }
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
}
