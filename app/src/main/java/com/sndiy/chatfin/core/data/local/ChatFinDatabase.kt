// app/src/main/java/com/sndiy/chatfin/core/data/local/ChatFinDatabase.kt

package com.sndiy.chatfin.core.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.sndiy.chatfin.core.data.local.dao.*
import com.sndiy.chatfin.core.data.local.entity.*

@Database(
    entities = [
        FinanceAccountEntity::class,    // tabel akun keuangan
        WalletEntity::class,            // tabel dompet/rekening
        CategoryEntity::class,          // tabel kategori transaksi
        TransactionEntity::class,       // tabel transaksi
        BudgetEntity::class,            // tabel budget per kategori
        SavingsGoalEntity::class,       // tabel target tabungan
        CharacterProfileEntity::class,  // tabel profil karakter AI
        ChatSessionEntity::class,       // tabel sesi chat
        ChatMessageEntity::class,       // tabel pesan chat
    ],
    version = 1,
    exportSchema = true                 // export schema ke folder /schemas untuk migrasi
)
abstract class ChatFinDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao
    abstract fun walletDao(): WalletDao
    abstract fun categoryDao(): CategoryDao
    abstract fun transactionDao(): TransactionDao
    abstract fun budgetDao(): BudgetDao
    abstract fun savingsGoalDao(): SavingsGoalDao
    abstract fun characterDao(): CharacterDao
    abstract fun chatDao(): ChatDao
}