package com.hrconnect.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Foreground service that watches the phone's WiFi connection. When the phone
 * joins the configured company network it queues a clock-in event; when it
 * leaves (and stays away longer than the grace period) it queues a clock-out
 * event. The WebView app merges queued events into the attendance history.
 *
 * Android only exposes the WiFi name (SSID) to apps holding location
 * permission with Location Services enabled, and battery savers may stop the
 * service on some phones — the notification keeps it alive where allowed.
 */
public class WifiClockService extends Service {

    public static final String PREFS = "hrc_auto";
    public static final String KEY_CONFIG = "config";   // {enabled, ssid, grace}
    public static final String KEY_EVENTS = "events";   // [{type:'in'|'out', ts:millis}]
    private static final String CHANNEL_ID = "wifi_clock";
    private static final int NOTIF_ID = 1;

    private ConnectivityManager cm;
    private ConnectivityManager.NetworkCallback callback;
    private Handler handler;
    private Runnable pendingOut;
    private boolean onCompanyWifi = false;

    // GPS cross-check state for the current WiFi session
    private boolean gpsCheckRunning = false;
    private boolean gpsRejected = false;
    private int gpsAttempts = 0;
    private LocationListener gpsListener;

    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onCreate() {
        super.onCreate();
        handler = new Handler(Looper.getMainLooper());
        cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(NOTIF_ID, buildNotification("Watching for company WiFi"));
        registerCallback();
        // Evaluate the current state right away (we may already be on WiFi).
        handler.postDelayed(new Runnable() {
            @Override public void run() { evaluate(); }
        }, 1500);
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        if (callback != null) {
            try { cm.unregisterNetworkCallback(callback); } catch (Exception ignored) {}
            callback = null;
        }
        super.onDestroy();
    }

    private void registerCallback() {
        if (callback != null) return;
        NetworkRequest req = new NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .build();
        callback = new ConnectivityManager.NetworkCallback() {
            @Override public void onAvailable(Network network) { postEvaluate(); }
            @Override public void onLost(Network network) { postEvaluate(); }
            @Override public void onCapabilitiesChanged(Network n, NetworkCapabilities c) { postEvaluate(); }
        };
        cm.registerNetworkCallback(req, callback);
    }

    private void postEvaluate() {
        // SSID can lag a network change; settle briefly before reading it.
        handler.removeCallbacksAndMessages("eval");
        handler.postDelayed(new Runnable() {
            @Override public void run() { evaluate(); }
        }, 2000);
    }

    private void evaluate() {
        JSONObject cfg = readConfig(this);
        if (cfg == null || !cfg.optBoolean("enabled", false)) { stopSelf(); return; }
        String target = normalizeSsid(cfg.optString("ssid", ""));
        if (target.length() == 0) return;
        String current = normalizeSsid(currentSsid(this));
        boolean match = current.equalsIgnoreCase(target);

        if (!match) {
            // Left (or never joined) the network: reset the GPS session state so
            // the next arrival is checked afresh.
            gpsRejected = false;
            gpsAttempts = 0;
            stopGpsListener();
            gpsCheckRunning = false;
        }

        if (match && !onCompanyWifi) {
            if (pendingOut != null) { handler.removeCallbacks(pendingOut); pendingOut = null; }
            if (cfg.optBoolean("gps", false)) {
                if (!gpsRejected && !gpsCheckRunning) startGpsCheck(cfg, target);
            } else {
                clockInNow(target, null);
            }
        } else if (!match && onCompanyWifi && pendingOut == null) {
            // Debounce: only clock out if we stay off the network for the grace period.
            final long leftAt = System.currentTimeMillis();
            long graceMs = Math.max(0, cfg.optLong("grace", 10)) * 60000L;
            pendingOut = new Runnable() {
                @Override public void run() {
                    pendingOut = null;
                    onCompanyWifi = false;
                    appendEvent(WifiClockService.this, "out", leftAt, null);
                    updateNotification("Left company WiFi — clocked out");
                }
            };
            handler.postDelayed(pendingOut, graceMs);
            updateNotification("WiFi lost — clocking out soon unless it returns");
        } else if (match && pendingOut != null) {
            handler.removeCallbacks(pendingOut);
            pendingOut = null;
            updateNotification("On company WiFi (" + target + ") — clocked in");
        }
    }

    private void clockInNow(String target, String verifiedLoc) {
        onCompanyWifi = true;
        appendEvent(this, "in", System.currentTimeMillis(), verifiedLoc);
        updateNotification("On company WiFi (" + target + ") — clocked in"
                + (verifiedLoc != null ? " (GPS verified)" : ""));
    }

    /* ---------- GPS cross-check ---------- */

