package com.example.planehunter.util;

public class UtilMath {

    public static double haversineMeters(double lat1, double lon1, double lat2, double lon2) {
        //equation to calculate the distance between 2 points on a sphere(Haversine)
        //https://www.movable-type.co.uk/scripts/latlong.html
        /*
        a = sin²(Δφ/2) + cos φ1 ⋅ cos φ2 ⋅ sin²(Δλ/2)
        c = 2 ⋅ atan2( √a, √(1−a) )
        d = R ⋅ c
        * */

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
        //get heading form curr location to plane
        //https://www.movable-type.co.uk/scripts/latlong.html
        //θ = atan2( sin Δλ ⋅ cos φ2 , cos φ1 ⋅ sin φ2 − sin φ1 ⋅ cos φ2 ⋅ cos Δλ )

        double phi1 = Math.toRadians(lat1);
        double phi2 = Math.toRadians(lat2);
        double dLambda = Math.toRadians(lon2 - lon1);

        double y = Math.sin(dLambda) * Math.cos(phi2);
        double x = Math.cos(phi1) * Math.sin(phi2)
                - Math.sin(phi1) * Math.cos(phi2) * Math.cos(dLambda);

        double brng = Math.toDegrees(Math.atan2(y, x));
        return (brng + 360.0) % 360.0; //0-360 degs
    }

}
