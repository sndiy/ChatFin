package com.sndiy.chatfin.core.data.local

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import com.sndiy.chatfin.core.data.local.dao.AccountDao
import com.sndiy.chatfin.core.data.local.dao.BudgetDao
import com.sndiy.chatfin.core.data.local.dao.CategoryDao
import com.sndiy.chatfin.core.data.local.dao.CategoryKeywordDao
import com.sndiy.chatfin.core.data.local.dao.ChatDao
import com.sndiy.chatfin.core.data.local.dao.TransactionDao
import com.sndiy.chatfin.core.data.local.dao.WalletDao
import com.sndiy.chatfin.core.data.local.entity.BudgetEntity
import com.sndiy.chatfin.core.data.local.entity.CategoryEntity
import com.sndiy.chatfin.core.data.local.entity.CategoryKeywordEntity
import com.sndiy.chatfin.core.data.local.entity.ChatMessageEntity
import com.sndiy.chatfin.core.data.local.entity.ChatSessionEntity
import com.sndiy.chatfin.core.data.local.entity.FinanceAccountEntity
import com.sndiy.chatfin.core.data.local.entity.TransactionEntity
import com.sndiy.chatfin.core.data.local.entity.TransactionItemEntity
import com.sndiy.chatfin.core.data.local.entity.WalletEntity

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
    version = 14,
    exportSchema = true
)
@ConstructedBy(ChatFinDatabaseConstructor::class)
abstract class ChatFinDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao
    abstract fun walletDao(): WalletDao
    abstract fun categoryDao(): CategoryDao
    abstract fun transactionDao(): TransactionDao
    abstract fun budgetDao(): BudgetDao
    abstract fun chatDao(): ChatDao
    abstract fun categoryKeywordDao(): CategoryKeywordDao
}

// Room compiler generates the actual implementation for each target
@Suppress("KotlinNoActualForExpect")
expect object ChatFinDatabaseConstructor : RoomDatabaseConstructor<ChatFinDatabase> {
    override fun initialize(): ChatFinDatabase
}
