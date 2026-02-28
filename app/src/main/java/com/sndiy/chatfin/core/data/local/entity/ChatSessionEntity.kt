// app/src/main/java/com/sndiy/chatfin/core/data/local/entity/ChatSessionEntity.kt

package com.sndiy.chatfin.core.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chat_sessions")
data class ChatSessionEntity(
    @PrimaryKey val id: String,
    val characterProfileId: String,     // FK ke CharacterProfileEntity
    val title: String? = null,          // judul sesi, null = auto dari pesan pertama
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)