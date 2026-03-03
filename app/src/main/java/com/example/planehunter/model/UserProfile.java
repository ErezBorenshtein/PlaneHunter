package com.example.planehunter.model;

public class UserProfile {
    public String uid;
    public String displayName;

    public long xp;
    public long caughtCount;

    public boolean notifyEnabled;
    public int radiusKm;

    public long createdAtMs;
    public long updatedAtMs;

    public UserProfile() { }

    public UserProfile(String uid, String displayName) {
        this.uid = uid;
        this.displayName = displayName;

        this.xp = 0;
        this.caughtCount = 0;

        this.notifyEnabled = true;
        this.radiusKm = 4;

        long now = System.currentTimeMillis();
        this.createdAtMs = now;
        this.updatedAtMs = now;
    }
}