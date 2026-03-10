package com.example.planehunter.services;

import android.Manifest;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.core.app.ActivityCompat;

import com.example.planehunter.R;
import com.example.planehunter.data.network.OpenSkyFetcher;
import com.example.planehunter.model.Plane;
import com.example.planehunter.notifications.NotificationHelper;
import com.example.planehunter.receivers.PlaneBroadcast;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;

import java.util.ArrayList;

import com.example.planehunter.util.UtilMath;

public class PlaneService extends Service {

    private static final String TAG = "PlaneServiceDebug";
    private static final int FOREGROUND_ID = 1;


    public static final String ACTION_SET_POLL_INTERVAL = "com.example.planehunter.SET_POLL_INTERVAL";
    public static final String EXTRA_POLL_INTERVAL_MS = "poll_interval_ms";

    public static final String ACTION_SET_APP_FOREGROUND = "com.example.planehunter.SET_APP_FOREGROUND";
    public static final String EXTRA_APP_FOREGROUND = "app_foreground";

    private static final long DEFAULT_POLL_INTERVAL_MS = 60_000L;  // 1 minute
    private static final long MIN_POLL_INTERVAL_MS = 2_000L;       // safety clamp
    private static final long MAX_POLL_INTERVAL_MS = 5 * 60_000L;  // safety clamp

    private static final long FOREGROUND_UPDATE_MS = 5 * 60_000L;
    private long lastForegroundUpdateMs = 0;

    private Handler handler;
    private Runnable task;
    private FusedLocationProviderClient locationClient;

    private OpenSkyFetcher fetcher;

    private long pollIntervalMs = DEFAULT_POLL_INTERVAL_MS;

    // keep last known user location for UI/notification context
    //private double lastUserLat = Double.NaN;
    //private double lastUserLon = Double.NaN;

    private boolean isAppInForeground = false;

    @Override
    public void onCreate() {
        super.onCreate();

        NotificationHelper.ensureChannels(this);

        //start foreground with a single ongoing notification
        startForeground(
                FOREGROUND_ID,
                NotificationHelper.buildForegroundNotification(this,
                        "✈ PlaneHunter",
                        "Starting...")
        );

        locationClient = LocationServices.getFusedLocationProviderClient(this);//start location tracking

        fetcher = new OpenSkyFetcher();
        fetcher.setRadiusKm(300); //!temporary for testing

        String id = getString(R.string.opensky_client_id);
        String secret = getString(R.string.opensky_client_secret);
        fetcher.setClientCredentials(id, secret);

        //creates a loop with delay
        handler = new Handler(Looper.getMainLooper());
        task = () -> { //runable variable
            pollOnce();
            handler.postDelayed(task, pollIntervalMs);
        };

        handler.post(task);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        if (intent != null) {

            if (ACTION_SET_APP_FOREGROUND.equals(intent.getAction())) { //update foreground message
                isAppInForeground = intent.getBooleanExtra(EXTRA_APP_FOREGROUND, false);
                Log.d(TAG, "App foreground = " + isAppInForeground);
                return START_STICKY;
            }

            if (ACTION_SET_POLL_INTERVAL.equals(intent.getAction())) { //update interval time
                long ms = intent.getLongExtra(EXTRA_POLL_INTERVAL_MS, pollIntervalMs);
                applyPollInterval(ms);
                return START_STICKY;
            }
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        //stop service
        Log.d(TAG, "PlaneService destroyed");

        if (handler != null && task != null) {
            handler.removeCallbacks(task);
        }

        stopForeground(STOP_FOREGROUND_REMOVE);//stop and remove the notification;

        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {//not bounded service
        return null;
    }

    private void applyPollInterval(long requestedMs) {

        pollIntervalMs = Math.max(MIN_POLL_INTERVAL_MS, Math.min(requestedMs, MAX_POLL_INTERVAL_MS));//for safety check if less than max

        if (handler != null && task != null) {
            handler.removeCallbacks(task);//stop the task
            handler.post(task); //restart the task with new interval
        }

        Log.d(TAG, "Poll interval updated to " + pollIntervalMs + " ms");
    }

    private void pollOnce() {

        if (!hasLocationPermission()) {
            Log.w(TAG, "No location permission");
            return;
        }

        //android studio wanted me to put this too
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) !=
                PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) !=
                        PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
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

                    //lastUserLat = lat;
                    //lastUserLon = lon;

                    fetchBroadcastAndUpdateFg(lat, lon);
                })
                .addOnFailureListener(e -> Log.e(TAG, "Failed to get location", e));
    }

    private boolean hasLocationPermission() {
        return ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private void fetchBroadcastAndUpdateFg(double lat, double lon) {

        fetcher.fetchPlanes(lat, lon, planesFound -> {

            //broadcast data to app (to the UI)
            Intent intent = PlaneBroadcast.buildPlanesUpdatedIntent(lat, lon, planesFound);
            intent.setPackage(getPackageName());
            sendBroadcast(intent);

            Log.d(TAG, "Broadcast sent. planes=" + (planesFound == null ? 0 : planesFound.size()));

            //update the foreground notification every 5 minutes
            long now = System.currentTimeMillis();
            if (now - lastForegroundUpdateMs < FOREGROUND_UPDATE_MS) {
                return;
            }

            lastForegroundUpdateMs = now;
            updateForeground(lat, lon, planesFound);
        });
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

        //update the foreground notification (same ID)
        startForeground(
                FOREGROUND_ID,
                NotificationHelper.buildForegroundNotification(this, title, text)
        );
    }

    private double findClosestPlaneDistance(double userLat, double userLon, ArrayList<Plane> planesFound) {
        if (planesFound == null || planesFound.isEmpty()) return Double.NaN;

        double minKm = fetcher.getRadiusKm(); //closest plane can be maximum in radius

        for (Plane p : planesFound) { //go over each plane and calc distance
            if (p == null) continue; //for safety

            double lat = p.getLat();
            double lon = p.getLon();
            if (Double.isNaN(lat) || Double.isNaN(lon)) continue;

            double d = UtilMath.haversineMeters(userLat, userLon, lat, lon)/1000;//convert meters to KM
            if (d < minKm) minKm = d;
        }

        return (minKm == fetcher.getRadiusKm()) ? Double.NaN : minKm; //NaN if not in radius else minKm
    }

    private String formatKm(double km) {
        //if under 10->in decimal else->round number
        if (Double.isNaN(km)) return "?";
        if (km < 10.0) {
            return String.format(java.util.Locale.US, "%.1f", km);
        }
        return String.valueOf(Math.round(km));
    }


}