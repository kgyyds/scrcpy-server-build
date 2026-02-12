package com.genymobile.scrcpy.video;

import android.media.ImageReader;
import android.graphics.ImageFormat;
import android.view.Surface;

public final class SurfaceUtils {
    public static Surface createDummySurface(int w, int h) {
        ImageReader reader = ImageReader.newInstance(
                w, h, ImageFormat.YUV_420_888, 1
        );
        return reader.getSurface();
    }
}