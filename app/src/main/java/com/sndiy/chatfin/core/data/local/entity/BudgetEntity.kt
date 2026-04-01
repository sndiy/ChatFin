// app/src/main/java/com/sndiy/chatfin/core/data/local/entity/BudgetEntity.kt

package com.sndiy.chatfin.core.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "budgets")
data class BudgetEntity(
    @PrimaryKey val id: String,
    val accountId: String,              // FK ke FinanceAccountEntity
    val categoryId: String,             // FK ke CategoryEntity
    val limitAmount: Long,              // batas maksimal pengeluaran
    val period: String = "MONTHLY",     // MONTHLY | WEEKLY
    val month: Int? = null,             // 1-12, null jika WEEKLY
    val year: Int,
    val createdAt: Long = System.currentTimeMillis()
)