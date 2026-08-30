package com.sndiy.chatfin.core.data.sync

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Event bus untuk menyiarkan event penyelesaian sinkronisasi data beserta statistiknya.
 */
class SyncEventBus {

    private val _syncCompleted = MutableSharedFlow<SyncStats>(extraBufferCapacity = 1)
    val syncCompleted: SharedFlow<SyncStats> = _syncCompleted.asSharedFlow()

    fun notifySyncCompleted(stats: SyncStats = SyncStats()) {
        _syncCompleted.tryEmit(stats)
    }
}
