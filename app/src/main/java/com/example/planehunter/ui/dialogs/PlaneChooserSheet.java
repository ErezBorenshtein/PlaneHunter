package com.example.planehunter.ui.dialogs;

import android.app.Dialog;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.planehunter.R;
import com.example.planehunter.model.Plane;
import com.example.planehunter.ui.adapters.PlaneChooserAdapter;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

/**
 * A BottomSheetDialogFragment that displays a list of planes for the user to choose from.
 * Typically used when multiple planes are located close to each other on the radar.
 */
public class PlaneChooserSheet extends BottomSheetDialogFragment {

    /** Set of ICAO24 addresses for planes that are in cooldown for the current user. */
    private Set<String> cooldownIcaos = new HashSet<>();

    /**
     * Interface for listening to plane selection events from this sheet.
     */
    public interface Listener {
        /**
         * Called when a plane is selected from the list.
         * @param plane The selected plane.
         */
        void onPlaneSelected(Plane plane);
    }

    /** Argument key for the list of planes to display. */
    private static final String ARG_PLANES = "arg_planes";

    /** Listener for selection events from this sheet. */
    private Listener listener;

    /**
     * Creates a new instance of PlaneChooserSheet with a list of planes.
     * @param planes The list of planes to display.
     * @return A new PlaneChooserSheet instance.
     */
    public static PlaneChooserSheet newInstance(ArrayList<Plane> planes) {
        PlaneChooserSheet sheet = new PlaneChooserSheet();

        Bundle args = new Bundle();
        args.putParcelableArrayList(ARG_PLANES, planes);
        sheet.setArguments(args);

        return sheet;
    }

    /**
     * Sets the listener for selection events.
     * @param listener The listener to be notified.
     */
    public void setListener(Listener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        BottomSheetDialog dialog = new BottomSheetDialog(requireContext());

        View view = getLayoutInflater().inflate(R.layout.bottom_sheet_plane_chooser, null);

        RecyclerView recyclerPlanes = view.findViewById(R.id.recyclerPlanes);
        recyclerPlanes.setLayoutManager(new LinearLayoutManager(requireContext()));

        PlaneChooserAdapter adapter = new PlaneChooserAdapter(getPlanes(), cooldownIcaos, plane -> {
            if (listener != null) {
                listener.onPlaneSelected(plane);
            }
            dismiss();
        });

        recyclerPlanes.setAdapter(adapter);

        dialog.setContentView(view);
        return dialog;
    }

    private ArrayList<Plane> getPlanes() {
        Bundle args = getArguments();
        if (args == null) return new ArrayList<>();

        ArrayList<Plane> planes = args.getParcelableArrayList(ARG_PLANES);
        if (planes == null) return new ArrayList<>();

        return planes;
    }

    /**
     * Sets the set of ICAO24 addresses for planes that are in cooldown.
     * These planes will be visually distinguished in the list.
     * @param cooldownIcaos A set of ICAO24 strings.
     */
    public void setCooldownIcaos(Set<String> cooldownIcaos) {
        this.cooldownIcaos.clear();

        if (cooldownIcaos == null) return;

        for (String icao : cooldownIcaos) {
            if (icao != null && !icao.trim().isEmpty()) {
                this.cooldownIcaos.add(icao.trim().toUpperCase());
            }
        }
    }
}
