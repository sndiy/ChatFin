package com.sndiy.chatfin.core.notification

import android.content.Context
import android.content.Intent
import com.sndiy.chatfin.core.data.local.AppPreferences
import kotlinx.coroutines.flow.first

class BackgroundTaskNotifier(
    private val context: Context,
    private val appPreferences: AppPreferences
) {
    suspend fun notifyProgress(
        notifId: Int = NotificationHelper.NOTIF_ID_BG_TASK_DEFAULT,
        title: String,
        contentText: String,
        progress: Int,
        maxProgress: Int = 100
    ) {
        val isEnabled = appPreferences.backgroundTaskNotifEnabled.first()
        if (!isEnabled) return

        NotificationHelper.showTaskProgressNotification(
            context = context,
            notifId = notifId,
            title = title,
            contentText = contentText,
            progress = progress,
            maxProgress = maxProgress
        )
    }

    suspend fun notifyCompleted(
        notifId: Int = NotificationHelper.NOTIF_ID_BG_TASK_DEFAULT,
        title: String,
        contentText: String,
        targetIntent: Intent? = null
    ) {
        val isEnabled = appPreferences.backgroundTaskNotifEnabled.first()
        if (!isEnabled) return

        NotificationHelper.showTaskCompletedNotification(
            context = context,
            notifId = notifId,
            title = title,
            contentText = contentText,
            targetIntent = targetIntent
        )
    }

    fun cancel(notifId: Int = NotificationHelper.NOTIF_ID_BG_TASK_DEFAULT) {
        NotificationHelper.cancelNotification(context, notifId)
    }
}
