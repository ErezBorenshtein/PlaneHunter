package com.example.planehunter.data.network;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.example.planehunter.model.AircraftCategory;
import com.example.planehunter.model.Plane;
import com.example.planehunter.util.UtilMath;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Locale;

/**
 * Fetches real-time aircraft state vectors from the OpenSky Network API.
 * Handles authentication via OAuth2 and manages token caching.
 */
public class OpenSkyFetcher {

    /** Log tag. */
    private static final String TAG = "OpenSkyFetcher";

    /** OpenSky OAuth2 token URL. */
    private static final String TOKEN_URL =
            "https://auth.opensky-network.org/auth/realms/opensky-network/protocol/openid-connect/token";

    /**
     * Callback interface for plane fetching results.
     */
    public interface PlanesCallback {
        /**
         * Called when planes have been successfully fetched and filtered.
         * @param planes The list of fetched Plane objects.
         */
        void onPlanesFetched(ArrayList<Plane> planes);
    }

    /** Radius in kilometers to search for aircraft around the user's location. */
    private double radiusKm = 30.0;

    /** OAuth2 Client ID for OpenSky API access. */
    private String clientId = null;
    /** OAuth2 Client Secret for OpenSky API access. */
    private String clientSecret = null;

    /** Synchronizes access to the cached token. */
    private final Object tokenLock = new Object();

    /** Cached OAuth2 access token. */
    private String cachedToken = null;
    /** Expiry time of the cached token in milliseconds. */
    private long tokenExpiryMs = 0;

    /**
     * Data object for the token response from OpenSky.
     */
    private static class TokenResponse {
        /** The access token string. */
        String accessToken;
        /** The token's time-to-live in seconds. */
        int expiresInSec;
    }

