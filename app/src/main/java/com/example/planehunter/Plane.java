package com.example.planehunter;

public  class Plane {
    public String icao24;
    public String callSign;
    public double lat;
    public double lon;
    public double altitude;

    public Plane(String icao24, String callSign, double lat, double lon, double altitude) {
        this.icao24 = icao24;
        this.callSign = callSign;
        this.lat = lat;
        this.lon = lon;
        this.altitude = altitude;
    }
}
