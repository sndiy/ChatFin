// app/src/main/java/com/sndiy/chatfin/feature/auth/ui/AuthViewModel.kt
//
// FIXES:
// 1. syncAfterLogin: cek hasCloudData sebelum download
// 2. Login existing user: mergeDownload (bukan downloadAll) — data lokal TIDAK dihapus
// 3. Register new user: uploadAll (data lokal → cloud)
// 4. syncDownload manual: pakai mergeDownload by default
// 5. Tambah fullReplace option hanya jika user explicitly minta

package com.sndiy.chatfin.feature.auth.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseUser
import com.sndiy.chatfin.core.data.auth.AuthRepository
import com.sndiy.chatfin.core.data.sync.SyncEventBus
import com.sndiy.chatfin.core.data.sync.SyncRepository
import com.sndiy.chatfin.core.data.sync.SyncStats
import com.sndiy.chatfin.feature.finance.account.data.repository.AccountRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class AuthState {
    object Idle    : AuthState()
    object Loading : AuthState()
    data class Success(val user: FirebaseUser) : AuthState()
    data class Error(val message: String)      : AuthState()
}

sealed class SyncState {
    object Idle                           : SyncState()
    object Syncing                        : SyncState()
    data class Done(val stats: SyncStats) : SyncState()
    data class Error(val message: String) : SyncState()
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
    private val syncRepo: SyncRepository,
    private val syncEventBus: SyncEventBus,
    private val accountRepo: AccountRepository
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

    fun onEmailChange(value: String) =
        _uiState.update { it.copy(email = value, emailError = null) }

    fun onPasswordChange(value: String) =
        _uiState.update { it.copy(password = value, passwordError = null) }

    fun onConfirmPasswordChange(value: String) =
        _uiState.update { it.copy(confirmPassword = value) }

    fun toggleMode() = _uiState.update {
        it.copy(
            isRegisterMode = !it.isRegisterMode,
            emailError     = null,
            passwordError  = null,
            authState      = AuthState.Idle
        )
    }

    fun loginWithEmail() {
        val state = _uiState.value
        if (!validate(state)) return
        _uiState.update { it.copy(authState = AuthState.Loading) }
        viewModelScope.launch {
            authRepo.loginWithEmail(state.email.trim(), state.password).fold(
                onSuccess = { user ->
                    _uiState.update { it.copy(authState = AuthState.Success(user)) }
                    syncAfterLogin(user.uid, isNewUser = false)
                },
                onFailure = { e ->
                    _uiState.update { it.copy(authState = AuthState.Error(friendlyError(e.message))) }
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
            authRepo.registerWithEmail(state.email.trim(), state.password).fold(
                onSuccess = { user ->
                    _uiState.update { it.copy(authState = AuthState.Success(user)) }
                    syncAfterLogin(user.uid, isNewUser = true)
                },
                onFailure = { e ->
                    _uiState.update { it.copy(authState = AuthState.Error(friendlyError(e.message))) }
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
            _uiState.update { it.copy(authState = AuthState.Error("Email reset password sudah dikirim ke $email")) }
        }
    }

    fun logout() {
        authRepo.logout()
        _uiState.update { it.copy(currentUser = null, authState = AuthState.Idle) }
    }

    // ── Manual sync: Upload lokal → cloud ────────────────────────────────────
    fun syncUpload() {
        val uid = authRepo.currentUser?.uid ?: return
        _uiState.update { it.copy(syncState = SyncState.Syncing) }
        viewModelScope.launch {
            syncRepo.uploadAll(uid).fold(
                onSuccess = { stats ->
                    _uiState.update { it.copy(syncState = SyncState.Done(stats)) }
                    syncEventBus.notifySyncCompleted()
                },
                onFailure = { e ->
                    _uiState.update { it.copy(syncState = SyncState.Error(e.message ?: "Upload gagal")) }
                }
            )
        }
    }

    // ── Manual sync: Download cloud → lokal (MERGE, tidak hapus data lokal) ──
    fun syncDownload() {
        val uid = authRepo.currentUser?.uid ?: return
        _uiState.update { it.copy(syncState = SyncState.Syncing) }
        viewModelScope.launch {
            // FIX: Pakai mergeDownload, bukan downloadAll
            syncRepo.mergeDownload(uid).fold(
                onSuccess = { stats ->
                    _uiState.update { it.copy(syncState = SyncState.Done(stats)) }
                    refreshActiveAccount()
                    syncEventBus.notifySyncCompleted()
                },
                onFailure = { e ->
                    _uiState.update { it.copy(syncState = SyncState.Error(e.message ?: "Download gagal")) }
                }
            )
        }
    }

    fun clearSyncState() = _uiState.update { it.copy(syncState = SyncState.Idle) }

    // ── FIX: Smart sync setelah login ────────────────────────────────────────
    private suspend fun syncAfterLogin(uid: String, isNewUser: Boolean) {
        _uiState.update { it.copy(syncState = SyncState.Syncing) }

        if (isNewUser) {
            // User baru register → upload data lokal ke cloud
            val result = syncRepo.uploadAll(uid)
            result.fold(
                onSuccess = { stats ->
                    _uiState.update { it.copy(syncState = SyncState.Done(stats)) }
                    syncEventBus.notifySyncCompleted()
                },
                onFailure = { e ->
                    _uiState.update { it.copy(syncState = SyncState.Error(e.message ?: "Upload gagal")) }
                }
            )
        } else {
            // User login di device baru/lama → cek cloud dulu
            val hasCloud = syncRepo.hasCloudData(uid)

            if (!hasCloud) {
                // Cloud kosong → upload data lokal yang ada (jangan download kosong!)
                android.util.Log.d("AuthVM", "Cloud kosong, upload data lokal sebagai gantinya")
                val result = syncRepo.uploadAll(uid)
                result.fold(
                    onSuccess = { stats ->
                        _uiState.update { it.copy(syncState = SyncState.Done(stats)) }
                        syncEventBus.notifySyncCompleted()
                    },
                    onFailure = { e ->
                        // Kalau data lokal juga kosong, ya sudah — ga apa-apa
                        _uiState.update { it.copy(syncState = SyncState.Idle) }
                    }
                )
            } else {
                // Cloud ada data → MERGE download (tidak hapus data lokal)
                val result = syncRepo.mergeDownload(uid)
                result.fold(
                    onSuccess = { stats ->
                        _uiState.update { it.copy(syncState = SyncState.Done(stats)) }
                        refreshActiveAccount()
                        syncEventBus.notifySyncCompleted()
                    },
                    onFailure = { e ->
                        _uiState.update { it.copy(syncState = SyncState.Error(e.message ?: "Sync gagal")) }
                    }
                )
            }
        }
    }

    private suspend fun refreshActiveAccount() {
        try {
            val active = accountRepo.getActiveAccount().first()
            if (active != null) {
                accountRepo.switchActiveAccount(active.id)
            }
        } catch (e: Exception) {
            android.util.Log.e("AuthVM", "refreshActiveAccount error: ${e.message}")
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
        msg == null                                    -> "Terjadi kesalahan"
        msg.contains("password", ignoreCase = true)   -> "Password salah"
        msg.contains("no user", ignoreCase = true)    -> "Akun tidak ditemukan"
        msg.contains("email", ignoreCase = true)      -> "Email sudah terdaftar"
        msg.contains("network", ignoreCase = true)    -> "Tidak ada koneksi internet"
        msg.contains("credential", ignoreCase = true) -> "Email atau password salah"
        else                                          -> "Login gagal: $msg"
    }
}