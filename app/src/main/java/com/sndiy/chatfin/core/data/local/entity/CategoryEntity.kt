// app/src/main/java/com/sndiy/chatfin/core/data/local/entity/CategoryEntity.kt

package com.sndiy.chatfin.core.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey val id: String,
    val accountId: String? = null,      // null = kategori global (tersedia semua akun)
    val name: String,
    val type: String,                   // INCOME | EXPENSE
    val iconName: String = "category",
    val colorHex: String = "#0061A4",
    val isCustom: Boolean = false,      // false = kategori default bawaan app
    val sortOrder: Int = 0
)