package com.eazpire.creator.wear

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.wear.ongoing.OngoingActivity
import androidx.wear.ongoing.Status

/**
 * Keeps the session visible on Wear OS 5+ so the system does not replace the app with the watch face
 * (which looks like a tiny strip over the clock).
 */
object WearOngoingSession {
    private const val NOTIFICATION_ID = 42_001
    private const val CHANNEL_ID = "eaz_wear_active"

    fun start(context: Context) {
        createChannel(context)
        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notificationBuilder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(context.getString(R.string.wear_ongoing_status))
            .setContentIntent(pendingIntent)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)

        val status = Status.Builder()
            .addTemplate(context.getString(R.string.wear_ongoing_status))
            .build()

        val ongoingActivity = OngoingActivity.Builder(context, NOTIFICATION_ID, notificationBuilder)
            .setStaticIcon(R.mipmap.ic_launcher)
            .setTouchIntent(pendingIntent)
            .setStatus(status)
            .build()

        ongoingActivity.apply(context)
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notificationBuilder.build())
    }

    fun stop(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }

    private fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.wear_ongoing_channel),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = context.getString(R.string.wear_ongoing_channel_desc)
            setShowBadge(false)
        }
        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }
}
