package com.example.planehunter.data.network;

import android.content.Context;
import android.util.Base64;
import android.util.Log;

import com.example.planehunter.R;
import com.example.planehunter.model.Plane;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Locale;

/**
 * OpenSkyFetcher
 * - Fetches states from OpenSky REST API (states/all) using bbox around a center point.
 * - Supports Basic Auth (username/password).
 * - Applies safe backoff on HTTP 429 (Retry-After if provided; otherwise exponential backoff).
 *
 * NOTE:
 *  - This class tries to fill your Plane model via reflection (setters/fields),
 *    so it should compile even if your Plane class has different method names.
 *  - If some fields don't get populated, adjust the setter names in applyToPlaneViaReflection().
 */
public class OpenSkyFetcher {

    private static final String TAG = "OpenSkyFetcher";
    private static final String API_URL = "https://opensky-network.org/api/states/all";

    public interface PlanesCallback {
        void onPlanesFetched(ArrayList<Plane> planes);

        default void onError(Exception e) {
        }
    }

    // ---- Config ----
    private double radiusKm = 4.0;

    // ---- Backoff state ----
    private long nextAllowedRequestMs = 0;
    private long backoffMs = 0; // exponential backoff for 429, capped

    public OpenSkyFetcher() { }

    public void setRadiusKm(double radiusKm) {
        this.radiusKm = radiusKm;
    }

    public void fetchPlanes(Context ctx, double latCenter, double lonCenter, PlanesCallback callback) {
        new Thread(() -> {
            try {
                if (System.currentTimeMillis() < nextAllowedRequestMs) {
                    Log.w(TAG, "Backoff active. Skipping request until " + nextAllowedRequestMs);
                    callback.onPlanesFetched(new ArrayList<>());
                    return;
                }

                double[] bbox = boundingBox(latCenter, lonCenter, radiusKm);
                String urlString = String.format(
                        Locale.US,
                        "%s?lamin=%f&lamax=%f&lomin=%f&lomax=%f",
                        API_URL, bbox[0], bbox[1], bbox[2], bbox[3]
                );

                Log.d(TAG, "Request URL: " + urlString);

                HttpURLConnection conn = null;
                try {
                    conn = (HttpURLConnection) new URL(urlString).openConnection();
                    configureConnection(ctx, conn);

                    int code = conn.getResponseCode();
                    Log.d(TAG, "HTTP code=" + code);

                    if (code == 429) {
                        handle429(conn);
                        callback.onPlanesFetched(new ArrayList<>());
                        return;
                    }

                    // Reset backoff after any non-429 response
                    backoffMs = 0;

                    InputStream is = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
                    String body = readAll(is);

                    if (code < 200 || code >= 300) {
                        Log.w(TAG, "HTTP error " + code + " body=" + body);
                        callback.onPlanesFetched(new ArrayList<>());
                        return;
                    }

                    ArrayList<Plane> planes = parsePlanes(body, bbox);
                    callback.onPlanesFetched(planes);

                } finally {
                    if (conn != null) conn.disconnect();
                }

            } catch (Exception e) {
                Log.e(TAG, "fetchPlanes failed", e);
                callback.onError(e);
            }
        }).start();
    }

    // --------------------------
    // Networking helpers
    // --------------------------

    private void configureConnection(Context ctx, HttpURLConnection conn) throws Exception {
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(8000);
        conn.setReadTimeout(12000);

        // Good practice
        conn.setRequestProperty("Accept", "application/json");
        conn.setRequestProperty("User-Agent", "PlaneHunter/1.0");

        applyBasicAuth(ctx, conn);
    }

