package com.genymobile.scrcpy.location;

/**
 * 位置信息结果封装类
 */
public class LocationResult {
    private final double latitude;
    private final double longitude;
    private final float accuracy;
    private final long timestamp;

    public LocationResult(double latitude, double longitude, float accuracy, long timestamp) {
        this.latitude = latitude;
        this.longitude = longitude;
        this.accuracy = accuracy;
        this.timestamp = timestamp;
    }

    public double getLatitude() {
        return latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public float getAccuracy() {
        return accuracy;
    }

    public long getTimestamp() {
        return timestamp;
    }

    @Override
    public String toString() {
        return String.format("LocationResult{lat=%.6f, lon=%.6f, acc=%.1f, time=%d}",
                latitude, longitude, accuracy, timestamp);
    }
}