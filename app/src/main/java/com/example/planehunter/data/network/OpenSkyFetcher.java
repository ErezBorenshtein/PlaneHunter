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

    private String clientId = null;
    private String clientSecret = null;

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
            ArrayList<Plane> planes = new ArrayList<>();

            try {
                double[] bbox = boundingBox(latCenter, lonCenter, radiusKm);

                String urlString = String.format(
                        "https://opensky-network.org/api/states/all?lamin=%f&lamax=%f&lomin=%f&lomax=%f&extended=1",
                        bbox[0], bbox[1], bbox[2], bbox[3]
                );

                Log.d(TAG, "Request URL: " + urlString);

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
                        ? connection.getInputStream()
                        : connection.getErrorStream();

                String body = readAll(is);
                connection.disconnect();

                if (code < 200 || code >= 300) {
                    Log.d(TAG, "HTTP error body: " + body);
                    postResult(callback, planes);
                    return;
                }

                JSONObject data = new JSONObject(body);
                JSONArray states = data.optJSONArray("states");

                Log.d(TAG, "states array = " + (states == null ? "null" : states.length()));

                if (states != null) {
                    for (int i = 0; i < states.length(); i++) {
                        JSONArray state = states.optJSONArray(i);

                        if (state == null) continue;

                        boolean onGround = state.optBoolean(8, false);
                        if (onGround) continue;

                        double lon = state.optDouble(5, Double.NaN);
                        double lat = state.optDouble(6, Double.NaN);
                        if (Double.isNaN(lat) || Double.isNaN(lon)) continue;

                        double distanceKm = UtilMath.haversineMeters(latCenter, lonCenter, lat, lon) / 1000.0;
                        if (distanceKm > radiusKm) continue;

                        String icao = safe(state.optString(0, ""));
                        String callSign = safe(state.optString(1, "N/A"));
                        double altitude = state.optDouble(7, 0.0);
                        double trackDeg = state.optDouble(10, Double.NaN);

                        Plane plane = new Plane(icao, callSign, lat, lon, altitude, null, trackDeg, (int) AircraftCategory.UNKNOWN);
                        plane.setCategory(AircraftCategory.UNKNOWN);
                        planes.add(plane);
                    }
                }

                Log.d(TAG, "Filtered planes count=" + planes.size());

            } catch (Exception e) {
                Log.e(TAG, "Fetch failed: " + e.getMessage(), e);
            }

            postResult(callback, planes);
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
        HttpURLConnection connection = null;
        try {
            String body =
                    "grant_type=client_credentials" +
                            "&client_id=" + URLEncoder.encode(clientId, "UTF-8") +
                            "&client_secret=" + URLEncoder.encode(clientSecret, "UTF-8");

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

            if (responseCode < 200 || responseCode >= 300) {
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

    private void postResult(PlanesCallback callback, ArrayList<Plane> planes) {
        new Handler(Looper.getMainLooper()).post(() -> callback.onPlanesFetched(planes));
    }

    private String readAll(InputStream inputStream) throws Exception {
        if (inputStream == null) return "";
        BufferedReader input = new BufferedReader(new InputStreamReader(inputStream));
        StringBuilder stringBuilder = new StringBuilder();
        String line;
        while ((line = input.readLine()) != null) stringBuilder.append(line);
        input.close();
        return stringBuilder.toString();
    }

    private String safe(String s) {
        return s == null ? "" : s.trim();
    }

    private double[] boundingBox(double lat, double lon, double radiusKm) {
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