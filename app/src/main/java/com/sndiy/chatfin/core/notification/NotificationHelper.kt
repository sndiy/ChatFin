package com.sndiy.chatfin.core.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.sndiy.chatfin.MainActivity
import com.sndiy.chatfin.R

object NotificationHelper {

    const val CHANNEL_BG_TASK = "bg_task_channel"
    const val CHANNEL_DAILY_REMINDER = "daily_reminder_channel"

    const val NOTIF_ID_DAILY_REMINDER = 1001
    const val NOTIF_ID_BG_TASK_DEFAULT = 2001

    fun createNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val bgTaskChannel = NotificationChannel(
                CHANNEL_BG_TASK,
                context.getString(R.string.notif_channel_bg_task_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = context.getString(R.string.notif_channel_bg_task_desc)
                setShowBadge(false)
            }

            val dailyReminderChannel = NotificationChannel(
                CHANNEL_DAILY_REMINDER,
                context.getString(R.string.notif_channel_daily_reminder_name),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = context.getString(R.string.notif_channel_daily_reminder_desc)
                setShowBadge(true)
            }

            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannels(listOf(bgTaskChannel, dailyReminderChannel))
        }
    }

    fun showTaskProgressNotification(
        context: Context,
        notifId: Int = NOTIF_ID_BG_TASK_DEFAULT,
        title: String,
        contentText: String,
        progress: Int,
        maxProgress: Int = 100
    ) {
        val builder = NotificationCompat.Builder(context, CHANNEL_BG_TASK)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(contentText)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(maxProgress, progress, maxProgress <= 0)

        notifyIfPermitted(context, notifId, builder.build())
    }

    fun showTaskCompletedNotification(
        context: Context,
        notifId: Int = NOTIF_ID_BG_TASK_DEFAULT,
        title: String = context.getString(R.string.notif_bg_task_completed_title),
        contentText: String = context.getString(R.string.notif_bg_task_completed_default_msg),
        targetIntent: Intent? = null
    ) {
        val pendingIntent = createPendingIntent(context, targetIntent, notifId)

        val builder = NotificationCompat.Builder(context, CHANNEL_BG_TASK)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(contentText)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setOngoing(false)
            .setAutoCancel(true)
            .setProgress(0, 0, false)

        if (pendingIntent != null) {
            builder.setContentIntent(pendingIntent)
        }

        notifyIfPermitted(context, notifId, builder.build())
    }

    fun showDailyReminderNotification(
        context: Context,
        message: String,
        targetIntent: Intent? = null
    ) {
        val pendingIntent = createPendingIntent(context, targetIntent, NOTIF_ID_DAILY_REMINDER)

        val builder = NotificationCompat.Builder(context, CHANNEL_DAILY_REMINDER)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(context.getString(R.string.notif_daily_reminder_title))
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)

        if (pendingIntent != null) {
            builder.setContentIntent(pendingIntent)
        }

        notifyIfPermitted(context, NOTIF_ID_DAILY_REMINDER, builder.build())
    }

    fun cancelNotification(context: Context, notifId: Int) {
        try {
            NotificationManagerCompat.from(context).cancel(notifId)
        } catch (_: Exception) {}
    }

    private fun createPendingIntent(context: Context, targetIntent: Intent?, requestCode: Int): PendingIntent? {
        val intent = targetIntent ?: Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getActivity(context, requestCode, intent, flags)
    }

    private fun notifyIfPermitted(context: Context, notifId: Int, notification: android.app.Notification) {
        try {
            val manager = NotificationManagerCompat.from(context)
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                androidx.core.content.ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                manager.notify(notifId, notification)
            }
        } catch (e: SecurityException) {
            // Permission not granted by user, ignore safely
        }
    }
}
