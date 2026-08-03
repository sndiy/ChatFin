package com.sndiy.chatfin.feature.settings.backup

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.firebase.auth.FirebaseAuth
import com.sndiy.chatfin.core.data.sync.SyncRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

class AutoBackupWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface AutoBackupWorkerEntryPoint {
        fun backupRepository(): BackupRepository
        fun backupPreferences(): BackupPreferences
        fun syncRepository(): SyncRepository
    }

    override suspend fun doWork(): Result {
        return try {
            val entryPoint = EntryPointAccessors.fromApplication(
                applicationContext,
                AutoBackupWorkerEntryPoint::class.java
            )
            val backupRepo = entryPoint.backupRepository()
            val backupPrefs = entryPoint.backupPreferences()
            val syncRepo = entryPoint.syncRepository()

            // 1. Eksekusi backup lokal ke file
            val result = backupRepo.createAutoBackup()
            if (result.isSuccess) {
                val now = System.currentTimeMillis()
                backupPrefs.setLastBackupTimestamp(now)

                // 2. Jika user login ke cloud, otomatis upload ke Firebase
                val currentUser = FirebaseAuth.getInstance().currentUser
                if (currentUser != null) {
                    syncRepo.uploadAll(currentUser.uid)
                }
                Result.success()
            } else {
                Result.retry()
            }
        } catch (e: Exception) {
            Result.failure()
        }
    }
}
