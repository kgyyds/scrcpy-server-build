package com.genymobile.scrcpy.location;

import com.genymobile.scrcpy.util.Ln;

/**
 * 位置服务提供者
 */
public final class LocationProvider {

    private LocationProvider() {
        /* not instantiable */
    }

    /**
     * 获取当前位置信息
     *
     * @return LocationResult 包含经纬度信息，如果获取失败则返回null
     */
    public static LocationResult getLocation() {
        try {
            // 检查是否有可用的位置提供者
            if (!LocationManager.hasLocationProvider()) {
                Ln.w("No location provider available");
                return null;
            }

            // 检查GPS是否可用
            boolean gpsAvailable = LocationManager.isGpsProviderAvailable();
            boolean networkAvailable = LocationManager.isNetworkProviderAvailable();

            if (!gpsAvailable && !networkAvailable) {
                Ln.w("Neither GPS nor network provider is enabled");
                return null;
            }

            // 获取最后已知位置
            LocationResult location = LocationManager.getLastKnownLocation();

            if (location != null) {
                Ln.i("Location retrieved: " + location);
                return location;
            } else {
                Ln.w("No last known location available");
                return null;
            }
        } catch (Exception e) {
            Ln.e("Failed to get location: " + e.getMessage(), e);
            return null;
        }
    }

    /**
     * 检查位置服务是否可用
     */
    public static boolean isLocationServiceAvailable() {
        try {
            return LocationManager.hasLocationProvider();
        } catch (Exception e) {
            return false;
        }
    }
}