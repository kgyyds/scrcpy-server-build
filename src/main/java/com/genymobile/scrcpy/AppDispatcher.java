package com.genymobile.scrcpy;

import com.genymobile.scrcpy.device.Device;
import com.genymobile.scrcpy.device.DeviceApp;
import com.genymobile.scrcpy.util.Ln;

import java.util.List;

/**
 * 应用列表分发器 - 处理应用列表请求和响应
 */
public final class AppDispatcher {

    private AppDispatcher() {
        /* not instantiable */
    }

    /**
     * 处理应用列表请求
     *
     * @param options 命令行选项
     */
    public static void dispatchAppListRequest(Options options) {
        Ln.i("App list request received");

        if (!options.getGetapp()) {
            Ln.w("App list not requested");
            return;
        }

        // 获取应用列表
        List<DeviceApp> apps = Device.listApps();

        if (apps != null && !apps.isEmpty()) {
            Ln.i("App list successfully retrieved: " + apps.size() + " apps");
            // 输出JSON格式应用列表到标准输出
            String jsonOutput = formatAppsAsJson(apps);
            Ln.println(jsonOutput);
        } else {
            Ln.e("Failed to retrieve app list");
            // 输出错误信息
            Ln.println("{\"error\":\"Failed to get app list\"}");
            // 不再抛出异常，直接返回
        }
    }

    /**
     * 格式化应用列表为JSON字符串
     */
    public static String formatAppsAsJson(List<DeviceApp> apps) {
        if (apps == null || apps.isEmpty()) {
            return "{\"apps\":[]}";
        }

        StringBuilder json = new StringBuilder("{\"apps\":[");
        for (int i = 0; i < apps.size(); i++) {
            DeviceApp app = apps.get(i);
            if (i > 0) {
                json.append(",");
            }
            json.append(String.format(
                "{\"package_name\":\"%s\",\"name\":\"%s\",\"system\":%b}",
                escapeJson(app.getPackageName()),
                escapeJson(app.getName()),
                app.isSystem()
            ));
        }
        json.append("]}");
        return json.toString();
    }

    /**
     * 转义JSON字符串中的特殊字符
     */
    private static String escapeJson(String input) {
        if (input == null) {
            return "";
        }
        return input.replace("\\", "\\\\")
                   .replace("\"", "\\\"")
                   .replace("\b", "\\b")
                   .replace("\f", "\\f")
                   .replace("\n", "\\n")
                   .replace("\r", "\\r")
                   .replace("\t", "\\t");
    }
}