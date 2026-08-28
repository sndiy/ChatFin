package com.sndiy.chatfin.core.data.sync

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository tunggal untuk status sinkronisasi Firestore.
 * Di-update oleh RealtimeSyncRepository, di-observe oleh ViewModel.
 */
@Singleton
class SyncStatusRepository @Inject constructor() {

    private val _status = MutableStateFlow(SyncStatus.IDLE)
    val status: StateFlow<SyncStatus> = _status.asStateFlow()

    fun update(status: SyncStatus) {
        _status.value = status
    }
}
