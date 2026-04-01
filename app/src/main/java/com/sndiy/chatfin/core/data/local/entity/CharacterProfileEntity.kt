// app/src/main/java/com/sndiy/chatfin/core/data/local/entity/CharacterProfileEntity.kt

package com.sndiy.chatfin.core.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "character_profiles")
data class CharacterProfileEntity(
    @PrimaryKey val id: String,
    val name: String,                   // nama karakter, contoh: "Aqua", "Geralt"
    val origin: String? = null,         // asal karakter, contoh: "Anime: KonoSuba"
    val personalityDesc: String,        // deskripsi kepribadian bebas (text area)
    val speechStyle: String? = null,    // ciri khas cara bicara
    val likes: String? = null,          // hal yang disukai karakter
    val dislikes: String? = null,       // hal yang tidak disukai karakter
    val catchphrase: String? = null,    // kalimat khas yang sering diucapkan
    val restrictions: String? = null,   // hal yang karakter tidak akan lakukan
    val language: String = "INDONESIAN",    // INDONESIAN | ENGLISH | BILINGUAL
    val responseLength: String = "MEDIUM",  // SHORT | MEDIUM | LONG
    val useEmoji: Boolean = false,
    val avatarUri: String? = null,      // URI foto/avatar karakter
    val isActive: Boolean = false,      // karakter yang sedang aktif dipakai
    val isPreset: Boolean = false,      // true = preset bawaan app
    val createdAt: Long = System.currentTimeMillis()
)