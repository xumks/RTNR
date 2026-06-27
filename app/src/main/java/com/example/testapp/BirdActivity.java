package com.example.testapp;

import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;

public class BirdActivity extends AppCompatActivity {

    private Button btnRecord;
    private TextView tvResult;
    private BirdRecognizer birdRecognizer;
    private boolean isRecording = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 这里需要你自己建一个简单的布局文件 activity_bird.xml
        setContentView(R.layout.activity_bird);

        btnRecord = findViewById(R.id.btn_record_bird);
        tvResult = findViewById(R.id.tv_bird_result);
        birdRecognizer = new BirdRecognizer();

        btnRecord.setOnClickListener(v -> {
            if (!isRecording) {
                // 开始录音
                birdRecognizer.startRecording(this);
                btnRecord.setText("停止录音并识别");
                tvResult.setText("正在录音...");
                isRecording = true;
            } else {
                // 停止并上传
                tvResult.setText("正在分析数据，请稍候...");
                btnRecord.setText("开始识别鸟叫");
                isRecording = false;

                birdRecognizer.stopAndUpload(new Callback() {
                    @Override
                    public void onFailure(Call call, IOException e) {
                        runOnUiThread(() -> tvResult.setText("网络请求失败: " + e.getMessage()));
                    }

                    @Override
                    public void onResponse(Call call, Response response) throws IOException {
                        // 1. 先把响应体读取出来存到字符串变量里
                        String responseBodyString = response.body() != null ? response.body().string() : "null";

                        // 2. 现在你可以放心大胆地打印它了
                        android.util.Log.e("API_DEBUG", "响应体: " + responseBodyString);
                        android.util.Log.e("API_DEBUG", "响应头: " + response.headers().toString());

                        // 3. 然后使用这个变量去做后续的逻辑
                        if (response.isSuccessful()) {
                            runOnUiThread(() -> {
                                // 这里用 responseBodyString 做解析
                                tvResult.setText("识别成功，响应内容: " + responseBodyString);
                            });
                        } else {
                            runOnUiThread(() -> {
                                tvResult.setText("识别服务器报错，错误码: " + response.code() + "\n详情见 Logcat");
                            });
                        }
                    }
                });
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // 确保退出页面时释放录音资源
        if (isRecording) {
            // 可以在 BirdRecognizer 里加个简单的 stop() 方法，不带回调
        }
    }
}