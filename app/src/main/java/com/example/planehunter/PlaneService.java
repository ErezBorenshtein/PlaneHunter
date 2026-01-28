package com.example.planehunter;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;

import androidx.core.app.NotificationCompat;

public class PlaneService extends Service {

    private static final String CHANNEL_ID = "PlaneHunterChannel";
    private Handler handler;
    private Runnable task;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("PlaneHunter running")
                .setContentText("Tracking planes near youu")
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .build();

        startForeground(1, notification);

        handler = new Handler();
        task = new Runnable() {
            @Override
            public void run() {
                OpenSkyFetcher fetcher = new OpenSkyFetcher();
                fetcher.fetchPlanes(currLot,currLat,planes -> {
                    for (Plane plane : planes) {
                        Log.d("banana","ICAO24: " + plane.icao24 + " Callsign: " + plane.callSign);
                    }
                });
                handler.postDelayed(this, 60000);
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "PlaneHunter Background Service",
                    NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(channel);
        }
    }
}
