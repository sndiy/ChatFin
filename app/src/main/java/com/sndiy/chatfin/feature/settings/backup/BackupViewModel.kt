package com.sndiy.chatfin.feature.settings.backup

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject

data class BackupUiState(
    val isLoading: Boolean = false,
    val successMessage: String? = null,
    val errorMessage: String? = null,
    val fileName: String = "",
    val autoBackupFrequency: AutoBackupFrequency = AutoBackupFrequency.OFF,
    val lastBackupTimestamp: Long = 0L
)

@HiltViewModel
class BackupViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val backupRepo: BackupRepository,
    private val backupPrefs: BackupPreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(BackupUiState())
    val uiState: StateFlow<BackupUiState> = _uiState

    init {
        _uiState.update { it.copy(fileName = backupRepo.generateFileName()) }
        observeAutoBackupSettings()
    }

    private fun observeAutoBackupSettings() {
        viewModelScope.launch {
            combine(
                backupPrefs.autoBackupFrequency,
                backupPrefs.lastBackupTimestamp
            ) { freq, lastTime ->
                freq to lastTime
            }.collect { (freq, lastTime) ->
                _uiState.update {
                    it.copy(
                        autoBackupFrequency = freq,
                        lastBackupTimestamp = lastTime
                    )
                }
            }
        }
    }

    fun setAutoBackupFrequency(frequency: AutoBackupFrequency) {
        viewModelScope.launch {
            backupPrefs.setAutoBackupFrequency(frequency)
            scheduleWorkManager(frequency)
        }
    }

    fun runManualBackupNow() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, successMessage = null, errorMessage = null) }
            val result = backupRepo.createAutoBackup()
            val now = System.currentTimeMillis()
            if (result.isSuccess) {
                backupPrefs.setLastBackupTimestamp(now)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        successMessage = "Pencadangan berhasil dilakukan!"
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = result.exceptionOrNull()?.message ?: "Pencadangan gagal"
                    )
                }
            }
        }
    }

    private fun scheduleWorkManager(frequency: AutoBackupFrequency) {
        val workManager = WorkManager.getInstance(context)
        val workName = "auto_backup_periodic_work"

        if (frequency == AutoBackupFrequency.OFF) {
            workManager.cancelUniqueWork(workName)
            return
        }

        val repeatIntervalDays = when (frequency) {
            AutoBackupFrequency.DAILY -> 1L
            AutoBackupFrequency.WEEKLY -> 7L
            AutoBackupFrequency.MONTHLY -> 30L
            AutoBackupFrequency.OFF -> 1L
        }

        val workRequest = PeriodicWorkRequestBuilder<AutoBackupWorker>(
            repeatIntervalDays, TimeUnit.DAYS
        ).build()

        workManager.enqueueUniquePeriodicWork(
            workName,
            ExistingPeriodicWorkPolicy.UPDATE,
            workRequest
        )
    }

    fun exportBackup(uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, successMessage = null, errorMessage = null) }
            val result = backupRepo.exportToUri(uri)
            result.fold(
                onSuccess = { msg -> _uiState.update { it.copy(isLoading = false, successMessage = msg) } },
                onFailure = { e -> _uiState.update { it.copy(isLoading = false, errorMessage = e.message) } }
            )
        }
    }

    fun importBackup(uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, successMessage = null, errorMessage = null) }
            val result = backupRepo.importFromUri(uri)
            result.fold(
                onSuccess = { msg -> _uiState.update { it.copy(isLoading = false, successMessage = msg) } },
                onFailure = { e -> _uiState.update { it.copy(isLoading = false, errorMessage = e.message) } }
            )
        }
    }

    fun clearMessages() {
        _uiState.update { it.copy(successMessage = null, errorMessage = null) }
    }

    fun refreshFileName() {
        _uiState.update { it.copy(fileName = backupRepo.generateFileName()) }
    }
}