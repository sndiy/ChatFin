package com.sndiy.chatfin.core.data.local

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
        TransactionItemEntity::class,
        BudgetEntity::class,
        ChatSessionEntity::class,
        ChatMessageEntity::class,
        CategoryKeywordEntity::class,
    ],
    version = 8,
    exportSchema = true
)
abstract class ChatFinDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao
    abstract fun walletDao(): WalletDao
    abstract fun categoryDao(): CategoryDao
    abstract fun transactionDao(): TransactionDao
    abstract fun budgetDao(): BudgetDao
    abstract fun chatDao(): ChatDao
    abstract fun categoryKeywordDao(): CategoryKeywordDao
}
