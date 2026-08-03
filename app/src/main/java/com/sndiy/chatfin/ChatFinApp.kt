// app/src/main/java/com/sndiy/chatfin/ChatFinApp.kt

package com.sndiy.chatfin

import android.app.Application
import com.sndiy.chatfin.core.data.local.AppPreferences
import com.sndiy.chatfin.core.notification.DailyReminderScheduler
import com.sndiy.chatfin.core.notification.NotificationHelper
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class ChatFinApp : Application() {

    @Inject
    lateinit var appPreferences: AppPreferences

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createNotificationChannels(this)

        CoroutineScope(Dispatchers.IO).launch {
            val dailyReminderEnabled = appPreferences.dailyReminderNotifEnabled.first()
            if (dailyReminderEnabled) {
                DailyReminderScheduler.schedule(this@ChatFinApp)
            }
        }
    }
}