package com.hrconnect.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import org.json.JSONObject;

/** Restarts the WiFi auto-clock service after the phone reboots. */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) return;
        JSONObject cfg = WifiClockService.readConfig(context);
        if (cfg != null && cfg.optBoolean("enabled", false)) {
            WifiClockService.start(context);
        }
    }
}
