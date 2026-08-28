// app/src/main/java/com/sndiy/chatfin/ChatFinApp.kt

package com.sndiy.chatfin

import android.app.Application
import com.sndiy.chatfin.core.data.local.AppPreferences
import com.sndiy.chatfin.core.data.sync.AppSyncOrchestrator
import com.sndiy.chatfin.core.notification.DailyReminderScheduler
import com.sndiy.chatfin.core.notification.NotificationHelper
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class ChatFinApp : Application() {

    @Inject
    lateinit var appPreferences: AppPreferences

    @Inject
    lateinit var syncOrchestrator: AppSyncOrchestrator

    // Scope yang hidup selama proses aplikasi — supervisor agar satu child gagal tidak
    // membatalkan yang lain; IO dispatcher sesuai AGENTS.md Bagian 2.5
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createNotificationChannels(this)

        // Start real-time sync listeners — bereaksi ke auth state otomatis
        syncOrchestrator.start(applicationScope)

        applicationScope.launch {
            val dailyReminderEnabled = appPreferences.dailyReminderNotifEnabled.first()
            if (dailyReminderEnabled) {
                DailyReminderScheduler.schedule(this@ChatFinApp)
            }
        }
    }
}