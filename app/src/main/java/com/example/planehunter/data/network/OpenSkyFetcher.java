package com.example.planehunter.data.network;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.example.planehunter.model.Plane;
import com.example.planehunter.util.UtilMath;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

public class OpenSkyFetcher {

    private static final String TAG = "OpenSkyFetcher";

    private static final String TOKEN_URL =
            "https://auth.opensky-network.org/auth/realms/opensky-network/protocol/openid-connect/token";

    public interface PlanesCallback {
        void onPlanesFetched(ArrayList<Plane> planes);
    }

    private double radiusKm = 30.0;

    // OAuth client credentials
    private String clientId = null;
    private String clientSecret = null;

    // token cache
    private final Object tokenLock = new Object();
    private String cachedToken = null;
    private long tokenExpiryMs = 0;

    private static class TokenResponse {
        String accessToken;
        int expiresInSec;
    }

    public void setClientCredentials(String clientId, String clientSecret) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        if (this.clientId.isEmpty() || this.clientSecret.isEmpty()) {
            this.clientId = null;
            this.clientSecret = null;
        }
    }

    public void fetchPlanes(double latCenter, double lonCenter, PlanesCallback callback) {
        new Thread(() -> {
            //I need new thread for http request

            ArrayList<Plane> planes = new ArrayList<>();

            try {
                double[] bbox = boundingBox(latCenter, lonCenter, radiusKm);

                String urlString = String.format(
                        "https://opensky-network.org/api/states/all?lamin=%f&lamax=%f&lomin=%f&lomax=%f&extended=1",
                        bbox[0], bbox[1], bbox[2], bbox[3]
                );

                Log.d(TAG, "Request URL: " + urlString);

                //API docs: https://openskynetwork.github.io/opensky-api/
                HttpURLConnection connection = (HttpURLConnection) new URL(urlString).openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(8000);
                connection.setReadTimeout(8000);


                String token = getValidToken();
                if (token != null && !token.isEmpty()) {
                    connection.setRequestProperty("Authorization", "Bearer " + token);
                } else {
                    Log.d(TAG, "No token (will likely get 401). Check client_id/client_secret.");
                }

                int code = connection.getResponseCode();
                Log.d(TAG, "HTTP code=" + code);

                InputStream is = (code >= 200 && code < 300)
                        ? connection.getInputStream() //for good response
                        : connection.getErrorStream();//for bad response

                String body = readAll(is); //read all data for bad or good response
                connection.disconnect();

                if (code < 200 || code >= 300) {
                    Log.d(TAG, "HTTP error body: " + body);
                    postResult(callback, planes); //returns the result for the UI thread(Empty because its a bad response)
                    return;
                }

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
                //...
                //17 = category
                // ------------------------------------------------------------

                JSONObject data = new JSONObject(body); //response is JSON
                JSONArray states = data.optJSONArray("states"); //take only the states, time doesn't matter

                Log.d(TAG, "states array = " + (states == null ? "null" : states.length()));

                if (states != null) {
                    for (int i = 0; i < states.length(); i++) { //go over each state(plane)
                        JSONArray state = states.optJSONArray(i);

                        Log.d(TAG, "state[" + i + "] raw=" + state.toString());

                        if (state == null) continue;

                        boolean onGround = state.optBoolean(8, false);
                        if (onGround) continue; //dismiss if on ground

                        double lon = state.optDouble(5, Double.NaN);
                        double lat = state.optDouble(6, Double.NaN);
                        if (Double.isNaN(lat) || Double.isNaN(lon)) continue;

                        double distanceKm =UtilMath.haversineMeters(latCenter, lonCenter, lat, lon)/1000; //convert meters to KM
                        if (distanceKm > radiusKm) continue;

                        String icao = safe(state.optString(0, ""));
                        String callSign = safe(state.optString(1, "N/A"));
                        double altitude = state.optDouble(7, 0.0);
                        double trackDeg = state.optDouble(10, Double.NaN);
                        int category = state.optInt(17,-1);


                        planes.add(new Plane(icao, callSign, lat, lon,altitude, null ,trackDeg,category));
                    }
                }

                Log.d(TAG, "Filtered planes count=" + planes.size());

            } catch (Exception e) {
                Log.e(TAG, "Fetch failed: " + e.getMessage(), e);
            }

            postResult(callback, planes); //return result for main thread

        }).start();
    }

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

    private TokenResponse fetchToken(String clientId, String clientSecret) {
        //for OAuth2

        HttpURLConnection connection = null;
        try {
            String body =
                    "grant_type=client_credentials" +
                            "&client_id=" + URLEncoder.encode(clientId, "UTF-8") + //API client id
                            "&client_secret=" + URLEncoder.encode(clientSecret, "UTF-8"); //API client secret

            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);

            connection = (HttpURLConnection) new URL(TOKEN_URL).openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(8000);
            connection.setReadTimeout(8000);
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");

            OutputStream outputStream = connection.getOutputStream();
            outputStream.write(bytes);
            outputStream.flush();
            outputStream.close();

            int responseCode = connection.getResponseCode();
            InputStream inputStream = (responseCode >= 200 && responseCode < 300) ? connection.getInputStream() : connection.getErrorStream();
            String response = readAll(inputStream);

            if (responseCode < 200 || responseCode >= 300) { //bad request
                Log.e(TAG, "Token HTTP " + responseCode + " body=" + response);
                return null;
            }

            JSONObject jsonResponse = new JSONObject(response);//response in json
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

    private void postResult(PlanesCallback callback, ArrayList<Plane> planes) {
        //posts the callback to the main thread so the result can update the UI
        new Handler(Looper.getMainLooper()).post(() -> callback.onPlanesFetched(planes));
    }

    private String readAll(InputStream inputStream) throws Exception { //needed for the readLine
        if (inputStream == null) return "";
        BufferedReader input = new BufferedReader(new InputStreamReader(inputStream));
        StringBuilder stringBuilder = new StringBuilder();
        String line;
        while ((line = input.readLine()) != null) stringBuilder.append(line);
        input.close();
        return stringBuilder.toString();
    }

    private String safe(String s) {
        //had problems with bad strings from API
        return s == null ? "" : s.trim();
    }

    private double[] boundingBox(double lat, double lon, double radiusKm) {
        //cords calculation(rounded to 111)
        //https://stackoverflow.com/questions/1253499/simple-calculations-for-working-with-lat-lon-and-km-distance
        double deltaLat = radiusKm / 111.0;
        double deltaLon = radiusKm / (111.0 * Math.cos(Math.toRadians(lat)));
        return new double[]{lat - deltaLat, lat + deltaLat, lon - deltaLon, lon + deltaLon};
    }

    public void setRadiusKm(double radiusKm) {
        this.radiusKm = radiusKm;
    }

    public double getRadiusKm() {
        return radiusKm;
    }
}