package com.example.planehunter.ui.adapters;

import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.planehunter.R;
import com.example.planehunter.model.Plane;
import com.example.planehunter.model.AircraftCategory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

/**
 * Adapter for a list of planes used in a selection dialog.
 * Shows plane details and applies a grayscale filter to planes that are currently in cooldown.
 */
public class PlaneChooserAdapter extends RecyclerView.Adapter<PlaneChooserAdapter.PlaneViewHolder> {

    /** Set of ICAO24 addresses for planes that are currently in cooldown for the user. */
    private final Set<String> cooldownIcaos;

    /** Color filter used to grayscale icons of planes in cooldown. */
    private final ColorMatrixColorFilter grayFilter;

    /**
     * Interface for listening to plane selection events.
     */
    public interface Listener {
        /**
         * Called when a plane item is clicked.
         * @param plane The clicked plane.
         */
        void onPlaneClicked(Plane plane);
    }

    /** The list of planes to be displayed in the chooser. */
    private final ArrayList<Plane> planes;
    /** The listener for plane selection events. */
    private final Listener listener;

    /**
     * Constructs a new PlaneChooserAdapter.
     * @param planes The list of planes to display.
     * @param cooldownIcaos Set of ICAO24 addresses for planes in cooldown.
     * @param listener The selection listener.
     */
    public PlaneChooserAdapter(ArrayList<Plane> planes, Set<String> cooldownIcaos, Listener listener) {
        this.planes = planes == null ? new ArrayList<>() : planes;
        this.cooldownIcaos = cooldownIcaos == null ? new HashSet<>() : cooldownIcaos;
        this.listener = listener;

        ColorMatrix matrix = new ColorMatrix();
        matrix.setSaturation(0f);
        grayFilter = new ColorMatrixColorFilter(matrix);
    }

    @NonNull
    @Override
    public PlaneViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_plane_chooser, parent, false);

        return new PlaneViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull PlaneViewHolder holder, int position) {
        Plane plane = planes.get(position);

        holder.textTitle.setText(getPlaneTitle(plane));
        holder.textSubtitle.setText(getPlaneSubtitle(plane));
        holder.imagePlaneIcon.setImageResource(getPlaneImageResId(plane));

        boolean inCooldown = isPlaneInCooldown(plane);

        holder.imagePlaneIcon.clearColorFilter();
        holder.imagePlaneIcon.setAlpha(1f);
        holder.textTitle.setAlpha(1f);
        holder.textSubtitle.setAlpha(1f);
        holder.itemView.setAlpha(1f);

        if (inCooldown) {
            holder.imagePlaneIcon.setColorFilter(grayFilter);
            holder.imagePlaneIcon.setAlpha(0.5f);
            holder.textTitle.setAlpha(0.5f);
            holder.textSubtitle.setAlpha(0.5f);
            holder.itemView.setAlpha(0.6f);
        }

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onPlaneClicked(plane);
            }
        });
    }

    private boolean isPlaneInCooldown(Plane plane) {
        if (plane == null || plane.getIcao24() == null) return false;

        String normalized = plane.getIcao24().trim().toUpperCase();
        return cooldownIcaos.contains(normalized);
    }

    private int getPlaneImageResId(Plane plane) {
        if (plane == null) {
            return R.drawable.plane_turboprop;
        }

        long category = plane.getCategory();

        if (category == AircraftCategory.AIRLINER) {
            return R.drawable.plane_airliner;
        }

        if (category == AircraftCategory.CARGO) {
            return R.drawable.plane_cargo;
        }

        if (category == AircraftCategory.BUSINESS_JET) {
            return R.drawable.plane_business_jet;
        }

        if (category == AircraftCategory.GENERAL_AVIATION) {
            return R.drawable.plane_turboprop;
        }

        if (category == AircraftCategory.TURBOPROP_REGIONAL) {
            return R.drawable.plane_turboprop;
        }

        if (category == AircraftCategory.HELICOPTER) {
            return R.drawable.plane_helicopter;
        }

        if (category == AircraftCategory.MILITARY_GOVERNMENT) {
            return R.drawable.plane_military;
        }

        return R.drawable.plane_turboprop;
    }

    @Override
    public int getItemCount() {
        return planes.size();
    }

    private String getPlaneTitle(Plane plane) {
        if (plane == null) return "Unknown plane";

        if (!isBlank(plane.getCallSign())) return plane.getCallSign().trim();
        if (!isBlank(plane.getRegistration())) return plane.getRegistration().trim();
        if (!isBlank(plane.getIcao24())) return plane.getIcao24().trim();

        return "Unknown plane";
    }

    private String getPlaneSubtitle(Plane plane) {
        if (plane == null) return "";

        String type = isBlank(plane.getTypeName()) ? "Unknown type" : plane.getTypeName();
        int altitude = (int) plane.getAltitude();

        return type + " • " + altitude + " m";
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    /**
     * ViewHolder for plane items in the chooser.
     */
    static class PlaneViewHolder extends RecyclerView.ViewHolder {

        /** TextView for the plane's title (Callsign, Registration, or ICAO). */
        TextView textTitle;
        /** TextView for the plane's subtitle (Type and Altitude). */
        TextView textSubtitle;

        /** ImageView for the plane's category icon. */
        ImageView imagePlaneIcon;

        public PlaneViewHolder(@NonNull View itemView) {
            super(itemView);

            textTitle = itemView.findViewById(R.id.textPlaneTitle);
            textSubtitle = itemView.findViewById(R.id.textPlaneSubtitle);
            imagePlaneIcon = itemView.findViewById(R.id.imagePlaneIcon);
        }
    }
}
