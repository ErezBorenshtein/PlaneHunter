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

    private static final String ARG_PLANE = "plane";

    private Plane plane;
    private Listener listener;

    //cashes to save API calls
    private static final Map<String, String> photoUrlCache = new HashMap<>();
    private static final Set<String> noPhotoCache = new HashSet<>();
    private static final Map<String, String> registrationCache = new HashMap<>();
    private static final Map<String, String> typeCodeCache = new HashMap<>();

    private boolean isLoadingAircraftInfo = false;

    public static PlaneSheet newInstance(@NonNull Plane plane) {

        PlaneSheet sheet = new PlaneSheet();

        Bundle args = new Bundle();
        args.putParcelable(ARG_PLANE, plane);
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
        isLoadingAircraftInfo = shouldLoadAircraftInfo();

        TextView tvRegistration = v.findViewById(R.id.tvRegistration);
        TextView tvTypeName = v.findViewById(R.id.tvTypeName);
        TextView tvAltitude = v.findViewById(R.id.tvAltitude);
        TextView tvHeading = v.findViewById(R.id.tvHeading);
        Button btnCapture = v.findViewById(R.id.btnCapture);

        bindBasicDetails(tvRegistration, tvTypeName, tvAltitude, tvHeading);
        loadPlanePhoto(imgPlane, progPlane);

        btnCapture.setOnClickListener(view -> {
            if (listener != null && plane != null) {
                listener.onCapturePressed(plane);
            }
            dismiss();
        });

        return v;
    }

    private void refreshAircraftTextViews() {
        View root = getView();
        if (root == null) return;

        bindBasicDetails(
                root.findViewById(R.id.tvRegistration),
                root.findViewById(R.id.tvTypeName),
                root.findViewById(R.id.tvAltitude),
                root.findViewById(R.id.tvHeading)
        );
    }

    private boolean shouldLoadAircraftInfo() {
        if (plane == null) return false;

        return isBlank(plane.getRegistration())
                || isBlank(plane.getTypeCode())
                || isBlank(plane.getTypeName());
    }

    private void restorePlaneFromArgs() {
        Bundle args = getArguments();
        if (args == null) return;

        plane = args.getParcelable(ARG_PLANE);
        if (plane == null) return;

        String icao = normalizeIcao(plane.getIcao24());
        if (icao == null) return;

        String cachedRegistration = registrationCache.get(icao);
        if (isBlank(plane.getRegistration()) && !isBlank(cachedRegistration)) {
            plane.setRegistration(cachedRegistration);
        }

        String cachedTypeCode = typeCodeCache.get(icao);
        if (isBlank(plane.getTypeCode()) && !isBlank(cachedTypeCode)) {
            plane.setTypeCode(cachedTypeCode);
        }
    }

    private void bindBasicDetails(TextView registration,
                                  TextView typeName,
                                  TextView altitude,
                                  TextView heading) {

        if (plane == null) return;

        String reg = plane.getRegistration();
        String name = plane.getTypeName();

        if (!isBlank(reg)) {
            registration.setText("Registration: " + reg.trim());
        } else if (isLoadingAircraftInfo) {
            registration.setText("Registration: Loading...");
        } else {
            registration.setText("Registration: Unknown");
        }

        if (!isBlank(name)) {
            typeName.setText("Aircraft: " + name.trim());
        } else if (isLoadingAircraftInfo) {
            typeName.setText("Aircraft: Loading...");
        } else {
            typeName.setText("Aircraft: Unknown");
        }

        altitude.setText(String.format(
                Locale.US,
                "Altitude: %.0f ft",
                plane.getAltitude()* 3.28084//convert to feet
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

        isLoadingAircraftInfo = shouldLoadAircraftInfo();

        String icaoRaw = plane.getIcao24();
        if (icaoRaw == null || icaoRaw.trim().isEmpty()) {
            isLoadingAircraftInfo = false;
            prog.setVisibility(View.GONE);
            img.setImageResource(R.drawable.plane_placeholder);
            return;
        }

        String icao = icaoRaw.trim().toUpperCase(Locale.US);

        //if no registration and no TypeCode
        if (!isBlank(plane.getRegistration()) || !isBlank(plane.getTypeCode())) {
            isLoadingAircraftInfo = false;
        }

        String cachedUrl = photoUrlCache.get(icao);
        if (cachedUrl != null) {

            prog.setVisibility(View.VISIBLE);

            Glide.with(this)
                    .load(cachedUrl)
                    .placeholder(R.drawable.plane_placeholder)
                    .error(R.drawable.plane_placeholder)
                    .listener(new RequestListener<>() {
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
                        isLoadingAircraftInfo = false;
                        prog.setVisibility(View.GONE);
                        img.setImageResource(R.drawable.plane_placeholder);

                        View root = getView();
                        if (root != null) {
                            bindBasicDetails(
                                    root.findViewById(R.id.tvRegistration),
                                    root.findViewById(R.id.tvTypeName),
                                    root.findViewById(R.id.tvAltitude),
                                    root.findViewById(R.id.tvHeading)
                            );
                        }
                        refreshAircraftTextViews();
                    });
                }

                String photoUrl = extractFirstPhotoUrl(body);

                Log.d("PlaneSheet", "Extracted photoUrl=" + photoUrl);

                if (photoUrl == null || photoUrl.isEmpty()) {

                    noPhotoCache.add(icao);

                    safeUi(() -> {
                        prog.setVisibility(View.GONE);
                        img.setImageResource(R.drawable.plane_placeholder);
                        refreshAircraftTextViews();
                    });

                    return;
                }

                photoUrlCache.put(icao, photoUrl);

                safeUi(() ->
                        Glide.with(this)
                                .load(photoUrl)
                                .placeholder(R.drawable.plane_placeholder)
                                .error(R.drawable.plane_placeholder)
                                .listener(new RequestListener<>() {
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
                    refreshAircraftTextViews();
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

            String registration = null;
            String typeCode = null;
            String typeName = null;

            if (aircraft != null) {
                registration = aircraft.optString("registration", null);
                typeCode = aircraft.optString("icao_type", null);
                typeName = aircraft.optString("type_name", null);
            }

            String icao = normalizeIcao(plane != null ? plane.getIcao24() : null);

            if (icao != null) {
                if (!isBlank(registration)) {
                    registrationCache.put(icao, registration.trim());
                }

                if (!isBlank(typeCode)) {
                    typeCodeCache.put(icao, typeCode.trim());
                }
            }

            String finalRegistration = registration;
            String finalTypeCode = typeCode;
            String finalTypeName = typeName;

            Log.d("PlaneSheet", "TypeCode=" + String.valueOf(finalTypeCode));
            Log.d("PlaneSheet", "TypeName=" + String.valueOf(finalTypeName));

            safeUi(() -> {
                if (!isAdded() || plane == null) return;

                isLoadingAircraftInfo = false;

                if (!isBlank(finalRegistration)) {
                    plane.setRegistration(finalRegistration.trim());
                }

                if (!isBlank(finalTypeCode)) {
                    plane.setTypeCode(finalTypeCode.trim());
                }

                if (!isBlank(finalTypeName)) {
                    plane.setTypeName(finalTypeName.trim());
                }

                refreshAircraftTextViews();
            });

        } catch (Exception e) {
            Log.e("PlaneSheet", "updateAircraftInfoFromSkyLink failed", e);

            safeUi(() -> {
                isLoadingAircraftInfo = false;
                refreshAircraftTextViews();
            });
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

    @Nullable
    private String normalizeIcao(@Nullable String icao24) {
        if (icao24 == null) return null;

        String trimmed = icao24.trim();
        if (trimmed.isEmpty()) return null;

        return trimmed.toUpperCase(Locale.US);
    }

    private boolean isBlank(@Nullable String value) {
        return value == null || value.trim().isEmpty();
    }

    private void safeUi(Runnable r) {
        if (!isAdded()) return;
        requireActivity().runOnUiThread(() -> {
            if (!isAdded()) return;
            r.run();
        });
    }
}