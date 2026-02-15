package com.example.planehunter;

public class Plane {
    public String icao24;
    public String callSign;
    public double lat;
    public double lon;
    public double altitude;

    // Direction of travel (degrees 0..360). NaN if unknown
    public double trackDeg = Double.NaN;

    // Backward-compatible constructor (no track)
    public Plane(String icao24, String callSign, double lat, double lon, double altitude) {
        this.icao24 = icao24;
        this.callSign = callSign;
        this.lat = lat;
        this.lon = lon;
        this.altitude = altitude;
    }

    // New constructor with track
    public Plane(String icao24, String callSign, double lat, double lon, double altitude, double trackDeg) {
        this.icao24 = icao24;
        this.callSign = callSign;
        this.lat = lat;
        this.lon = lon;
        this.altitude = altitude;
        this.trackDeg = trackDeg;
    }

    public String getIcao24() {
        return icao24;
    }

    public String getCallSign() {
        return callSign;
    }

    public double getLat() {
        return lat;
    }

    public double getLon() {
        return lon;
    }

    public double getAltitude() {
        return altitude;
    }

    public double getTrackDeg() {
        return trackDeg;
    }
}
