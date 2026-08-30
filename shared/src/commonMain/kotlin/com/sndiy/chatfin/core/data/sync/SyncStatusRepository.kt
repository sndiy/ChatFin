package com.sndiy.chatfin.core.data.sync

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Repository tunggal untuk mengamati dan memperbarui status sinkronisasi Firestore di Desktop/KMP.
 */
class SyncStatusRepository {

    private val _status = MutableStateFlow(SyncStatus.IDLE)
    val status: StateFlow<SyncStatus> = _status.asStateFlow()

    fun update(status: SyncStatus) {
        _status.value = status
    }
}
