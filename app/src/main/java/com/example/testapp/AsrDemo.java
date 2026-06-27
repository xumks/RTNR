package com.example.testapp;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.RadioGroup.OnCheckedChangeListener;
import android.widget.Toast;

import com.example.testapp.util.FucUtil;
import com.example.testapp.util.JsonParser;
import com.example.testapp.util.XmlParser;
import com.iflytek.cloud.ErrorCode;
import com.iflytek.cloud.GrammarListener;
import com.iflytek.cloud.InitListener;
import com.iflytek.cloud.LexiconListener;
import com.iflytek.cloud.RecognizerListener;
import com.iflytek.cloud.RecognizerResult;
import com.iflytek.cloud.SpeechConstant;
import com.iflytek.cloud.SpeechError;
import com.iflytek.cloud.SpeechRecognizer;
import com.iflytek.cloud.util.ResourceUtil;
import com.iflytek.cloud.util.ResourceUtil.RESOURCE_TYPE;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AsrDemo extends Activity implements OnClickListener {
    private final static String TAG = AsrDemo.class.getSimpleName();

    private Toast mToast;
    // 语音识别对象
    private SpeechRecognizer mAsr;
    // 缓存
    private SharedPreferences mSharedPreferences;
    // 本地语法文件
    private String mLocalGrammar = null;
    // 本地词典
//    private String mLocalLexicon = null;
    // 本地语法构建路径
    private String grmPath;
    // 返回结果格式，支持：xml,json
    private String mResultType = "json";

    private final String KEY_GRAMMAR_ABNF_ID = "grammar_abnf_id";
    private final String GRAMMAR_TYPE_ABNF = "abnf";
    private final String GRAMMAR_TYPE_BNF = "bnf";
    private String mEngineType = SpeechConstant.TYPE_LOCAL;

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.isrdemo2);
        initLayout();
        grmPath = getExternalFilesDir("msc").getAbsolutePath() + "/test";
        // 初始化识别对象
        mAsr = SpeechRecognizer.createRecognizer(this, mInitListener);
        if (mAsr == null) {
            Log.e(TAG, "masr is null");
        }
        // 初始化语法、命令词
