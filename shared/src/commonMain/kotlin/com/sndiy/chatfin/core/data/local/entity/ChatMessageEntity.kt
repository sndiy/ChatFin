// app/src/main/java/com/sndiy/chatfin/core/data/local/entity/ChatMessageEntity.kt

package com.sndiy.chatfin.core.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chat_messages")
data class ChatMessageEntity(
    @PrimaryKey val id: String,
    val sessionId: String,              // FK ke ChatSessionEntity
    val role: String,                   // USER | ASSISTANT
    val content: String,                // isi pesan
    val isError: Boolean = false,       // true jika pesan error dari AI
    // ChatOption terserialisasi (chip kategori/dompet, kartu konfirmasi, dll).
    // null = pesan biasa ATAU opsi sudah dipakai/dibatalkan — dikosongkan lewat
    // ChatDao.clearMessageOption supaya pertanyaan yang sudah dijawab tidak
    // muncul lagi sebagai tombol aktif setelah layar dibuka ulang.
    val optionJson: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)