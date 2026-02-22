package com.genymobile.scrcpy;

import com.genymobile.scrcpy.device.ConfigurationException;
import com.genymobile.scrcpy.util.Ln;
import com.genymobile.scrcpy.video.CameraCapture;
import com.genymobile.scrcpy.location.LocationDispatcher;
import com.genymobile.scrcpy.location.LocationResult;
import com.genymobile.scrcpy.AppDispatcher;

import android.os.Build;

import android.os.Looper;
import java.io.File;
import java.io.IOException;

/**
 * 简化的scrcpy服务器 - 只支持拍照功能，不涉及连接
 *
 * 使用方法：
 * CLASSPATH=/data/local/tmp/scrcpy-server.jar app_process /data/local/tmp com.genymobile.scrcpy.Server camera_id=1
 * 或
 * CLASSPATH=/data/local/tmp/scrcpy-server.jar app_process /data/local/tmp com.genymobile.scrcpy.Server camera_facing=back
 */
public final class Server {

    public static final String SERVER_PATH;

    static {
        String[] classPaths = System.getProperty("java.class.path").split(File.pathSeparator);
        // By convention, scrcpy is always executed with the absolute path of scrcpy-server.jar as the first item in the classpath
        SERVER_PATH = classPaths[0];
    }

    // 简化的拍照方法：不涉及连接，直接拍照保存
    private static void captureAndSavePhoto(Options options) throws IOException, ConfigurationException {
        CameraCapture capture = new CameraCapture(options);
        capture.init();
        capture.prepare();

        // 启动摄像头拍照（内部处理ImageReader和照片保存）
        capture.startForPhoto();

        // 等待拍照完成
        try {
            Thread.sleep(1000); // 给 Camera2 + ImageReader 出 JPEG 的时间
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        capture.release();
        Ln.i("Photo capture completed");
    }

    private Server() {
        // not instantiable
    }

    /**
     * 检测是否在 app_process 环境中运行
     */
    private static boolean isRunningInAppProcess() {
        // 检查类路径中是否包含 scrcpy-server.jar
        String classPath = System.getProperty("java.class.path");
        return classPath != null && classPath.contains("scrcpy-server.jar");
    }

    private static void scrcpy(Options options) throws IOException, ConfigurationException {
        // 应用列表模式：检查getapp参数
        if (options.getGetapp()) {
            Ln.i("App list mode triggered");
            AppDispatcher.dispatchAppListRequest(options);
            return;
        }

        // 定位模式：检查getloc参数
        if (options.getGetLoc()) {
            Ln.i("Location mode triggered");
            getLocationAndReturn(options);
            return;
        }

        // 拍照模式：检查是否为拍照模式（camera_id 或 camera_facing 参数自动触发）
        // 直接拍照，不涉及任何连接逻辑
        if (options.getCameraId() != null || options.getCameraFacing() != null) {
            Ln.i("Camera capture mode triggered");
            captureAndSavePhoto(options);
            return;
        }

        // 如果不是拍照、定位或应用列表模式，报错
        Ln.e("This simplified version only supports camera_id, camera_facing, getloc, or getapp mode");
        throw new ConfigurationException("Only camera capture, location, or app list is supported in this version");
    }

    /**
     * 获取位置信息并返回
     */
    private static void getLocationAndReturn(Options options) throws IOException {
        try {
            // 获取位置信息
            LocationResult location = LocationDispatcher.dispatchLocationRequest(options);

            if (location != null) {
                // 输出JSON格式位置信息到标准输出
                String jsonOutput = LocationDispatcher.formatLocationAsJson(location);
                System.out.println(jsonOutput);
                Ln.i("Location data sent to stdout");
            } else {
                // 输出错误信息
                System.out.println("{\"error\":\"Failed to get location\"}");
                Ln.e("Failed to get location");
                throw new IOException("Failed to get location");
            }
        } catch (Exception e) {
            Ln.e("Location capture failed", e);
            System.out.println("{\"error\":\"" + e.getMessage() + "\"}");
            throw new IOException("Location capture failed: " + e.getMessage());
        }
    }

    public static void main(String... args) {
        // 设置主线程Looper以支持Handler创建
        Looper.prepareMainLooper();

        int status = 0;
        try {
            internalMain(args);
            Ln.v("All operations completed successfully");
        } catch (Throwable t) {
            Ln.e("Error: " + t.getMessage(), t);
            status = 1;
        } finally {
            // 清理Looper - 但仅在非app_process环境中退出
            if (Looper.myLooper() != null && !isRunningInAppProcess()) {
                Looper.myLooper().quit();
            }
            // 退出JVM
            System.exit(status);
        }
    }

    private static void internalMain(String... args) throws Exception {
        Thread.UncaughtExceptionHandler defaultHandler = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
            // 只在非正常退出时打印错误
            if (!t.getName().equals("main") || !e.getMessage().contains("VM exit")) {
                Ln.e("Exception on thread " + t.getName(), e);
            }
            if (defaultHandler != null) {
                defaultHandler.uncaughtException(t, e);
            }
        });

        Options options = Options.parse(args);

        Ln.disableSystemStreams();
        Ln.initLogLevel(options.getLogLevel());

        Ln.i("Device: [" + Build.MANUFACTURER + "] " + Build.BRAND + " " + Build.MODEL + " (Android " + Build.VERSION.RELEASE + ")");

        try {
            scrcpy(options);
        } catch (ConfigurationException e) {
            // Do not print stack trace, a user-friendly error-message has already been logged
        } finally {
            // 确保所有资源被正确清理
            Ln.v("Cleaning up resources...");
        }
    }
}
