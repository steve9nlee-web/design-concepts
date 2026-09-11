package com.scamcallguard.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager

class ScamGuardApp : Application() {

    override fun onCreate() {
        super.onCreate()
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_BLOCKED,
                getString(R.string.channel_blocked_calls),
                NotificationManager.IMPORTANCE_DEFAULT
            )
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_WARNINGS,
                getString(R.string.channel_warnings),
                NotificationManager.IMPORTANCE_HIGH
            )
        )
    }

    companion object {
        const val CHANNEL_BLOCKED = "blocked_calls"
        const val CHANNEL_WARNINGS = "scam_warnings"
    }
}
