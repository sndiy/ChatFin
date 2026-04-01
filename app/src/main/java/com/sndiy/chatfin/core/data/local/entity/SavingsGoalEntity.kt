// app/src/main/java/com/sndiy/chatfin/core/data/local/entity/SavingsGoalEntity.kt

package com.sndiy.chatfin.core.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "savings_goals")
data class SavingsGoalEntity(
    @PrimaryKey val id: String,
    val accountId: String,              // FK ke FinanceAccountEntity
    val name: String,
    val targetAmount: Long,             // target tabungan
    val currentAmount: Long = 0L,       // jumlah yang sudah terkumpul
    val deadline: String? = null,       // format: "yyyy-MM-dd", null = tanpa deadline
    val iconName: String = "savings",
    val colorHex: String = "#B08800",
    val isCompleted: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)