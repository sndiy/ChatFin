package com.sndiy.chatfin.core.data.sync

/**
 * Status sinkronisasi real-time dengan Firestore.
 * Ditampilkan di UI sebagai badge kecil di TopAppBar.
 *
 * IDLE     -> user tidak login / sync tidak aktif
 * SYNCING  -> ada pending writes ke cloud (hasPendingWrites = true)
 * OFFLINE  -> snapshot dari cache lokal (isFromCache = true), belum dikonfirmasi server
 * IN_SYNC  -> snapshot dari server, tidak ada pending writes
 */
enum class SyncStatus {
    IDLE,
    SYNCING,
    OFFLINE,
    IN_SYNC
}
