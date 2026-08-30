package com.sndiy.chatfin.core.data.sync

import com.sndiy.chatfin.core.data.auth.DesktopAuthRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Pengelola siklus hidup sinkronisasi dan snapshot listeners Firestore untuk Desktop.
 *
 * Bereaksi ke DesktopAuthRepository.authState:
 * 1. Saat login -> Menjalankan Two-Way Reconciliation (SyncStats) dan memasang 5 snapshot listeners.
 * 2. Saat logout -> Membatalkan seluruh listener coroutine Job dan mereset status ke IDLE.
 * 3. Mencegah duplikasi listener saat sesi auth dipancarkan ulang.
 */
class DesktopSyncOrchestrator(
    private val authRepo: DesktopAuthRepository,
    private val syncRepo: DesktopSyncRepository,
    private val realtimeSyncRepo: DesktopRealtimeSyncRepository,
    private val syncStatusRepo: SyncStatusRepository,
    private val syncEventBus: SyncEventBus
) {
    private var appScope: CoroutineScope? = null

    private var accJob: Job? = null
    private var txJob: Job? = null
    private var wltJob: Job? = null
    private var catJob: Job? = null
    private var budgetJob: Job? = null
    private var initialSyncJob: Job? = null
    private var authJob: Job? = null

    private var lastStartedUid: String? = null

    private val _lastActivationStats = MutableStateFlow<SyncStats?>(null)
    val lastActivationStats: StateFlow<SyncStats?> = _lastActivationStats.asStateFlow()

    fun start(scope: CoroutineScope) {
        appScope = scope
        authJob?.cancel()
        authJob = scope.launch {
            authRepo.authState.collect { user ->
                if (user != null) {
                    if (user.uid == lastStartedUid) {
                        println("[DesktopSyncOrchestrator] Auth re-emit for uid=${user.uid}, skipping restart")
                        return@collect
                    }
                    println("[DesktopSyncOrchestrator] User logged in (${user.uid}), starting listeners & initial merge")
                    lastStartedUid = user.uid
                    startListeners(user.uid)
                } else {
                    println("[DesktopSyncOrchestrator] User logged out, stopping all sync listeners")
                    lastStartedUid = null
                    stopListeners()
                    syncStatusRepo.update(SyncStatus.IDLE)
                    _lastActivationStats.value = null
                }
            }
        }
    }

    private fun startListeners(uid: String) {
        val scope = appScope ?: return
        stopListeners()

        println("[DesktopSyncOrchestrator] Starting real-time listeners & two-way merge for uid=$uid")

        // 1. Initial Two-Way Merge & Reconciliation
        initialSyncJob = scope.launch {
            try {
                val res = syncRepo.twoWayMerge(uid)
                if (res.isSuccess) {
                    val stats = res.getOrNull() ?: SyncStats()
                    _lastActivationStats.value = stats
                    syncEventBus.notifySyncCompleted(stats)
                }
            } catch (e: Exception) {
                println("[DesktopSyncOrchestrator] Initial merge failed: ${e.message}")
            }
        }

        // 2. Listen Accounts
        accJob = scope.launch {
            try {
                realtimeSyncRepo.listenAccounts(uid).collect {}
            } catch (e: Exception) {
                println("[DesktopSyncOrchestrator] Account listener error: ${e.message}")
            }
        }

        // 3. Listen Transactions
        txJob = scope.launch {
            try {
                realtimeSyncRepo.listenTransactions(uid).collect {}
            } catch (e: Exception) {
                println("[DesktopSyncOrchestrator] Transaction listener error: ${e.message}")
                syncStatusRepo.update(SyncStatus.OFFLINE)
            }
        }

        // 4. Listen Wallets
        wltJob = scope.launch {
            try {
                realtimeSyncRepo.listenWallets(uid).collect {}
            } catch (e: Exception) {
                println("[DesktopSyncOrchestrator] Wallet listener error: ${e.message}")
            }
        }

        // 5. Listen Categories
        catJob = scope.launch {
            try {
                realtimeSyncRepo.listenCategories(uid).collect {}
            } catch (e: Exception) {
                println("[DesktopSyncOrchestrator] Category listener error: ${e.message}")
            }
        }

        // 6. Listen Budgets
        budgetJob = scope.launch {
            try {
                realtimeSyncRepo.listenBudgets(uid).collect {}
            } catch (e: Exception) {
                println("[DesktopSyncOrchestrator] Budget listener error: ${e.message}")
            }
        }
    }

    fun stopListeners() {
        initialSyncJob?.cancel(); initialSyncJob = null
        accJob?.cancel(); accJob = null
        txJob?.cancel(); txJob = null
        wltJob?.cancel(); wltJob = null
        catJob?.cancel(); catJob = null
        budgetJob?.cancel(); budgetJob = null
    }

    fun dismissActivationStats() {
        _lastActivationStats.value = null
    }
}
