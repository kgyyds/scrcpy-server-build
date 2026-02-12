package com.genymobile.scrcpy;

import com.genymobile.scrcpy.audio.*;
import com.genymobile.scrcpy.control.Controller;
import com.genymobile.scrcpy.device.*;
import com.genymobile.scrcpy.opengl.OpenGLRunner;
import com.genymobile.scrcpy.util.Ln;
import com.genymobile.scrcpy.util.LogUtils;
import com.genymobile.scrcpy.video.*;

import android.annotation.SuppressLint;
import android.os.Build;
import android.os.Looper;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

public final class Server {

    public static final String SERVER_PATH;

    static {
        String[] classPaths = System.getProperty("java.class.path").split(File.pathSeparator);
        SERVER_PATH = classPaths[0];
    }

    private Server() {}

    private static void scrcpy(Options options) throws Exception {

        boolean localMode = true; // 👈 现在强制本地模式（你以后可以加参数）

        // ===== 基本校验 =====
        if (Build.VERSION.SDK_INT < AndroidVersions.API_31_ANDROID_12
                && options.getVideoSource() == VideoSource.CAMERA) {
            throw new ConfigurationException("Camera requires Android 12+");
        }

        Workarounds.apply();

        // ===== 本地文件连接 =====
        LocalFileConnection localConnection = null;
        try {
            File videoFile = new File("/data/local/tmp/output.h264");
            File audioFile = new File("/data/local/tmp/output.aac");
            localConnection = new LocalFileConnection(videoFile, audioFile);

            List<AsyncProcessor> processors = new ArrayList<>();

            // ===== 视频 =====
            if (options.getVideo()) {
                Streamer videoStreamer = new Streamer(
                        localConnection.getVideoFd(),
                        options.getVideoCodec(),
                        options.getSendCodecMeta(),
                        options.getSendFrameMeta()
                );

                SurfaceCapture capture;
                if (options.getVideoSource() == VideoSource.DISPLAY) {
                    capture = new ScreenCapture(null, options);
                } else {
                    capture = new CameraCapture(options);
                }

                SurfaceEncoder encoder =
                        new SurfaceEncoder(capture, videoStreamer, options);
                processors.add(encoder);
            }

            // ===== 音频 =====
            if (options.getAudio()) {
                AudioCapture audioCapture;
                if (options.getAudioSource().isDirect()) {
                    audioCapture = new AudioDirectCapture(options.getAudioSource());
                } else {
                    audioCapture = new AudioPlaybackCapture(options.getAudioDup());
                }

                Streamer audioStreamer = new Streamer(
                        localConnection.getAudioFd(),
                        options.getAudioCodec(),
                        options.getSendCodecMeta(),
                        options.getSendFrameMeta()
                );

                AsyncProcessor audioProcessor;
                if (options.getAudioCodec() == AudioCodec.RAW) {
                    audioProcessor = new AudioRawRecorder(audioCapture, audioStreamer);
                } else {
                    audioProcessor = new AudioEncoder(audioCapture, audioStreamer, options);
                }
                processors.add(audioProcessor);
            }

            // ===== 启动 =====
            for (AsyncProcessor p : processors) {
                p.start(null);
            }

            // ===== 运行一段时间（你以后可以改成 photo 模式直接 stop）=====
            Thread.sleep(2000);

            // ===== 停止 =====
            for (AsyncProcessor p : processors) {
                p.stop();
            }
            for (AsyncProcessor p : processors) {
                p.join();
            }

        } finally {
            if (localConnection != null) {
                localConnection.close();
            }
            OpenGLRunner.quit();
            OpenGLRunner.join();
        }
    }

    private static void prepareMainLooper() {
        Looper.prepare();
        synchronized (Looper.class) {
            try {
                @SuppressLint("DiscouragedPrivateApi")
                Field f = Looper.class.getDeclaredField("sMainLooper");
                f.setAccessible(true);
                f.set(null, Looper.myLooper());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    public static void main(String... args) {
        try {
            prepareMainLooper();
            Options options = Options.parse(args);
            Ln.initLogLevel(options.getLogLevel());
            scrcpy(options);
        } catch (Throwable t) {
            Ln.e("Fatal error", t);
            System.exit(1);
        }
        System.exit(0);
    }
}