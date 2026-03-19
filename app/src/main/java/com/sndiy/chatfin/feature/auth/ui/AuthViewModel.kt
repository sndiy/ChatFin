package com.sndiy.chatfin.feature.auth.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseUser
import com.sndiy.chatfin.core.data.auth.AuthRepository
import com.sndiy.chatfin.core.data.sync.SyncRepository
import com.sndiy.chatfin.core.data.sync.SyncStats
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class AuthState {
    object Idle       : AuthState()
    object Loading    : AuthState()
    data class Success(val user: FirebaseUser) : AuthState()
    data class Error(val message: String)      : AuthState()
}

sealed class SyncState {
    object Idle                              : SyncState()
    object Syncing                           : SyncState()
    data class Done(val stats: SyncStats)    : SyncState()
    data class Error(val message: String)    : SyncState()
}

data class AuthUiState(
    val currentUser: FirebaseUser? = null,
    val email: String              = "",
    val password: String           = "",
    val confirmPassword: String    = "",
    val isRegisterMode: Boolean    = false,
    val emailError: String?        = null,
    val passwordError: String?     = null,
    val authState: AuthState       = AuthState.Idle,
    val syncState: SyncState       = SyncState.Idle
)

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepo: AuthRepository,
    private val syncRepo: SyncRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            authRepo.authState.collect { user ->
                _uiState.update { it.copy(currentUser = user) }
            }
        }
    }

    fun onEmailChange(value: String) {
        _uiState.update { it.copy(email = value, emailError = null) }
    }

    fun onPasswordChange(value: String) {
        _uiState.update { it.copy(password = value, passwordError = null) }
    }

    fun onConfirmPasswordChange(value: String) {
        _uiState.update { it.copy(confirmPassword = value) }
    }

    fun toggleMode() {
        _uiState.update { it.copy(
            isRegisterMode  = !it.isRegisterMode,
            emailError      = null,
            passwordError   = null,
            authState       = AuthState.Idle
        )}
    }

    fun loginWithEmail() {
        val state = _uiState.value
        if (!validate(state)) return

        _uiState.update { it.copy(authState = AuthState.Loading) }
        viewModelScope.launch {
            val result = authRepo.loginWithEmail(state.email.trim(), state.password)
            result.fold(
                onSuccess = { user ->
                    _uiState.update { it.copy(authState = AuthState.Success(user)) }
                    syncAfterLogin(user.uid, isNewUser = false)
                },
                onFailure = { e ->
                    _uiState.update { it.copy(
                        authState = AuthState.Error(friendlyError(e.message))
                    )}
                }
            )
        }
    }

    fun registerWithEmail() {
        val state = _uiState.value
        if (!validate(state)) return
        if (state.password != state.confirmPassword) {
            _uiState.update { it.copy(passwordError = "Password tidak sama") }
            return
        }

        _uiState.update { it.copy(authState = AuthState.Loading) }
        viewModelScope.launch {
            val result = authRepo.registerWithEmail(state.email.trim(), state.password)
            result.fold(
                onSuccess = { user ->
                    _uiState.update { it.copy(authState = AuthState.Success(user)) }
                    syncAfterLogin(user.uid, isNewUser = true)
                },
                onFailure = { e ->
                    _uiState.update { it.copy(
                        authState = AuthState.Error(friendlyError(e.message))
                    )}
                }
            )
        }
    }

    fun sendPasswordReset() {
        val email = _uiState.value.email.trim()
        if (email.isBlank()) {
            _uiState.update { it.copy(emailError = "Masukkan email dulu") }
            return
        }
        viewModelScope.launch {
            authRepo.sendPasswordReset(email)
            _uiState.update { it.copy(authState = AuthState.Error("Email reset password sudah dikirim")) }
        }
    }

    fun logout() {
        authRepo.logout()
        _uiState.update { it.copy(currentUser = null, authState = AuthState.Idle) }
    }

    fun syncUpload() {
        val uid = authRepo.currentUser?.uid ?: return
        _uiState.update { it.copy(syncState = SyncState.Syncing) }
        viewModelScope.launch {
            val result = syncRepo.uploadAll(uid)
            result.fold(
                onSuccess = { stats -> _uiState.update { it.copy(syncState = SyncState.Done(stats)) } },
                onFailure = { e   -> _uiState.update { it.copy(syncState = SyncState.Error(e.message ?: "Gagal")) } }
            )
        }
    }

    fun syncDownload() {
        val uid = authRepo.currentUser?.uid ?: return
        _uiState.update { it.copy(syncState = SyncState.Syncing) }
        viewModelScope.launch {
            val result = syncRepo.downloadAll(uid)
            result.fold(
                onSuccess = { stats -> _uiState.update { it.copy(syncState = SyncState.Done(stats)) } },
                onFailure = { e   -> _uiState.update { it.copy(syncState = SyncState.Error(e.message ?: "Gagal")) } }
            )
        }
    }

    fun clearSyncState() {
        _uiState.update { it.copy(syncState = SyncState.Idle) }
    }

    private suspend fun syncAfterLogin(uid: String, isNewUser: Boolean) {
        _uiState.update { it.copy(syncState = SyncState.Syncing) }
        if (isNewUser) {
            // User baru → upload data lokal ke cloud
            val result = syncRepo.uploadAll(uid)
            result.fold(
                onSuccess = { stats -> _uiState.update { it.copy(syncState = SyncState.Done(stats)) } },
                onFailure = { e    -> _uiState.update { it.copy(syncState = SyncState.Error(e.message ?: "Gagal")) } }
            )
        } else {
            // User lama → download data dari cloud
            val result = syncRepo.downloadAll(uid)
            result.fold(
                onSuccess = { stats -> _uiState.update { it.copy(syncState = SyncState.Done(stats)) } },
                onFailure = { e    -> _uiState.update { it.copy(syncState = SyncState.Error(e.message ?: "Gagal")) } }
            )
        }
    }

    private fun validate(state: AuthUiState): Boolean {
        var valid = true
        if (state.email.isBlank()) {
            _uiState.update { it.copy(emailError = "Email tidak boleh kosong") }
            valid = false
        } else if (!android.util.Patterns.EMAIL_ADDRESS.matcher(state.email).matches()) {
            _uiState.update { it.copy(emailError = "Format email tidak valid") }
            valid = false
        }
        if (state.password.length < 6) {
            _uiState.update { it.copy(passwordError = "Password minimal 6 karakter") }
            valid = false
        }
        return valid
    }

    private fun friendlyError(msg: String?): String = when {
        msg == null                                       -> "Terjadi kesalahan"
        msg.contains("password", ignoreCase = true)      -> "Password salah"
        msg.contains("no user", ignoreCase = true)       -> "Akun tidak ditemukan"
        msg.contains("email", ignoreCase = true)         -> "Email sudah terdaftar"
        msg.contains("network", ignoreCase = true)       -> "Tidak ada koneksi internet"
        msg.contains("credential", ignoreCase = true)    -> "Email atau password salah"
        else                                              -> "Login gagal: $msg"
    }
}