package com.example.planehunter.services;

import android.Manifest;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;

import com.example.planehunter.R;
import com.example.planehunter.data.firebase.FirebaseHandler;
import com.example.planehunter.data.network.OpenSkyFetcher;
import com.example.planehunter.data.network.SkyLinkFetcher;
import com.example.planehunter.model.AircraftCategory;
import com.example.planehunter.model.Plane;
import com.example.planehunter.model.UserProfile;
import com.example.planehunter.notifications.NotificationHelper;
import com.example.planehunter.receivers.PlaneBroadcast;
import com.example.planehunter.util.UtilMath;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Foreground service that periodically polls for nearby aircraft using OpenSky API.
 * Handles location updates, data enrichment via SkyLink, and triggers notifications for interesting aircraft.
 */
public class PlaneService extends Service {

    /** Log tag for debugging. */
    private static final String TAG = "PlaneServiceDebug";
    /** Unique ID for the foreground notification. */
    private static final int FOREGROUND_ID = 1;

    /** Intent action to update the polling interval. */
    public static final String ACTION_SET_POLL_INTERVAL = "com.example.planehunter.SET_POLL_INTERVAL";
    /** Intent extra key for the polling interval value in milliseconds. */
    public static final String EXTRA_POLL_INTERVAL_MS = "poll_interval_ms";

    /** Intent action to signal if the app is in the foreground or background. */
    public static final String ACTION_SET_APP_FOREGROUND = "com.example.planehunter.SET_APP_FOREGROUND";
    /** Intent extra key for the app foreground state boolean. */
    public static final String EXTRA_APP_FOREGROUND = "app_foreground";

    /** Default time between aircraft polls (1 minute). */
    private static final long DEFAULT_POLL_INTERVAL = 60_000L;
    /** Minimum allowed time between aircraft polls (2 seconds). */
    private static final long MIN_POLL_INTERVAL = 2_000L;
    /** Maximum allowed time between aircraft polls (5 minutes). */
    private static final long MAX_POLL_INTERVAL = 5 * 60_000L;
    /** Minimum time between duplicate aircraft alerts (30 minutes). */
    private static final long AIRCRAFT_ALERT_COOLDOWN = 30 * 60 * 1000L;
    /** Interval for updating the foreground notification text (5 minutes). */
    private static final long FOREGROUND_UPDATE = 5 * 60_000L;

    /** Timestamp of the last foreground notification update. */
    private long lastForegroundUpdateMs = 0;

    /** Main thread handler for scheduling polling tasks. */
    private Handler handler;
    /** Runnable task that performs the periodic polling. */
    private Runnable task;
    /** Client for accessing device location services. */
    private FusedLocationProviderClient locationClient;

    /** Fetcher for real-time aircraft state data from OpenSky. */
    private OpenSkyFetcher openSkyFetcher;
    /** Fetcher for additional aircraft metadata from SkyLink. */
    private SkyLinkFetcher skyLinkFetcher;

    /** Current time interval between aircraft polls in milliseconds. */
    private long pollIntervalMs = DEFAULT_POLL_INTERVAL;

    /** Singleton instance for Firebase data operations. */
    private FirebaseHandler firebaseHandler;
    /** Whether the main activity is currently in the foreground. */
    private boolean isAppInForeground = false;

    /** Registration for the Firestore real-time profile listener. */
    private ListenerRegistration profileListenerRegistration;
    /** Cache of ICAO24 addresses and their last alert timestamp to prevent notification spam. */
    private final Map<String, Long> lastAlertedIcao24 = new HashMap<>();
    /** Set of aircraft category IDs that the user has opted into for alerts. */
    private final Set<Long> selectedAlertCategories = new HashSet<>();

    @Override
    public void onCreate() {
        super.onCreate();

        NotificationHelper.ensureChannels(this);

        startForeground(
                FOREGROUND_ID,
                NotificationHelper.buildForegroundNotification(this,
                        "✈ PlaneHunter",
                        "Starting...")
        );
;
        locationClient = LocationServices.getFusedLocationProviderClient(this);

        openSkyFetcher = new OpenSkyFetcher();
        openSkyFetcher.setRadiusKm(200); //!Temporary for testing

        String openSkyId = getString(R.string.opensky_client_id);
        String openSkySecret = getString(R.string.opensky_client_secret);
        openSkyFetcher.setClientCredentials(openSkyId, openSkySecret);

        skyLinkFetcher = new SkyLinkFetcher(getApplicationContext(), getString(R.string.skylink_key));

        firebaseHandler = FirebaseHandler.getInstance();
        startListeningToProfileSettings();

        handler = new Handler(Looper.getMainLooper());
        task = () -> {
            pollOnce();
            handler.postDelayed(task, pollIntervalMs);
        };

        handler.post(task);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            if (ACTION_SET_APP_FOREGROUND.equals(intent.getAction())) {
                isAppInForeground = intent.getBooleanExtra(EXTRA_APP_FOREGROUND, false);
                Log.d(TAG, "App foreground = " + isAppInForeground);
                return START_STICKY;
            }

            if (ACTION_SET_POLL_INTERVAL.equals(intent.getAction())) {
                long ms = intent.getLongExtra(EXTRA_POLL_INTERVAL_MS, pollIntervalMs);
                applyPollInterval(ms);
                return START_STICKY;
            }
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "PlaneService destroyed");

