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

    private static final long POLL_INTERVAL_MS = 60000;          // 1 minute
    private static final long NOTIFY_COOLDOWN_MS = 10 * 60000;    // 10 minutes per aircraft

    private Handler handler;
    private Runnable task;
    private FusedLocationProviderClient locationClient;

    private OpenSkyFetcher fetcher;

    private final Object planesLock = new Object();
    public final ArrayList<Plane> planes = new ArrayList<>();

    private final Map<String, Long> lastNotifiedMsByIcao = new HashMap<>();

    @Override
    public void onCreate() {
        super.onCreate();

        NotificationHelper.ensureChannels(this);
        startForeground(FOREGROUND_ID, NotificationHelper.buildForegroundNotification(this));

        locationClient = LocationServices.getFusedLocationProviderClient(this);

        handler = new Handler(Looper.getMainLooper());
        task = () -> {
            pollOnce();
            handler.postDelayed(task, POLL_INTERVAL_MS);
        };

        fetcher = new OpenSkyFetcher();

        handler.post(task);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
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

    private void pollOnce() {

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) !=
                PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) !=
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

        this.fetcher.fetchPlanes(lat, lon, planesFound -> {
            synchronized (planesLock) {
                planes.clear();
                planes.addAll(planesFound);
            }

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

        //? Stable per-aircraft notification id (won't spam new ids)
        int notifId = Math.abs(icao.hashCode());

        NotificationHelper.notifyPlaneFound(this, title, text, notifId);
    }

    private String safe(String s) {
        return s == null ? "" : s.trim();
    }
}
