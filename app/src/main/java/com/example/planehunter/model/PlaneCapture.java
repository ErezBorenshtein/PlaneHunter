package com.example.planehunter.model;

/**
 * Data model representing a recorded aircraft capture by a user.
 * Stores technical details about the plane and statistics about when and how many times it was caught.
 */
public class PlaneCapture {
    /** The aircraft registration number. */
    public String registration;
    /** The ICAO 24-bit address of the aircraft. */
    public String icao24;
    /** The ICAO aircraft type code. */
    public String typeCode;
    /** The descriptive aircraft type name. */
    public String typeName;
    /** The flight callsign. */
    public String callSign;

    /** Timestamp in milliseconds when the aircraft was first caught. */
    public long firstCaughtAtMs;
    /** Timestamp in milliseconds when the aircraft was last caught. */
    public long lastCaughtAtMs;
    /** The total number of times this aircraft has been caught by the user. */
    public long timesCaught;

    /**
     * Default constructor for Firebase deserialization.
     */
    public PlaneCapture() {
    }

    /**
     * Constructs a new PlaneCapture record.
     * @param registration Aircraft registration.
     * @param icao24 ICAO 24-bit address.
     * @param typeCode ICAO type code.
     * @param typeName Descriptive type name.
     * @param callSign Flight callsign.
     * @param now Current timestamp in milliseconds.
     */
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