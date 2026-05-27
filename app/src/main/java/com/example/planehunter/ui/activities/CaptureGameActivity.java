package com.example.planehunter.ui.activities;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.planehunter.R;
import com.example.planehunter.ui.views.CaptureGameView;

/**
 * Activity that hosts the plane capture mini-game.
 * The user attempts to "photograph" a moving plane on the screen.
 */
public class CaptureGameActivity extends AppCompatActivity {

    public static final String EXTRA_ICAO24 = "extra_icao24";
    public static final String EXTRA_CALLSIGN = "extra_callsign";

    public static final String RESULT_HIT = "hit";

    private CaptureGameView captureView;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_capture_game);

        captureView = findViewById(R.id.captureView);
        ImageButton btnShutter = findViewById(R.id.btnShutter);

        btnShutter.setOnClickListener(v -> onShutterPressed());
    }

    @Override
    protected void onResume() {
        super.onResume();
        captureView.startGame();
    }

    @Override
    protected void onPause() {
        super.onPause();
        captureView.stopGame();
    }

    /**
     * Logic executed when the user presses the shutter button.
     * Determines if the plane was hit and returns the result to the calling activity.
     */
    private void onShutterPressed() {

        boolean hit = captureView.tryCapture();

        if (hit) {
            Toast.makeText(this, "Nice shot!", Toast.LENGTH_SHORT).show();

            Intent data = new Intent();
            data.putExtra(RESULT_HIT, true);
            setResult(RESULT_OK, data);
            finish();
        } else {
            Toast.makeText(this, "Miss! Try again.", Toast.LENGTH_SHORT).show();
            Intent data = new Intent();
            data.putExtra(RESULT_HIT, false);
            setResult(RESULT_OK, data);
            finish(); //so you wont be able to spam the button
        }
    }
}
