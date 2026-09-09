package com.savault.app;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;

public class MainActivity extends Activity {

    private WebView web;
    private SpeechRecognizer recognizer;
    private final Handler main = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        web = new WebView(this);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        web.setWebChromeClient(new WebChromeClient());
        web.addJavascriptInterface(new VoiceBridge(), "AndroidVoice");
        web.addJavascriptInterface(new StoreBridge(), "AndroidStore");
        setContentView(web);

        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 1);
        }
        web.loadUrl("file:///android_asset/index.html");
    }

    @Override
    protected void onDestroy() {
        if (recognizer != null) {
            recognizer.destroy();
            recognizer = null;
        }
        super.onDestroy();
    }

    private void js(String script) {
        main.post(() -> web.evaluateJavascript(script, null));
    }

    private void sendError(String code) {
        js("window.__savaultOnError && __savaultOnError(" + JSONObject.quote(code) + ")");
    }

    private void startListening(String lang) {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 1);
            sendError("not-allowed");
            return;
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            sendError("service-not-allowed");
            return;
        }
        if (recognizer == null) {
            recognizer = SpeechRecognizer.createSpeechRecognizer(this);
            recognizer.setRecognitionListener(new RecognitionListener() {
                @Override public void onResults(Bundle results) {
                    ArrayList<String> alts =
                            results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    if (alts == null || alts.isEmpty()) {
                        sendError("no-speech");
                        return;
                    }
                    String json = new JSONArray(alts).toString();
                    js("window.__savaultOnResult && __savaultOnResult(" + JSONObject.quote(json) + ")");
                }
                @Override public void onError(int error) {
                    switch (error) {
                        case SpeechRecognizer.ERROR_NO_MATCH:
                        case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:
                            sendError("no-speech");
                            break;
                        case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS:
                            sendError("not-allowed");
                            break;
                        default:
                            sendError("aborted");
                    }
                }
                @Override public void onReadyForSpeech(Bundle params) {}
                @Override public void onBeginningOfSpeech() {}
                @Override public void onRmsChanged(float rmsdB) {}
                @Override public void onBufferReceived(byte[] buffer) {}
                @Override public void onEndOfSpeech() {}
                @Override public void onPartialResults(Bundle partialResults) {}
                @Override public void onEvent(int eventType, Bundle params) {}
            });
        }
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang);
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3);
        recognizer.startListening(intent);
    }

    private class VoiceBridge {
        @JavascriptInterface
        public void start(final String lang) {
            main.post(() -> startListening(lang));
        }
    }

    // Durable key-value storage for the web app: WebView localStorage on
    // file:// URLs does not reliably survive app restarts, SharedPreferences does.
    private class StoreBridge {
        private android.content.SharedPreferences prefs() {
            return getSharedPreferences("savault", MODE_PRIVATE);
        }

        @JavascriptInterface
        public String getItem(String key) {
            return prefs().getString(key, null);
        }

        @JavascriptInterface
        public void setItem(String key, String value) {
            prefs().edit().putString(key, value).commit();
        }

        @JavascriptInterface
        public void removeItem(String key) {
            prefs().edit().remove(key).commit();
        }
    }
}
