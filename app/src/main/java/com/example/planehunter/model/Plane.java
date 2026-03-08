package com.example.planehunter.model;

public class Plane {
    public String icao24;
    public String callSign;
    public double lat;
    public double lon;
    public double altitude;
    public String registration;
    public String model;
    public String manufacturerName;
    public String typeName;
    public double trackDeg = Double.NaN;

    public Plane(String icao24, String callSign, double lat, double lon, double altitude, double trackDeg) {
        this.icao24 = icao24;
        this.callSign = callSign;
        this.lat = lat;
        this.lon = lon;
        this.altitude = altitude;
        this.trackDeg = trackDeg;
        this.registration = null;
    }

    public Plane(String icao24, String callSign, double lat, double lon, double altitude, String registration, double trackDeg) {
        this.icao24 = icao24;
        this.callSign = callSign;
        this.lat = lat;
        this.lon = lon;
        this.altitude = altitude;
        this.registration = registration;
        this.trackDeg = trackDeg;
        this.model = null;
        this.manufacturerName = null;
        this.typeName = null;
    }

    public Plane(String icao24, String callSign, double lat, double lon, double altitude, String registration, String model, String manufacturerName, String typeCode, double trackDeg) {
        this.icao24 = icao24;
        this.callSign = callSign;
        this.lat = lat;
        this.lon = lon;
        this.altitude = altitude;
        this.registration = registration;
        this.model = model;
        this.manufacturerName = manufacturerName;
        this.typeName = typeCode;
        this.trackDeg = trackDeg;
    }

    public String getTypeName() {
        return typeName;
    }

    public void setTypeName(String typeName) {
        this.typeName = typeName;
    }

    public String getManufacturerName() {
        return manufacturerName;
    }

    public void setManufacturerName(String manufacturerName) {
        this.manufacturerName = manufacturerName;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
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

    public String getRegistration() {
        return registration;
    }

    public void setRegistration(String registration) {
        this.registration = registration;
    }
}