    private void applyBasicAuth(Context ctx, HttpURLConnection conn) {
        // Put these in res/values/opensky.xml:
        // <string name="opensky_user">USERNAME</string>
        // <string name="opensky_pass">PASSWORD</string>
        String user = safeGetString(ctx, R.string.opensky_user);
        String pass = safeGetString(ctx, R.string.opensky_pass);

        if (user == null || pass == null) {
            Log.w(TAG, "OpenSky credentials missing (strings not found). Sending anonymous request.");
            return;
        }

        user = user.trim();
        pass = pass.trim();

        if (user.isEmpty() || pass.isEmpty() || "YOUR_USER".equals(user) || "YOUR_PASS".equals(pass)) {
            Log.w(TAG, "OpenSky credentials not set. Sending anonymous request.");
            return;
        }

        String token = user + ":" + pass;
        String b64 = Base64.encodeToString(token.getBytes(), Base64.NO_WRAP);
        conn.setRequestProperty("Authorization", "Basic " + b64);
    }

    private String safeGetString(Context ctx, int resId) {
        try {
            return ctx.getString(resId);
        } catch (Exception e) {
            return null;
        }
    }

    private void handle429(HttpURLConnection conn) {
        long now = System.currentTimeMillis();

        // Try Retry-After header (seconds)
        long waitMs = parseRetryAfterMs(conn);
        if (waitMs <= 0) {
            // Exponential backoff: 1min, 2min, 4min, ... capped at 15min
            if (backoffMs <= 0) backoffMs = 60_000L;
            else backoffMs = Math.min(backoffMs * 2, 15 * 60_000L);

            waitMs = backoffMs;
        }

        nextAllowedRequestMs = now + waitMs;
        Log.w(TAG, "HTTP 429 - backing off for " + (waitMs / 1000) + "s");
    }

    private long parseRetryAfterMs(HttpURLConnection conn) {
        try {
            String ra = conn.getHeaderField("Retry-After");
            if (ra == null) return 0;
            ra = ra.trim();
            if (ra.isEmpty()) return 0;
            long sec = Long.parseLong(ra);
            return Math.max(60_000L, sec * 1000L);
        } catch (Exception e) {
            return 0;
        }
    }

