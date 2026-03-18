package com.example.planehunter.model;

public class LeaderboardEntry {
    public String uid;
    public String name;
    public long xp;
    public long captures;

    public LeaderboardEntry() {}//for firebase

    public LeaderboardEntry(String uid,String name, long xp, long captures) {
        this.uid = uid;
        this.name = name;
        this.xp = xp;
        this.captures = captures;
    }

}
