package com.example.planehunter.model;

import android.os.Parcel;
import android.os.Parcelable;

public class Plane implements Parcelable {

    public String icao24;
    public String callSign;
    public double lat;
    public double lon;
    public double altitude;
    public String registration;
    public String typeCode;
    public String typeName;
    public String ownerOperator;
    public String photoUrl;
    public double trackDeg = Double.NaN;
    public long category = AircraftCategory.UNKNOWN;
    public boolean isMilitary = false;
    public String countryCode;

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

    protected Plane(Parcel in) {
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
        isMilitary = in.readByte() != 0;
        countryCode = in.readString();
    }

    public static final Creator<Plane> CREATOR = new Creator<Plane>() {
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
        dest.writeByte((byte) (isMilitary ? 1 : 0)); //boolean doesn't work with all parcelable versions
        dest.writeString(countryCode);
        //dest.writeBoolean(isMilitary);
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

    public void setCountryCode(String countryCode) {
        this.countryCode = countryCode;
    }
}