    private void startGpsCheck(final JSONObject cfg, final String target) {
        gpsCheckRunning = true;
        gpsAttempts++;
        updateNotification("On company WiFi — verifying office location…");

        final double lat = cfg.optDouble("lat", 0);
        final double lng = cfg.optDouble("lng", 0);
        final float radius = (float) Math.max(50, cfg.optDouble("radius", 200));

        final LocationManager lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

        // A recent existing fix is good enough (phones on WiFi usually have one).
        Location last = bestLastKnown(lm);
        if (last != null && System.currentTimeMillis() - last.getTime() < 10 * 60000L) {
            finishGpsCheck(cfg, target, last, lat, lng, radius);
            return;
        }

        // Otherwise ask for one fresh fix, with a timeout.
        gpsListener = new LocationListener() {
            @Override public void onLocationChanged(Location location) {
                stopGpsListener();
                if (!gpsCheckRunning) return;
                finishGpsCheck(cfg, target, location, lat, lng, radius);
            }
            @Override public void onStatusChanged(String p, int s, Bundle e) {}
            @Override public void onProviderEnabled(String p) {}
            @Override public void onProviderDisabled(String p) {}
        };
        boolean requested = false;
        try {
            if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                lm.requestSingleUpdate(LocationManager.NETWORK_PROVIDER, gpsListener, Looper.getMainLooper());
                requested = true;
            }
            if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                lm.requestSingleUpdate(LocationManager.GPS_PROVIDER, gpsListener, Looper.getMainLooper());
                requested = true;
            }
        } catch (SecurityException e) {
            stopGpsListener();
            gpsCheckRunning = false;
            updateNotification("Location permission missing — cannot verify office, not clocked in");
            return;
        }
        if (!requested) {
            stopGpsListener();
            gpsCheckRunning = false;
            updateNotification("Location Services off — cannot verify office, not clocked in");
            return;
        }
        // Timeout: no fix within 45s → retry a few times while still on the WiFi.
        handler.postDelayed(new Runnable() {
            @Override public void run() {
                if (!gpsCheckRunning || gpsListener == null) return;
                stopGpsListener();
                gpsCheckRunning = false;
                if (gpsAttempts < 5) {
                    handler.postDelayed(new Runnable() {
                        @Override public void run() { evaluate(); }
                    }, 3 * 60000L);
                    updateNotification("Waiting for a location fix to verify office…");
                } else {
                    updateNotification("Could not get a location fix — not clocked in. Clock in manually in the app.");
                }
            }
        }, 45000L);
    }

    private void finishGpsCheck(JSONObject cfg, String target, Location loc,
                                double lat, double lng, float radius) {
        gpsCheckRunning = false;
        float[] dist = new float[1];
        Location.distanceBetween(loc.getLatitude(), loc.getLongitude(), lat, lng, dist);
        if (dist[0] <= radius) {
            String stamp = String.format(java.util.Locale.US, "%.4f, %.4f",
                    loc.getLatitude(), loc.getLongitude());
            clockInNow(target, "Company WiFi + GPS (" + stamp + ")");
        } else {
            gpsRejected = true;
            updateNotification("On company WiFi but " + Math.round(dist[0])
                    + " m from the office — not clocked in");
        }
    }

    private void stopGpsListener() {
        if (gpsListener != null) {
            try {
                LocationManager lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
                lm.removeUpdates(gpsListener);
            } catch (Exception ignored) {}
            gpsListener = null;
        }
    }

    private Location bestLastKnown(LocationManager lm) {
        Location best = null;
        String[] providers = { LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER,
                LocationManager.PASSIVE_PROVIDER };
        for (int i = 0; i < providers.length; i++) {
            try {
                Location l = lm.getLastKnownLocation(providers[i]);
                if (l != null && (best == null || l.getTime() > best.getTime())) best = l;
            } catch (Exception ignored) {}
        }
        return best;
    }

    /* ---------- shared helpers (also used by MainActivity / BootReceiver) ---------- */

    public static JSONObject readConfig(Context ctx) {
        try {
            String raw = ctx.getSharedPreferences(PREFS, MODE_PRIVATE).getString(KEY_CONFIG, null);
            return raw == null ? null : new JSONObject(raw);
        } catch (Exception e) { return null; }
    }

    public static String currentSsid(Context ctx) {
        try {
            WifiManager wm = (WifiManager) ctx.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            WifiInfo info = wm == null ? null : wm.getConnectionInfo();
            String ssid = info == null ? null : info.getSSID();
            if (ssid == null || ssid.contains("unknown")) return "";
            return normalizeSsid(ssid);
        } catch (Exception e) { return ""; }
    }

    public static String normalizeSsid(String s) {
        if (s == null) return "";
        s = s.trim();
        if (s.length() >= 2 && s.startsWith("\"") && s.endsWith("\"")) {
            s = s.substring(1, s.length() - 1);
        }
        return s.trim();
    }

    public static synchronized void appendEvent(Context ctx, String type, long ts, String loc) {
        try {
            SharedPreferences sp = ctx.getSharedPreferences(PREFS, MODE_PRIVATE);
            JSONArray arr = new JSONArray(sp.getString(KEY_EVENTS, "[]"));
            JSONObject ev = new JSONObject();
            ev.put("type", type);
            ev.put("ts", ts);
            if (loc != null) ev.put("loc", loc);
            arr.put(ev);
            sp.edit().putString(KEY_EVENTS, arr.toString()).apply();
        } catch (Exception ignored) {}
    }

    public static void start(Context ctx) {
        Intent i = new Intent(ctx, WifiClockService.class);
        if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i);
        else ctx.startService(i);
    }

    /* ---------- notification ---------- */

    private Notification buildNotification(String text) {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                NotificationChannel ch = new NotificationChannel(CHANNEL_ID,
                        "WiFi auto clock", NotificationManager.IMPORTANCE_LOW);
                ch.setDescription("Shown while HR Connect watches for the company WiFi");
                nm.createNotificationChannel(ch);
            }
        }
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, open,
                Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);
        b.setContentTitle("HR Connect auto clock")
         .setContentText(text)
         .setSmallIcon(android.R.drawable.ic_menu_recent_history)
         .setOngoing(true)
         .setContentIntent(pi);
        return b.build();
    }

    private void updateNotification(String text) {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        nm.notify(NOTIF_ID, buildNotification(text));
    }
}
