package com.hrconnect.app;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.webkit.GeolocationPermissions;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

public class MainActivity extends Activity {

    private static final int REQ_FILE_CHOOSER = 1001;
    private static final int REQ_LOCATION = 1002;

    private WebView webView;
    private ValueCallback<Uri[]> filePathCallback;
    private String pendingGeoOrigin;
    private GeolocationPermissions.Callback pendingGeoCallback;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        webView = new WebView(this);
        setContentView(webView);

        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setGeolocationEnabled(true);
        s.setAllowFileAccess(true);
        s.setTextZoom(100);

        webView.addJavascriptInterface(new Bridge(), "AndroidBridge");
        webView.setWebViewClient(new WebViewClient());
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback,
                                             FileChooserParams params) {
                if (filePathCallback != null) {
                    filePathCallback.onReceiveValue(null);
                }
                filePathCallback = callback;
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("*/*");
                try {
                    startActivityForResult(Intent.createChooser(intent, "Attach document"), REQ_FILE_CHOOSER);
                } catch (Exception e) {
                    filePathCallback.onReceiveValue(null);
                    filePathCallback = null;
                    return false;
                }
                return true;
            }

            @Override
            public void onGeolocationPermissionsShowPrompt(String origin,
                                                           GeolocationPermissions.Callback callback) {
                if (hasLocationPermission()) {
                    callback.invoke(origin, true, false);
                } else {
                    pendingGeoOrigin = origin;
                    pendingGeoCallback = callback;
                    requestPermissions(new String[]{
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                    }, REQ_LOCATION);
                }
            }
        });

        webView.loadUrl("file:///android_asset/www/index.html");

        // Resume WiFi auto-clock monitoring if the user enabled it.
        org.json.JSONObject cfg = WifiClockService.readConfig(this);
        if (cfg != null && cfg.optBoolean("enabled", false)) {
            WifiClockService.start(this);
        }
    }

    private boolean hasLocationPermission() {
        return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == REQ_LOCATION && pendingGeoCallback != null) {
            pendingGeoCallback.invoke(pendingGeoOrigin, hasLocationPermission(), false);
            pendingGeoCallback = null;
            pendingGeoOrigin = null;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQ_FILE_CHOOSER && filePathCallback != null) {
            Uri[] result = null;
            if (resultCode == Activity.RESULT_OK && data != null && data.getData() != null) {
                result = new Uri[]{data.getData()};
            }
            filePathCallback.onReceiveValue(result);
            filePathCallback = null;
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    @Override
    public void onBackPressed() {
        // Let the SPA decide: it navigates back to the dashboard, or calls
        // AndroidBridge.exitApp() when already on the dashboard.
        webView.evaluateJavascript(
                "(function(){ if (window.appBack) { return appBack(); } return 'exit'; })();",
                new ValueCallback<String>() {
                    @Override
                    public void onReceiveValue(String value) {
                        if (value != null && value.contains("exit")) {
                            finish();
                        }
                    }
                });
    }

    private class Bridge {
        @JavascriptInterface
        public void exitApp() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    finish();
                }
            });
        }

        @JavascriptInterface
        public String appVersion() {
            return "1.2";
        }

        /* ---------- WiFi auto clock-in/out ---------- */

        @JavascriptInterface
        public String getAutoConfig() {
            org.json.JSONObject cfg = WifiClockService.readConfig(MainActivity.this);
            return cfg == null ? "{}" : cfg.toString();
        }

        @JavascriptInterface
        public void setAutoConfig(String json) {
            getSharedPreferences(WifiClockService.PREFS, MODE_PRIVATE)
                    .edit().putString(WifiClockService.KEY_CONFIG, json).apply();
            org.json.JSONObject cfg = WifiClockService.readConfig(MainActivity.this);
            final boolean enabled = cfg != null && cfg.optBoolean("enabled", false);
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (enabled) {
                        if (!hasLocationPermission()) {
                            requestPermissions(new String[]{
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION
                            }, REQ_LOCATION);
                        }
                        WifiClockService.start(MainActivity.this);
                    } else {
                        stopService(new Intent(MainActivity.this, WifiClockService.class));
                    }
                }
            });
        }

        @JavascriptInterface
        public String getCurrentSsid() {
            if (!hasLocationPermission()) return "";
            return WifiClockService.currentSsid(MainActivity.this);
        }

        @JavascriptInterface
        public boolean hasLocation() {
            return hasLocationPermission();
        }

        @JavascriptInterface
        public String getPendingEvents() {
            return getSharedPreferences(WifiClockService.PREFS, MODE_PRIVATE)
                    .getString(WifiClockService.KEY_EVENTS, "[]");
        }

        @JavascriptInterface
        public void clearPendingEvents() {
            getSharedPreferences(WifiClockService.PREFS, MODE_PRIVATE)
                    .edit().putString(WifiClockService.KEY_EVENTS, "[]").apply();
        }
    }
}