    /**
     * Sets the client credentials for OpenSky Network API authentication.
     * @param clientId The client ID.
     * @param clientSecret The client secret.
     */
    public void setClientCredentials(String clientId, String clientSecret) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        if (this.clientId.isEmpty() || this.clientSecret.isEmpty()) {
            this.clientId = null;
            this.clientSecret = null;
        }
    }

    /**
     * Fetches planes within a certain radius of the specified coordinates.
     * Performs the network request on a background thread and returns results via callback on the main thread.
     * @param latCenter Latitude of the center point.
     * @param lonCenter Longitude of the center point.
     * @param callback Callback to receive the results.
     */
    public void fetchPlanes(double latCenter, double lonCenter, PlanesCallback callback) {
        new Thread(() -> {
            ArrayList<Plane> planes = new ArrayList<>();

            try {
                double[] bbox = boundingBox(latCenter, lonCenter, radiusKm);

                //https://openskynetwork.github.io/opensky-api/rest.html
                String urlString = String.format(
                        "https://opensky-network.org/api/states/all?lamin=%f&lamax=%f&lomin=%f&lomax=%f&extended=1",
                        bbox[0], bbox[1], bbox[2], bbox[3]
                );

                Log.d(TAG, "Request URL: " + urlString);

                HttpURLConnection connection = (HttpURLConnection) new URL(urlString).openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(8000); //8 secs
                connection.setReadTimeout(8000);   //8 secs

                String token = getValidToken();
                if (token != null && !token.isEmpty()) {
                    connection.setRequestProperty("Authorization", "Bearer " + token);
                } else {
                    Log.d(TAG, "No token (will likely get 401). Check client_id/client_secret.");
                }

                int code = connection.getResponseCode();
                Log.d(TAG, "HTTP code=" + code);

                InputStream is =null;
                String body;

                if(code >= 200 && code < 300){
                    Log.d(TAG,"HTTP success");
                    is =connection.getInputStream();
                    body = readAll(is);
                }
                else{
                    is =connection.getErrorStream();
                    body = readAll(is);
                    Log.d(TAG, "HTTP error body: " + body);
                    postResult(callback, planes);
                    return;
                }

                connection.disconnect();

                // ------------------------------------------------------------
                // OpenSky API response format
                //
                // {
                //   "time": 1710000000,
                //   "states": [
                //    ["4ca123","DLH123",...],
                //    ["3c5abc","RYR55",...]
                //  ]
                // }
                //
                // Each aircraft is represented as an array.
                // Important indices used below:
                // 0 = icao24
                // 1 = callsign
                // 5 = longitude
                // 6 = latitude
                // 7 = altitude
                // 8 = on_ground
                // 10 = track
                // ------------------------------------------------------------

                JSONObject data = new JSONObject(body);
                JSONArray states = data.optJSONArray("states"); //divides by states(planes)

                Log.d(TAG, "states array = " + (states == null ? "null" : states.length()));

                if (states != null) {
                    for (int i = 0; i < states.length(); i++) {
                        JSONArray state = states.optJSONArray(i);

                        if (state == null){
                            Log.d(TAG,"null state");
                            continue;
                        }

                        if (state.optBoolean(8, false)){ //we don't need planes that are on ground
                            Log.d(TAG,"on ground");
                            continue;
                        }

                        double lon = state.optDouble(5, Double.NaN);
                        double lat = state.optDouble(6, Double.NaN);

                        if (Double.isNaN(lat) || Double.isNaN(lon)) continue;

                        double distanceKm = UtilMath.haversineMeters(latCenter, lonCenter, lat, lon) / 1000.0;
                        if (distanceKm > radiusKm){
                            //Log.d(TAG,"bigger than radius");
                            continue;
                        }

                        String icao = cleanString(state.optString(0, ""));
                        String callSign = cleanString(state.optString(1, "N/A"));
                        double altitude = state.optDouble(7, 0.0);
                        double trackDeg = state.optDouble(10, Double.NaN);
                        String countryCode = getCountryCode(state.optString(2,"N/A"));

                        Plane plane = new Plane(icao, callSign, lat, lon, altitude, null, trackDeg, (int) AircraftCategory.UNKNOWN,countryCode);
                        planes.add(plane);
                    }
                }

                //Log.d(TAG, "Filtered planes count=" + planes.size());

            } catch (Exception e) {
                Log.e(TAG, "Fetch failed: " + e.getMessage(), e);
            }

            postResult(callback, planes);
        }).start();
    }

    /**
     * Gets the country code for a given country name.
     * @param countryName The name of the country.
     * @return The country code or null if not found.
     */
    private String getCountryCode(String countryName) {
        //https://www.javamadesoeasy.com/2016/10/display-name-of-all-countries-with.html
        for (String countryCode : Locale.getISOCountries()) {
            Locale locale = new Locale("", countryCode);
            if (locale.getDisplayCountry(Locale.ENGLISH).equalsIgnoreCase(countryName)) {
                return countryCode;
            }
        }
        return null;
    }

    /**
     * Gets a valid token from the OpenSky Network API.
     * @return The token string or null if not available.
     */
    private String getValidToken() {
        if (clientId == null || clientSecret == null) return null;

        long now = System.currentTimeMillis();

        synchronized (tokenLock) {
            if (cachedToken != null && now < (tokenExpiryMs - 60_000L)) {
                return cachedToken;
            }
        }

        TokenResponse tokenResponse = fetchToken(clientId, clientSecret);
        if (tokenResponse == null || tokenResponse.accessToken == null) return null;

        synchronized (tokenLock) {
            cachedToken = tokenResponse.accessToken;
            tokenExpiryMs = now + (tokenResponse.expiresInSec * 1000L);
            return cachedToken;
        }
    }

    /**
     * Fetches a token from the OpenSky Network API.
     * @param clientId The Client ID.
     * @param clientSecret The Client Secret.
     * @return The TokenResponse object or null if failed.
     */
    private TokenResponse fetchToken(String clientId, String clientSecret) {
        //https://openskynetwork.github.io/opensky-api/rest.html#all-state-vectors
        HttpURLConnection connection = null;
        try {
            /*
            export CLIENT_ID=your_client_id
            export CLIENT_SECRET=your_client_secret

            export TOKEN=$(curl -X POST "https://auth.opensky-network.org/auth/realms/opensky-network/protocol/openid-connect/token" \
              -H "Content-Type: application/x-www-form-urlencoded" \
              -d "grant_type=client_credentials" \
              -d "client_id=$CLIENT_ID" \
              -d "client_secret=$CLIENT_SECRET" | jq -r .access_token)

            curl -H "Authorization: Bearer $TOKEN" https://opensky-network.org/api/states/all | jq .
            */

            String body =
                    "grant_type=client_credentials" +
                            "&client_id=" + URLEncoder.encode(clientId, "UTF-8") +
                            "&client_secret=" + URLEncoder.encode(clientSecret, "UTF-8");

            byte[] bytes = body.getBytes(StandardCharsets.UTF_8); //http work with bytes not Strings

            connection = (HttpURLConnection) new URL(TOKEN_URL).openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(8000);
            connection.setReadTimeout(8000);
            connection.setDoOutput(true); //lets you send bytes
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");

            OutputStream outputStream = connection.getOutputStream();
            outputStream.write(bytes);
            outputStream.flush();
            outputStream.close();


            int responseCode = connection.getResponseCode();

            InputStream inputStream;
            String response;

            if(responseCode >= 200 && responseCode < 300){
                inputStream = connection.getInputStream();
                response = readAll(inputStream);
            }
            else{
                inputStream = connection.getErrorStream();
                response = readAll(inputStream);
                Log.e(TAG, "Token HTTP " + responseCode + " body=" + response);
                return null;
            }

            JSONObject jsonResponse = new JSONObject(response);
            TokenResponse tokenResponse = new TokenResponse();
            tokenResponse.accessToken = jsonResponse.optString("access_token", null);
            tokenResponse.expiresInSec = jsonResponse.optInt("expires_in", 1800);
            return tokenResponse;

        } catch (Exception e) {
            Log.e(TAG, "Token fetch failed: " + e.getMessage(), e);
            return null;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    /**
     * Posts the result to the UI thread.
     * @param callback Callback to receive the results.
     * @param planes The list of fetched Plane objects.
     */
    private void postResult(PlanesCallback callback, ArrayList<Plane> planes) {
        new Handler(Looper.getMainLooper()).post(() -> callback.onPlanesFetched(planes)); //returns data to UI thread
    }

    /**
     * Reads all data from an InputStream.
     * @param inputStream The InputStream to read.
     * @return The data as a String.
     * @throws IOException if read fails.
     */
    private String readAll(InputStream inputStream) throws IOException {
        if (inputStream == null) return "";
        BufferedReader input = new BufferedReader(new InputStreamReader(inputStream));
        StringBuilder stringBuilder = new StringBuilder();
        String line;
        while ((line = input.readLine()) != null){
            stringBuilder.append(line);
        }
        input.close();
        return stringBuilder.toString();
    }

    /**
     * Cleans a string by removing leading/trailing whitespace.
     * @param s The string to clean.
     * @return The cleaned string.
     */
    private String cleanString(String s) {
        if (s == null) return "";

        String trimmed = s.trim();
        return trimmed.isEmpty() ? "" : trimmed;
    }

    /**
     * Calculates the bounding box for a given center point and radius.
     * @param lat Latitude of the center point.
     * @param lon Longitude of the center point.
     * @param radiusKm Radius in kilometers.
     * @return An array containing the bounding box coordinates.
     */
    private double[] boundingBox(double lat, double lon, double radiusKm) {
        double deltaLat = radiusKm / 111.0; //1° longitude ≈ 111 km
        double deltaLon = radiusKm / (111.0 * Math.cos(Math.toRadians(lat))); //1° longitude ≈ 111 * cos(lat)
        return new double[]{lat - deltaLat, lat + deltaLat, lon - deltaLon, lon + deltaLon};
    }

    public void setRadiusKm(double radiusKm) {
        this.radiusKm = radiusKm;
    }

    public double getRadiusKm() {
        return radiusKm;
    }
}