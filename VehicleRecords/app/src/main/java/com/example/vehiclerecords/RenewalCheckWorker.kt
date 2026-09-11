package com.example.vehiclerecords

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.Worker
import androidx.work.WorkerParameters

/**
 * Runs once a day: checks every vehicle's road tax, insurance and inspection
 * dates and posts a reminder notification when anything is due (red) or
 * within [Renewal.DUE_SOON_DAYS] days (yellow).
 */
class RenewalCheckWorker(context: Context, params: WorkerParameters) : Worker(context, params) {

    override fun doWork(): Result {
        val ctx = applicationContext
        val dueLines = mutableListOf<String>()
        val soonLines = mutableListOf<String>()

        VehicleStore.load(ctx).forEach { vehicle ->
            listOf(
                ctx.getString(R.string.road_tax) to vehicle.roadTax,
                ctx.getString(R.string.insurance) to vehicle.insurance,
                ctx.getString(R.string.inspection) to vehicle.inspection
            ).forEach { (label, renewal) ->
                val days = renewal.daysLeft() ?: return@forEach
                when {
                    days < 0 -> dueLines.add(
                        ctx.getString(R.string.notif_line_overdue, vehicle.plateNo, label, -days)
                    )
                    days == 0L -> dueLines.add(
                        ctx.getString(R.string.notif_line_due_today, vehicle.plateNo, label)
                    )
                    days <= Renewal.DUE_SOON_DAYS -> soonLines.add(
                        ctx.getString(
                            R.string.notif_line_soon, vehicle.plateNo, label, days, renewal.date
                        )
                    )
                }
            }
        }

        if (dueLines.isEmpty() && soonLines.isEmpty()) return Result.success()
        notify(ctx, dueLines, soonLines)
        return Result.success()
    }

    private fun notify(ctx: Context, dueLines: List<String>, soonLines: List<String>) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        ensureChannel(ctx)

        val title = if (dueLines.isNotEmpty()) {
            ctx.getString(R.string.notif_title_due)
        } else {
            ctx.getString(R.string.notif_title_soon)
        }
        val body = (dueLines + soonLines).joinToString("\n")

        val openApp = PendingIntent.getActivity(
            ctx, 0,
            Intent(ctx, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle(title)
            .setContentText((dueLines + soonLines).first())
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .setPriority(
                if (dueLines.isNotEmpty()) NotificationCompat.PRIORITY_HIGH
                else NotificationCompat.PRIORITY_DEFAULT
            )
            .build()

        val manager = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun ensureChannel(ctx: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val manager = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            ctx.getString(R.string.notif_channel_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = ctx.getString(R.string.notif_channel_desc)
        }
        manager.createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_ID = "renewal_reminders"
        const val NOTIFICATION_ID = 1001
        const val WORK_NAME = "renewal_daily_check"
    }
}
