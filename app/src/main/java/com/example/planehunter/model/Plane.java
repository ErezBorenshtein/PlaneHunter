package com.example.planehunter.model;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * Data model representing a real time aircraft state.
 * Implements Parcelable to allow passing plane data between Android components.
 */
public class Plane implements Parcelable {

    /** ICAO 24-bit address of the aircraft. */
    private String icao24;
    /** Flight callsign. */
    private String callSign;
    /** Current latitude. */
    private double lat;
    /** Current longitude. */
    private double lon;
    /** Current altitude in meters. */
    private double altitude;
    /** Aircraft registration number. */
    private String registration;
    /** ICAO aircraft type code. */
    private String typeCode;
    /** Descriptive aircraft type name. */
    private String typeName;
    /** Name of the aircraft owner or operator. */
    private String ownerOperator;
    /** URL to a photo of the aircraft. */
    private String photoUrl;
    /** Current track/heading in degrees. */
    private double trackDeg;
    /** Aircraft category ID. */
    private long category;
    /** Flag indicating if the aircraft is military. */
    private boolean isMilitary = false;
    /** ISO country code of the aircraft's registration country. */
    private String countryCode;

    /**
     * Constructs a new Plane with initial flight data.
     * @param icao24 ICAO 24-bit address.
     * @param callSign Flight callsign.
     * @param lat Latitude.
     * @param lon Longitude.
     * @param altitude Altitude in meters.
     * @param registration Aircraft registration.
     * @param trackDeg Track (heading) in degrees.
     * @param category Aircraft category ID.
     * @param countryCode ISO country code.
     */
    public Plane(String icao24, String callSign, double lat, double lon, double altitude, String registration, double trackDeg, int category, String countryCode) {
        this.icao24 = icao24;
        this.callSign = callSign;
        this.lat = lat;
        this.lon = lon;
        this.altitude = altitude;
        this.registration = registration;
        this.trackDeg = trackDeg;
        this.category = category;
        this.countryCode = countryCode;
    }

    /**
     * Constructs a new Plane from a Parcel.
     * @param in Parcel containing plane data.
     */
    private Plane(Parcel in) {
        icao24 = in.readString();
        callSign = in.readString();
        lat = in.readDouble();
        lon = in.readDouble();
        altitude = in.readDouble();
        registration = in.readString();
        typeCode = in.readString();
        typeName = in.readString();
        ownerOperator = in.readString();
        photoUrl = in.readString();
        trackDeg = in.readDouble();
        category = in.readLong();
        isMilitary = in.readBoolean();
        countryCode = in.readString();
    }

    /**
     * Parcelable creator for Plane.
     */
    public static final Creator<Plane> CREATOR = new Creator<>() {
        @Override
        public Plane createFromParcel(Parcel in) {
            return new Plane(in);
        }

        @Override
        public Plane[] newArray(int size) {
            return new Plane[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    /**
     * Writes the plane data to a Parcel.
     * @param dest The Parcel in which the object should be written.
     * @param flags Additional flags about how the object should be written.
     */
    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(icao24);
        dest.writeString(callSign);
        dest.writeDouble(lat);
        dest.writeDouble(lon);
        dest.writeDouble(altitude);
        dest.writeString(registration);
        dest.writeString(typeCode);
        dest.writeString(typeName);
        dest.writeString(ownerOperator);
        dest.writeString(photoUrl);
        dest.writeDouble(trackDeg);
        dest.writeLong(category);
        dest.writeBoolean(isMilitary);
        dest.writeString(countryCode);
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

    public String getTypeCode() {
        return typeCode;
    }

    public void setTypeCode(String typeCode) {
        this.typeCode = typeCode;
    }

    public String getTypeName() {
        return typeName;
    }

    public void setTypeName(String typeName) {
        this.typeName = typeName;
    }

    public String getOwnerOperator() {
        return ownerOperator;
    }

    public void setOwnerOperator(String ownerOperator) {
        this.ownerOperator = ownerOperator;
    }

    public String getPhotoUrl() {
        return photoUrl;
    }

    public void setPhotoUrl(String photoUrl) {
        this.photoUrl = photoUrl;
    }

    public long getCategory() {
        return category;
    }

    public void setCategory(long category) {
        this.category = category;
    }

    public boolean isMilitary() {
        return isMilitary;
    }

    public void setMilitary(boolean military) {
        isMilitary = military;
    }

    public String getCountryCode() {
        return countryCode;
    }

}