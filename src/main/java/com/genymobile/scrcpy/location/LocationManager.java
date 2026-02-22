package com.genymobile.scrcpy.location;

import com.genymobile.scrcpy.wrappers.ServiceManager;
import com.genymobile.scrcpy.util.Ln;

import android.annotation.SuppressLint;
import android.location.Location;

import java.lang.reflect.Method;

/**
 * LocationManager系统服务包装器
 */
@SuppressLint("PrivateApi,DiscouragedPrivateApi")
public final class LocationManager {

    private LocationManager() {
        /* not instantiable */
    }

    /**
     * 获取LocationManager实例
     */
    public static synchronized android.location.LocationManager getInstance() {
        return ServiceManager.getLocationManager();
    }

    
    /**
     * 获取最后已知位置
     */
    public static LocationResult getLastKnownLocation() {
        try {
            // 获取LocationManager实例
            android.location.LocationManager lm = ServiceManager.getLocationManager();
            android.location.Location location = null;

            // 尝试获取网络位置
            android.location.Location networkLocation = lm.getLastKnownLocation(android.location.LocationManager.NETWORK_PROVIDER);
            if (networkLocation != null) {
                location = networkLocation;
            }

            // 尝试获取GPS位置
            android.location.Location gpsLocation = lm.getLastKnownLocation(android.location.LocationManager.GPS_PROVIDER);
            if (gpsLocation != null && (location == null ||
                gpsLocation.getTime() > location.getTime())) {
                location = gpsLocation;
            }

            if (location != null) {
                return new LocationResult(
                    location.getLatitude(),
                    location.getLongitude(),
                    location.getAccuracy(),
                    location.getTime()
                );
            }

            return null;
        } catch (Exception e) {
            Ln.e("Failed to get last known location", e);
            return null;
        }
    }

  
    /**
     * 检查是否有可用的位置提供者
     */
    public static boolean hasLocationProvider() {
        try {
            android.location.LocationManager lm = getInstance();
            return lm.getAllProviders() != null && !lm.getAllProviders().isEmpty();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 检查GPS提供者是否可用
     */
    public static boolean isGpsProviderAvailable() {
        try {
            android.location.LocationManager lm = getInstance();
            return lm.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 检查网络提供者是否可用
     */
    public static boolean isNetworkProviderAvailable() {
        try {
            android.location.LocationManager lm = getInstance();
            return lm.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER);
        } catch (Exception e) {
            return false;
        }
    }
}