package com.genymobile.scrcpy.location;

import com.genymobile.scrcpy.Options;
import com.genymobile.scrcpy.util.Ln;

/**
 * 定位分发器 - 处理定位请求和响应
 */
public final class LocationDispatcher {

    private LocationDispatcher() {
        /* not instantiable */
    }

    /**
     * 处理定位请求
     *
     * @param options 命令行选项
     * @return LocationResult 位置信息，如果获取失败则返回null
     */
    public static LocationResult dispatchLocationRequest(Options options) {
        Ln.i("Location request received");

        if (!options.getGetLoc()) {
            Ln.w("Location not requested");
            return null;
        }

        // 检查位置服务是否可用
        if (!LocationProvider.isLocationServiceAvailable()) {
            Ln.e("Location service is not available");
            return null;
        }

        // 获取位置信息
        LocationResult location = LocationProvider.getLocation();

        if (location != null) {
            Ln.i("Location successfully retrieved: " + location);
            return location;
        } else {
            Ln.e("Failed to retrieve location");
            return null;
        }
    }

    /**
     * 格式化位置信息为JSON字符串
     */
    public static String formatLocationAsJson(LocationResult location) {
        if (location == null) {
            return "{\"error\":\"Failed to get location\"}";
        }

        return String.format(
            "{\"latitude\":%.6f,\"longitude\":%.6f,\"accuracy\":%.1f,\"timestamp\":%d}",
            location.getLatitude(),
            location.getLongitude(),
            location.getAccuracy(),
            location.getTimestamp()
        );
    }
}