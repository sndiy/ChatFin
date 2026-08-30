// app/src/main/java/com/sndiy/chatfin/feature/auth/ui/AuthViewModel.kt
//
// Sync:
// 1. syncAfterLogin: cek hasCloudData sebelum download
// 2. Login existing user: mergeDownload — data lokal TIDAK dihapus
// 3. Register new user: uploadAll (data lokal → cloud)
// 4. syncSmart: manual bi-directional sync dari DataBackupScreen
// 5. isSyncingInitial & syncProgressMessage: full screen loading sinkronisasi awal saat login

package com.sndiy.chatfin.feature.auth.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseUser
import com.sndiy.chatfin.core.data.auth.AuthRepository
import com.sndiy.chatfin.core.data.local.AppPreferences
import com.sndiy.chatfin.core.data.sync.SyncEventBus
import com.sndiy.chatfin.core.data.sync.SyncRepository
import com.sndiy.chatfin.core.data.sync.SyncStats
import com.sndiy.chatfin.feature.finance.account.data.repository.AccountRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
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
    val currentUser: FirebaseUser?       = null,
    val email: String                    = "",
    val password: String                 = "",
    val confirmPassword: String          = "",
    val isRegisterMode: Boolean          = false,
    val emailError: String?              = null,
    val passwordError: String?           = null,
    val authState: AuthState             = AuthState.Idle,
    val syncState: SyncState             = SyncState.Idle,
    val isSyncingInitial: Boolean        = false,
    val syncProgressMessage: String      = "",
    val isSyncComplete: Boolean          = false
)

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepo: AuthRepository,
    private val syncRepo: SyncRepository,
    private val syncEventBus: SyncEventBus,
    private val accountRepo: AccountRepository,
    private val appPreferences: AppPreferences
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

    fun setRegisterMode(isRegister: Boolean) = _uiState.update {
        it.copy(
            isRegisterMode = isRegister,
            emailError     = null,
            passwordError  = null,
            authState      = AuthState.Idle
        )
    }

    fun toggleMode() = _uiState.update {
        it.copy(
            isRegisterMode = !it.isRegisterMode,
            emailError     = null,
            passwordError  = null,
            authState      = AuthState.Idle
        )
    }

    fun startOffline() {
        viewModelScope.launch {
            val existing = accountRepo.getAllAccounts().first()
            if (existing.isEmpty()) {
                val accountId = accountRepo.createAccount(name = "Utama")
                accountRepo.switchActiveAccount(accountId)
            } else {
                val active = accountRepo.getActiveAccount().first()
                if (active == null) {
                    accountRepo.switchActiveAccount(existing.first().id)
                }
            }
            appPreferences.setOnboardingDone(true)
        }
    }

    fun loginWithEmail() {
        val state = _uiState.value
        if (!validate(state)) return
        _uiState.update { it.copy(authState = AuthState.Loading) }
        viewModelScope.launch {
            authRepo.loginWithEmail(state.email.trim(), state.password).fold(
                onSuccess = { user ->
                    _uiState.update {
                        it.copy(
                            currentUser = user,
                            isSyncingInitial = true,
                            syncProgressMessage = "Menghubungkan ke akun...",
                            authState = AuthState.Idle
                        )
                    }
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
                    _uiState.update {
                        it.copy(
                            currentUser = user,
                            isSyncingInitial = true,
                            syncProgressMessage = "Menyiapkan akun baru...",
                            authState = AuthState.Idle
                        )
                    }
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
        viewModelScope.launch {
            authRepo.logout()
            _uiState.update { it.copy(currentUser = null, authState = AuthState.Idle, isSyncComplete = false) }
        }
    }

    // ── Manual sync: Smart Bi-directional Sync (Upload + Merge Download) ─────
    fun syncSmart() {
        _uiState.update { it.copy(syncState = SyncState.Syncing) }
        viewModelScope.launch {
            val uid = resolveUid()
            if (uid == null) {
                _uiState.update { it.copy(syncState = SyncState.Error("Sesi login sudah habis. Silakan login ulang.")) }
                return@launch
            }
            val uploadRes = syncRepo.uploadAll(uid)
            val downloadRes = syncRepo.mergeDownload(uid)

            if (uploadRes.isSuccess || downloadRes.isSuccess) {
                val stats = downloadRes.getOrNull() ?: uploadRes.getOrNull() ?: SyncStats(0, 0, 0, 0)
                _uiState.update { it.copy(syncState = SyncState.Done(stats)) }
                refreshActiveAccount()
                syncEventBus.notifySyncCompleted()
            } else {
                val errorMsg = downloadRes.exceptionOrNull()?.message ?: uploadRes.exceptionOrNull()?.message ?: "Sinkronisasi gagal"
                _uiState.update { it.copy(syncState = SyncState.Error(errorMsg)) }
            }
        }
    }

    fun clearSyncState() = _uiState.update { it.copy(syncState = SyncState.Idle) }

    private suspend fun resolveUid(): String? {
        authRepo.currentUser?.uid?.let { return it }
        return try {
            kotlinx.coroutines.withTimeoutOrNull(2_000L) {
                authRepo.authState
                    .mapNotNull { it?.uid }
                    .first()
            }
        } catch (_: Exception) {
            null
        }
    }

    // ── FIX: Smart sync & loading setelah login (Paralel & Cepat) ───────────
    private suspend fun syncAfterLogin(uid: String, isNewUser: Boolean) {
        _uiState.update { it.copy(isSyncingInitial = true, syncProgressMessage = "Menyinkronkan data...") }

        try {
            kotlinx.coroutines.withTimeoutOrNull(4_000L) {
                if (isNewUser) {
                    val localAccounts = accountRepo.getAllAccounts().first()
                    if (localAccounts.isEmpty()) {
                        val accId = accountRepo.createAccount(name = "Utama")
                        accountRepo.switchActiveAccount(accId)
                    }
                    syncRepo.uploadAll(uid)
                } else {
                    syncRepo.mergeDownload(uid)
                }
            }
        } catch (e: Exception) {
            android.util.Log.w("AuthVM", "Sync timeout/error: ${e.message}")
        } finally {
            refreshActiveAccount()
            appPreferences.setOnboardingDone(true)
            _uiState.update {
                it.copy(
                    isSyncingInitial = false,
                    isSyncComplete = true,
                    syncState = SyncState.Done(SyncStats(1, 1, 0, 0))
                )
            }
            syncEventBus.notifySyncCompleted()
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
        msg == null -> "Terjadi kesalahan, coba lagi"
        msg.contains("password", ignoreCase = true) -> "Password salah"
        msg.contains("user-not-found", ignoreCase = true) ||
                msg.contains("no user", ignoreCase = true) -> "Akun belum terdaftar"
        msg.contains("email-already-in-use", ignoreCase = true) -> "Email sudah terdaftar, silakan masuk"
        msg.contains("invalid-email", ignoreCase = true) -> "Format email tidak valid"
        msg.contains("network", ignoreCase = true) -> "Koneksi internet bermasalah"
        else -> msg
    }
}