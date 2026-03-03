package com.example.planehunter.data.network;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.example.planehunter.model.Plane;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;

public class OpenSkyFetcher {

    private static final String TAG = "OpenSkyFetcher";

    public interface PlanesCallback {
        void onPlanesFetched(ArrayList<Plane> planes);
    }

    private double radiusKm = 4.0;

    public void fetchPlanes(double latCenter, double lonCenter, PlanesCallback callback) {
        new Thread(() -> {

            ArrayList<Plane> planes = new ArrayList<>();

            try {
                double[] bbox = boundingBox(latCenter, lonCenter, radiusKm);

                String urlString = String.format(
                        "https://opensky-network.org/api/states/all?lamin=%f&lamax=%f&lomin=%f&lomax=%f",
                        bbox[0], bbox[1], bbox[2], bbox[3]
                );

                Log.d(TAG, "Request URL: " + urlString);

                HttpURLConnection connection = (HttpURLConnection) new URL(urlString).openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(8000);
                connection.setReadTimeout(8000);
                //connection.setRequestProperty("X-Forwarded-For", "203.0.113.10"); //test because TOO many requests

                int code = connection.getResponseCode();
                Log.d(TAG, "HTTP code=" + code);

                InputStream is = (code >= 200 && code < 300)
                        ? connection.getInputStream()
                        : connection.getErrorStream();

                String body = readAll(is);
                connection.disconnect();

                if (code < 200 || code >= 300) {
                    Log.e(TAG, "HTTP error body: " + body);
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

                        double distanceKm = haversineKm(latCenter, lonCenter, lat, lon);
                        if (distanceKm > radiusKm) continue;

                        String icao = safe(state.optString(0, ""));
                        String callSign = safe(state.optString(1, "N/A"));
                        double altitude = state.optDouble(7, 0.0);
                        double trackDeg = state.optDouble(10, Double.NaN);

                        planes.add(new Plane(icao, callSign, lat, lon, altitude, trackDeg));
                    }
                }

                Log.d(TAG, "Filtered planes count=" + planes.size());

            } catch (Exception e) {
                Log.e(TAG, "Fetch failed: " + e.getMessage(), e);
            }

            postResult(callback, planes);

        }).start();
    }

    private void postResult(PlanesCallback callback, ArrayList<Plane> planes) {
        new Handler(Looper.getMainLooper()).post(() -> callback.onPlanesFetched(planes));
    }

    private String readAll(InputStream is) throws Exception {
        if (is == null) return "";
        BufferedReader in = new BufferedReader(new InputStreamReader(is));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = in.readLine()) != null) sb.append(line);
        in.close();
        return sb.toString();
    }

    private String safe(String s) {
        return s == null ? "" : s.trim();
    }

    private double[] boundingBox(double lat, double lon, double radiusKm) {
        double deltaLat = radiusKm / 111.0;
        double deltaLon = radiusKm / (111.0 * Math.cos(Math.toRadians(lat)));
        return new double[]{lat - deltaLat, lat + deltaLat, lon - deltaLon, lon + deltaLon};
    }

    private double haversineKm(double lat1, double lon1, double lat2, double lon2) {
        double R = 6371.0;
        double phi1 = Math.toRadians(lat1);
        double phi2 = Math.toRadians(lat2);
        double dPhi = Math.toRadians(lat2 - lat1);
        double dLambda = Math.toRadians(lon2 - lon1);

        double a = Math.sin(dPhi / 2) * Math.sin(dPhi / 2)
                + Math.cos(phi1) * Math.cos(phi2) * Math.sin(dLambda / 2) * Math.sin(dLambda / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }

    public void setRadiusKm(double radiusKm) {
        this.radiusKm = radiusKm;
    }

    public double getRadiusKm() {
        return radiusKm;
    }
}