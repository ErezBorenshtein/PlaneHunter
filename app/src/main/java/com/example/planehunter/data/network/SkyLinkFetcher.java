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

/**
 * Enrichment service that fetches additional aircraft details (registration, type, owner, photo)
 * from the SkyLink API. Implements multi-level caching (memory and SharedPreferences).
 */
public class SkyLinkFetcher {

    /** Tag used for logging. */
    private static final String TAG = "SkyLinkFetcher";
    /** Name of the SharedPreferences file for caching. */
    private static final String PREFS_NAME = "skylink_aircraft_cache";
    /** Prefix for aircraft keys in SharedPreferences. */
    private static final String PREF_KEY_PREFIX = "aircraft_";
    /** Maximum number of API lookups allowed in a single batch. */
    private static final int MAX_LOOKUPS_PER_BATCH = 3;
    /** The SkyLink API key. */
    private final String apiKey;
    /** SharedPreferences instance for persistent caching. */
    private final SharedPreferences prefs;
    /** Handler for posting results to the main thread. */
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    /** Lock object for synchronizing cache access. */
    private final Object cacheLock = new Object();

    /** In-memory cache for aircraft data. */
    private final Map<String, AircraftData> memoryCache = new HashMap<>();
    /** Set of ICAO identifiers that returned no data from the API. */
    private final Set<String> negativeCache = new HashSet<>();
    /** Set of ICAO identifiers currently being fetched. */
    private final Set<String> inFlight = new HashSet<>();

    /**
     * Callback interface to signal completion of an enrichment task.
     */
    public interface DoneCallback {
        /**
         * Called on the main thread when enrichment is complete.
         */
        void onDone();
    }

    /**
     * Data object for aircraft details.
     */
    private static class AircraftData {
        /** Aircraft registration number. */
        String registration;
        /** ICAO type code. */
        String typeCode;
        /** Descriptive type name. */
        String typeName;
        /** Owner or operator name. */
        String ownerOperator;
        /** URL to aircraft photo. */
        String photoUrl;
        /** Whether the aircraft is military. */
        boolean isMilitary;
        /** Aircraft category ID. */
        long category = AircraftCategory.UNKNOWN;
    }

