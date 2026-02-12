package com.genymobile.scrcpy.device;

import android.os.ParcelFileDescriptor;

import java.io.File;
import java.io.FileDescriptor;
import java.io.IOException;

public final class LocalFileConnection {

    private final ParcelFileDescriptor videoPfd;
    private final ParcelFileDescriptor audioPfd;

    public LocalFileConnection(File videoFile, File audioFile) throws IOException {
        this.videoPfd =
                ParcelFileDescriptor.open(videoFile,
                        ParcelFileDescriptor.MODE_CREATE
                                | ParcelFileDescriptor.MODE_TRUNCATE
                                | ParcelFileDescriptor.MODE_WRITE_ONLY);

        this.audioPfd =
                ParcelFileDescriptor.open(audioFile,
                        ParcelFileDescriptor.MODE_CREATE
                                | ParcelFileDescriptor.MODE_TRUNCATE
                                | ParcelFileDescriptor.MODE_WRITE_ONLY);
    }

    public FileDescriptor getVideoFd() {
        return videoPfd.getFileDescriptor();
    }

    public FileDescriptor getAudioFd() {
        return audioPfd.getFileDescriptor();
    }

    public void close() {
        try {
            videoPfd.close();
        } catch (IOException ignored) {
        }
        try {
            audioPfd.close();
        } catch (IOException ignored) {
        }
    }
}