    private String readAll(InputStream is) throws Exception {
        if (is == null) return "";
        BufferedReader br = new BufferedReader(new InputStreamReader(is));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) sb.append(line);
        return sb.toString();
    }

    // --------------------------
    // Parsing
    // --------------------------

    private ArrayList<Plane> parsePlanes(String body, double[] bbox) throws Exception {
        ArrayList<Plane> out = new ArrayList<>();

        JSONObject root = new JSONObject(body);
        JSONArray states = root.optJSONArray("states");

        int n = (states == null) ? 0 : states.length();
        Log.d(TAG, "states array = " + n);

        if (states == null) return out;

        for (int i = 0; i < states.length(); i++) {
            JSONArray s = states.optJSONArray(i);
            if (s == null) continue;

            // OpenSky "states" array indices (common):
            // 0: icao24 (string)
            // 1: callsign (string)
            // 5: longitude (number)
            // 6: latitude (number)
            // 7: baro_altitude (number)
            // 8: on_ground (boolean)
            // 9: velocity (number)
            // 10: true_track (number)
            // 13: geo_altitude (number)
            String icao24 = optTrimmedString(s, 0);
            String callSign = optTrimmedString(s, 1);

            Double lon = optDouble(s, 5);
            Double lat = optDouble(s, 6);

            if (lat == null || lon == null) continue;
            if (!isInsideBbox(lat, lon, bbox)) continue;

            Double baroAlt = optDouble(s, 7);
            Double geoAlt = optDouble(s, 13);
            double altitude = pickAltitude(baroAlt, geoAlt);

            Boolean onGround = optBoolean(s, 8);
            Double velocity = optDouble(s, 9);
            Double track = optDouble(s, 10);

            Plane p = createPlaneObject(icao24);
            applyToPlaneViaReflection(p, icao24, callSign, lat, lon, altitude, onGround, velocity, track);

            out.add(p);
        }

        Log.d(TAG, "Filtered planes count=" + out.size());
        return out;
    }

    private String optTrimmedString(JSONArray a, int idx) {
        try {
            if (a.isNull(idx)) return "";
            String s = a.optString(idx, "");
            return (s == null) ? "" : s.trim();
        } catch (Exception e) {
            return "";
        }
    }

    private Double optDouble(JSONArray a, int idx) {
        try {
            if (a.isNull(idx)) return null;
            // optDouble returns 0 for non-number sometimes; use get() check
            Object o = a.get(idx);
            if (o instanceof Number) return ((Number) o).doubleValue();
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    private Boolean optBoolean(JSONArray a, int idx) {
        try {
            if (a.isNull(idx)) return null;
            Object o = a.get(idx);
            if (o instanceof Boolean) return (Boolean) o;
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    private double pickAltitude(Double baroAlt, Double geoAlt) {
        if (geoAlt != null) return geoAlt;
        if (baroAlt != null) return baroAlt;
        return 0.0;
    }

    private boolean isInsideBbox(double lat, double lon, double[] bbox) {
        double lamin = bbox[0], lamax = bbox[1], lomin = bbox[2], lomax = bbox[3];
        return lat >= lamin && lat <= lamax && lon >= lomin && lon <= lomax;
    }

    // --------------------------
    // Plane creation / mapping (reflection-safe)
    // --------------------------

    private Plane createPlaneObject(String icao24) {
        // Try Plane(String icao24) then Plane()
        try {
            Constructor<?> c = Plane.class.getConstructor(String.class);
            return (Plane) c.newInstance(icao24);
        } catch (Exception ignored) { }

        try {
            Constructor<?> c0 = Plane.class.getConstructor();
            return (Plane) c0.newInstance();
        } catch (Exception e) {
            // If Plane has no accessible ctor, this will crash; better to know early.
            throw new RuntimeException("Plane model must have a public ctor Plane() or Plane(String).", e);
        }
    }

    private void applyToPlaneViaReflection(
            Plane p,
            String icao24,
            String callSign,
            double lat,
            double lon,
            double altitude,
            Boolean onGround,
            Double velocity,
            Double track
    ) {
        // Try common setter names without breaking compilation if they don't exist.
        invokeSetter(p, "setIcao24", String.class, icao24);
        invokeSetter(p, "setCallSign", String.class, callSign);

        invokeSetter(p, "setLatitude", double.class, lat);
        invokeSetter(p, "setLat", double.class, lat);

        invokeSetter(p, "setLongitude", double.class, lon);
        invokeSetter(p, "setLon", double.class, lon);

        invokeSetter(p, "setAltitude", double.class, altitude);
        invokeSetter(p, "setAlt", double.class, altitude);

        if (onGround != null) {
            invokeSetter(p, "setOnGround", boolean.class, onGround);
        }

        if (velocity != null) {
            invokeSetter(p, "setVelocity", double.class, velocity);
            invokeSetter(p, "setSpeed", double.class, velocity);
        }

        if (track != null) {
            invokeSetter(p, "setHeading", double.class, track);
            invokeSetter(p, "setTrack", double.class, track);
        }
    }

    private void invokeSetter(Object obj, String methodName, Class<?> paramType, Object value) {
        try {
            Method m = obj.getClass().getMethod(methodName, paramType);
            m.invoke(obj, value);
        } catch (Exception ignored) { }
    }

    // --------------------------
    // Geometry
    // --------------------------

    /**
     * Returns bbox: [lamin, lamax, lomin, lomax]
     * Uses a simple approximation:
     *  - 1 deg latitude ~ 111.32 km
     *  - 1 deg longitude ~ 111.32*cos(lat) km
     */
    private double[] boundingBox(double latCenter, double lonCenter, double radiusKm) {
        double latRad = Math.toRadians(latCenter);

        double latDelta = radiusKm / 111.32;
        double lonDeltaDen = 111.32 * Math.cos(latRad);
        double lonDelta = (lonDeltaDen < 1e-6) ? 180.0 : (radiusKm / lonDeltaDen);

        double lamin = clamp(latCenter - latDelta, -90.0, 90.0);
        double lamax = clamp(latCenter + latDelta, -90.0, 90.0);

        double lomin = clamp(lonCenter - lonDelta, -180.0, 180.0);
        double lomax = clamp(lonCenter + lonDelta, -180.0, 180.0);

        return new double[]{lamin, lamax, lomin, lomax};
    }

    private double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    public double getRadiusKm() {
        return radiusKm;
    }
}