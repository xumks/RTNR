package com.example.testapp;

import android.annotation.SuppressLint;
import android.content.Context;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;

public class BirdRecognizer {
    private AudioRecord audioRecord;
    private boolean isRecording = false;
    private Thread recordingThread = null;
    private String mFilePath;
    private int bufferSize;
    // 使用 44100Hz 采样率，全机型兼容，且 BirdNET 模型完美支持
    private final int SAMPLE_RATE = 44100;

    @SuppressLint("MissingPermission")
    public void startRecording(Context context) {
        // 这次我们生成纯正的 .wav 文件
        mFilePath = context.getExternalCacheDir().getAbsolutePath() + "/bird_audio.wav";

        bufferSize = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT);

        try {
            audioRecord = new AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferSize);

            audioRecord.startRecording();
            isRecording = true;

            // 开辟后台线程将录音数据实时写入本地文件
            recordingThread = new Thread(() -> writeWavFile(mFilePath));
            recordingThread.start();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void writeWavFile(String path) {
        byte[] data = new byte[bufferSize];
        try (FileOutputStream os = new FileOutputStream(path)) {
            // 写入44字节的占位符（等录完再替换为真实的 WAV 文件头）
            for (int i = 0; i < 44; i++) {
                os.write(0);
            }

            while (isRecording && audioRecord != null) {
                int read = audioRecord.read(data, 0, bufferSize);
                if (read > 0) {
                    os.write(data, 0, read);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (audioRecord != null) {
                try {
                    audioRecord.stop();
                    audioRecord.release();
                } catch (Exception e) {
                    e.printStackTrace();
                }
                audioRecord = null;
            }
        }

        // 录音结束后，给文件打上标准的 WAV 格式烙印
        try {
            writeWavHeader(new File(path));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void writeWavHeader(File file) throws IOException {
        long totalAudioLen = file.length() - 44;
        long totalDataLen = totalAudioLen + 36;
        long byteRate = 16 * SAMPLE_RATE * 1 / 8;

        byte[] header = new byte[44];
        header[0] = 'R'; header[1] = 'I'; header[2] = 'F'; header[3] = 'F';
        header[4] = (byte) (totalDataLen & 0xff);
        header[5] = (byte) ((totalDataLen >> 8) & 0xff);
        header[6] = (byte) ((totalDataLen >> 16) & 0xff);
        header[7] = (byte) ((totalDataLen >> 24) & 0xff);
        header[8] = 'W'; header[9] = 'A'; header[10] = 'V'; header[11] = 'E';
        header[12] = 'f'; header[13] = 'm'; header[14] = 't'; header[15] = ' ';
        header[16] = 16; header[17] = 0; header[18] = 0; header[19] = 0;
        header[20] = 1; header[21] = 0;
        header[22] = 1; header[23] = 0;
        header[24] = (byte) (SAMPLE_RATE & 0xff);
        header[25] = (byte) ((SAMPLE_RATE >> 8) & 0xff);
        header[26] = (byte) ((SAMPLE_RATE >> 16) & 0xff);
        header[27] = (byte) ((SAMPLE_RATE >> 24) & 0xff);
        header[28] = (byte) (byteRate & 0xff);
        header[29] = (byte) ((byteRate >> 8) & 0xff);
        header[30] = (byte) ((byteRate >> 16) & 0xff);
        header[31] = (byte) ((byteRate >> 24) & 0xff);
        header[32] = 2; header[33] = 0;
        header[34] = 16; header[35] = 0;
        header[36] = 'd'; header[37] = 'a'; header[38] = 't'; header[39] = 'a';
        header[40] = (byte) (totalAudioLen & 0xff);
        header[41] = (byte) ((totalAudioLen >> 8) & 0xff);
        header[42] = (byte) ((totalAudioLen >> 16) & 0xff);
        header[43] = (byte) ((totalAudioLen >> 24) & 0xff);

        try (RandomAccessFile raf = new RandomAccessFile(file, "rw")) {
            raf.seek(0);
            raf.write(header, 0, 44);
        }
    }

    public void stopAndUpload(Callback callback) {
        // 强制把录音停止逻辑先注释掉，只测试网络连接
        // isRecording = false;

        // 假设你桌面上已经有一个现成的 bird_audio.wav 文件供测试
        File file = new File(mFilePath);

        // 打印一下文件信息看看存不存在
        android.util.Log.e("API_DEBUG", "文件路径: " + mFilePath + " 是否存在: " + file.exists() + " 大小: " + file.length());

        String myToken = "YOUR_BIRDWEATHER_STATION_TOKEN";

        // 最简单的请求体
        RequestBody fileBody = RequestBody.create(file, MediaType.parse("audio/wav"));

        Request request = new Request.Builder()
                .url("https://app.birdweather.com/api/v1/stations/" + myToken + "/soundscapes")
                .addHeader("Authorization", "Bearer " + myToken)
                .post(fileBody)
                .build();

        // 直接在当前线程执行（如果这里报错，Logcat 里的 Error 级别一定会显示出来）
        new OkHttpClient().newCall(request).enqueue(callback);
    }
}
