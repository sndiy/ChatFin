// app/src/main/java/com/sndiy/chatfin/core/data/local/entity/WalletEntity.kt

package com.sndiy.chatfin.core.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "wallets")
data class WalletEntity(
    @PrimaryKey val id: String,
    val accountId: String,              // FK ke FinanceAccountEntity
    val name: String,
    val type: String = "CASH",          // CASH | BANK | E_WALLET | CREDIT_CARD
    val balance: Long = 0L,             // dalam rupiah (Long untuk hindari float error)
    val currency: String = "IDR",
    val colorHex: String = "#0061A4",
    val iconName: String = "account_balance_wallet",
    val sortOrder: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)