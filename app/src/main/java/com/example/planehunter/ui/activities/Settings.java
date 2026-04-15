package com.example.planehunter.ui.activities;

import android.content.Intent;
import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

import com.example.planehunter.R;
import com.example.planehunter.data.firebase.FirebaseHandler;
import com.example.planehunter.model.AircraftCategory;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.checkbox.MaterialCheckBox;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Settings extends AppCompatActivity {

    private MaterialButton btnSelectAll;
    private MaterialButton btnClearAll;
    private MaterialButton btnSave;
    private Map<Long, MaterialCheckBox> categoryCheckboxes;

    private FirebaseHandler firebaseHandler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_settings);

        init();

        firebaseHandler.getMyProfile()
                .addOnSuccessListener(profile -> {
                    List<Long> categories;

                    if (profile == null) {
                        categories = AircraftCategory.getDefaultAlertCategories();
                    } else {
                        categories = AircraftCategory.normalizeAlertCategories(profile.getAlertCategories());
                    }

                    setSelected(categories);
                });
    }

    private void setSelected(List<Long> categories) {
        for (MaterialCheckBox cb : categoryCheckboxes.values()) {
            cb.setChecked(false);
        }

        for (Long category : categories) {
            MaterialCheckBox cb = categoryCheckboxes.get(category);
            if (cb != null) {
                cb.setChecked(true);
            }
        }
    }

    private void init() {
        firebaseHandler = FirebaseHandler.getInstance();

        btnSelectAll = findViewById(R.id.buttonSelectAll);
        btnClearAll = findViewById(R.id.buttonClearAll);
        btnSave = findViewById(R.id.buttonSave);

        categoryCheckboxes = new HashMap<>();

        MaterialCheckBox check1 = findViewById(R.id.checkCategory1);
        MaterialCheckBox check2 = findViewById(R.id.checkCategory2);
        MaterialCheckBox check3 = findViewById(R.id.checkCategory3);
        MaterialCheckBox check4 = findViewById(R.id.checkCategory4);
        MaterialCheckBox check5 = findViewById(R.id.checkCategory5);
        MaterialCheckBox check6 = findViewById(R.id.checkCategory6);
        MaterialCheckBox check7 = findViewById(R.id.checkCategory7);

        categoryCheckboxes.put(AircraftCategory.AIRLINER, check1);
        categoryCheckboxes.put(AircraftCategory.CARGO, check2);
        categoryCheckboxes.put(AircraftCategory.BUSINESS_JET, check3);
        categoryCheckboxes.put(AircraftCategory.GENERAL_AVIATION, check4);
        categoryCheckboxes.put(AircraftCategory.TURBOPROP_REGIONAL, check5);
        categoryCheckboxes.put(AircraftCategory.HELICOPTER, check6);
        categoryCheckboxes.put(AircraftCategory.MILITARY_GOVERNMENT, check7);


        btnSave.setOnClickListener(v -> {
            ArrayList<Long> result = new ArrayList<>();

            for (Map.Entry<Long, MaterialCheckBox> entry : categoryCheckboxes.entrySet()) {
                if (entry.getValue().isChecked()) {
                    result.add(entry.getKey());
                }
            }

            if (result.isEmpty()) {
                result = AircraftCategory.getDefaultAlertCategories();
            }

            firebaseHandler.updateAlertCategories(result);

            Intent intent = new Intent(this, MainActivity.class);
            startActivity(intent);
            finish();
        });

        btnSelectAll.setOnClickListener(v -> {
            for (MaterialCheckBox cb : categoryCheckboxes.values()) {
                cb.setChecked(true);
            }
        });

        btnClearAll.setOnClickListener(v -> {
            for (MaterialCheckBox cb : categoryCheckboxes.values()) {
                cb.setChecked(false);
            }
        });
    }
}