// app/src/main/java/com/sndiy/chatfin/core/data/local/ChatFinDatabase.kt

package com.sndiy.chatfin.core.data.local

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import com.sndiy.chatfin.core.data.local.dao.*
import com.sndiy.chatfin.core.data.local.entity.*

@Database(
    entities = [
        FinanceAccountEntity::class,
        WalletEntity::class,
        CategoryEntity::class,
        TransactionEntity::class,
        BudgetEntity::class,
    ],
    version = 3,
    exportSchema = true
)
abstract class ChatFinDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao
    abstract fun walletDao(): WalletDao
    abstract fun categoryDao(): CategoryDao
    abstract fun transactionDao(): TransactionDao
    abstract fun budgetDao(): BudgetDao
}
