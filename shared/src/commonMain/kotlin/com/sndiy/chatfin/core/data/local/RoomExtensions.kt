package com.sndiy.chatfin.core.data.local

import androidx.room.RoomDatabase
import androidx.room.immediateTransaction
import androidx.room.useWriterConnection

/**
 * Multiplatform implementation of withTransaction for Room KMP.
 * Executes the given block atomically using a writer connection with immediate transaction.
 */
suspend fun <R> RoomDatabase.withTransaction(block: suspend () -> R): R {
    return useWriterConnection { transactor ->
        transactor.immediateTransaction {
            block()
        }
    }
}
