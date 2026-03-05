package com.example.planehunter.ui.dialogs;

import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.example.planehunter.R;
import com.example.planehunter.model.Plane;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;

public class PlaneSheet extends BottomSheetDialogFragment {

    // Listener used to notify the Activity when the user presses Capture
    public interface Listener {
        void onCapturePressed(@NonNull Plane plane);
    }

    private static final String ARG_ICAO = "icao";
    private static final String ARG_CALL = "call";
    private static final String ARG_LAT = "lat";
    private static final String ARG_LON = "lon";
    private static final String ARG_ALT = "alt";
    private static final String ARG_TRACK = "track";

    private Plane plane;
    private Listener listener;

    /**
     * Creates a new instance of the sheet and stores the plane data in arguments.
     * We pass primitive values instead of the object itself to avoid Serializable/Parcelable.
     */
    public static PlaneSheet newInstance(@NonNull Plane plane) {

        PlaneSheet sheet = new PlaneSheet();

        Bundle args = new Bundle();
        args.putString(ARG_ICAO, plane.getIcao24());
        args.putString(ARG_CALL, plane.getCallSign());
        args.putDouble(ARG_LAT, plane.getLat());
        args.putDouble(ARG_LON, plane.getLon());
        args.putDouble(ARG_ALT, plane.getAltitude());
        args.putDouble(ARG_TRACK, plane.getTrackDeg());

        sheet.setArguments(args);
        return sheet;
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {

        View v = inflater.inflate(R.layout.bottomsheet_plane, container, false);

        ImageView imgPlane = v.findViewById(R.id.imgPlane);
        ProgressBar progPlane = v.findViewById(R.id.progPlane);

        restorePlaneFromArgs();

        loadPlanePhoto(imgPlane, progPlane);

        TextView tvTitle = v.findViewById(R.id.tvTitle);
        TextView tvSubtitle = v.findViewById(R.id.tvSubtitle);
        TextView tvAltitude = v.findViewById(R.id.tvAltitude);
        TextView tvHeading = v.findViewById(R.id.tvHeading);
        TextView tvCoords = v.findViewById(R.id.tvCoords);
        Button btnCapture = v.findViewById(R.id.btnCapture);

        bindHeader(tvTitle, tvSubtitle);
        bindDetails(tvAltitude, tvHeading, tvCoords);

        btnCapture.setOnClickListener(view -> {
            if (listener != null && plane != null) {
                listener.onCapturePressed(plane);
            }
            dismiss();
        });

        return v;
    }

    /**
     * Rebuilds a Plane object from the arguments bundle.
     */
    private void restorePlaneFromArgs() {

        Bundle args = getArguments();
        if (args == null) return;

        plane = new Plane(
                args.getString(ARG_ICAO),
                args.getString(ARG_CALL),
                args.getDouble(ARG_LAT),
                args.getDouble(ARG_LON),
                args.getDouble(ARG_ALT),
                args.getDouble(ARG_TRACK)
        );
    }

    /**
     * Displays the main title and subtitle.
     */
    private void bindHeader(TextView title, TextView subtitle) {

        if (plane == null) return;

        String call = plane.getCallSign();
        String icao = plane.getIcao24();

        if (call != null && !call.trim().isEmpty()) {
            title.setText(call.trim());
        } else {
            title.setText("Aircraft");
        }

        subtitle.setText("ICAO24: " + icao);
    }

    /**
     * Displays altitude, heading and coordinates.
     */
    private void bindDetails(TextView alt, TextView heading, TextView coords) {

        if (plane == null) return;

        alt.setText(String.format(
                Locale.US,
                "Altitude: %.0f m",
                plane.getAltitude()
        ));

        if (Double.isNaN(plane.getTrackDeg())) {
            heading.setText("Heading: Unknown");
        } else {
            heading.setText(String.format(
                    Locale.US,
                    "Heading: %.0f°",
                    plane.getTrackDeg()
            ));
        }

        coords.setText(String.format(
                Locale.US,
                "Lat/Lon: %.5f , %.5f",
                plane.getLat(),
                plane.getLon()
        ));
    }

    private void loadPlanePhoto(ImageView img, ProgressBar prog) {

        if (plane == null) return;

        String icaoRaw = plane.getIcao24();
        if (icaoRaw == null || icaoRaw.trim().isEmpty()) {
            prog.setVisibility(View.GONE);
            return;
        }

        prog.setVisibility(View.VISIBLE);

        new Thread(() -> {

            HttpURLConnection conn = null;

            try {
                String icao = icaoRaw.trim().toUpperCase(Locale.US);
                String apiUrl = "https://skylinkapi.p.rapidapi.com/v3/photos/icao24/" + icao;

                conn = (HttpURLConnection) new URL(apiUrl).openConnection();
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(8000);

                String key = getString(R.string.skylink_key);
                conn.setRequestProperty("X-RapidAPI-Key", key);
                conn.setRequestProperty("X-RapidAPI-Host", "skylinkapi.p.rapidapi.com");

                int code = conn.getResponseCode();

                InputStream is = (code >= 200 && code < 300)
                        ? conn.getInputStream()
                        : conn.getErrorStream();

                String body = readAll(is);

                android.util.Log.d("PlaneSheet", "SkyLink code=" + code + " body=" + body);

                if (code < 200 || code >= 300) {
                    safeUi(() -> prog.setVisibility(View.GONE));
                    return;
                }

                String photoUrl = extractFirstPhotoUrl(body);

                if (photoUrl == null || photoUrl.isEmpty()) {
                    safeUi(() -> prog.setVisibility(View.GONE));
                    return;
                }

                safeUi(() ->
                        Glide.with(this)
                                .load(photoUrl)
                                .placeholder(R.drawable.plane_placeholder)
                                .error(R.drawable.plane_placeholder)
                                .listener(new RequestListener<Drawable>() {
                                    @Override
                                    public boolean onLoadFailed(@Nullable GlideException e, Object model, Target<Drawable> target, boolean isFirstResource) {
                                        android.util.Log.e("PlaneSheet", "Glide failed: " + (e == null ? "null" : e.getMessage()));
                                        prog.setVisibility(View.GONE);
                                        return false;
                                    }

                                    @Override
                                    public boolean onResourceReady(Drawable resource, Object model, Target<Drawable> target, DataSource dataSource, boolean isFirstResource) {
                                        prog.setVisibility(View.GONE);
                                        return false;
                                    }
                                })
                                .into(img)
                );

            } catch (Exception e) {
                android.util.Log.e("PlaneSheet", "loadPlanePhoto exception", e);
                safeUi(() -> prog.setVisibility(View.GONE));
            } finally {
                if (conn != null) conn.disconnect();
            }

        }).start();
    }

    private String readAll(InputStream is) throws Exception {
        if (is == null) return "";
        BufferedReader br = new BufferedReader(new InputStreamReader(is));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) sb.append(line);
        return sb.toString();
    }

    private String extractFirstPhotoUrl(String json) {
        try {
            JSONObject obj = new JSONObject(json);

            // לפעמים זה "photos", לפעמים משהו אחר – הלוג למעלה יגיד לנו.
            JSONArray photos = obj.optJSONArray("photos");
            if (photos == null || photos.length() == 0) return null;

            JSONObject p0 = photos.optJSONObject(0);
            if (p0 == null) return null;

            // בדוק בלוג מה השם המדויק. נפוץ: "url" / "link" / "image"
            String url = p0.optString("url", null);
            if (url != null && !url.isEmpty()) return url;

            url = p0.optString("link", null);
            if (url != null && !url.isEmpty()) return url;

            url = p0.optString("image", null);
            return (url == null || url.isEmpty()) ? null : url;

        } catch (Exception e) {
            android.util.Log.e("PlaneSheet", "extractFirstPhotoUrl failed", e);
            return null;
        }
    }

    private void safeUi(Runnable r) {
        if (!isAdded()) return;
        requireActivity().runOnUiThread(() -> {
            if (!isAdded()) return;
            r.run();
        });
    }
}
