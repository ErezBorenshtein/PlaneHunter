package com.example.planehunter.ui.activities;

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
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.planehunter.R;
import com.example.planehunter.data.firebase.FirebaseHandler;
import com.example.planehunter.model.Plane;
import com.example.planehunter.notifications.NotificationHelper;
import com.example.planehunter.receivers.PlaneBroadcast;
import com.example.planehunter.services.PlaneService;
import com.example.planehunter.ui.dialogs.PlaneSheet;
import com.example.planehunter.ui.views.RadarView;

import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "PlaneHunterDebug";

    private RadarView radarView;
    private Button btnStart;
    private Button btnStop;
    private Button btnLogout;

    private boolean isLoggingOut = false;

    private final BroadcastReceiver planesReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (!PlaneBroadcast.ACTION_PLANES_UPDATED.equals(intent.getAction())) return;

            Log.d(TAG, "planes update received");

            String planesJson = intent.getStringExtra(PlaneBroadcast.EXTRA_PLANES_JSON);
            double userLat = intent.getDoubleExtra(PlaneBroadcast.EXTRA_USER_LAT, 0.0);
            double userLon = intent.getDoubleExtra(PlaneBroadcast.EXTRA_USER_LON, 0.0);

            ArrayList<Plane> planes = PlaneBroadcast.planesFromJson(planesJson);

            Log.d(TAG, "planes=" + planes.size());

            radarView.setUserLocation(userLat, userLon);
            radarView.setPlanes(planes);
        }
    };

    // Foreground permissions (location + notifications on Android 13+)
    private final ActivityResultLauncher<String[]> permissionsLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                if (!hasAllRequiredPermissions()) {
                    Toast.makeText(this, "Need location (and notifications) permission", Toast.LENGTH_LONG).show();
                    return;
                }

                // If foreground permissions are OK, request background separately
                requestBackgroundLocationIfNeeded();
            });

    // Background location permission (requested separately)
    private final ActivityResultLauncher<String> backgroundLocationLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (!granted) {
                    Toast.makeText(this,
                            "Background location not granted (service may not update in background)",
                            Toast.LENGTH_LONG).show();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (!FirebaseHandler.getInstance().isSignedIn()) {
            startActivity(new Intent(this, LogIn.class));
            finish();
            return;
        }

        NotificationHelper.ensureChannels(this);

        setContentView(R.layout.activity_main);

        radarView = findViewById(R.id.radarView);
        btnStart = findViewById(R.id.btnStart);
        btnStop = findViewById(R.id.btnStop);
        btnLogout = findViewById(R.id.btnLogout);

        radarView.setOnPlaneClickListener(plane -> {
            radarView.setSelectedPlane(plane);
            PlaneSheet sheet = PlaneSheet.newInstance(plane);

            sheet.setListener(p -> {
                Intent i = new Intent(this, CaptureGameActivity.class);
                i.putExtra(CaptureGameActivity.EXTRA_ICAO24, p.getIcao24());
                i.putExtra(CaptureGameActivity.EXTRA_CALLSIGN, p.getCallSign());
                startActivityForResult(i, 2001);
                // This will later connect to the XP / capture system
                // capturePlane(p);
            });

            sheet.show(getSupportFragmentManager(), "plane_sheet");

        });

        btnStart.setOnClickListener(v -> {
            ensurePermissions();
            if (!hasAllRequiredPermissions()) return;

            // Ask background (if needed) before starting the service
            requestBackgroundLocationIfNeeded();

            startPlaneService();
        });

        btnStop.setOnClickListener(v -> stopPlaneService());

        btnLogout.setOnClickListener(view -> {
            isLoggingOut = true;

            FirebaseHandler.getInstance().signOut();

            stopPlaneService();

            Intent i = new Intent(this, LogIn.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(i);
            finish();
        });

        //! temporary
        radarView.setRadarRangeMeters(300_000.0); // 300km
        //radarView.setRadarRangeMeters(100_000.0); // 100km
    }

    @Override
    protected void onStart() {
        super.onStart();

        IntentFilter f = new IntentFilter(PlaneBroadcast.ACTION_PLANES_UPDATED);
        registerReceiver(planesReceiver, f, Context.RECEIVER_NOT_EXPORTED);

        //setServicePollInterval(3_000L); // update every 3 seconds when app is opened
        setServicePollInterval(25_000L); // update every 25 seconds when app is opened

        setAppForegroundState(true);
    }

    @Override
    protected void onStop() {
        unregisterReceiverSafe();
        super.onStop();

        if (isLoggingOut) {
            return;
        }

        setServicePollInterval(60_000L * 3);
        setAppForegroundState(false);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == 2001 && resultCode == RESULT_OK && data != null) {
            boolean hit = data.getBooleanExtra(CaptureGameActivity.RESULT_HIT, false);
            if (hit) {
                // TODO: give XP + save capture to Firebase
            }
        }
    }

    private void unregisterReceiverSafe() {
        try {
            unregisterReceiver(planesReceiver);
        } catch (Exception ignored) {}
    }

    private void startPlaneService() {
        Intent i = new Intent(this, PlaneService.class);
        startForegroundService(i);
    }

    private void stopPlaneService() {
        Intent i = new Intent(this, PlaneService.class);
        stopService(i);
    }

    private void ensurePermissions() {
        if (hasAllRequiredPermissions()) {
            // If we already have required foreground permissions, we can still request background (if needed)
            requestBackgroundLocationIfNeeded();
            return;
        }

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

    private boolean hasBackgroundLocationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return true; // No background permission pre-Android 10
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void requestBackgroundLocationIfNeeded() {
        // Must request separately AFTER foreground location is granted
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return; // Android 9 and below
        if (!hasForegroundLocationPermission()) return;            // don’t ask background before foreground
        if (hasBackgroundLocationPermission()) return;

        backgroundLocationLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION);
    }

    private boolean hasForegroundLocationPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    @NonNull
    private String[] buildRequiredPermissionsArray() {
        // IMPORTANT: do NOT include ACCESS_BACKGROUND_LOCATION here.
        // Android will only offer "While using the app" if you request background together with foreground.
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