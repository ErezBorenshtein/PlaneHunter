package com.example.planehunter.model;

import java.util.ArrayList;
import java.util.List;

public class UserProfile {
    public String uid;
    public String displayName;

    public long xp;
    public long caughtCount;
    public long uniqueRegistrationsCount;

    public boolean notifyEnabled;
    public int radiusKm;

    public long createdAtMs;
    public long updatedAtMs;

    public List<Long> alertCategories = new ArrayList<>();

    public UserProfile() {
    }

    public UserProfile(String uid, String displayName) {
        this.uid = uid;
        this.displayName = displayName;

        this.xp = 0L;
        this.caughtCount = 0L;
        this.uniqueRegistrationsCount = 0L;

        this.notifyEnabled = true;
        this.radiusKm = 4;

        long now = System.currentTimeMillis();
        this.createdAtMs = now;
        this.updatedAtMs = now;

        alertCategories = getDefaultCategories();

    }

    public static ArrayList<Long> getDefaultCategories(){
        return AircraftCategory.getDefaultAlertCategories();
    }

    public List<Long> getAlertCategories() {
        return alertCategories;
    }

    public void setAlertCategories(List<Long> alertCategories) {
        this.alertCategories = alertCategories;
    }
}