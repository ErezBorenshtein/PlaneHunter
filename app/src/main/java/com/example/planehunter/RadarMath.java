package com.example.planehunter;

public class RadarMath {

    public static double haversineMeters(double lat1, double lon1, double lat2, double lon2) {
        double R = 6371000.0; // meters
        double phi1 = Math.toRadians(lat1);
        double phi2 = Math.toRadians(lat2);
        double dPhi = Math.toRadians(lat2 - lat1);
        double dLambda = Math.toRadians(lon2 - lon1);

        double a = Math.sin(dPhi / 2) * Math.sin(dPhi / 2)
                + Math.cos(phi1) * Math.cos(phi2) * Math.sin(dLambda / 2) * Math.sin(dLambda / 2);

        double c = 2.0 * Math.atan2(Math.sqrt(a), Math.sqrt(1.0 - a));
        return R * c;
    }

    public static double bearingDeg(double lat1, double lon1, double lat2, double lon2) {
        double phi1 = Math.toRadians(lat1);
        double phi2 = Math.toRadians(lat2);
        double dLambda = Math.toRadians(lon2 - lon1);

        double y = Math.sin(dLambda) * Math.cos(phi2);
        double x = Math.cos(phi1) * Math.sin(phi2)
                - Math.sin(phi1) * Math.cos(phi2) * Math.cos(dLambda);

        double brng = Math.toDegrees(Math.atan2(y, x));
        return (brng + 360.0) % 360.0;
    }


    public static double angleDiffDeg(double a, double b) {
        double d = Math.abs(a - b) % 360.0;
        return d > 180.0 ? 360.0 - d : d;
    }

    public static float normalize360(float deg) {
        float d = deg % 360f;
        return d < 0 ? d + 360f : d;
    }
}
