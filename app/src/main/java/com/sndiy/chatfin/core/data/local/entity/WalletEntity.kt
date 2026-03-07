package com.sndiy.chatfin.core.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "wallets")
data class WalletEntity(
    @PrimaryKey val id: String,
    val accountId: String,
    val name: String,
    val type: String = "CASH",
    val balance: Long = 0L,
    val currency: String = "IDR",
    val bankName: String? = null,
    val accountNumber: String? = null,
    val iconName: String = "account_balance_wallet",
    val colorHex: String = "#0061A4",
    val isDefault: Boolean = false,
    val sortOrder: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)