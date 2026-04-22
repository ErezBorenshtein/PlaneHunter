package com.example.planehunter.receivers;

import android.content.Intent;

import com.example.planehunter.model.Plane;

import java.util.ArrayList;

public class PlaneBroadcast {

    public static final String ACTION_PLANES_UPDATED = "com.example.planehunter.PLANES_UPDATED";
    public static final String EXTRA_PLANES = "planes";
    public static final String EXTRA_USER_LAT = "user_lat";
    public static final String EXTRA_USER_LON = "user_lon";

    public static Intent buildPlanesUpdatedIntent(double userLat, double userLon, ArrayList<Plane> planes) {
        Intent intent = new Intent(ACTION_PLANES_UPDATED);
        intent.putExtra(EXTRA_USER_LAT, userLat);
        intent.putExtra(EXTRA_USER_LON, userLon);
        intent.putParcelableArrayListExtra(EXTRA_PLANES, planes);
        return intent;
    }

    public static double getUserLat(Intent intent) {
        if (intent == null) return Double.NaN;
        return intent.getDoubleExtra(EXTRA_USER_LAT, Double.NaN);
    }

    public static double getUserLon(Intent intent) {
        if (intent == null) return Double.NaN;
        return intent.getDoubleExtra(EXTRA_USER_LON, Double.NaN);
    }

    public static ArrayList<Plane> getPlanes(Intent intent) {
        if (intent == null) {
            return new ArrayList<>();
        }

        ArrayList<Plane> planes;
        planes = intent.getParcelableArrayListExtra(EXTRA_PLANES, Plane.class);
        return planes != null ? planes : new ArrayList<>();
    }
}