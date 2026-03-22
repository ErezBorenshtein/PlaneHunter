package com.example.planehunter.model;

import android.os.Parcel;
import android.os.Parcelable;

public class Plane implements Parcelable { //so I can use it in the intent

    public String icao24;
    public String callSign;
    public double lat;
    public double lon;
    public double altitude;
    public String registration;
    public String typeCode;
    public String typeName;
    public double trackDeg = Double.NaN;
    public long category;

    public Plane(String icao24, String callSign, double lat, double lon, double altitude, double trackDeg) {
        this.icao24 = icao24;
        this.callSign = callSign;
        this.lat = lat;
        this.lon = lon;
        this.altitude = altitude;
        this.trackDeg = trackDeg;
        this.registration = null;
    }

    public Plane(String icao24, String callSign, double lat, double lon, double altitude, String registration, double trackDeg, int category) {
        this.icao24 = icao24;
        this.callSign = callSign;
        this.lat = lat;
        this.lon = lon;
        this.altitude = altitude;
        this.registration = registration;
        this.trackDeg = trackDeg;
        this.category = category;
    }

    public Plane(String icao24, String callSign, double lat, double lon, double altitude,
                 String registration, String model, String manufacturerName, String typeCode, double trackDeg, String typeName) {
        this.icao24 = icao24;
        this.callSign = callSign;
        this.lat = lat;
        this.lon = lon;
        this.altitude = altitude;
        this.registration = registration;
        this.typeCode = typeCode;
        this.typeName = typeName;
        this.trackDeg = trackDeg;
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
        trackDeg = in.readDouble();
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
        dest.writeDouble(trackDeg);
    }

    public String getTypeName() {
        return typeName;
    }

    public void setTypeName(String typeName) {
        this.typeName = typeName;
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

    public String getTypeCode() {
        return typeCode;
    }

    public void setTypeCode(String typeCode) {
        this.typeCode = typeCode;
    }

    public void setRegistration(String registration) {
        this.registration = registration;
    }

    public long getCategory() {
        return category;
    }

    public void setCategory(long category) {
        this.category = category;
    }
}