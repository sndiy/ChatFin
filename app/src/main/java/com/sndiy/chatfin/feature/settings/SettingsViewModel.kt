package com.sndiy.chatfin.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sndiy.chatfin.core.data.security.SecureStorage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val apiKey: String = "",
    val savedSuccessfully: Boolean = false
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val secureStorage: SecureStorage
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        val key = secureStorage.geminiApiKey ?: ""
        val masked = if (key.length > 8) key.take(4) + "****" + key.takeLast(4) else key
        _uiState.update { it.copy(apiKey = masked) }
    }

    fun saveApiKey(key: String) {
        viewModelScope.launch {
            secureStorage.geminiApiKey = key.trim()
            _uiState.update { it.copy(savedSuccessfully = true) }
        }
    }

    fun clearSaved() {
        _uiState.update { it.copy(savedSuccessfully = false) }
    }
}
