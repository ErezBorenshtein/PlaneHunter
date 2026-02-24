package com.example.planehunter.receivers;

import android.content.Intent;

import com.example.planehunter.model.Plane;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;

public class PlaneBroadcast {

    public static final String ACTION_PLANES_UPDATED = "com.example.planehunter.PLANES_UPDATED";
    public static final String EXTRA_PLANES_JSON = "planes_json";
    public static final String EXTRA_USER_LAT = "user_lat";
    public static final String EXTRA_USER_LON = "user_lon";

    public static Intent buildPlanesUpdatedIntent(double userLat, double userLon, ArrayList<Plane> planes) {
        Intent i = new Intent(ACTION_PLANES_UPDATED);
        i.putExtra(EXTRA_USER_LAT, userLat);
        i.putExtra(EXTRA_USER_LON, userLon);
        i.putExtra(EXTRA_PLANES_JSON, planesToJson(planes));
        return i;
    }


    public static double getUserLat(Intent intent) {
        if (intent == null) return 0.0;
        return intent.getDoubleExtra(EXTRA_USER_LAT, 0.0);
    }

    public static double getUserLon(Intent intent) {
        if (intent == null) return 0.0;
        return intent.getDoubleExtra(EXTRA_USER_LON, 0.0);
    }

    public static ArrayList<Plane> getPlanes(Intent intent) {
        if (intent == null) return new ArrayList<>();
        String json = intent.getStringExtra(EXTRA_PLANES_JSON);
        return planesFromJson(json);
    }

    public static String planesToJson(ArrayList<Plane> planes) {
        JSONArray arr = new JSONArray();

        if (planes != null) {
            for (Plane p : planes) {
                if (p == null) continue;

                JSONObject o = new JSONObject();
                try {
                    o.put("icao24", safe(p.getIcao24()));
                    o.put("callSign", safe(p.getCallSign()));
                    o.put("lat", p.getLat());
                    o.put("lon", p.getLon());
                    o.put("altitude", p.getAltitude());

                    double track = p.getTrackDeg();
                    if (!Double.isNaN(track)) {
                        o.put("trackDeg", track);
                    }

                    arr.put(o);
                } catch (Exception ignored) {}
            }
        }

        return arr.toString();
    }

    public static ArrayList<Plane> planesFromJson(String json) {
        ArrayList<Plane> planes = new ArrayList<>();
        if (json == null || json.trim().isEmpty()) return planes;

        try {
            JSONArray arr = new JSONArray(json);

            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o == null) continue;

                String icao = safe(o.optString("icao24", ""));
                String call = safe(o.optString("callSign", "N/A"));

                double lat = o.optDouble("lat", Double.NaN);
                double lon = o.optDouble("lon", Double.NaN);
                double alt = o.optDouble("altitude", 0.0);

                double track = o.optDouble("trackDeg", Double.NaN);

                if (Double.isNaN(lat) || Double.isNaN(lon)) continue;

                planes.add(new Plane(icao, call, lat, lon, alt, track));
            }
        } catch (Exception ignored) {}

        return planes;
    }

    private static String safe(String s) {
        return s == null ? "" : s.trim();
    }
}
