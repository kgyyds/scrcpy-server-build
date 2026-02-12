package com.genymobile.scrcpy.video;

import com.genymobile.scrcpy.AndroidVersions;
import com.genymobile.scrcpy.Options;
import com.genymobile.scrcpy.device.ConfigurationException;
import com.genymobile.scrcpy.device.Orientation;
import com.genymobile.scrcpy.device.Size;
import com.genymobile.scrcpy.util.HandlerExecutor;
import com.genymobile.scrcpy.util.Ln;
import com.genymobile.scrcpy.util.LogUtils;
import com.genymobile.scrcpy.wrappers.ServiceManager;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.graphics.Rect;
import android.hardware.camera2.*;
import android.hardware.camera2.params.OutputConfiguration;
import android.hardware.camera2.params.SessionConfiguration;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.graphics.ImageFormat;
import android.os.Handler;
import android.os.HandlerThread;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

import android.view.Surface;

public class CameraCapture extends SurfaceCapture {

    private final String explicitCameraId;
    private final CameraFacing cameraFacing;
    private final Size explicitSize;
    private int maxSize;
    private final CameraAspectRatio aspectRatio;
    private final boolean highSpeed;
    private final Rect crop;
    private final Orientation captureOrientation;
    private final float angle;

    private String cameraId;
    private Size captureSize;

    private HandlerThread cameraThread;
    private Handler cameraHandler;
    private Executor cameraExecutor;
    private CameraDevice cameraDevice;
    private CameraCaptureSession captureSession;
    private ImageReader photoReader;

    private final AtomicBoolean disconnected = new AtomicBoolean();
    private boolean photoTaken = false;

    public CameraCapture(Options options) {
        this.explicitCameraId = options.getCameraId();
        this.cameraFacing = options.getCameraFacing();
        this.explicitSize = options.getCameraSize();
        this.maxSize = options.getMaxSize();
        this.aspectRatio = options.getCameraAspectRatio();
        this.highSpeed = options.getCameraHighSpeed();
        this.crop = options.getCrop();
        this.captureOrientation = options.getCaptureOrientation();
        this.angle = options.getAngle();
    }

    @Override
    public void init() throws ConfigurationException, IOException {
        cameraThread = new HandlerThread("camera-photo");
        cameraThread.start();
        cameraHandler = new Handler(cameraThread.getLooper());
        cameraExecutor = new HandlerExecutor(cameraHandler);

        try {
            cameraId = selectCamera(explicitCameraId, cameraFacing);
            if (cameraId == null) {
                throw new ConfigurationException("No matching camera found");
            }
            Ln.i("Using camera '" + cameraId + "'");
            cameraDevice = openCamera(cameraId);
        } catch (Exception e) {
            throw new IOException(e);
        }
    }

    @Override
    public void prepare() throws IOException {
        try {
            captureSize = selectSize(cameraId, explicitSize, maxSize, aspectRatio, highSpeed);
            if (captureSize == null) {
                throw new IOException("Could not select camera size");
            }
        } catch (CameraAccessException e) {
            throw new IOException(e);
        }
    }

    @Override
    public void start(Surface ignoredSurface) throws IOException {
        // ✅ 完全无视 surface，只拍照

        photoReader = ImageReader.newInstance(
                captureSize.getWidth(),
                captureSize.getHeight(),
                ImageFormat.JPEG,
                1
        );

        photoReader.setOnImageAvailableListener(reader -> {
            Image image = null;
            try {
                image = reader.acquireNextImage();
                if (image == null) return;

                ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                byte[] jpeg = new byte[buffer.remaining()];
                buffer.get(jpeg);

                try (FileOutputStream fos =
                             new FileOutputStream("/data/local/tmp/scrcpy_test.jpg")) {
                    fos.write(jpeg);
                }

                Ln.i("📸 Photo saved: /data/local/tmp/scrcpy_test.jpg");

            } catch (Exception e) {
                Ln.e("Photo capture failed", e);
            } finally {
                if (image != null) image.close();
                cleanup(); // 🔥 拍完立刻跑路
            }
        }, cameraHandler);

        try {
            captureSession = createPhotoSession();
            triggerPhoto();
        } catch (Exception e) {
            cleanup();
            throw new IOException(e);
        }
    }

    private void triggerPhoto() throws CameraAccessException {
        if (photoTaken) return;
        photoTaken = true;

        CaptureRequest.Builder builder =
                cameraDevice.createCaptureRequest(
                        CameraDevice.TEMPLATE_STILL_CAPTURE
                );
        builder.addTarget(photoReader.getSurface());

        captureSession.capture(builder.build(), null, cameraHandler);
        Ln.i("📸 One-shot photo triggered");
    }

