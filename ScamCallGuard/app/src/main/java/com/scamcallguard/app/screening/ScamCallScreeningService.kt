package com.scamcallguard.app.screening

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.telecom.Call
import android.telecom.CallScreeningService
import com.scamcallguard.app.R
import com.scamcallguard.app.ScamGuardApp
import com.scamcallguard.app.db.BlocklistDb
import com.scamcallguard.app.db.PhoneNumbers
import com.scamcallguard.app.ui.MainActivity

/**
 * Screens every incoming call while the app holds ROLE_CALL_SCREENING.
 * Matching numbers are rejected (block mode) or allowed with a high-priority
 * warning notification (warn mode), per the user's setting.
 */
class ScamCallScreeningService : CallScreeningService() {

    override fun onScreenCall(callDetails: Call.Details) {
        // Only screen incoming calls.
        if (callDetails.callDirection != Call.Details.DIRECTION_INCOMING) {
            respondToCall(callDetails, CallResponse.Builder().build())
            return
        }

        val rawNumber = callDetails.handle?.schemeSpecificPart
        val db = BlocklistDb.get(this)
        val entry = rawNumber?.let { db.lookup(it) }

        if (entry == null) {
            respondToCall(callDetails, CallResponse.Builder().build())
            return
        }

        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        val blockMode = prefs.getBoolean(KEY_BLOCK_MODE, true)

        val response = if (blockMode) {
            CallResponse.Builder()
                .setDisallowCall(true)
                .setRejectCall(true)
                .setSkipCallLog(false)
                .setSkipNotification(true)
                .build()
        } else {
            CallResponse.Builder().build()
        }
        respondToCall(callDetails, response)

        val action = if (blockMode) ACTION_BLOCKED else ACTION_WARNED
        val displayNumber = PhoneNumbers.normalize(rawNumber)?.let(PhoneNumbers::display) ?: rawNumber
        db.logCall(displayNumber, entry.label, action)
        notifyUser(displayNumber, entry.label, blockMode)
    }

    private fun notifyUser(number: String, label: String, blocked: Boolean) {
        val channel = if (blocked) ScamGuardApp.CHANNEL_BLOCKED else ScamGuardApp.CHANNEL_WARNINGS
        val title = getString(
            if (blocked) R.string.notif_blocked_title else R.string.notif_warning_title
        )
        val contentIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = Notification.Builder(this, channel)
            .setSmallIcon(R.drawable.ic_shield)
            .setContentTitle(title)
            .setContentText(getString(R.string.notif_body, number, label))
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .build()
        getSystemService(NotificationManager::class.java)
            .notify(number.hashCode(), notification)
    }

    companion object {
        const val PREFS = "scamguard_prefs"
        const val KEY_BLOCK_MODE = "block_mode"
        const val ACTION_BLOCKED = "blocked"
        const val ACTION_WARNED = "warned"
    }
}
