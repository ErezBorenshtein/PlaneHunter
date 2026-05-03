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

public class PlaneChooserSheet extends BottomSheetDialogFragment {

    private Set<String> cooldownIcaos = new HashSet<>();

    public interface Listener {
        void onPlaneSelected(Plane plane);
    }

    private static final String ARG_PLANES = "arg_planes";

    private Listener listener;

    public static PlaneChooserSheet newInstance(ArrayList<Plane> planes) {
        PlaneChooserSheet sheet = new PlaneChooserSheet();

        Bundle args = new Bundle();
        args.putParcelableArrayList(ARG_PLANES, planes);
        sheet.setArguments(args);

        return sheet;
    }

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