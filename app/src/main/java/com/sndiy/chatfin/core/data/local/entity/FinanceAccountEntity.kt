// app/src/main/java/com/sndiy/chatfin/core/data/local/entity/FinanceAccountEntity.kt

package com.sndiy.chatfin.core.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "finance_accounts")
data class FinanceAccountEntity(
    @PrimaryKey val id: String,
    val name: String,
    val iconName: String = "account_balance_wallet",
    val colorHex: String = "#0061A4",
    val currency: String = "IDR",
    val description: String? = null,
    val isPinProtected: Boolean = false,
    val pinHash: String? = null,
    val isBiometricEnabled: Boolean = false,
    val isActive: Boolean = false,
    val sortOrder: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)