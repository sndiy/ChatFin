// app/src/main/java/com/sndiy/chatfin/feature/settings/ui/SettingsViewModel.kt

package com.sndiy.chatfin.feature.settings.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sndiy.chatfin.BuildConfig
import com.sndiy.chatfin.core.data.security.SecureStorage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val apiKeySet: Boolean       = false,
    val apiKeyInput: String      = "",
    val showApiKeyDialog: Boolean = false,
    val savedMessage: String?    = null
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val secureStorage: SecureStorage
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        // Cek apakah API key sudah diset
        // Prioritas: SecureStorage → BuildConfig
        val storedKey  = secureStorage.geminiApiKey
        val buildKey   = runCatching { BuildConfig.GEMINI_API_KEY }.getOrNull()
        val isSet      = (!storedKey.isNullOrBlank()) || (!buildKey.isNullOrBlank() && buildKey != "")
        _uiState.value = _uiState.value.copy(apiKeySet = isSet)
    }

    fun showApiKeyDialog() {
        val current = secureStorage.geminiApiKey ?: ""
        _uiState.value = _uiState.value.copy(
            showApiKeyDialog = true,
            apiKeyInput      = current
        )
    }

    fun hideApiKeyDialog() {
        _uiState.value = _uiState.value.copy(showApiKeyDialog = false)
    }

    fun onApiKeyChange(value: String) {
        _uiState.value = _uiState.value.copy(apiKeyInput = value.trim())
    }

    fun saveApiKey() {
        val key = _uiState.value.apiKeyInput.trim()
        if (key.isBlank()) return

        secureStorage.geminiApiKey = key
        _uiState.value = _uiState.value.copy(
            apiKeySet        = true,
            showApiKeyDialog = false,
            savedMessage     = "API Key berhasil disimpan"
        )
    }
}