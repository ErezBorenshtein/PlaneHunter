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

    // Defaults
    private static final long DEFAULT_POLL_INTERVAL_MS = 60_000L;  // 1 minute
    private static final long MIN_POLL_INTERVAL_MS = 2_000L;       // safety clamp
    private static final long MAX_POLL_INTERVAL_MS = 5 * 60_000L;  // safety clamp

    private static final long NOTIFY_COOLDOWN_MS = 10 * 60_000L;   // 10 minutes per aircraft

    private Handler handler;
    private Runnable task;
    private FusedLocationProviderClient locationClient;

    private OpenSkyFetcher fetcher;

    private long pollIntervalMs = DEFAULT_POLL_INTERVAL_MS;

    private final Object planesLock = new Object();
    public final ArrayList<Plane> planes = new ArrayList<>();

    private final Map<String, Long> lastNotifiedMsByIcao = new HashMap<>();

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

        if (intent != null && ACTION_SET_POLL_INTERVAL.equals(intent.getAction())) {
            long ms = intent.getLongExtra(EXTRA_POLL_INTERVAL_MS, pollIntervalMs);
            applyPollInterval(ms);
            return START_STICKY;
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

        long now = System.currentTimeMillis();

        Plane candidate = null;

        for (Plane p : planesFound) {
            if (p == null) continue;

            String icao = safe(p.getIcao24());
            if (icao.isEmpty()) continue;

            long last = lastNotifiedMsByIcao.containsKey(icao) ? lastNotifiedMsByIcao.get(icao) : 0L;
            if (now - last >= NOTIFY_COOLDOWN_MS) {
                candidate = p;
                break;
            }
        }

        if (candidate == null) return;

        String icao = safe(candidate.getIcao24());
        String call = safe(candidate.getCallSign());

        int altM = (int) Math.round(candidate.getAltitude());

        String title = "✈Plane nearby";
        String text;

        if (!call.isEmpty() && !call.equalsIgnoreCase("N/A")) {
            text = call + " | ICAO: " + icao + " | Alt: " + altM + " m";
        } else {
            text = "ICAO: " + icao + " | Alt: " + altM + " m";
        }

        lastNotifiedMsByIcao.put(icao, now);

        int notifId = Math.abs(icao.hashCode());
        NotificationHelper.notifyPlaneFound(this, title, text, notifId);
    }

    private String safe(String s) {
        return s == null ? "" : s.trim();
    }
}
