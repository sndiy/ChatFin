package com.sndiy.chatfin.core.data.sync

/**
 * Status sinkronisasi real-time dengan cloud Firestore.
 *
 * IDLE     -> User tidak login / sync tidak aktif
 * SYNCING  -> Ada pending writes ke cloud (hasPendingWrites = true)
 * OFFLINE  -> Snapshot dari cache lokal (isFromCache = true), belum tersambung ke server
 * IN_SYNC  -> Snapshot dari server terverifikasi, tidak ada pending writes
 */
enum class SyncStatus {
    IDLE,
    SYNCING,
    OFFLINE,
    IN_SYNC
}