    /**
     * Constructs a new SkyLinkFetcher.
     * @param context Application context for SharedPreferences.
     * @param apiKey API key for the SkyLink RapidAPI service.
     */
    public SkyLinkFetcher(@NonNull Context context, @Nullable String apiKey) {
        Context appContext = context.getApplicationContext();
        this.apiKey = apiKey == null ? "" : apiKey;
        this.prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    /**
     * Enriches a list of planes with metadata from SkyLink. Limits lookups to avoid API rate limiting.
     * @param planes The list of planes to enrich.
     * @param callback Callback notified when finished.
     */
    public void enrichPlanesData(@Nullable ArrayList<Plane> planes, @NonNull DoneCallback callback) {
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

                    applyCachedDataToPlane(plane);

                    if (!needsDataForClassification(plane)) {
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

                    AircraftData data = fetchDataSync(icao, false);
                    lookedUpNow.add(icao);
                    lookupsDone++;

                    if (data != null) {
                        applyDataToPlane(plane, data);
                    }
                }

            } catch (Exception e) {
                Log.e(TAG, "enrichPlanesAsync failed", e);
            }

            postDone(callback);
        }).start();
    }

    /**
     * Enriches a single plane with full metadata including photo URL, suitable for detail sheets.
     * @param plane The plane to enrich.
     * @param callback Callback notified when finished.
     */
    public void enrichPlaneForSheetAsync(@Nullable Plane plane, @NonNull DoneCallback callback) {
        new Thread(() -> {
            try {
                if (plane != null) {
                    applyCachedDataToPlane(plane);

                    String icao = normalizeIcao(plane.getIcao24());
                    if (icao != null && !isInNegativeCache(icao)) {
                        boolean includePhoto = isBlank(plane.getPhotoUrl());
                        boolean needdata = needsdataForSheet(plane);

                        if (needdata || includePhoto) {
                            AircraftData data = fetchDataSync(icao, includePhoto);
                            if (data != null) {
                                applyDataToPlane(plane, data);
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

    private boolean needsDataForClassification(@NonNull Plane plane) {
        return plane.getCategory() == AircraftCategory.UNKNOWN
                || isBlank(plane.getTypeCode())
                || isBlank(plane.getTypeName());
    }

    private boolean needsdataForSheet(@NonNull Plane plane) {
        return isBlank(plane.getRegistration())
                || isBlank(plane.getTypeCode())
                || isBlank(plane.getTypeName())
                || plane.getCategory() == AircraftCategory.UNKNOWN;
    }

    /**
     * Fetches cached data for a given plane.
     * @param plane The plane to fetch data for.
     */
    private void applyCachedDataToPlane(@NonNull Plane plane) {
        String icao = normalizeIcao(plane.getIcao24());
        if (icao == null) {
            ensureCategory(plane);
            return;
        }

        AircraftData data = getCacheEntry(icao);

        if (data == null) {
            data = readDataFromPrefs(icao);
            if (data != null) {
                putMemoryCacheEntry(icao, data);
            }
        }

        if (data != null) {
            applyDataToPlane(plane, data);
            return;
        }

        ensureCategory(plane);
    }

    @Nullable
    private AircraftData fetchDataSync(@NonNull String icao, boolean includePhoto) {
        AircraftData cached = getCacheEntry(icao);
        if (cached != null && (!includePhoto || !isBlank(cached.photoUrl))) {
            return cached;
        }

        AircraftData cachedFromPrefs = readDataFromPrefs(icao);
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
            InputStream inputStream;
            String body;

            if(code >= 200 && code < 300){
                inputStream =connection.getInputStream();
                body = readAll(inputStream);
            }
            else{
                inputStream =connection.getErrorStream();
                body = readAll(inputStream);
                return cachedFromPrefs;
            }

            AircraftData data = parseData(body);
            if (data == null) {
                addToNegativeCache(icao);
                return cachedFromPrefs;
            }

            putMemoryCacheEntry(icao, data);
            writeDataToPrefs(icao, data);
            return data;

        } catch (Exception e) {
            Log.e(TAG, "fetchDataSync failed for " + icao, e);
            return cachedFromPrefs;
        } finally {
            clearInFlight(icao);

            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    @Nullable
    private AircraftData parseData(@Nullable String json) {
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

            AircraftData data = new AircraftData();
            data.registration = trimToNull(aircraft.optString("registration", null));
            data.typeCode = trimToNull(aircraft.optString("icao_type", null));
            data.typeName = trimToNull(aircraft.optString("type_name", null));
            data.ownerOperator = trimToNull(aircraft.optString("owner_operator", null));
            data.isMilitary = aircraft.optBoolean("is_military", false);
            data.photoUrl = extractPhotoUrl(aircraft);

            data.category = AircraftCategoryClassifier.classify(
                    data.typeCode,
                    data.typeName,
                    data.ownerOperator,
                    data.isMilitary
            );

            return data;

        } catch (Exception e) {
            Log.e(TAG, "parsedata failed", e);
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

    private void applyDataToPlane(@NonNull Plane plane, @NonNull AircraftData data) {
        if (!isBlank(data.registration)) {
            plane.setRegistration(data.registration);
        }

        if (!isBlank(data.typeCode)) {
            plane.setTypeCode(data.typeCode);
        }

        if (!isBlank(data.typeName)) {
            plane.setTypeName(data.typeName);
        }

        if (!isBlank(data.ownerOperator)) {
            plane.setOwnerOperator(data.ownerOperator);
        }

        if (!isBlank(data.photoUrl)) {
            plane.setPhotoUrl(data.photoUrl);
        }

        plane.setMilitary(data.isMilitary);

        if (data.category != AircraftCategory.UNKNOWN) {
            plane.setCategory(data.category);
        } else {
            ensureCategory(plane);
        }
    }

    /**
     * Ensures the plane has a valid category.
     * @param plane The plane to ensure.
     */
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
    private AircraftData readDataFromPrefs(@NonNull String icao) {
        try {
            String json = prefs.getString(PREF_KEY_PREFIX + icao, null);
            if (json == null || json.trim().isEmpty()) {
                return null;
            }

            JSONObject obj = new JSONObject(json);

            AircraftData data = new AircraftData();
            data.registration = trimToNull(obj.optString("registration", null));
            data.typeCode = trimToNull(obj.optString("typeCode", null));
            data.typeName = trimToNull(obj.optString("typeName", null));
            data.ownerOperator = trimToNull(obj.optString("ownerOperator", null));
            data.photoUrl = trimToNull(obj.optString("photoUrl", null));
            data.isMilitary = obj.optBoolean("isMilitary", false);
            data.category = obj.optLong("category", AircraftCategory.UNKNOWN);

            return data;

        } catch (Exception e) {
            Log.e(TAG, "readdataFromPrefs failed for " + icao, e);
            return null;
        }
    }

    private void writeDataToPrefs(@NonNull String icao, @NonNull AircraftData data) {
        try {
            JSONObject obj = new JSONObject();
            obj.put("registration", data.registration);
            obj.put("typeCode", data.typeCode);
            obj.put("typeName", data.typeName);
            obj.put("ownerOperator", data.ownerOperator);
            obj.put("photoUrl", data.photoUrl);
            obj.put("isMilitary", data.isMilitary);
            obj.put("category", data.category);

            prefs.edit()
                    .putString(PREF_KEY_PREFIX + icao, obj.toString())
                    .apply();

        } catch (Exception e) {
            Log.e(TAG, "writedataToPrefs failed for " + icao, e);
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
    private AircraftData getCacheEntry(@NonNull String icao) {
        synchronized (cacheLock) {
            return memoryCache.get(icao);
        }
    }

    private void putMemoryCacheEntry(@NonNull String icao, @NonNull AircraftData data) {
        synchronized (cacheLock) {
            memoryCache.put(icao, data);
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

    /**
     * Marks the plane as in flight.
     * @param icao ICAO of the plane.
     * @return True if not already in flight.
     */
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
}