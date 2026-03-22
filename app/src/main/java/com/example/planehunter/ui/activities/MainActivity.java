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
    private static final int REQUEST_CAPTURE_GAME = 2001;

    private RadarView radarView;
    private Button btnLogout;
    private Button btnLeaderBoard;
    private Button btnSettings;

    private boolean isLoggingOut = false;
    private boolean isReceiverRegistered = false;

    private Plane pendingCapturePlane;

    private final BroadcastReceiver planesReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null) return;
            if (!PlaneBroadcast.ACTION_PLANES_UPDATED.equals(intent.getAction())) return;

            Log.d(TAG, "planes update received");

            double userLat = PlaneBroadcast.getUserLat(intent);
            double userLon = PlaneBroadcast.getUserLon(intent);
            ArrayList<Plane> planes = PlaneBroadcast.getPlanes(intent);

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
                    Toast.makeText(
                            this,
                            "Background location not granted (service may not update in background)",
                            Toast.LENGTH_LONG
                    ).show();
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
        btnLogout = findViewById(R.id.btnLogout);
        btnLeaderBoard = findViewById(R.id.btnLeaderboard);
        btnSettings = findViewById(R.id.btnSettings);

        radarView.setOnPlaneClickListener(plane -> {
            radarView.setSelectedPlane(plane);

            PlaneSheet sheet = PlaneSheet.newInstance(plane);
            sheet.setListener(p -> {
                pendingCapturePlane = p;

                Intent i = new Intent(this, CaptureGameActivity.class);
                i.putExtra(CaptureGameActivity.EXTRA_ICAO24, p.getIcao24());
                i.putExtra(CaptureGameActivity.EXTRA_CALLSIGN, p.getCallSign());
                startActivityForResult(i, REQUEST_CAPTURE_GAME);
            });

            sheet.show(getSupportFragmentManager(), "plane_sheet");
        });

        btnLogout.setOnClickListener(view -> {
            isLoggingOut = true;

            FirebaseHandler.getInstance().signOut();
            stopPlaneService();

            Intent i = new Intent(this, LogIn.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(i);
            finish();
        });

        btnLeaderBoard.setOnClickListener(view -> {
            Intent intent = new Intent(MainActivity.this, LeaderboardActivity.class);
            startActivity(intent);
        });

        btnSettings.setOnClickListener(View ->{
            Intent intent = new Intent(MainActivity.this, Settings.class);
            startActivity(intent);
        });

        // Temporary
        radarView.setRadarRangeMeters(300_000.0); // 300 km
        // radarView.setRadarRangeMeters(100_000.0); // 100 km
    }

    @Override
    protected void onStart() {
        super.onStart();

        registerPlanesReceiver();
        loadCooldownPlanes();

        // setServicePollInterval(3_000L); // update every 3 seconds when app is opened
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

        if (requestCode != REQUEST_CAPTURE_GAME || resultCode != RESULT_OK || data == null) {
            return;
        }

        boolean hit = data.getBooleanExtra(CaptureGameActivity.RESULT_HIT, false);
        if (!hit) {
            return;
        }

        if (pendingCapturePlane == null) {
            Toast.makeText(this, "Capture succeeded but plane data is missing", Toast.LENGTH_SHORT).show();
            return;
        }

        FirebaseHandler.getInstance()
                .awardCaptureXp(pendingCapturePlane)
                .addOnSuccessListener(result -> {
                    if (result.cooldownActive) {
                        long minutesLeft = Math.max(1L, (result.cooldownRemainingMs + 59999L) / 60000L);
                        Toast.makeText(
                                this,
                                "You already captured this plane recently. Try again in about " + minutesLeft + " min",
                                Toast.LENGTH_LONG
                        ).show();
                        return;
                    }

                    if (!result.awarded) {
                        Toast.makeText(this, "No XP awarded", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    radarView.addCooldownIcao(pendingCapturePlane.getIcao24());//add plane to cooldwon list

                    String msg = result.firstTime
                            ? "New plane! +" + result.xpAwarded + " XP"
                            : "Captured again! +" + result.xpAwarded + " XP";

                    Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
                })
                .addOnFailureListener(e -> Toast.makeText(
                        this,
                        "Failed to award XP: " + e.getMessage(),
                        Toast.LENGTH_LONG
                ).show());
    }

    private void loadCooldownPlanes() {
        FirebaseHandler.getInstance()
                .getMyPlanesInCooldown()
                .addOnSuccessListener(cooldownIcaos -> radarView.setCooldownIcaos(cooldownIcaos))
                .addOnFailureListener(e -> Log.w(TAG, "Failed to load cooldown planes", e));
    }

    private void registerPlanesReceiver() {
        if (isReceiverRegistered) return;

        IntentFilter filter = new IntentFilter(PlaneBroadcast.ACTION_PLANES_UPDATED);

        registerReceiver(planesReceiver, filter, Context.RECEIVER_NOT_EXPORTED);

        isReceiverRegistered = true;
    }

    private void unregisterReceiverSafe() {
        if (!isReceiverRegistered) return;

        try {
            unregisterReceiver(planesReceiver);
        } catch (Exception e) {
            Log.w(TAG, "Receiver was not registered or already unregistered", e);
        }

        isReceiverRegistered = false;
    }

    private void stopPlaneService() {
        Intent i = new Intent(this, PlaneService.class);
        stopService(i);
    }

    private void ensurePermissions() {
        if (hasAllRequiredPermissions()) {
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            hasNotif = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    == PackageManager.PERMISSION_GRANTED;
        }

        return hasLoc && hasNotif;
    }

    private boolean hasBackgroundLocationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return true;
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void requestBackgroundLocationIfNeeded() {
        // Must request separately after foreground location is granted
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return;
        if (!hasForegroundLocationPermission()) return;
        if (hasBackgroundLocationPermission()) return;

        backgroundLocationLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION);
    }

    private boolean hasForegroundLocationPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    @NonNull
    private String[] buildRequiredPermissionsArray() {
        // Do not include ACCESS_BACKGROUND_LOCATION here.
        // Android will only offer "While using the app" if you request background together with foreground.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
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