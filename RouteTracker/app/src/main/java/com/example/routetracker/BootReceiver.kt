package com.example.routetracker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Re-arms monitoring after a reboot if the user left it enabled. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED &&
            Prefs.isMonitoringEnabled(context) &&
            Prefs.getHomeSsid(context) != null
        ) {
            runCatching { TrackingService.start(context) }
        }
    }
}
