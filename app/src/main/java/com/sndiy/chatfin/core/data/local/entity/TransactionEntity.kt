// app/src/main/java/com/sndiy/chatfin/core/data/local/entity/TransactionEntity.kt

package com.sndiy.chatfin.core.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
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
    val transferPairId: String? = null,     // link antar transaksi cross-account transfer
    val isInitialBalance: Boolean = false,  // true = transaksi saldo awal wallet
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()  // untuk conflict resolution saat sync
)