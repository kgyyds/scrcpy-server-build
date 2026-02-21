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
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.OutputConfiguration;
import android.hardware.camera2.params.SessionConfiguration;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.media.MediaCodec;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Range;
import android.graphics.ImageFormat;
import android.view.Surface;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.stream.Stream;

/**
 * 简化的拍照捕获类 - 不涉及视频流，只拍照保存
 */
public class CameraCapture {

    private final String explicitCameraId;
    private final CameraFacing cameraFacing;
    private final Size explicitSize;
    private final CameraAspectRatio aspectRatio;

    private String cameraId;
    private Size captureSize;

    private HandlerThread cameraThread;
    private Handler cameraHandler;
    private CameraDevice cameraDevice;
    private Executor cameraExecutor;

    private ImageReader photoReader;
    private boolean photoTaken = false;

    public CameraCapture(Options options) {
        this.explicitCameraId = options.getCameraId();
        this.cameraFacing = options.getCameraFacing();
        this.explicitSize = options.getCameraSize();
        this.aspectRatio = options.getCameraAspectRatio();
    }

    public void init() throws ConfigurationException, IOException {
        cameraThread = new HandlerThread("camera");
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
        } catch (CameraAccessException | InterruptedException e) {
            throw new IOException(e);
        }
    }

    public void prepare() throws IOException {
        try {
            captureSize = selectSize(cameraId, explicitSize, aspectRatio);
            if (captureSize == null) {
                throw new IOException("Could not select camera size");
            }
        } catch (CameraAccessException e) {
            throw new IOException(e);
        }
    }

    /**
     * 启动拍照 - 不涉及视频流
     */
    public void startForPhoto() throws IOException {
        // 创建ImageReader用于拍照
        photoReader = ImageReader.newInstance(
                captureSize.getWidth(),
                captureSize.getHeight(),
                ImageFormat.JPEG,
                1
        );

        // 设置照片回调
        photoReader.setOnImageAvailableListener(reader -> {
            Image image = null;
            try {
                image = reader.acquireNextImage();
                if (image == null) return;

                ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                byte[] jpeg = new byte[buffer.remaining()];
                buffer.get(jpeg);

                // 保存照片
                try (FileOutputStream fos = new FileOutputStream("/data/local/tmp/scrcpy_photo.jpg")) {
                    fos.write(jpeg);
                    Ln.i("📸 Photo saved: /data/local/tmp/scrcpy_photo.jpg");
                }
            } catch (Exception e) {
                Ln.e("Photo capture failed", e);
            } finally {
                if (image != null) image.close();
            }
        }, cameraHandler);

        try {
            // 创建拍照会话（只需要photoReader的Surface）
            Surface photoSurface = photoReader.getSurface();
            List<OutputConfiguration> outputs = Arrays.asList(
                    new OutputConfiguration(photoSurface)
            );

            SessionConfiguration sessionConfig = new SessionConfiguration(
                    SessionConfiguration.SESSION_REGULAR, outputs, cameraExecutor,
                    new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured(CameraCaptureSession session) {
                            triggerPhotoCapture(session);
                        }

                        @Override
                        public void onConfigureFailed(CameraCaptureSession session) {
                            Ln.e("Failed to configure camera session");
                        }
                    }
            );

            cameraDevice.createCaptureSession(sessionConfig);
        } catch (CameraAccessException e) {
            throw new IOException(e);
        }
    }

    private void triggerPhotoCapture(CameraCaptureSession session) {
        if (photoTaken) return;

        try {
            CaptureRequest.Builder stillBuilder =
                    cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            stillBuilder.addTarget(photoReader.getSurface());
            session.capture(stillBuilder.build(), null, cameraHandler);
            photoTaken = true;
            Ln.i("📸 Triggered photo capture");
        } catch (CameraAccessException e) {
            Ln.e("Failed to trigger photo capture", e);
        }
    }

    public void release() {
        if (cameraDevice != null) {
            cameraDevice.close();
        }
        if (cameraThread != null) {
            cameraThread.quitSafely();
        }
        if (photoReader != null) {
            photoReader.close();
        }
    }

    public Size getSize() {
        return captureSize;
    }

    @SuppressLint("MissingPermission")
    @TargetApi(AndroidVersions.API_31_ANDROID_12)
    private CameraDevice openCamera(String id) throws CameraAccessException, InterruptedException {
        CompletableFuture<CameraDevice> future = new CompletableFuture<>();
        ServiceManager.getCameraManager().openCamera(id, new CameraDevice.StateCallback() {
            @Override
            public void onOpened(CameraDevice camera) {
                Ln.d("Camera opened successfully");
                future.complete(camera);
            }

            @Override
            public void onDisconnected(CameraDevice camera) {
                Ln.w("Camera disconnected");
            }

            @Override
            public void onError(CameraDevice camera, int error) {
                int cameraAccessExceptionErrorCode;
                switch (error) {
                    case CameraDevice.StateCallback.ERROR_CAMERA_IN_USE:
                        cameraAccessExceptionErrorCode = CameraAccessException.CAMERA_IN_USE;
                        break;
                    case CameraDevice.StateCallback.ERROR_MAX_CAMERAS_IN_USE:
                        cameraAccessExceptionErrorCode = CameraAccessException.MAX_CAMERAS_IN_USE;
                        break;
                    case CameraDevice.StateCallback.ERROR_CAMERA_DISABLED:
                        cameraAccessExceptionErrorCode = CameraAccessException.CAMERA_DISABLED;
                        break;
                    case CameraDevice.StateCallback.ERROR_CAMERA_DEVICE:
                    case CameraDevice.StateCallback.ERROR_CAMERA_SERVICE:
                    default:
                        cameraAccessExceptionErrorCode = CameraAccessException.CAMERA_ERROR;
                        break;
                }
                future.completeExceptionally(new CameraAccessException(cameraAccessExceptionErrorCode));
            }
        }, cameraHandler);

        try {
            return future.get();
        } catch (ExecutionException e) {
            throw (CameraAccessException) e.getCause();
        }
    }

    private static String selectCamera(String explicitCameraId, CameraFacing cameraFacing)
            throws CameraAccessException, ConfigurationException {
        CameraManager cameraManager = ServiceManager.getCameraManager();

        String[] cameraIds = cameraManager.getCameraIdList();
        if (explicitCameraId != null) {
            if (!Arrays.asList(cameraIds).contains(explicitCameraId)) {
                Ln.e("Camera with id " + explicitCameraId + " not found\n" + LogUtils.buildCameraListMessage(false));
                throw new ConfigurationException("Camera id not found");
            }
            return explicitCameraId;
        }

        if (cameraFacing == null) {
            // Use the first one
            return cameraIds.length > 0 ? cameraIds[0] : null;
        }

        for (String cameraId : cameraIds) {
            CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(cameraId);

            int facing = characteristics.get(CameraCharacteristics.LENS_FACING);
            if (cameraFacing.value() == facing) {
                return cameraId;
            }
        }

        // Not found
        return null;
    }

    @TargetApi(AndroidVersions.API_24_ANDROID_7_0)
    private static Size selectSize(String cameraId, Size explicitSize, CameraAspectRatio aspectRatio)
            throws CameraAccessException {
        if (explicitSize != null) {
            return explicitSize;
        }

        CameraManager cameraManager = ServiceManager.getCameraManager();
        CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(cameraId);

        StreamConfigurationMap configs = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        // 使用JPEG格式的尺寸而不是MediaCodec的尺寸
        android.util.Size[] sizes = configs.getOutputSizes(ImageFormat.JPEG);
        if (sizes == null) {
            return null;
        }

        Stream<android.util.Size> stream = Arrays.stream(sizes);

        Float targetAspectRatio = resolveAspectRatio(aspectRatio, characteristics);
        if (targetAspectRatio != null) {
            stream = stream.filter(it -> {
                float ar = ((float) it.getWidth() / it.getHeight());
                float arRatio = ar / targetAspectRatio;
                // Accept if the aspect ratio is the target aspect ratio + or - 10%
                return arRatio >= 0.9f && arRatio <= 1.1f;
            });
        }

        Optional<android.util.Size> selected = stream.max((s1, s2) -> {
            // Greater width is better
            int cmp = Integer.compare(s1.getWidth(), s2.getWidth());
            if (cmp != 0) {
                return cmp;
            }

            if (targetAspectRatio != null) {
                // Closer to the target aspect ratio is better
                float ar1 = ((float) s1.getWidth() / s1.getHeight());
                float arRatio1 = ar1 / targetAspectRatio;
                float distance1 = Math.abs(1 - arRatio1);

                float ar2 = ((float) s2.getWidth() / s2.getHeight());
                float arRatio2 = ar2 / targetAspectRatio;
                float distance2 = Math.abs(1 - arRatio2);

                // Reverse the order because lower distance is better
                cmp = Float.compare(distance2, distance1);
                if (cmp != 0) {
                    return cmp;
                }
            }

            // Greater height is better
            return Integer.compare(s1.getHeight(), s2.getHeight());
        });

        if (selected.isPresent()) {
            android.util.Size size = selected.get();
            return new Size(size.getWidth(), size.getHeight());
        }

        // Not found
        return null;
    }

    private static Float resolveAspectRatio(CameraAspectRatio ratio, CameraCharacteristics characteristics) {
        if (ratio == null) {
            return null;
        }

        if (ratio.isSensor()) {
            Rect activeSize = characteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);
            return (float) activeSize.width() / activeSize.height();
        }

        return ratio.getAspectRatio();
    }
}
