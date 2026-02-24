package com.example.planehunter;

import android.Manifest;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.core.app.ActivityCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class PlaneService extends Service {

    private static final String TAG = "PlaneServiceDebug";
    private static final int FOREGROUND_ID = 1;

    // --- NEW: actions to control service behavior ---
    public static final String ACTION_SET_POLL_INTERVAL = "com.example.planehunter.SET_POLL_INTERVAL";
    public static final String EXTRA_POLL_INTERVAL_MS = "poll_interval_ms";
    public static final String ACTION_SET_APP_FOREGROUND =
            "com.example.planehunter.SET_APP_FOREGROUND";
    public static final String EXTRA_APP_FOREGROUND = "app_foreground";

    // Defaults
    private static final long DEFAULT_POLL_INTERVAL_MS = 60_000L;  // 1 minute
    private static final long MIN_POLL_INTERVAL_MS = 2_000L;       // safety clamp
    private static final long MAX_POLL_INTERVAL_MS = 5 * 60_000L;  // safety clamp

    private static final long NOTIFY_COOLDOWN_MS = 10 * 60_000L;   // 10 minutes per aircraft

    private static final long GLOBAL_NOTIFICATION_COOLDOWN_MS = 5 * 60_000L;
    private long lastSummaryNotificationMs = 0;

    private Handler handler;
    private Runnable task;
    private FusedLocationProviderClient locationClient;

    private OpenSkyFetcher fetcher;

    private long pollIntervalMs = DEFAULT_POLL_INTERVAL_MS;

    private final Object planesLock = new Object();
    public final ArrayList<Plane> planes = new ArrayList<>();

    private final Map<String, Long> lastNotifiedMsByIcao = new HashMap<>();

    private boolean isAppInForeground = false;

    @Override
    public void onCreate() {
        super.onCreate();

        NotificationHelper.ensureChannels(this);
        startForeground(FOREGROUND_ID, NotificationHelper.buildForegroundNotification(this));

        locationClient = LocationServices.getFusedLocationProviderClient(this);

        fetcher = new OpenSkyFetcher();
        fetcher.setRadiusKm(100); // your temporary setting

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
                isAppInForeground =
                        intent.getBooleanExtra(EXTRA_APP_FOREGROUND, false);

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
        if (handler != null && task != null) handler.removeCallbacks(task);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void applyPollInterval(long requestedMs) {
        long clamped = Math.max(MIN_POLL_INTERVAL_MS, Math.min(requestedMs, MAX_POLL_INTERVAL_MS));
        pollIntervalMs = clamped;

        if (handler != null && task != null) {
            handler.removeCallbacks(task);
            handler.post(task); // restart immediately with new cadence
        }

        Log.d(TAG, "Poll interval updated to " + pollIntervalMs + " ms");
    }

    private void pollOnce() {

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) !=
                PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) !=
                        PackageManager.PERMISSION_GRANTED) {
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

                    fetchAndNotify(lat, lon);
                })
                .addOnFailureListener(e -> Log.e(TAG, "Failed to get location", e));
    }

    private void fetchAndNotify(double lat, double lon) {
        fetcher.fetchPlanes(lat, lon, planesFound -> {
            synchronized (planesLock) {
                planes.clear();
                planes.addAll(planesFound);
            }

            Intent i = PlaneBroadcast.buildPlanesUpdatedIntent(lat, lon, planesFound);
            i.setPackage(getPackageName());
            sendBroadcast(i);

            Log.d(TAG, "Broadcast sent. planes=" + (planesFound == null ? 0 : planesFound.size()));

            notifyIfNewPlane(planesFound);
        });
    }

    private void notifyIfNewPlane(ArrayList<Plane> planesFound) {

        if (planesFound == null || planesFound.isEmpty()) return;

        // Do not show notifications while app is in foreground
        if (isAppInForeground) {
            return;
        }

        long now = System.currentTimeMillis();

        // Global cooldown to prevent spam (max once every 5 minutes)
        if (now - lastSummaryNotificationMs < GLOBAL_NOTIFICATION_COOLDOWN_MS) {
            return;
        }

        lastSummaryNotificationMs = now;

        int count = planesFound.size();

        // Find lowest altitude plane for display purposes
        Plane closest = planesFound.get(0);
        double minAlt = closest.getAltitude();

        for (Plane p : planesFound) {
            if (p.getAltitude() < minAlt) {
                closest = p;
                minAlt = p.getAltitude();
            }
        }

        String title = "✈ PlaneHunter";
        String text = "There are " + count + " planes nearby. "
                + "Lowest altitude: " + Math.round(closest.getAltitude()) + "m";

        // Fixed notification ID → updates existing notification instead of spamming
        int notifId = 1001;

        NotificationHelper.notifyPlaneFound(this, title, text, notifId);
    }

    private String safe(String s) {
        return s == null ? "" : s.trim();
    }
}
