package com.example.planehunter.ui.dialogs;

import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.util.Log;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class PlaneSheet extends BottomSheetDialogFragment {

    public interface Listener {
        void onCapturePressed(@NonNull Plane plane);
    }

    private static final String ARG_ICAO = "icao";
    private static final String ARG_CALL = "call";
    private static final String ARG_REG = "reg";
    private static final String ARG_LAT = "lat";
    private static final String ARG_LON = "lon";
    private static final String ARG_ALT = "alt";
    private static final String ARG_TRACK = "track";

    private Plane plane;
    private Listener listener;

    private static final Map<String, String> photoUrlCache = new HashMap<>();
    private static final Set<String> noPhotoCache = new HashSet<>();

    public static PlaneSheet newInstance(@NonNull Plane plane) {

        PlaneSheet sheet = new PlaneSheet();

        Bundle args = new Bundle();
        args.putString(ARG_ICAO, plane.getIcao24());
        args.putString(ARG_CALL, plane.getCallSign());
        args.putString(ARG_REG, plane.getRegistration());
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

        TextView tvRegistration = v.findViewById(R.id.tvRegistration);
        TextView tvTypeCode = v.findViewById(R.id.tvTypeCode);
        TextView tvAltitude = v.findViewById(R.id.tvAltitude);
        TextView tvHeading = v.findViewById(R.id.tvHeading);
        Button btnCapture = v.findViewById(R.id.btnCapture);

        bindBasicDetails(tvRegistration, tvTypeCode, tvAltitude, tvHeading);
        loadPlanePhoto(imgPlane, progPlane);

        btnCapture.setOnClickListener(view -> {
            if (listener != null && plane != null) {
                listener.onCapturePressed(plane);
            }
            dismiss();
        });

        return v;
    }

    private void restorePlaneFromArgs() {

        Bundle args = getArguments();
        if (args == null) return;

        plane = new Plane(
                args.getString(ARG_ICAO),
                args.getString(ARG_CALL),
                args.getDouble(ARG_LAT),
                args.getDouble(ARG_LON),
                args.getDouble(ARG_ALT),
                args.getString(ARG_REG),
                args.getDouble(ARG_TRACK)
        );
    }

    private void bindBasicDetails(TextView registration,
                                  TextView typeCode,
                                  TextView altitude,
                                  TextView heading) {

        if (plane == null) return;

        String reg = plane.getRegistration();
        String type = plane.getTypeName();

        if (reg != null && !reg.trim().isEmpty()) {
            registration.setText("Registration: " + reg.trim());
        } else {
            registration.setText("Registration: Loading...");
        }

        if (type != null && !type.trim().isEmpty()) {
            typeCode.setText("Type: " + type.trim());
        } else {
            typeCode.setText("Type: Loading...");
        }

        altitude.setText(String.format(
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
    }

    private void loadPlanePhoto(ImageView img, ProgressBar prog) {

        if (plane == null) return;

        String icaoRaw = plane.getIcao24();
        if (icaoRaw == null || icaoRaw.trim().isEmpty()) {
            prog.setVisibility(View.GONE);
            img.setImageResource(R.drawable.plane_placeholder);
            return;
        }

        String icao = icaoRaw.trim().toUpperCase(Locale.US);

        String cachedUrl = photoUrlCache.get(icao);
        if (cachedUrl != null) {

            prog.setVisibility(View.VISIBLE);

            Glide.with(this)
                    .load(cachedUrl)
                    .placeholder(R.drawable.plane_placeholder)
                    .error(R.drawable.plane_placeholder)
                    .listener(new RequestListener<Drawable>() {
                        @Override
                        public boolean onLoadFailed(
                                @Nullable GlideException e,
                                Object model,
                                Target<Drawable> target,
                                boolean isFirstResource) {

                            prog.setVisibility(View.GONE);
                            return false;
                        }

                        @Override
                        public boolean onResourceReady(
                                Drawable resource,
                                Object model,
                                Target<Drawable> target,
                                DataSource dataSource,
                                boolean isFirstResource) {

                            prog.setVisibility(View.GONE);
                            return false;
                        }
                    })
                    .into(img);

            return;
        }

        if (noPhotoCache.contains(icao)) {
            prog.setVisibility(View.GONE);
            img.setImageResource(R.drawable.plane_placeholder);
            return;
        }

        prog.setVisibility(View.VISIBLE);

        new Thread(() -> {

            HttpURLConnection conn = null;

            try {
                String apiUrl = "https://skylink-api.p.rapidapi.com/aircraft/icao24/" + icao + "?photos=true";

                conn = (HttpURLConnection) new URL(apiUrl).openConnection();
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(8000);
                conn.setRequestMethod("GET");

                String key = getString(R.string.skylink_key);
                conn.setRequestProperty("x-rapidapi-key", key);
                conn.setRequestProperty("x-rapidapi-host", "skylink-api.p.rapidapi.com");

                int code = conn.getResponseCode();

                InputStream is = (code >= 200 && code < 300)
                        ? conn.getInputStream()
                        : conn.getErrorStream();

                String body = readAll(is);


                Log.d("PlaneSheet", "SkyLink URL=" + apiUrl);
                Log.d("PlaneSheet", "SkyLink code=" + code + " body=" + body);

                updateAircraftInfoFromSkyLink(body);

                if (code < 200 || code >= 300) {
                    safeUi(() -> {
                        prog.setVisibility(View.GONE);
                        img.setImageResource(R.drawable.plane_placeholder);
                    });
                    return;
                }

                String photoUrl = extractFirstPhotoUrl(body);

                Log.d("PlaneSheet", "Extracted photoUrl=" + photoUrl);

                if (photoUrl == null || photoUrl.isEmpty()) {

                    noPhotoCache.add(icao);

                    safeUi(() -> {
                        prog.setVisibility(View.GONE);
                        img.setImageResource(R.drawable.plane_placeholder);
                    });

                    return;
                }

                photoUrlCache.put(icao, photoUrl);

                safeUi(() ->
                        Glide.with(this)
                                .load(photoUrl)
                                .placeholder(R.drawable.plane_placeholder)
                                .error(R.drawable.plane_placeholder)
                                .listener(new RequestListener<Drawable>() {
                                    @Override
                                    public boolean onLoadFailed(
                                            @Nullable GlideException e,
                                            Object model,
                                            Target<Drawable> target,
                                            boolean isFirstResource) {

                                        prog.setVisibility(View.GONE);
                                        return false;
                                    }

                                    @Override
                                    public boolean onResourceReady(
                                            Drawable resource,
                                            Object model,
                                            Target<Drawable> target,
                                            DataSource dataSource,
                                            boolean isFirstResource) {

                                        prog.setVisibility(View.GONE);
                                        return false;
                                    }
                                })
                                .into(img)
                );

            } catch (Exception e) {

                Log.e("PlaneSheet", "loadPlanePhoto exception", e);

                safeUi(() -> {
                    prog.setVisibility(View.GONE);
                    img.setImageResource(R.drawable.plane_placeholder);
                });

            } finally {
                if (conn != null) conn.disconnect();
            }

        }).start();
    }

    private void updateAircraftInfoFromSkyLink(String json) {
        try {
            JSONObject obj = new JSONObject(json);
            JSONObject aircraft = obj.optJSONObject("aircraft");
            if (aircraft == null) return;

            String registration = aircraft.optString("registration", null);
            String typeCode = aircraft.optString("icao_type", null);
            String typeName = aircraft.optString("type_name", null);

            safeUi(() -> {
                if (!isAdded() || plane == null) return;

                plane.setRegistration(registration);
                plane.setTypeName(typeCode);
                plane.setTypeName(typeName);

                View root = getView();
                if (root == null) return;

                TextView tvRegistration = root.findViewById(R.id.tvRegistration);
                TextView tvTypeCode = root.findViewById(R.id.tvTypeCode);

                if (registration != null && !registration.trim().isEmpty()) {
                    tvRegistration.setText("Registration: " + registration.trim());
                } else {
                    tvRegistration.setText("Registration: Unknown");
                }

                if (typeCode != null && !typeCode.trim().isEmpty()) {
                    tvTypeCode.setText("Type: " + typeCode.trim());
                } else {
                    tvTypeCode.setText("Type: Unknown");
                }
            });

        } catch (Exception e) {
            Log.e("PlaneSheet", "updateAircraftInfoFromSkyLink failed", e);
        }
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

            Log.d("PlaneSheet", "Full JSON: " + obj.toString());

            JSONArray photos = obj.optJSONArray("photos");
            if (photos != null && photos.length() > 0) {
                String url = extractUrlFromPhotoObject(photos.optJSONObject(0));
                if (url != null) return url;
            }

            JSONObject aircraft = obj.optJSONObject("aircraft");
            if (aircraft != null) {
                photos = aircraft.optJSONArray("photos");
                if (photos != null && photos.length() > 0) {
                    String url = extractUrlFromPhotoObject(photos.optJSONObject(0));
                    if (url != null) return url;
                }
            }

            JSONObject data = obj.optJSONObject("data");
            if (data != null) {
                photos = data.optJSONArray("photos");
                if (photos != null && photos.length() > 0) {
                    String url = extractUrlFromPhotoObject(photos.optJSONObject(0));
                    if (url != null) return url;
                }

                JSONObject aircraftInData = data.optJSONObject("aircraft");
                if (aircraftInData != null) {
                    photos = aircraftInData.optJSONArray("photos");
                    if (photos != null && photos.length() > 0) {
                        String url = extractUrlFromPhotoObject(photos.optJSONObject(0));
                        if (url != null) return url;
                    }
                }
            }

            return null;

        } catch (Exception e) {
            Log.e("PlaneSheet", "extractFirstPhotoUrl failed", e);
            return null;
        }
    }

    private String extractUrlFromPhotoObject(JSONObject photoObj) {
        if (photoObj == null) return null;

        String url = photoObj.optString("image", null);
        if (url != null && !url.isEmpty()) return url;

        url = photoObj.optString("url", null);
        if (url != null && !url.isEmpty()) return url;

        url = photoObj.optString("link", null);
        if (url != null && !url.isEmpty()) return url;

        return null;
    }

    private void safeUi(Runnable r) {
        if (!isAdded()) return;
        requireActivity().runOnUiThread(() -> {
            if (!isAdded()) return;
            r.run();
        });
    }
}