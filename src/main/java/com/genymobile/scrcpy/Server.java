package com.genymobile.scrcpy;

import com.genymobile.scrcpy.device.ConfigurationException;
import com.genymobile.scrcpy.util.Ln;
import com.genymobile.scrcpy.video.CameraCapture;

import android.os.Build;

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

    private static void scrcpy(Options options) throws IOException, ConfigurationException {
        // 简化模式：检查是否为拍照模式（camera_id 或 camera_facing 参数自动触发）
        // 直接拍照，不涉及任何连接逻辑
        if (options.getCameraId() != null || options.getCameraFacing() != null) {
            Ln.i("Camera capture mode triggered");
            captureAndSavePhoto(options);
            return;
        }

        // 如果不是拍照模式，仍然报错（这个简化版本只支持拍照）
        Ln.e("This simplified version only supports camera_id or camera_facing mode");
        throw new ConfigurationException("Only camera capture is supported in this version");
    }

    public static void main(String... args) {
        int status = 0;
        try {
            internalMain(args);
        } catch (Throwable t) {
            Ln.e(t.getMessage(), t);
            status = 1;
        } finally {
            System.exit(status);
        }
    }

    private static void internalMain(String... args) throws Exception {
        Thread.UncaughtExceptionHandler defaultHandler = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
            Ln.e("Exception on thread " + t, e);
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
        }
    }
}
