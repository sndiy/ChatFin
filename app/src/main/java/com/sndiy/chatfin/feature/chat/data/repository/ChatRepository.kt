// app/src/main/java/com/sndiy/chatfin/feature/chat/data/repository/ChatRepository.kt

package com.sndiy.chatfin.feature.chat.data.repository

import com.sndiy.chatfin.core.data.local.dao.ChatDao
import com.sndiy.chatfin.core.data.local.entity.ChatMessageEntity
import com.sndiy.chatfin.core.data.local.entity.ChatSessionEntity
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatRepository @Inject constructor(
    private val chatDao: ChatDao
) {
    // Satu akun = satu sesi chat berkelanjutan (bukan multi-percakapan) —
    // konsisten dengan UX saat ini: satu layar Chat per akun, di-reset lewat
    // "Hapus Percakapan", bukan daftar sesi terpisah.
    suspend fun getOrCreateSession(accountId: String): ChatSessionEntity {
        return chatDao.getLatestSessionForAccount(accountId) ?: run {
            val session = ChatSessionEntity(id = UUID.randomUUID().toString(), accountId = accountId)
            chatDao.insertSession(session)
            session
        }
    }

    suspend fun getMessagesOnce(sessionId: String): List<ChatMessageEntity> =
        chatDao.getMessagesBySessionOnce(sessionId)

    // `id` datang dari UiMessage, tidak dibuat ulang di sini: kalau baris DB
    // punya id sendiri, edit/hapus pesan dari sesi berjalan menyasar id yang
    // tidak ada sehingga perubahannya hilang begitu chat dimuat ulang.
    suspend fun saveMessage(
        id: String,
        sessionId: String,
        role: String,
        content: String,
        isError: Boolean,
        optionJson: String? = null
    ) {
        chatDao.insertMessage(
            ChatMessageEntity(
                id         = id,
                sessionId  = sessionId,
                role       = role,
                content    = content,
                isError    = isError,
                optionJson = optionJson
            )
        )
        chatDao.getSessionById(sessionId)?.let { session ->
            chatDao.updateSession(
                session.copy(
                    messageCount = session.messageCount + 1,
                    updatedAt    = System.currentTimeMillis()
                )
            )
        }
    }

    suspend fun deleteMessage(id: String) {
        chatDao.deleteMessageById(id)
    }

    suspend fun updateMessage(id: String, newContent: String) {
        chatDao.updateMessageContent(id, newContent)
    }

    suspend fun clearMessageOption(id: String) {
        chatDao.clearMessageOption(id)
    }

    suspend fun clearAllOptions(sessionId: String) {
        chatDao.clearOptionsBySession(sessionId)
    }

    suspend fun clearSession(sessionId: String) {
        chatDao.deleteMessagesBySession(sessionId)
        chatDao.getSessionById(sessionId)?.let { session ->
            chatDao.updateSession(session.copy(messageCount = 0, updatedAt = System.currentTimeMillis()))
        }
    }
}
