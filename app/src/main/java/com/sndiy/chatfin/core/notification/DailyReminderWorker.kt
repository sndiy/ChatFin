package com.sndiy.chatfin.core.notification

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.sndiy.chatfin.R
import com.sndiy.chatfin.core.data.local.AppPreferences
import com.sndiy.chatfin.core.data.local.dao.TransactionDao
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DailyReminderWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface WorkerEntryPoint {
        fun appPreferences(): AppPreferences
        fun transactionDao(): TransactionDao
    }

    override suspend fun doWork(): Result {
        return try {
            val entryPoint = EntryPointAccessors.fromApplication(
                applicationContext,
                WorkerEntryPoint::class.java
            )
            val appPreferences = entryPoint.appPreferences()
            val transactionDao = entryPoint.transactionDao()

            val isEnabled = appPreferences.dailyReminderNotifEnabled.first()
            if (!isEnabled) {
                return Result.success()
            }

            val activeAccountId = appPreferences.activeAccountId.firstOrNull()
            val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

            if (!activeAccountId.isNullOrBlank()) {
                val todayTransactions = transactionDao.getTransactionsByPeriod(
                    accountId = activeAccountId,
                    startDate = todayStr,
                    endDate = todayStr
                ).firstOrNull()

                if (!todayTransactions.isNullOrEmpty()) {
                    // Pengguna sudah mencatat transaksi hari ini
                    return Result.success()
                }
            }

            // Pilih acak 1 dari 8 variasi pesan motivasi Duolingo-style
            val messageResIds = listOf(
                R.string.notif_daily_reminder_msg_1,
                R.string.notif_daily_reminder_msg_2,
                R.string.notif_daily_reminder_msg_3,
                R.string.notif_daily_reminder_msg_4,
                R.string.notif_daily_reminder_msg_5,
                R.string.notif_daily_reminder_msg_6,
                R.string.notif_daily_reminder_msg_7,
                R.string.notif_daily_reminder_msg_8
            )

            val selectedMessage = applicationContext.getString(messageResIds.random())
            NotificationHelper.showDailyReminderNotification(applicationContext, selectedMessage)

            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    private fun String?.isNullOrEmpty(): Boolean = this == null || this.trim().isEmpty()
}
