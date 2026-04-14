package com.example.planehunter.data.network;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.planehunter.model.AircraftCategory;
import com.example.planehunter.model.AircraftCategoryClassifier;
import com.example.planehunter.model.Plane;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class SkyLinkFetcher {

    private static final String TAG = "SkyLinkFetcher";
    private static final String PREFS_NAME = "skylink_aircraft_cache";
    private static final String PREF_KEY_PREFIX = "aircraft_";
    private static final int MAX_LOOKUPS_PER_BATCH = 3;

    private final Context appContext;
    private final String apiKey;
    private final SharedPreferences prefs;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private final Object cacheLock = new Object();

    private final Map<String, AircraftMetadata> memoryCache = new HashMap<>();
    private final Set<String> negativeCache = new HashSet<>();
    private final Set<String> inFlight = new HashSet<>();

    public interface DoneCallback {
        void onDone();
    }

    public SkyLinkFetcher(@NonNull Context context, @Nullable String apiKey) {
        this.appContext = context.getApplicationContext();
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.prefs = this.appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public void enrichPlanesAsync(@Nullable ArrayList<Plane> planes, @NonNull DoneCallback callback) {
        new Thread(() -> {
            try {
                if (planes == null || planes.isEmpty()) {
                    postDone(callback);
                    return;
                }

                int lookupsDone = 0;
                Set<String> lookedUpNow = new HashSet<>();

                for (Plane plane : planes) {
                    if (plane == null) {
                        continue;
                    }

                    applyCachedMetadataToPlane(plane);

                    if (!needsMetadataForClassification(plane)) {
                        continue;
                    }

                    String icao = normalizeIcao(plane.getIcao24());
                    if (icao == null) {
                        continue;
                    }

                    if (lookedUpNow.contains(icao)) {
                        continue;
                    }

                    if (isInNegativeCache(icao)) {
                        continue;
                    }

                    if (lookupsDone >= MAX_LOOKUPS_PER_BATCH) {
                        continue;
                    }

                    AircraftMetadata metadata = fetchMetadataSync(icao, false);
                    lookedUpNow.add(icao);
                    lookupsDone++;

                    if (metadata != null) {
                        applyMetadataToPlane(plane, metadata);
                    }
                }

                for (Plane plane : planes) {
                    if (plane != null) {
                        applyCachedMetadataToPlane(plane);
                    }
                }

            } catch (Exception e) {
                Log.e(TAG, "enrichPlanesAsync failed", e);
            }

            postDone(callback);
        }).start();
    }

    public void enrichPlaneForSheetAsync(@Nullable Plane plane, @NonNull DoneCallback callback) {
        new Thread(() -> {
            try {
                if (plane != null) {
                    applyCachedMetadataToPlane(plane);

                    String icao = normalizeIcao(plane.getIcao24());
                    if (icao != null && !isInNegativeCache(icao)) {
                        boolean includePhoto = isBlank(plane.getPhotoUrl());
                        boolean needMetadata = needsMetadataForSheet(plane);

                        if (needMetadata || includePhoto) {
                            AircraftMetadata metadata = fetchMetadataSync(icao, includePhoto);
                            if (metadata != null) {
                                applyMetadataToPlane(plane, metadata);
                            }
                        }
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "enrichPlaneForSheetAsync failed", e);
            }

            postDone(callback);
        }).start();
    }

    private void postDone(@NonNull DoneCallback callback) {
        mainHandler.post(callback::onDone);
    }

    private boolean needsMetadataForClassification(@NonNull Plane plane) {
        return plane.getCategory() == AircraftCategory.UNKNOWN
                || isBlank(plane.getTypeCode())
                || isBlank(plane.getTypeName());
    }

    private boolean needsMetadataForSheet(@NonNull Plane plane) {
        return isBlank(plane.getRegistration())
                || isBlank(plane.getTypeCode())
                || isBlank(plane.getTypeName())
                || plane.getCategory() == AircraftCategory.UNKNOWN;
    }

    private void applyCachedMetadataToPlane(@NonNull Plane plane) {
        String icao = normalizeIcao(plane.getIcao24());
        if (icao == null) {
            ensureCategory(plane);
            return;
        }

        AircraftMetadata metadata = getMemoryCacheEntry(icao);

        if (metadata == null) {
            metadata = readMetadataFromPrefs(icao);
            if (metadata != null) {
                putMemoryCacheEntry(icao, metadata);
            }
        }

        if (metadata != null) {
            applyMetadataToPlane(plane, metadata);
            return;
        }

        ensureCategory(plane);
    }

    @Nullable
    private AircraftMetadata fetchMetadataSync(@NonNull String icao, boolean includePhoto) {
        AircraftMetadata cached = getMemoryCacheEntry(icao);
        if (cached != null && (!includePhoto || !isBlank(cached.photoUrl))) {
            return cached;
        }

        AircraftMetadata cachedFromPrefs = readMetadataFromPrefs(icao);
        if (cachedFromPrefs != null) {
            putMemoryCacheEntry(icao, cachedFromPrefs);

            if (!includePhoto || !isBlank(cachedFromPrefs.photoUrl)) {
                return cachedFromPrefs;
            }
        }

        if (isBlank(apiKey)) {
            return cachedFromPrefs;
        }

        if (!tryMarkInFlight(icao)) {
            return cachedFromPrefs;
        }

        HttpURLConnection connection = null;

        try {
            String url = "https://skylink-api.p.rapidapi.com/aircraft/icao24/" + icao + "?photos=" + includePhoto;

            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(8000);
            connection.setReadTimeout(8000);
            connection.setRequestProperty("x-rapidapi-key", apiKey);
            connection.setRequestProperty("x-rapidapi-host", "skylink-api.p.rapidapi.com");

            int code = connection.getResponseCode();
            InputStream inputStream = (code >= 200 && code < 300)
                    ? connection.getInputStream()
                    : connection.getErrorStream();

            String body = readAll(inputStream);

            Log.d(TAG, "SkyLink URL=" + url);
            Log.d(TAG, "SkyLink code=" + code + " body=" + body);

            if (code < 200 || code >= 300) {
                return cachedFromPrefs;
            }

            AircraftMetadata metadata = parseMetadata(body);
            if (metadata == null) {
                addToNegativeCache(icao);
                return cachedFromPrefs;
            }

            putMemoryCacheEntry(icao, metadata);
            writeMetadataToPrefs(icao, metadata);
            return metadata;

        } catch (Exception e) {
            Log.e(TAG, "fetchMetadataSync failed for " + icao, e);
            return cachedFromPrefs;
        } finally {
            clearInFlight(icao);

            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    @Nullable
    private AircraftMetadata parseMetadata(@Nullable String json) {
        if (json == null || json.trim().isEmpty()) {
            return null;
        }

        try {
            JSONObject root = new JSONObject(json);
            boolean found = root.optBoolean("found", true);
            if (!found) {
                return null;
            }

            JSONObject aircraft = root.optJSONObject("aircraft");
            if (aircraft == null) {
                return null;
            }

            AircraftMetadata metadata = new AircraftMetadata();
            metadata.registration = trimToNull(aircraft.optString("registration", null));
            metadata.typeCode = trimToNull(aircraft.optString("icao_type", null));
            metadata.typeName = trimToNull(aircraft.optString("type_name", null));
            metadata.ownerOperator = trimToNull(aircraft.optString("owner_operator", null));
            metadata.isMilitary = aircraft.optBoolean("is_military", false);
            metadata.photoUrl = extractPhotoUrl(aircraft);

            metadata.category = AircraftCategoryClassifier.classify(
                    metadata.typeCode,
                    metadata.typeName,
                    metadata.ownerOperator,
                    metadata.isMilitary
            );

            return metadata;

        } catch (Exception e) {
            Log.e(TAG, "parseMetadata failed", e);
            return null;
        }
    }

    @Nullable
    private String extractPhotoUrl(@NonNull JSONObject aircraft) {
        JSONArray photos = aircraft.optJSONArray("photos");
        if (photos == null || photos.length() == 0) {
            return null;
        }

        JSONObject firstPhoto = photos.optJSONObject(0);
        if (firstPhoto == null) {
            return null;
        }

        String url = trimToNull(firstPhoto.optString("image", null));
        if (url != null) {
            return url;
        }

        url = trimToNull(firstPhoto.optString("url", null));
        if (url != null) {
            return url;
        }

        return trimToNull(firstPhoto.optString("link", null));
    }

    private void applyMetadataToPlane(@NonNull Plane plane, @NonNull AircraftMetadata metadata) {
        if (!isBlank(metadata.registration)) {
            plane.setRegistration(metadata.registration);
        }

        if (!isBlank(metadata.typeCode)) {
            plane.setTypeCode(metadata.typeCode);
        }

        if (!isBlank(metadata.typeName)) {
            plane.setTypeName(metadata.typeName);
        }

        if (!isBlank(metadata.ownerOperator)) {
            plane.setOwnerOperator(metadata.ownerOperator);
        }

        if (!isBlank(metadata.photoUrl)) {
            plane.setPhotoUrl(metadata.photoUrl);
        }

        plane.setMilitary(metadata.isMilitary);

        if (metadata.category != AircraftCategory.UNKNOWN) {
            plane.setCategory(metadata.category);
        } else {
            ensureCategory(plane);
        }
    }

    private void ensureCategory(@NonNull Plane plane) {
        long category = AircraftCategoryClassifier.classify(
                plane.getTypeCode(),
                plane.getTypeName(),
                plane.getOwnerOperator(),
                plane.isMilitary()
        );

        plane.setCategory(category);
    }

    @Nullable
    private AircraftMetadata readMetadataFromPrefs(@NonNull String icao) {
        try {
            String json = prefs.getString(PREF_KEY_PREFIX + icao, null);
            if (json == null || json.trim().isEmpty()) {
                return null;
            }

            JSONObject obj = new JSONObject(json);

            AircraftMetadata metadata = new AircraftMetadata();
            metadata.registration = trimToNull(obj.optString("registration", null));
            metadata.typeCode = trimToNull(obj.optString("typeCode", null));
            metadata.typeName = trimToNull(obj.optString("typeName", null));
            metadata.ownerOperator = trimToNull(obj.optString("ownerOperator", null));
            metadata.photoUrl = trimToNull(obj.optString("photoUrl", null));
            metadata.isMilitary = obj.optBoolean("isMilitary", false);
            metadata.category = obj.optLong("category", AircraftCategory.UNKNOWN);

            return metadata;

        } catch (Exception e) {
            Log.e(TAG, "readMetadataFromPrefs failed for " + icao, e);
            return null;
        }
    }

    private void writeMetadataToPrefs(@NonNull String icao, @NonNull AircraftMetadata metadata) {
        try {
            JSONObject obj = new JSONObject();
            obj.put("registration", metadata.registration);
            obj.put("typeCode", metadata.typeCode);
            obj.put("typeName", metadata.typeName);
            obj.put("ownerOperator", metadata.ownerOperator);
            obj.put("photoUrl", metadata.photoUrl);
            obj.put("isMilitary", metadata.isMilitary);
            obj.put("category", metadata.category);

            prefs.edit()
                    .putString(PREF_KEY_PREFIX + icao, obj.toString())
                    .apply();

        } catch (Exception e) {
            Log.e(TAG, "writeMetadataToPrefs failed for " + icao, e);
        }
    }

    @Nullable
    private String normalizeIcao(@Nullable String icao24) {
        if (icao24 == null) {
            return null;
        }

        String trimmed = icao24.trim();
        if (trimmed.isEmpty()) {
            return null;
        }

        return trimmed.toUpperCase(Locale.US);
    }

    @Nullable
    private String trimToNull(@Nullable String value) {
        if (value == null) {
            return null;
        }

        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private boolean isBlank(@Nullable String value) {
        return value == null || value.trim().isEmpty();
    }

    @NonNull
    private String readAll(@Nullable InputStream inputStream) throws Exception {
        if (inputStream == null) {
            return "";
        }

        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
        StringBuilder builder = new StringBuilder();
        String line;

        while ((line = reader.readLine()) != null) {
            builder.append(line);
        }

        reader.close();
        return builder.toString();
    }

    @Nullable
    private AircraftMetadata getMemoryCacheEntry(@NonNull String icao) {
        synchronized (cacheLock) {
            return memoryCache.get(icao);
        }
    }

    private void putMemoryCacheEntry(@NonNull String icao, @NonNull AircraftMetadata metadata) {
        synchronized (cacheLock) {
            memoryCache.put(icao, metadata);
        }
    }

    private boolean isInNegativeCache(@NonNull String icao) {
        synchronized (cacheLock) {
            return negativeCache.contains(icao);
        }
    }

    private void addToNegativeCache(@NonNull String icao) {
        synchronized (cacheLock) {
            negativeCache.add(icao);
        }
    }

    private boolean tryMarkInFlight(@NonNull String icao) {
        synchronized (cacheLock) {
            if (inFlight.contains(icao)) {
                return false;
            }

            inFlight.add(icao);
            return true;
        }
    }

    private void clearInFlight(@NonNull String icao) {
        synchronized (cacheLock) {
            inFlight.remove(icao);
        }
    }

    private static class AircraftMetadata {
        String registration;
        String typeCode;
        String typeName;
        String ownerOperator;
        String photoUrl;
        boolean isMilitary;
        long category = AircraftCategory.UNKNOWN;
    }
}