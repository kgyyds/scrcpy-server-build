package com.genymobile.scrcpy;

import com.genymobile.scrcpy.audio.AudioCapture;
import com.genymobile.scrcpy.audio.AudioPlaybackCapture;
import com.genymobile.scrcpy.audio.AudioRawRecorder;
import com.genymobile.scrcpy.audio.AudioCaptureException;
import com.genymobile.scrcpy.audio.AudioConfig;
import com.genymobile.scrcpy.util.IO;
import com.genymobile.scrcpy.util.Ln;

import android.media.MediaCodec;
import android.os.Build;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * 音频分发器 - 处理音频录制请求和响应
 */
public final class AudioDispatcher {

    private AudioDispatcher() {
        /* not instantiable */
    }

    /**
     * 处理音频录制请求
     *
     * @param options 命令行选项
     */
    public static void dispatchAudioRequest(Options options) {
        Ln.i("Audio recording request received");

        if (!options.getGetAudio()) {
            Ln.w("Audio recording not requested");
            return;
        }

        // 检查 Android 版本
        if (Build.VERSION.SDK_INT < 30) {
            Ln.e("Audio recording requires Android 11+ (API 30)");
            Ln.println("{\"error\":\"Audio recording requires Android 11+ (API 30)\"}");
            return;
        }

        // 使用 FakeContext 获取系统服务
        android.content.Context context = FakeContext.get();

        // 创建音频捕获
        AudioCapture capture = new AudioPlaybackCapture(false);

        // 创建输出文件
        File outputFile = new File("/data/local/tmp/scrcpy_audio.pcm");

        try {
            // 开始录制音频
            startAudioRecording(capture, outputFile);
        } catch (Exception e) {
            Ln.e("Audio recording failed", e);
            Ln.println("{\"error\":\"Failed to record audio: " + e.getMessage() + "\"}");
        }
    }

    /**
     * 开始录制音频到文件
     *
     * @param capture 音频捕获器
     * @param outputFile 输出文件
     * @throws IOException 如果发生IO错误
     * @throws AudioCaptureException 如果音频捕获失败
     */
    private static void startAudioRecording(AudioCapture capture, File outputFile)
            throws IOException, AudioCaptureException {

        Ln.i("Starting audio recording to: " + outputFile.getAbsolutePath());

        // 检查兼容性
        capture.checkCompatibility();

        // 创建输出流
        FileOutputStream outputStream = new FileOutputStream(outputFile);

        // 创建缓冲区
        final ByteBuffer buffer = ByteBuffer.allocateDirect(AudioConfig.MAX_READ_SIZE);
        final MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();

        try {
            // 开始捕获
            capture.start();

            // 写入简单的 WAV 头（可选，因为 PCM 是原始数据）
            // 如果是原始 PCM，不需要头文件

            int totalBytesWritten = 0;
            while (!Thread.currentThread().isInterrupted()) {
                buffer.position(0);
                int bytesRead = capture.read(buffer, bufferInfo);

                if (bytesRead < 0) {
                    throw new IOException("Could not read audio: " + bytesRead);
                }

                // 读取的字节数
                byte[] audioData = new byte[bytesRead];
                buffer.get(audioData);

                // 写入文件
                outputStream.write(audioData);
                totalBytesWritten += bytesRead;

                // 定期输出进度
                if (totalBytesWritten % (1024 * 1024) == 0) { // 每MB输出一次
                    Ln.i("Audio recording progress: " + (totalBytesWritten / 1024 / 1024) + "MB written");
                }
            }
        } catch (IOException e) {
            // 破碎管道是预期的关闭情况
            if (!IO.isBrokenPipe(e)) {
                throw e;
            }
            Ln.i("Audio recording completed");
        } finally {
            // 停止捕获
            capture.stop();

            // 关闭输出流
            try {
                outputStream.close();
            } catch (IOException e) {
                Ln.w("Failed to close output stream", e);
            }
        }

        Ln.i("Audio recording completed. File saved to: " + outputFile.getAbsolutePath());
        Ln.println("{\"status\":\"success\",\"file\":\"" + outputFile.getAbsolutePath() + "\",\"size\":" + outputFile.length() + "}");
    }
}