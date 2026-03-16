package com.example.planehunter.model;

public class PlaneCapture {
    public String registration;
    public String icao24;
    public String typeName;
    public String model;
    public String manufacturerName;
    public String callSign;

    public double lat;
    public double lon;
    public double altitude;

    public long firstCaughtAtMs;
    public long lastCaughtAtMs;
    public long timesCaught;

    public PlaneCapture() {
    }

    public PlaneCapture(
            String registration,
            String icao24,
            String typeName,
            String model,
            String manufacturerName,
            String callSign,
            double lat,
            double lon,
            double altitude,
            long now
    ) {
        this.registration = registration;
        this.icao24 = icao24;
        this.typeName = typeName;
        this.model = model;
        this.manufacturerName = manufacturerName;
        this.callSign = callSign;
        this.lat = lat;
        this.lon = lon;
        this.altitude = altitude;
        this.firstCaughtAtMs = now;
        this.lastCaughtAtMs = now;
        this.timesCaught = 1L;
    }
}