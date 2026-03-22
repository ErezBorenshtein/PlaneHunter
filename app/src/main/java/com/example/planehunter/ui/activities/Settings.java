package com.example.planehunter.ui.activities;

import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

import com.example.planehunter.R;

import com.example.planehunter.data.firebase.FirebaseHandler;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.checkbox.MaterialCheckBox;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;


public class Settings extends AppCompatActivity {

    private MaterialSwitch switchAlertsEnabled;

    private MaterialButton btnSelectAll;
    private MaterialButton btnClearAll;
    private MaterialButton btnSave;
    private Map<Integer,MaterialCheckBox> categoryCheckboxes;

    FirebaseHandler firebaseHandler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_settings);

        init();


    }



    public void init(){

        firebaseHandler = FirebaseHandler.getInstance();

        switchAlertsEnabled = findViewById(R.id.switchAlertsEnabled);
        btnSelectAll = findViewById(R.id.buttonSelectAll);
        btnClearAll = findViewById(R.id.buttonClearAll);
        btnSave = findViewById(R.id.buttonSave);

        categoryCheckboxes = new java.util.HashMap<>();

        categoryCheckboxes.put(1, findViewById(R.id.checkCategory1));
        categoryCheckboxes.put(2, findViewById(R.id.checkCategory2));
        categoryCheckboxes.put(3, findViewById(R.id.checkCategory3));
        categoryCheckboxes.put(4, findViewById(R.id.checkCategory4));
        categoryCheckboxes.put(5, findViewById(R.id.checkCategory5));
        categoryCheckboxes.put(6, findViewById(R.id.checkCategory6));
        categoryCheckboxes.put(7, findViewById(R.id.checkCategory7));
        categoryCheckboxes.put(8, findViewById(R.id.checkCategory8));
        categoryCheckboxes.put(9, findViewById(R.id.checkCategory9));
        categoryCheckboxes.put(10, findViewById(R.id.checkCategory10));
        categoryCheckboxes.put(11, findViewById(R.id.checkCategory11));
        categoryCheckboxes.put(12, findViewById(R.id.checkCategory12));
        categoryCheckboxes.put(13, findViewById(R.id.checkCategory13));
        categoryCheckboxes.put(14, findViewById(R.id.checkCategory14));
        categoryCheckboxes.put(15, findViewById(R.id.checkCategory15));
        categoryCheckboxes.put(16, findViewById(R.id.checkCategory16));
        categoryCheckboxes.put(17, findViewById(R.id.checkCategory17));
        categoryCheckboxes.put(18, findViewById(R.id.checkCategory18));
        categoryCheckboxes.put(19, findViewById(R.id.checkCategory19));
        categoryCheckboxes.put(20, findViewById(R.id.checkCategory20));


        btnSave.setOnClickListener(v ->{
            List<Long> result = new ArrayList<>();

            for (Map.Entry<Integer,MaterialCheckBox> entry:categoryCheckboxes.entrySet()) {
                if(entry.getValue().isChecked()){
                    result.add(Long.valueOf(entry.getKey()));
                }
            }
            firebaseHandler
        });
    }
}