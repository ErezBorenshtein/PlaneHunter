package com.example.planehunter.model;

public class PlaneCapture {
    public String registration;
    public String icao24;
    public String typeCode;
    public String typeName;
    public String callSign;

    public long firstCaughtAtMs;
    public long lastCaughtAtMs;
    public long timesCaught;

    public PlaneCapture() {
    }

    public PlaneCapture(String registration,
                        String icao24,
                        String typeCode,
                        String typeName,
                        String callSign,
                        long now) {

        this.registration = registration;
        this.icao24 = icao24;
        this.typeCode = typeCode != null ? typeCode.toUpperCase() : null;
        this.typeName = typeName;
        this.callSign = callSign;

        this.firstCaughtAtMs = now;
        this.lastCaughtAtMs = now;
        this.timesCaught = 1;
    }
}