        if (profileListenerRegistration != null) {
            profileListenerRegistration.remove();
            profileListenerRegistration = null;
        }

        if (handler != null && task != null) {
            handler.removeCallbacks(task);
        }

        stopForeground(STOP_FOREGROUND_REMOVE);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    /**
     * Updates the polling interval, ensuring it stays within valid bounds.
     * @param requestedMs The new interval in milliseconds.
     */
    private void applyPollInterval(long requestedMs) {
        pollIntervalMs = Math.max(MIN_POLL_INTERVAL, Math.min(requestedMs, MAX_POLL_INTERVAL));

        if (handler != null && task != null) {
            handler.removeCallbacks(task);
            handler.post(task);
        }

        Log.d(TAG, "Poll interval updated to " + pollIntervalMs + " ms");
    }

    /**
     * Performs a single poll for aircraft based on the device's last known location.
     */
    private void pollOnce() {
        if (!hasLocationPermission()) {
            Log.w(TAG, "No location permission");
            return;
        }

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        locationClient.getLastLocation()
                .addOnSuccessListener(location -> {
                    if (location == null) {
                        Log.w(TAG, "Last location is null (GPS off / indoors / no recent fix)");
                        return;
                    }

                    double lat = location.getLatitude();
                    double lon = location.getLongitude();

                    fetchBroadcastAndUpdateFg(lat, lon);
                })
                .addOnFailureListener(e -> Log.e(TAG, "Failed to get location", e));
    }