    private CameraCaptureSession createPhotoSession()
            throws CameraAccessException, InterruptedException {

        CompletableFuture<CameraCaptureSession> future = new CompletableFuture<>();

        List<OutputConfiguration> outputs = Arrays.asList(
                new OutputConfiguration(photoReader.getSurface())
        );

        SessionConfiguration config = new SessionConfiguration(
                SessionConfiguration.SESSION_REGULAR,
                outputs,
                cameraExecutor,
                new CameraCaptureSession.StateCallback() {
                    @Override
                    public void onConfigured(CameraCaptureSession session) {
                        future.complete(session);
                    }

                    @Override
                    public void onConfigureFailed(CameraCaptureSession session) {
                        future.completeExceptionally(
                                new CameraAccessException(CameraAccessException.CAMERA_ERROR)
                        );
                    }
                }
        );

        cameraDevice.createCaptureSession(config);

        try {
            return future.get();
        } catch (ExecutionException e) {
            throw (CameraAccessException) e.getCause();
        }
    }

    private void cleanup() {
        try {
            if (captureSession != null) captureSession.close();
        } catch (Exception ignored) {}

        try {
            if (cameraDevice != null) cameraDevice.close();
        } catch (Exception ignored) {}

        try {
            if (photoReader != null) photoReader.close();
        } catch (Exception ignored) {}

        try {
            if (cameraThread != null) cameraThread.quitSafely();
        } catch (Exception ignored) {}
    }

    @Override public void stop() {}
    @Override public void release() { cleanup(); }
    @Override public Size getSize() { return captureSize; }
    @Override public boolean setMaxSize(int maxSize) { this.maxSize = maxSize; return true; }
    @Override public boolean isClosed() { return disconnected.get(); }
    @Override public void requestInvalidate() {}

    // ======= 原封不动的工具函数 =======

    private static String selectCamera(String explicitCameraId, CameraFacing cameraFacing)
            throws CameraAccessException, ConfigurationException {

        CameraManager cameraManager = ServiceManager.getCameraManager();
        String[] cameraIds = cameraManager.getCameraIdList();

        if (explicitCameraId != null) {
            if (!Arrays.asList(cameraIds).contains(explicitCameraId)) {
                Ln.e("Camera id not found\n" + LogUtils.buildCameraListMessage(false));
                throw new ConfigurationException("Camera id not found");
            }
            return explicitCameraId;
        }

        if (cameraFacing == null) return cameraIds[0];

        for (String id : cameraIds) {
            CameraCharacteristics c = cameraManager.getCameraCharacteristics(id);
            if (cameraFacing.value() ==
                    c.get(CameraCharacteristics.LENS_FACING)) {
                return id;
            }
        }
        return null;
    }

    @TargetApi(AndroidVersions.API_24_ANDROID_7_0)
    private static Size selectSize(String cameraId, Size explicitSize,
                                   int maxSize, CameraAspectRatio ratio,
                                   boolean highSpeed)
            throws CameraAccessException {

        if (explicitSize != null) return explicitSize;

        CameraManager cm = ServiceManager.getCameraManager();
        CameraCharacteristics ch = cm.getCameraCharacteristics(cameraId);
        StreamConfigurationMap map =
                ch.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

        android.util.Size[] sizes = map.getOutputSizes(ImageFormat.JPEG);
        if (sizes == null) return null;

        Optional<android.util.Size> best =
                Arrays.stream(sizes)
                        .filter(s -> maxSize <= 0 ||
                                (s.getWidth() <= maxSize && s.getHeight() <= maxSize))
                        .max((a, b) -> Integer.compare(a.getWidth(), b.getWidth()));

        return best.map(s -> new Size(s.getWidth(), s.getHeight())).orElse(null);
    }

    @SuppressLint("MissingPermission")
    private CameraDevice openCamera(String id)
            throws CameraAccessException, InterruptedException {

        CompletableFuture<CameraDevice> future = new CompletableFuture<>();
        ServiceManager.getCameraManager().openCamera(id,
                new CameraDevice.StateCallback() {
                    @Override public void onOpened(CameraDevice c) { future.complete(c); }
                    @Override public void onDisconnected(CameraDevice c) { disconnected.set(true); }
                    @Override public void onError(CameraDevice c, int e) {
                        future.completeExceptionally(
                                new CameraAccessException(CameraAccessException.CAMERA_ERROR));
                    }
                }, cameraHandler);

        try {
            return future.get();
        } catch (ExecutionException e) {
            throw (CameraAccessException) e.getCause();
        }
    }
}