package com.sndiy.chatfin.core.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * Satu baris kamus kata-kunci → kategori, dipakai TransactionParser lewat
 * RoomKeywordSource. Seed bawaan (DefaultKeywords) ditanam langsung di
 * MIGRATION_4_5 — tabel ini juga jadi tempat user menambah kata kunci sendiri
 * nanti (belum ada UI-nya di M6, isCustom disiapkan untuk itu).
 */
@Serializable
@Entity(tableName = "category_keywords")
data class CategoryKeywordEntity(
    @PrimaryKey val id: String,
    val keyword: String,
    val categoryId: String,
    val type: String,              // INCOME | EXPENSE — disalin dari kategori supaya query bisa filter tanpa join
    val isCustom: Boolean = false  // false = seed bawaan, true = ditambah user
)
