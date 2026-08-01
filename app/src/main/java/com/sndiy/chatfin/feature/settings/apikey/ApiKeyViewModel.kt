package com.sndiy.chatfin.feature.settings.apikey

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sndiy.chatfin.R
import com.sndiy.chatfin.ai.GeminiClient
import com.sndiy.chatfin.core.data.security.SecureStorage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ApiKeyUiState(
    val isLoading: Boolean = true,
    val hasStoredKey: Boolean = false,
    val maskedKey: String = "",
    val inputText: String = "",
    val isValidating: Boolean = false,
    val errorMessageRes: Int? = null,
    val successMessageRes: Int? = null
)

@HiltViewModel
class ApiKeyViewModel @Inject constructor(
    private val secureStorage: SecureStorage,
    private val geminiClient: GeminiClient
) : ViewModel() {

    private val _uiState = MutableStateFlow(ApiKeyUiState())
    val uiState: StateFlow<ApiKeyUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    /**
     * ViewModel ini melekat ke NavBackStackEntry Setelan, jadi hidup lebih
     * lama dari satu kunjungan ke ApiKeyScreen — init{} yang hanya jalan
     * sekali TIDAK akan tahu kalau key baru saja disimpan/dihapus dari layar
     * lain. Dipanggil ulang dari SettingsScreen setiap kali layar itu resume
     * (lihat ApiKeyScreen.kt/SettingsScreen.kt), supaya subtitle menu selalu
     * mencerminkan status tersimpan yang sebenarnya.
     */
    fun refresh() {
        viewModelScope.launch {
            val stored = secureStorage.getGeminiApiKey()
            _uiState.update {
                it.copy(
                    isLoading    = false,
                    hasStoredKey = !stored.isNullOrBlank(),
                    maskedKey    = stored?.let(::mask) ?: ""
                )
            }
        }
    }

    fun onInputChange(text: String) {
        _uiState.update { it.copy(inputText = text, errorMessageRes = null, successMessageRes = null) }
    }

    fun clearMessages() {
        _uiState.update { it.copy(errorMessageRes = null, successMessageRes = null) }
    }

    fun validateAndSave() {
        val key = _uiState.value.inputText.trim()
        if (key.isBlank()) {
            _uiState.update { it.copy(errorMessageRes = R.string.apikey_empty_error) }
            return
        }
        _uiState.update { it.copy(isValidating = true, errorMessageRes = null, successMessageRes = null) }
        viewModelScope.launch {
            geminiClient.validateApiKey(key).fold(
                onSuccess = {
                    secureStorage.setGeminiApiKey(key)
                    _uiState.update {
                        it.copy(
                            isValidating      = false,
                            hasStoredKey      = true,
                            maskedKey         = mask(key),
                            inputText         = "",
                            successMessageRes = R.string.apikey_save_success
                        )
                    }
                },
                onFailure = { e ->
                    _uiState.update {
                        it.copy(isValidating = false, errorMessageRes = friendlyErrorRes(e))
                    }
                }
            )
        }
    }

    fun clearKey() {
        if (_uiState.value.isValidating) return
        viewModelScope.launch {
            secureStorage.setGeminiApiKey(null)
            _uiState.update {
                it.copy(
                    hasStoredKey      = false,
                    maskedKey         = "",
                    inputText         = "",
                    successMessageRes = R.string.apikey_clear_success
                )
            }
        }
    }

    private fun mask(key: String): String =
        if (key.length <= 8) "••••" else "${key.take(4)}••••${key.takeLast(4)}"

    private fun friendlyErrorRes(e: Throwable): Int {
        val msg = e.message ?: ""
        return when {
            msg.contains("API_KEY_INVALID", ignoreCase = true) ||
                msg.contains("API key not valid", ignoreCase = true) -> R.string.apikey_invalid_error
            msg.contains("network", ignoreCase = true) ||
                msg.contains("Unable to resolve", ignoreCase = true) ||
                msg.contains("timeout", ignoreCase = true) -> R.string.apikey_network_error
            msg.contains("RESOURCE_EXHAUSTED", ignoreCase = true) ||
                msg.contains("quota", ignoreCase = true) -> R.string.apikey_quota_error
            else -> R.string.apikey_generic_error
        }
    }
}
