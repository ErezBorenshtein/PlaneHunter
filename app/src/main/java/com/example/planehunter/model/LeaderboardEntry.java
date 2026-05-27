package com.example.planehunter.model;

/**
 * Data model representing an entry in the leaderboard.
 * Includes user identification, display name, XP, and capture count.
 */
public class LeaderboardEntry {
    /** Unique user identifier. */
    public String uid;
    /** User's display name. */
    public String name;
    /** Total experience points. */
    public long xp;
    /** Total number of aircraft captures. */
    public long captures;

    /**
     * Default constructor for Firebase deserialization.
     */
    public LeaderboardEntry() {}

    /**
     * Constructs a LeaderboardEntry with specified details.
     * @param uid The user's unique ID.
     * @param name The user's display name.
     * @param xp The user's total experience points.
     * @param captures The total number of planes captured by the user.
     */
    public LeaderboardEntry(String uid, String name, long xp, long captures) {
        this.uid = uid;
        this.name = name;
        this.xp = xp;
        this.captures = captures;
    }

}
