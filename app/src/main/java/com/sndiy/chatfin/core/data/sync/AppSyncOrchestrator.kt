package com.sndiy.chatfin.core.data.sync

// AppSyncOrchestrator: mengelola lifecycle Firestore snapshot listener.
//
// - Singleton, hidup selama aplikasi berjalan
// - Bereaksi ke AuthRepository.authState: start listeners saat login, stop saat logout
// - Listener berjalan di applicationScope (CoroutineScope global), bukan viewModelScope
//   -> tetap aktif saat user berpindah screen
// - Setiap kali start dipanggil ulang (mis. ganti user), Job lama di-cancel dulu
//   -> anti-duplikat listener
// - Saat login/startup, menjalankan mergeDownload di background untuk mengambil data cloud terbaru

import android.util.Log
import com.sndiy.chatfin.core.data.auth.AuthRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppSyncOrchestrator @Inject constructor(
    private val authRepo: AuthRepository,
    private val syncRepo: SyncRepository,
    private val realtimeSyncRepo: RealtimeSyncRepository,
    private val syncStatusRepo: SyncStatusRepository,
    private val syncEventBus: SyncEventBus
) {
    private val TAG = "SyncOrchestrator"

    // Scope yang hidup selama proses aplikasi — di-provide via Hilt
    // Diinit dari ChatFinApp.onCreate() dengan scope aplikasi
    private var appScope: CoroutineScope? = null

    // Job per listener — di-cancel sebelum start baru
    private var accJob: Job?    = null
    private var txJob: Job?     = null
    private var wltJob: Job?    = null
    private var catJob: Job?    = null
    private var budgetJob: Job? = null
    private var initialSyncJob: Job? = null

    // Job yang bereaksi ke auth state
    private var authJob: Job? = null

    /**
     * Dipanggil dari ChatFinApp.onCreate() dengan applicationScope.
     * Mulai bereaksi ke perubahan auth state.
     */
    fun start(scope: CoroutineScope) {
        appScope = scope
        authJob?.cancel()
        authJob = scope.launch {
            authRepo.authState.collect { user ->
                if (user != null) {
                    Log.d(TAG, "User logged in (${user.uid}), starting listeners and initial sync")
                    startListeners(user.uid)
                } else {
                    Log.d(TAG, "User logged out, stopping listeners")
                    stopListeners()
                    syncStatusRepo.update(SyncStatus.IDLE)
                }
            }
        }
    }

    private fun startListeners(uid: String) {
        val scope = appScope ?: return

        // Batalkan listener lama sebelum start baru (anti-duplikat)
        stopListeners()

        Log.d(TAG, "Starting real-time sync & initial merge for uid=$uid")

        // 1. Initial full merge download di background agar data historis cloud langsung terunduh
        initialSyncJob = scope.launch {
            try {
                val res = syncRepo.mergeDownload(uid)
                if (res.isSuccess) {
                    Log.d(TAG, "Initial mergeDownload completed successfully")
                    syncEventBus.notifySyncCompleted()
                } else {
                    Log.w(TAG, "Initial mergeDownload notice: ${res.exceptionOrNull()?.message}")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Initial mergeDownload exception: ${e.message}")
            }
        }

        // 2. Listen accounts
        accJob = scope.launch {
            try {
                realtimeSyncRepo.listenAccounts(uid).collect {}
            } catch (e: Exception) {
                Log.e(TAG, "Account listener failed: ${e.message}", e)
            }
        }

        // 3. Listen transactions
        txJob = scope.launch {
            try {
                realtimeSyncRepo.listenTransactions(uid).collect {}
            } catch (e: Exception) {
                Log.e(TAG, "Transaction listener failed: ${e.message}", e)
                syncStatusRepo.update(SyncStatus.OFFLINE)
            }
        }

        // 4. Listen wallets
        wltJob = scope.launch {
            try {
                realtimeSyncRepo.listenWallets(uid).collect {}
            } catch (e: Exception) {
                Log.e(TAG, "Wallet listener failed: ${e.message}", e)
            }
        }

        // 5. Listen categories
        catJob = scope.launch {
            try {
                realtimeSyncRepo.listenCategories(uid).collect {}
            } catch (e: Exception) {
                Log.e(TAG, "Category listener failed: ${e.message}", e)
            }
        }

        // 6. Listen budgets
        budgetJob = scope.launch {
            try {
                realtimeSyncRepo.listenBudgets(uid).collect {}
            } catch (e: Exception) {
                Log.e(TAG, "Budget listener failed: ${e.message}", e)
            }
        }
    }

    private fun stopListeners() {
        initialSyncJob?.cancel(); initialSyncJob = null
        accJob?.cancel();         accJob = null
        txJob?.cancel();          txJob = null
        wltJob?.cancel();         wltJob = null
        catJob?.cancel();         catJob = null
        budgetJob?.cancel();      budgetJob = null
        Log.d(TAG, "All listeners stopped")
    }
}
