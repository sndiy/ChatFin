package com.sndiy.chatfin.core.notification

import android.content.Context
import com.sndiy.chatfin.core.data.local.AppPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appPreferences: AppPreferences
) {
    val backgroundTaskNotifEnabled: Flow<Boolean> = appPreferences.backgroundTaskNotifEnabled
    val dailyReminderNotifEnabled: Flow<Boolean> = appPreferences.dailyReminderNotifEnabled

    suspend fun setBackgroundTaskNotifEnabled(enabled: Boolean) {
        appPreferences.setBackgroundTaskNotifEnabled(enabled)
    }

    suspend fun setDailyReminderNotifEnabled(enabled: Boolean) {
        appPreferences.setDailyReminderNotifEnabled(enabled)
        if (enabled) {
            DailyReminderScheduler.schedule(context)
        } else {
            DailyReminderScheduler.cancel(context)
        }
    }
}
