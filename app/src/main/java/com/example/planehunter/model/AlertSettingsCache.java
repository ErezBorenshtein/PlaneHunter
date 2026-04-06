package com.example.planehunter.model;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

public class AlertSettingsCache {
    private static final String PREFS_NAME = "planehunter_alert_settings";
    private static final String KEY_RADIUS_KM = "radius_km";
    private static final String KEY_ALERT_CATEGORIES = "alert_categories";

    public static void save(@NonNull Context context, int radiusKm, @NonNull ArrayList<Long> categories){
        SharedPreferences preferences = context.getSharedPreferences(PREFS_NAME,Context.MODE_PRIVATE);

        preferences.edit()
                .putInt(KEY_RADIUS_KM,radiusKm)
                .putString(KEY_ALERT_CATEGORIES, join(categories))
                .apply();
    }

    public static int getRadiusKm(@NonNull Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getInt(KEY_RADIUS_KM, 5);
    }

    @NonNull
    public static Set<Long> getCategories(@NonNull Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String raw = prefs.getString(KEY_ALERT_CATEGORIES, "");
        return parse(raw);
    }

    private static String join(@NonNull ArrayList<Long> categories) {
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < categories.size(); i++) {
            if (i > 0) {
                sb.append(",");
            }
            sb.append(categories.get(i));
        }

        return sb.toString();
    }

    @NonNull
    private static Set<Long> parse(String raw) {
        Set<Long> result = new HashSet<>();

        if (raw == null || raw.trim().isEmpty()) {
            return result;
        }

        String[] parts = raw.split(",");
        for (String part : parts) {
            try {
                result.add(Long.parseLong(part.trim()));
            } catch (NumberFormatException ignored) {
            }
        }

        return result;
    }
}
