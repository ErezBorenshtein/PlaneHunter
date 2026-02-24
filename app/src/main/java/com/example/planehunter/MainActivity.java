package com.example.planehunter;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {

    private RadarView radarView;
    private Button btnStart;
    private Button btnStop;

    private final BroadcastReceiver planesReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (!PlaneBroadcast.ACTION_PLANES_UPDATED.equals(intent.getAction())) return;

            //Toast.makeText(context, "planes update received", Toast.LENGTH_SHORT).show();
            Log.d("PlaneHunterDebug","planes update received");

            String planesJson = intent.getStringExtra(PlaneBroadcast.EXTRA_PLANES_JSON);
            double userLat = intent.getDoubleExtra(PlaneBroadcast.EXTRA_USER_LAT, 0.0);
            double userLon = intent.getDoubleExtra(PlaneBroadcast.EXTRA_USER_LON, 0.0);

            ArrayList<Plane> planes = PlaneBroadcast.planesFromJson(planesJson);

            //Toast.makeText(context, "planes=" + planes.size(), Toast.LENGTH_SHORT).show();
            Log.d("PlaneHunterDebug","planes=" + planes.size());


            radarView.setUserLocation(userLat, userLon);
            radarView.setPlanes(planes);
        }
    };

    private final ActivityResultLauncher<String[]> permissionsLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                boolean ok = hasAllRequiredPermissions();
                if (!ok) {
                    Toast.makeText(this, "Need location (and notifications) permission", Toast.LENGTH_LONG).show();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        NotificationHelper.ensureChannels(this);

        setContentView(R.layout.activity_main);

        radarView = findViewById(R.id.radarView);
        btnStart = findViewById(R.id.btnStart);
        btnStop = findViewById(R.id.btnStop);

        radarView.setOnPlaneClickListener(plane -> {
            String msg = "Call: " + plane.getCallSign()
                    + " | ICAO: " + plane.getIcao24()
                    + " | Alt: " + Math.round(plane.getAltitude()) + "m";
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
        });

        btnStart.setOnClickListener(v -> {
            ensurePermissions();
            if (!hasAllRequiredPermissions()) return;

            startPlaneService();
        });

        btnStop.setOnClickListener(v -> stopPlaneService());


        //! temporary
        radarView.setRadarRangeMeters(100_000.0); // 100km
    }

    @Override
    protected void onStart() {
        super.onStart();

        IntentFilter f = new IntentFilter(PlaneBroadcast.ACTION_PLANES_UPDATED);
        registerReceiver(planesReceiver, f, Context.RECEIVER_NOT_EXPORTED);

        setServicePollInterval(3_000L); //update every 3 seconds when app is opened

        setAppForegroundState(true);

    }

    @Override
    protected void onStop() {
        unregisterReceiverSafe();
        super.onStop();

        setServicePollInterval(60_000L); //update every minute when app is not running

        setAppForegroundState(false);

    }

    private void unregisterReceiverSafe() {
        try {
            unregisterReceiver(planesReceiver);
        } catch (Exception ignored) {}
    }

    private void startPlaneService() {
        Intent i = new Intent(this, PlaneService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(i);
        } else {
            startService(i);
        }
    }

    private void stopPlaneService() {
        Intent i = new Intent(this, PlaneService.class);
        stopService(i);
    }

    private void ensurePermissions() {
        if (hasAllRequiredPermissions()) return;

        permissionsLauncher.launch(buildRequiredPermissionsArray());
    }

    private boolean hasAllRequiredPermissions() {
        boolean hasLoc =
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                        || ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;

        boolean hasNotif = true;
        if (Build.VERSION.SDK_INT >= 33) {
            hasNotif = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
        }

        return hasLoc && hasNotif;
    }

    @NonNull
    private String[] buildRequiredPermissionsArray() {
        if (Build.VERSION.SDK_INT >= 33) {
            return new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.POST_NOTIFICATIONS
            };
        }
        return new String[]{
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
        };
    }

    private void setServicePollInterval(long ms) {
        Intent i = new Intent(this, PlaneService.class);
        i.setAction(PlaneService.ACTION_SET_POLL_INTERVAL);
        i.putExtra(PlaneService.EXTRA_POLL_INTERVAL_MS, ms);
        startService(i);
    }

    private void setAppForegroundState(boolean foreground) {
        Intent i = new Intent(this, PlaneService.class);
        i.setAction(PlaneService.ACTION_SET_APP_FOREGROUND);
        i.putExtra(PlaneService.EXTRA_APP_FOREGROUND, foreground);
        startService(i);
    }

}
