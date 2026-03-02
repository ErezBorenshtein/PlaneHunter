package com.example.planehunter.model;

import java.util.HashMap;
import java.util.Map;

public class UserProfile {
    public String uid;
    public String displayName;
    public boolean notifyEnabled;
    public int radiusKm;
    public long createdAtMs;

    public UserProfile() { }

    public UserProfile(String uidOrThrow, String displayName) {
    }

    public static UserProfile createDefault(String uid, String displayName) {
        UserProfile u = new UserProfile();
        u.uid = uid;
        u.displayName = (displayName == null || displayName.isEmpty()) ? "Player" : displayName;
        u.notifyEnabled = true;
        u.radiusKm = 30;
        u.createdAtMs = System.currentTimeMillis();
        return u;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new HashMap<>();
        m.put("uid", uid);
        m.put("displayName", displayName);
        m.put("notifyEnabled", notifyEnabled);
        m.put("radiusKm", radiusKm);
        m.put("createdAtMs", createdAtMs);
        return m;
    }

    public static UserProfile fromMap(Map<String, Object> m) {
        if (m == null) return null;
        UserProfile u = new UserProfile();
        u.uid = str(m.get("uid"));
        u.displayName = str(m.get("displayName"));
        u.notifyEnabled = bool(m.get("notifyEnabled"), true);
        u.radiusKm = integer(m.get("radiusKm"), 30);
        u.createdAtMs = longv(m.get("createdAtMs"), 0L);
        return u;
    }

    private static String str(Object o) { return o == null ? null : String.valueOf(o); }

    private static boolean bool(Object o, boolean def) {
        return (o instanceof Boolean) ? (Boolean) o : def;
    }

    private static int integer(Object o, int def) {
        if (o instanceof Number) return ((Number) o).intValue();

        try {
            return Integer.parseInt(String.valueOf(o));
        }
        catch (Exception e) { return def; }
    }

    private static long longv(Object o, long def) {
        if (o instanceof Number) return ((Number) o).longValue();
        try { return Long.parseLong(String.valueOf(o)); } catch (Exception e) { return def; }
    }/**/
}