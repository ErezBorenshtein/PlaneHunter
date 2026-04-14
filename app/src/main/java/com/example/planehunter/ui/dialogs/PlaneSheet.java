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
import com.example.planehunter.data.network.SkyLinkFetcher;
import com.example.planehunter.model.Plane;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import java.util.Locale;

public class PlaneSheet extends BottomSheetDialogFragment {

    public interface Listener {
        void onCapturePressed(@NonNull Plane plane);
    }

    private static final String ARG_PLANE = "plane";

    private Plane plane;
    private Listener listener;
    private SkyLinkFetcher skyLinkFetcher;

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
            @Nullable Bundle savedInstanceState
    ) {
        View v = inflater.inflate(R.layout.bottomsheet_plane, container, false);

        restorePlaneFromArgs();
        skyLinkFetcher = new SkyLinkFetcher(requireContext().getApplicationContext(), getString(R.string.skylink_key));

        ImageView imgPlane = v.findViewById(R.id.imgPlane);
        ProgressBar progPlane = v.findViewById(R.id.progPlane);
        TextView tvRegistration = v.findViewById(R.id.tvRegistration);
        TextView tvTypeName = v.findViewById(R.id.tvTypeName);
        TextView tvAltitude = v.findViewById(R.id.tvAltitude);
        TextView tvHeading = v.findViewById(R.id.tvHeading);
        Button btnCapture = v.findViewById(R.id.btnCapture);

        isLoadingAircraftInfo = shouldLoadAircraftInfo();
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

    private void restorePlaneFromArgs() {
        Bundle args = getArguments();
        if (args == null) return;

        plane = args.getParcelable(ARG_PLANE);
    }

    private boolean shouldLoadAircraftInfo() {
        if (plane == null) {
            return false;
        }

        return isBlank(plane.getRegistration())
                || isBlank(plane.getTypeCode())
                || isBlank(plane.getTypeName())
                || isBlank(plane.getPhotoUrl());
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

    private void bindBasicDetails(
            TextView registration,
            TextView typeName,
            TextView altitude,
            TextView heading
    ) {
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
                plane.getAltitude() * 3.28084
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
        if (plane == null) {
            return;
        }

        if (!isBlank(plane.getPhotoUrl())) {
            loadImageUrl(img, prog, plane.getPhotoUrl());
            return;
        }

        if (!shouldLoadAircraftInfo()) {
            prog.setVisibility(View.GONE);
            img.setImageResource(R.drawable.plane_placeholder);
            return;
        }

        prog.setVisibility(View.VISIBLE);
        isLoadingAircraftInfo = true;
        refreshAircraftTextViews();

        skyLinkFetcher.enrichPlaneForSheetAsync(plane, () -> {
            if (!isAdded()) {
                return;
            }

            isLoadingAircraftInfo = false;
            refreshAircraftTextViews();

            if (isBlank(plane.getPhotoUrl())) {
                prog.setVisibility(View.GONE);
                img.setImageResource(R.drawable.plane_placeholder);
                return;
            }

            loadImageUrl(img, prog, plane.getPhotoUrl());
        });
    }

    private void loadImageUrl(ImageView img, ProgressBar prog, String url) {
        prog.setVisibility(View.VISIBLE);

        Glide.with(this)
                .load(url)
                .placeholder(R.drawable.plane_placeholder)
                .error(R.drawable.plane_placeholder)
                .listener(new RequestListener<Drawable>() {
                    @Override
                    public boolean onLoadFailed(
                            @Nullable GlideException e,
                            Object model,
                            Target<Drawable> target,
                            boolean isFirstResource
                    ) {
                        prog.setVisibility(View.GONE);
                        return false;
                    }

                    @Override
                    public boolean onResourceReady(
                            Drawable resource,
                            Object model,
                            Target<Drawable> target,
                            DataSource dataSource,
                            boolean isFirstResource
                    ) {
                        prog.setVisibility(View.GONE);
                        return false;
                    }
                })
                .into(img);
    }

    private boolean isBlank(@Nullable String value) {
        return value == null || value.trim().isEmpty();
    }
}