    /**
     * Checks if the app has the necessary location permissions.
     * @return true if granted, false otherwise.
     */
    private boolean hasLocationPermission() {
        return ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * Fetches planes from OpenSky, enriches data with SkyLink, and broadcasts the results.
     * @param lat User latitude.
     * @param lon User longitude.
     */
    private void fetchBroadcastAndUpdateFg(double lat, double lon) {
        openSkyFetcher.fetchPlanes(lat, lon, planesFound ->{
            if (planesFound == null){
                Log.d(TAG,"Fetch failed, keeping last known planes");
                return;
            }

            skyLinkFetcher.enrichPlanesData(planesFound, () -> {
                Intent intent = PlaneBroadcast.buildPlanesUpdatedIntent(lat, lon, planesFound);
                intent.setPackage(getPackageName());
                sendBroadcast(intent);

                Log.d(TAG, "Broadcast sent. planes=" + planesFound.size());

                notifyForPlanes(lat, lon, planesFound);

                long now = System.currentTimeMillis();
                if (now - lastForegroundUpdateMs < FOREGROUND_UPDATE) {
                    return;
                }

                lastForegroundUpdateMs = now;
                updateForeground(lat, lon, planesFound);
            });
        });
    }

    /**
     * Evaluates found planes and triggers notifications for those matching the user's alert criteria.
     * @param userLat User latitude.
     * @param userLon User longitude.
     * @param planesFound List of planes to check.
     */
    private void notifyForPlanes(double userLat, double userLon, ArrayList<Plane> planesFound) {
        if (planesFound == null || planesFound.isEmpty()) {
            return;
        }

        if (selectedAlertCategories.isEmpty()) {
            return;
        }

        for (Plane plane : planesFound) {
            if (plane == null) {
                continue;
            }

            long category = plane.getCategory();
            if (category == AircraftCategory.UNKNOWN) {
                continue;
            }

            if (!selectedAlertCategories.contains(category)) {
                continue;
            }

            if (wasRecentlyAlerted(plane)) {
                continue;
            }

            double planeLat = plane.getLat();
            double planeLon = plane.getLon();
            if (Double.isNaN(userLat) || Double.isNaN(userLon)) {
                continue;
            }

            double distanceKm = UtilMath.haversineMeters(planeLat, planeLon, userLat, userLon) / 1000.0;
            if (distanceKm > openSkyFetcher.getRadiusKm()) {
                continue;
            }

            String regOrIcao = plane.getRegistration();
            if (regOrIcao == null || regOrIcao.trim().isEmpty()) {
                regOrIcao = plane.getIcao24();
            }

            String title = "Cool aircraft nearby";
            String text = regOrIcao
                    + " • "
                    + AircraftCategory.getDisplayName(category)
                    + " • "
                    + formatKm(distanceKm)
                    + " km away";

            NotificationHelper.showAircraftAlert(this, title, text, plane.getIcao24());
            markPlaneAlerted(plane);
        }
    }

    /**
     * Checks if a notification was already sent for this plane within the cooldown period.
     * @param plane The plane to check.
     * @return true if recently alerted, false otherwise.
     */
    private boolean wasRecentlyAlerted(Plane plane) {
        String icao24 = plane.getIcao24();
        if (icao24 == null || icao24.trim().isEmpty()) {
            return false;
        }

        Long lastTime = lastAlertedIcao24.get(icao24);
        if (lastTime == null) {
            return false;
        }

        return System.currentTimeMillis() - lastTime < AIRCRAFT_ALERT_COOLDOWN;
    }

    /**
     * Marks a plane as having been alerted to start its cooldown timer.
     * @param plane The plane to mark.
     */
    private void markPlaneAlerted(Plane plane) {
        String icao24 = plane.getIcao24();
        if (icao24 == null || icao24.trim().isEmpty()) {
            return;
        }

        lastAlertedIcao24.put(icao24, System.currentTimeMillis());
    }

    /**
     * Updates the status text of the foreground service notification.
     * @param userLat User latitude.
     * @param userLon User longitude.
     * @param planesFound Current list of nearby planes.
     */
    private void updateForeground(double userLat, double userLon, ArrayList<Plane> planesFound) {
        int count = (planesFound == null) ? 0 : planesFound.size();
        double closestKm = findClosestPlaneDistance(userLat, userLon, planesFound);

        String title = "✈ PlaneHunter";
        String text;

        int radius = (int) Math.round(openSkyFetcher.getRadiusKm());

        if (count == 0) {
            text = "No planes in " + radius + "km";
        } else if (Double.isNaN(closestKm)) {
            text = count + " planes in " + radius + "km";
        } else {
            text = count + " planes in " + radius + "km • closest: " + formatKm(closestKm) + " km";
        }

        startForeground(
                FOREGROUND_ID,
                NotificationHelper.buildForegroundNotification(this, title, text)
        );
    }

    /**
     * Finds the distance in kilometers to the closest aircraft in the list.
     * @param userLat User latitude.
     * @param userLon User longitude.
     * @param planesFound List of planes.
     * @return Distance in km, or Double.NaN if no planes are found.
     */
    private double findClosestPlaneDistance(double userLat, double userLon, ArrayList<Plane> planesFound) {
        if (planesFound == null || planesFound.isEmpty()) return Double.NaN;

        double minKm = openSkyFetcher.getRadiusKm();

        for (Plane p : planesFound) {
            if (p == null) continue;

            double lat = p.getLat();
            double lon = p.getLon();
            if (Double.isNaN(lat) || Double.isNaN(lon)) continue;

            double d = UtilMath.haversineMeters(userLat, userLon, lat, lon) / 1000.0;
            if (d < minKm) minKm = d;
        }

        return (minKm == openSkyFetcher.getRadiusKm()) ? Double.NaN : minKm;
    }

    /**
     * Formats a kilometer distance as a string.
     * @param km Distance in kilometers.
     * @return Formatted string.
     */
    private String formatKm(double km) {
        if (Double.isNaN(km)) return "?";
        if (km < 10.0) {
            return String.format(java.util.Locale.US, "%.1f", km);
        }
        return String.valueOf(Math.round(km));
    }

    /**
     * Subscribes to the user's Firestore profile to sync alert settings.
     */
    private void startListeningToProfileSettings() {
        profileListenerRegistration = firebaseHandler.listenToMyProfile(new FirebaseHandler.ProfileListener() {
            @Override
            public void onProfile(@Nullable UserProfile profile) {
                selectedAlertCategories.clear();

                if (profile == null) {
                    selectedAlertCategories.addAll(AircraftCategory.getDefaultAlertCategories());
                    return;
                }

                selectedAlertCategories.addAll(
                        AircraftCategory.normalizeAlertCategories(profile.getAlertCategories())
                );
            }

            @Override
            public void onError(@NonNull Exception e) {
                Log.e(TAG, "Failed to listen to profile", e);
                selectedAlertCategories.clear();
                selectedAlertCategories.addAll(AircraftCategory.getDefaultAlertCategories());
            }
        });
    }
}