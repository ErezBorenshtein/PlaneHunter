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

public class PlaneService extends Service {

    private static final String TAG = "PlaneServiceDebug";
    private static final int FOREGROUND_ID = 1;

    public static final String ACTION_SET_POLL_INTERVAL = "com.example.planehunter.SET_POLL_INTERVAL";
    public static final String EXTRA_POLL_INTERVAL_MS = "poll_interval_ms";

    public static final String ACTION_SET_APP_FOREGROUND = "com.example.planehunter.SET_APP_FOREGROUND";
    public static final String EXTRA_APP_FOREGROUND = "app_foreground";

    private static final long DEFAULT_POLL_INTERVAL = 60_000L;
    private static final long MIN_POLL_INTERVAL = 2_000L;
    private static final long MAX_POLL_INTERVAL = 5 * 60_000L;
    private static final long AIRCRAFT_ALERT_COOLDOWN = 30 * 60 * 1000L;
    private static final long FOREGROUND_UPDATE = 5 * 60_000L;

    private long lastForegroundUpdateMs = 0;

    private Handler handler;
    private Runnable task;
    private FusedLocationProviderClient locationClient;

    private OpenSkyFetcher fetcher;
    private SkyLinkFetcher skyLinkFetcher;

    private long pollIntervalMs = DEFAULT_POLL_INTERVAL;

    private FirebaseHandler firebaseHandler;
    private boolean isAppInForeground = false;

    private ListenerRegistration profileListenerRegistration;
    private final Map<String, Long> lastAlertedIcao24 = new HashMap<>();
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

        locationClient = LocationServices.getFusedLocationProviderClient(this);

        fetcher = new OpenSkyFetcher();
        fetcher.setRadiusKm(200); //!Temporary

        String openSkyId = getString(R.string.opensky_client_id);
        String openSkySecret = getString(R.string.opensky_client_secret);
        fetcher.setClientCredentials(openSkyId, openSkySecret);

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

    private void applyPollInterval(long requestedMs) {
        pollIntervalMs = Math.max(MIN_POLL_INTERVAL, Math.min(requestedMs, MAX_POLL_INTERVAL));

        if (handler != null && task != null) {
            handler.removeCallbacks(task);
            handler.post(task);
        }

        Log.d(TAG, "Poll interval updated to " + pollIntervalMs + " ms");
    }

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

    private boolean hasLocationPermission() {
        return ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private void fetchBroadcastAndUpdateFg(double lat, double lon) {
        fetcher.fetchPlanes(lat, lon, planesFound ->
                skyLinkFetcher.enrichPlanesAsync(planesFound, () -> {
                    Intent intent = PlaneBroadcast.buildPlanesUpdatedIntent(lat, lon, planesFound);
                    intent.setPackage(getPackageName());
                    sendBroadcast(intent);

                    Log.d(TAG, "Broadcast sent. planes=" + (planesFound == null ? 0 : planesFound.size()));

                    notifyForPlanes(lat, lon, planesFound);

                    long now = System.currentTimeMillis();
                    if (now - lastForegroundUpdateMs < FOREGROUND_UPDATE) {
                        return;
                    }

                    lastForegroundUpdateMs = now;
                    updateForeground(lat, lon, planesFound);
                })
        );
    }

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
            if (distanceKm > fetcher.getRadiusKm()) {
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

    private void markPlaneAlerted(Plane plane) {
        String icao24 = plane.getIcao24();
        if (icao24 == null || icao24.trim().isEmpty()) {
            return;
        }

        lastAlertedIcao24.put(icao24, System.currentTimeMillis());
    }

    private void updateForeground(double userLat, double userLon, ArrayList<Plane> planesFound) {
        int count = (planesFound == null) ? 0 : planesFound.size();
        double closestKm = findClosestPlaneDistance(userLat, userLon, planesFound);

        String title = "✈ PlaneHunter";
        String text;

        int radius = (int) Math.round(fetcher.getRadiusKm());

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

    private double findClosestPlaneDistance(double userLat, double userLon, ArrayList<Plane> planesFound) {
        if (planesFound == null || planesFound.isEmpty()) return Double.NaN;

        double minKm = fetcher.getRadiusKm();

        for (Plane p : planesFound) {
            if (p == null) continue;

            double lat = p.getLat();
            double lon = p.getLon();
            if (Double.isNaN(lat) || Double.isNaN(lon)) continue;

            double d = UtilMath.haversineMeters(userLat, userLon, lat, lon) / 1000.0;
            if (d < minKm) minKm = d;
        }

        return (minKm == fetcher.getRadiusKm()) ? Double.NaN : minKm;
    }

    private String formatKm(double km) {
        if (Double.isNaN(km)) return "?";
        if (km < 10.0) {
            return String.format(java.util.Locale.US, "%.1f", km);
        }
        return String.valueOf(Math.round(km));
    }

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