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

    suspend fun saveMessage(sessionId: String, role: String, content: String, isError: Boolean) {
        chatDao.insertMessage(
            ChatMessageEntity(
                id        = UUID.randomUUID().toString(),
                sessionId = sessionId,
                role      = role,
                content   = content,
                isError   = isError
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

    suspend fun clearSession(sessionId: String) {
        chatDao.deleteMessagesBySession(sessionId)
        chatDao.getSessionById(sessionId)?.let { session ->
            chatDao.updateSession(session.copy(messageCount = 0, updatedAt = System.currentTimeMillis()))
        }
    }
}
