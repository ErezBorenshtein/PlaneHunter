package com.example.planehunter.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Data model representing a user's profile and settings.
 * Includes user stats, notification preferences, and alert categories.
 */
public class UserProfile {
    /** Unique user identifier. */
    public String uid;
    /** User's display name. */
    public String displayName;

    /** Total experience points earned. */
    public long xp;
    /** Total number of aircraft caught. */
    public long caughtCount;
    /** Number of unique aircraft registrations caught. */
    public long uniqueRegistrationsCount;

    /** Whether notifications are enabled for the user. */
    public boolean notifyEnabled;
    /** Detection radius in kilometers. */
    public int radiusKm;

    /** Timestamp in milliseconds when the profile was created. */
    public long createdAtMs;
    /** Timestamp in milliseconds when the profile was last updated. */
    public long updatedAtMs;

    /** List of aircraft category IDs for which the user wants alerts. */
    public List<Long> alertCategories = new ArrayList<>();

    /**
     * Default constructor for Firebase deserialization.
     */
    public UserProfile() {
    }

    /**
     * Constructs a new UserProfile with default settings.
     * @param uid The user's unique ID.
     * @param displayName The user's display name.
     */
    public UserProfile(String uid, String displayName) {
        this.uid = uid;
        this.displayName = displayName;

        this.xp = 0L;
        this.caughtCount = 0L;
        this.uniqueRegistrationsCount = 0L;

        this.notifyEnabled = true;
        this.radiusKm = 30;

        long now = System.currentTimeMillis();
        this.createdAtMs = now;
        this.updatedAtMs = now;

        alertCategories = AircraftCategory.getDefaultAlertCategories();

    }

    /**
     * Gets the list of categories for which the user wants to receive alerts.
     * @return A list of category IDs.
     */
    public List<Long> getAlertCategories() {
        return alertCategories;
    }

}