package com.sndiy.chatfin.core.data.sync

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncEventBus @Inject constructor() {
    private val _syncCompleted = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val syncCompleted: SharedFlow<Unit> = _syncCompleted

    fun notifySyncCompleted() {
        _syncCompleted.tryEmit(Unit)
    }
}