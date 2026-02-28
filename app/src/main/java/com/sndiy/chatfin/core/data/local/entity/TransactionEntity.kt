// app/src/main/java/com/sndiy/chatfin/core/data/local/entity/TransactionEntity.kt

package com.sndiy.chatfin.core.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "transactions")
data class TransactionEntity(
    @PrimaryKey val id: String,
    val accountId: String,              // FK ke FinanceAccountEntity
    val type: String,                   // INCOME | EXPENSE | TRANSFER
    val amount: Long,                   // dalam rupiah
    val categoryId: String,             // FK ke CategoryEntity
    val walletId: String,               // FK ke WalletEntity (sumber)
    val toWalletId: String? = null,     // FK ke WalletEntity (tujuan, khusus TRANSFER)
    val note: String? = null,
    val receiptImageUri: String? = null,
    val date: String,                   // format: "yyyy-MM-dd"
    val time: String,                   // format: "HH:mm"
    val isRecurring: Boolean = false,
    val recurringInterval: String? = null,  // DAILY | WEEKLY | MONTHLY
    val recurringParentId: String? = null,  // ID transaksi induk jika ini hasil recurring
    val transferPairId: String? = null,     // link antar transaksi cross-account transfer
    val createdAt: Long = System.currentTimeMillis()
)