package com.sndiy.chatfin.feature.settings.backup

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BackupUiState(
    val isLoading: Boolean  = false,
    val successMessage: String? = null,
    val errorMessage: String?   = null,
    val fileName: String        = ""
)

@HiltViewModel
class BackupViewModel @Inject constructor(
    private val backupRepo: BackupRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(BackupUiState())
    val uiState: StateFlow<BackupUiState> = _uiState

    init {
        _uiState.update { it.copy(fileName = backupRepo.generateFileName()) }
    }

    fun exportBackup(uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, successMessage = null, errorMessage = null) }
            val result = backupRepo.exportToUri(uri)
            result.fold(
                onSuccess = { msg -> _uiState.update { it.copy(isLoading = false, successMessage = msg) } },
                onFailure = { e  -> _uiState.update { it.copy(isLoading = false, errorMessage = e.message) } }
            )
        }
    }

    fun importBackup(uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, successMessage = null, errorMessage = null) }
            val result = backupRepo.importFromUri(uri)
            result.fold(
                onSuccess = { msg -> _uiState.update { it.copy(isLoading = false, successMessage = msg) } },
                onFailure = { e  -> _uiState.update { it.copy(isLoading = false, errorMessage = e.message) } }
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