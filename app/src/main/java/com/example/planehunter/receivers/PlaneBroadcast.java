package com.example.planehunter.receivers;

import android.content.Intent;

import com.example.planehunter.model.Plane;

import java.util.ArrayList;

/**
 * Utility class for managing broadcasts related to plane updates.
 * Defines intent actions, extra keys, and provides helper methods to build and parse these intents.
 */
public class PlaneBroadcast {

    public static final String ACTION_PLANES_UPDATED = "com.example.planehunter.PLANES_UPDATED";
    public static final String EXTRA_PLANES = "planes";
    public static final String EXTRA_USER_LAT = "user_lat";
    public static final String EXTRA_USER_LON = "user_lon";

    /**
     * Builds an intent to broadcast updated plane data and user location.
     * @param userLat Current user latitude.
     * @param userLon Current user longitude.
     * @param planes List of planes to include in the broadcast.
     * @return An Intent configured with the update action and data.
     */
    public static Intent buildPlanesUpdatedIntent(double userLat, double userLon, ArrayList<Plane> planes) {
        Intent intent = new Intent(ACTION_PLANES_UPDATED);
        intent.putExtra(EXTRA_USER_LAT, userLat);
        intent.putExtra(EXTRA_USER_LON, userLon);
        intent.putParcelableArrayListExtra(EXTRA_PLANES, planes);
        return intent;
    }

    /**
     * Extracts user latitude from a planes updated intent.
     * @param intent The intent received from the broadcast.
     * @return User latitude or Double.NaN if not found.
     */
    public static double getUserLat(Intent intent) {
        if (intent == null) return Double.NaN;
        return intent.getDoubleExtra(EXTRA_USER_LAT, Double.NaN);
    }

    /**
     * Extracts user longitude from a planes updated intent.
     * @param intent The intent received from the broadcast.
     * @return User longitude or Double.NaN if not found.
     */
    public static double getUserLon(Intent intent) {
        if (intent == null) return Double.NaN;
        return intent.getDoubleExtra(EXTRA_USER_LON, Double.NaN);
    }

    /**
     * Extracts the list of planes from a planes updated intent.
     * @param intent The intent received from the broadcast.
     * @return An ArrayList of Plane objects, or an empty list if not found.
     */
    public static ArrayList<Plane> getPlanes(Intent intent) {
        if (intent == null) {
            return new ArrayList<>();
        }

        ArrayList<Plane> planes;
        planes = intent.getParcelableArrayListExtra(EXTRA_PLANES, Plane.class);
        return planes != null ? planes : new ArrayList<>();
    }
}