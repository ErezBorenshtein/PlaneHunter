package com.example.planehunter;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;

import android.location.Location;

import java.util.ArrayList;

public class PlaneService extends Service {

    private static final String CHANNEL_ID = "PlaneHunterChannel";
    private Handler handler;
    private Runnable task;

    public ArrayList<Plane> planes = new ArrayList<Plane>();

    private double currLat = 31.9936; // default
    private double currLon = 34.8828; // default

    private FusedLocationProviderClient locationClient;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();


        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("PlaneHunter running")
                .setContentText("Tracking planes near you")
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .build();

        startForeground(1, notification);

        locationClient = LocationServices.getFusedLocationProviderClient(this);

        handler = new Handler(Looper.getMainLooper());
        task = new Runnable() {
            @Override
            public void run() {
                // Get current location if there is permission
                if (ActivityCompat.checkSelfPermission(
                        PlaneService.this,
                        android.Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED) {

                    locationClient.getLastLocation()
                            .addOnSuccessListener(location -> {
                                if (location != null) {
                                    currLat = location.getLatitude();
                                    currLon = location.getLongitude();
                                }

                                //Fetch planes at the current location
                                OpenSkyFetcher fetcher = new OpenSkyFetcher();
                                fetcher.fetchPlanes(currLat, currLon, planesFound -> {
                                    synchronized (this){
                                        planes.clear();
                                        planes.addAll(planesFound);
                                    }
                                });

                            })
                            .addOnFailureListener(e -> {
                                Log.e("PlaneService", "Failed to get location", e);
                            });

                } else {
                    Log.w("PlaneService", "Location permission not granted");
                }

                handler.postDelayed(this, 60000); // repeat every minute
            }
        };

        handler.post(task);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;

    }

    @Override
    public void onDestroy() {
        handler.removeCallbacks(task);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "PlaneHunter Background Service",
                NotificationManager.IMPORTANCE_LOW
        );
        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.createNotificationChannel(channel);
    }
}