//        mLocalLexicon = "张海羊\n刘婧\n王锋";
        mLocalGrammar = FucUtil.readFile(this, "call.bnf", "utf-8");

        mSharedPreferences = getSharedPreferences(getPackageName(), MODE_PRIVATE);

        initLocalISR();

    }

    /**
     * 初始化Layout。
     */
    private void initLayout() {
        findViewById(R.id.isr_recognize).setOnClickListener(this);

        findViewById(R.id.isr_stop).setOnClickListener(this);
        findViewById(R.id.isr_cancel).setOnClickListener(this);

    }


    String mContent;// 语法、词典临时变量
    int ret = 0;// 函数调用返回值
    void initLocalISR(){
        // 本地-构建语法文件，生成语法id

//        ((EditText) findViewById(R.id.isr_text)).setText(mLocalGrammar);
        mContent = new String(mLocalGrammar);
        mAsr.setParameter(SpeechConstant.PARAMS, null);
        // 设置文本编码格式
        mAsr.setParameter(SpeechConstant.TEXT_ENCODING, "utf-8");
        // 设置引擎类型
        mAsr.setParameter(SpeechConstant.ENGINE_TYPE, mEngineType);
        // 设置语法构建路径
        mAsr.setParameter(ResourceUtil.GRM_BUILD_PATH, grmPath);
        //使用8k音频的时候请解开注释
//					mAsr.setParameter(SpeechConstant.SAMPLE_RATE, "8000");
        // 设置资源路径
        mAsr.setParameter(ResourceUtil.ASR_RES_PATH, getResourcePath());
        ret = mAsr.buildGrammar(GRAMMAR_TYPE_BNF, mContent, grammarListener);
        if (ret != ErrorCode.SUCCESS) {
            showTip("语法构建失败,错误码：" + ret + ",请点击网址https://www.xfyun.cn/document/error-code查询解决方案");
        }

    }
    @Override
    public void onClick(View view) {
        if (null == mAsr) {
            // 创建单例失败，与 21001 错误为同样原因，参考 http://bbs.xfyun.cn/forum.php?mod=viewthread&tid=9688
            this.showTip("创建对象失败，请确认 libmsc.so 放置正确，\n 且有调用 createUtility 进行初始化");
            return;
        }

        if (null == mEngineType) {
            showTip("请先选择识别引擎类型");
            return;
        }
        switch (view.getId()) {


            // 开始识别
            case R.id.isr_recognize:
                ((EditText) findViewById(R.id.isr_text)).setText(null);// 清空显示内容
                // 设置参数
                if (!setParam()) {
                    showTip("请先构建语法。");
                    return;
                }
                ret = mAsr.startListening(mRecognizerListener);
                if (ret != ErrorCode.SUCCESS) {
                    showTip("识别失败,错误码: " + ret + ",请点击网址https://www.xfyun.cn/document/error-code查询解决方案");
                }
                break;
            // 停止识别
            case R.id.isr_stop:
                mAsr.stopListening();
                showTip("停止识别");
                break;
            // 取消识别
            case R.id.isr_cancel:
                mAsr.cancel();
                showTip("取消识别");
                break;
        }
    }

    /**
     * 初始化监听器。
     */
    private InitListener mInitListener = new InitListener() {

        @Override
        public void onInit(int code) {
            Log.d(TAG, "SpeechRecognizer init() code = " + code);
            if (code != ErrorCode.SUCCESS) {
                showTip("初始化失败,错误码：" + code + ",请点击网址https://www.xfyun.cn/document/error-code查询解决方案");
            }
        }
    };

    /**
     * 构建语法监听器。
     */
    private GrammarListener grammarListener = new GrammarListener() {
        @Override
        public void onBuildFinish(String grammarId, SpeechError error) {
            if (error == null) {
                if (mEngineType.equals(SpeechConstant.TYPE_CLOUD)) {
                    Editor editor = mSharedPreferences.edit();
                    if (!TextUtils.isEmpty(grammarId))
                        editor.putString(KEY_GRAMMAR_ABNF_ID, grammarId);
                    editor.commit();
                }
                showTip("语法构建成功：" + grammarId);
            } else {
                showTip("语法构建失败,错误码：" + error.getErrorCode());
            }
        }
    };
    /**
     * 识别监听器。
     */
    private RecognizerListener mRecognizerListener = new RecognizerListener() {

        @Override
        public void onVolumeChanged(int volume, byte[] data) {
            showTip("当前正在说话，音量大小：" + volume);
            Log.d(TAG, "返回音频数据：" + data.length);
        }

        @Override
        public void onResult(final RecognizerResult result, boolean isLast) {
            if (null != result && !TextUtils.isEmpty(result.getResultString())) {
                Log.d(TAG, "recognizer result：" + result.getResultString());
                String text = "";
                if (mResultType.equals("json")) {
                    text = JsonParser.parseGrammarResult(result.getResultString(), mEngineType);
                } else if (mResultType.equals("xml")) {
                    text = XmlParser.parseNluResult(result.getResultString());
                } else {
                    text = result.getResultString();
                }
                // 显示
                if (text.contains("李四的病历") || text.contains("李四病历")) {
                    startActivity(new Intent(AsrDemo.this, AsrResultActivity.class));
                    ((EditText) findViewById(R.id.isr_text)).setText(text);
                    return;
                }
                // 将中文数字转换为阿拉伯数字
                text = convertChineseNumbers(text);
                ((EditText) findViewById(R.id.isr_text)).setText(text);


            } else {
                Log.d(TAG, "recognizer result : null");
            }
        }

        private String convertChineseNumbers(String input) {

            if (TextUtils.isEmpty(input)) {
                return input;
            }

            // 定义中文数字到阿拉伯数字的映射
            Map<Character, Integer> numberMap = new HashMap<>();
            numberMap.put('零', 0);
            numberMap.put('一', 1);
            numberMap.put('二', 2);
            numberMap.put('三', 3);
            numberMap.put('四', 4);
            numberMap.put('五', 5);
            numberMap.put('六', 6);
            numberMap.put('七', 7);
            numberMap.put('八', 8);
            numberMap.put('九', 9);

            // 定义单位映射
            Map<Character, Integer> unitMap = new HashMap<>();
            unitMap.put('十', 10);
            unitMap.put('百', 100);
            unitMap.put('千', 1000);
            unitMap.put('万', 10000);
            unitMap.put('亿', 100000000);

            // 小数部分数字映射（直接转为字符）
            Map<Character, Character> fractionMap = new HashMap<>();
            fractionMap.put('零', '0');
            fractionMap.put('一', '1');
            fractionMap.put('二', '2');
            fractionMap.put('三', '3');
            fractionMap.put('四', '4');
            fractionMap.put('五', '5');
            fractionMap.put('六', '6');
            fractionMap.put('七', '7');
            fractionMap.put('八', '8');
            fractionMap.put('九', '9');

            // 正则匹配连续的中文数字序列（包括单位和小数点）
            Pattern pattern = Pattern.compile("[零一二三四五六七八九十百千万亿点.]+");
            Matcher matcher = pattern.matcher(input);
            StringBuffer sb = new StringBuffer();

            while (matcher.find()) {
                String numStr = matcher.group();
                try {
                    // 检查是否包含小数点
                    int dotIndex = -1;
                    if (numStr.contains("点")) {
                        dotIndex = numStr.indexOf("点");
                    } else if (numStr.contains(".")) {
                        dotIndex = numStr.indexOf('.');
                    }

                    String integerPart = "";
                    String fractionPart = "";
                    if (dotIndex != -1) {
                        integerPart = numStr.substring(0, dotIndex);
                        fractionPart = numStr.substring(dotIndex + 1);
                    } else {
                        integerPart = numStr;
                    }

                    // 转换整数部分
                    int integerValue = 0;
                    if (!integerPart.isEmpty()) {
                        int temp = 0;
                        int result = 0;

                        for (int i = 0; i < integerPart.length(); i++) {
                            char c = integerPart.charAt(i);
                            if (numberMap.containsKey(c)) {
                                temp = numberMap.get(c);
                            } else if (unitMap.containsKey(c)) {
                                int unit = unitMap.get(c);
                                if (unit >= 10000) {
                                    result = (result + temp) * unit;
                                    temp = 0;
                                } else {
                                    result += temp * unit;
                                    temp = 0;
                                }
                            }
                        }
                        result += temp;

                        // 处理"十"开头的情况
                        if (integerPart.startsWith("十") && result < 10) {
                            result += 10;
                        }
                        integerValue = result;
                    }

                    // 转换小数部分
                    StringBuilder fractionValue = new StringBuilder();
                    for (int i = 0; i < fractionPart.length(); i++) {
                        char c = fractionPart.charAt(i);
                        if (fractionMap.containsKey(c)) {
                            fractionValue.append(fractionMap.get(c));
                        } else if (c == '.' || c == '点') {
                            // 跳过多余的小数点
                        } else {
                            // 非数字字符保留原样
                            fractionValue.append(c);
                        }
                    }

                    // 组合结果
                    String resultStr;
                    if (fractionValue.length() == 0) {
                        resultStr = String.valueOf(integerValue);
                    } else {
                        resultStr = integerValue + "." + fractionValue;
                    }

                    matcher.appendReplacement(sb, resultStr);
                } catch (Exception e) {
                    // 转换失败时保留原始文本
                    matcher.appendReplacement(sb, numStr);
                }
            }
            matcher.appendTail(sb);
            return sb.toString();

        }

        @Override
        public void onEndOfSpeech() {
            // 此回调表示：检测到了语音的尾端点，已经进入识别过程，不再接受语音输入
            showTip("结束说话");
        }

        @Override
        public void onBeginOfSpeech() {
            // 此回调表示：sdk内部录音机已经准备好了，用户可以开始语音输入
            showTip("开始说话");
        }

        @Override
        public void onError(SpeechError error) {
            showTip("onError Code：" + error.getErrorCode());
        }

        @Override
        public void onEvent(int eventType, int arg1, int arg2, Bundle obj) {

        }

    };


    private void showTip(final String str) {
        runOnUiThread(() -> {
            if (mToast != null) {
                mToast.cancel();
            }
            mToast = Toast.makeText(getApplicationContext(), str, Toast.LENGTH_SHORT);
            mToast.show();
        });
    }

    /**
     * 参数设置
     *
     * @return
     */
    public boolean setParam() {
        boolean result = false;
        // 清空参数
        mAsr.setParameter(SpeechConstant.PARAMS, null);
        // 设置识别引擎
        mAsr.setParameter(SpeechConstant.ENGINE_TYPE, mEngineType);
        if ("cloud".equalsIgnoreCase(mEngineType)) {
            String grammarId = mSharedPreferences.getString(KEY_GRAMMAR_ABNF_ID, null);
            if (TextUtils.isEmpty(grammarId)) {
                result = false;
            } else {
                // 设置返回结果格式
                mAsr.setParameter(SpeechConstant.RESULT_TYPE, mResultType);
                // 设置云端识别使用的语法id
                mAsr.setParameter(SpeechConstant.CLOUD_GRAMMAR, grammarId);
                result = true;
            }
        } else {
            // 设置本地识别资源
            mAsr.setParameter(ResourceUtil.ASR_RES_PATH, getResourcePath());
            // 设置语法构建路径
            mAsr.setParameter(ResourceUtil.GRM_BUILD_PATH, grmPath);
            // 设置返回结果格式
            mAsr.setParameter(SpeechConstant.RESULT_TYPE, mResultType);
            // 设置本地识别使用语法id
            mAsr.setParameter(SpeechConstant.LOCAL_GRAMMAR, "call");
            // 设置识别的门限值
            mAsr.setParameter(SpeechConstant.MIXED_THRESHOLD, "30");
            // 使用8k音频的时候请解开注释
//			mAsr.setParameter(SpeechConstant.SAMPLE_RATE, "8000");
            result = true;
        }

        // 设置音频保存路径，保存音频格式支持pcm、wav，设置路径为sd卡请注意WRITE_EXTERNAL_STORAGE权限
        mAsr.setParameter(SpeechConstant.AUDIO_FORMAT, "wav");
        mAsr.setParameter(SpeechConstant.ASR_AUDIO_PATH,
                getExternalFilesDir("msc").getAbsolutePath() + "/asr.wav");
        return result;
    }

    //获取识别资源路径
    private String getResourcePath() {
        StringBuffer tempBuffer = new StringBuffer();
        //识别通用资源
        tempBuffer.append(ResourceUtil.generateResourcePath(this, RESOURCE_TYPE.assets, "asr/common.jet"));
        return tempBuffer.toString();
    }

    @Override
    protected void onDestroy() {
        if (null != mAsr) {
            // 退出时释放连接
            mAsr.cancel();
            mAsr.destroy();
        }
        super.onDestroy();
    }

}
