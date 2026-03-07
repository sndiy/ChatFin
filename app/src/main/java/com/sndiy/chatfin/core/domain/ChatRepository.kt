package com.sndiy.chatfin.feature.chat.data.repository

import com.sndiy.chatfin.core.data.local.dao.ChatDao
import com.sndiy.chatfin.core.data.local.entity.ChatMessageEntity
import com.sndiy.chatfin.core.data.local.entity.ChatSessionEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatRepository @Inject constructor(
    private val chatDao: ChatDao
) {
    fun getAllSessions(): Flow<List<ChatSessionEntity>> = chatDao.getAllSessions()
    fun getMessagesBySession(sessionId: String): Flow<List<ChatMessageEntity>> =
        chatDao.getMessagesBySession(sessionId)
    suspend fun insertSession(session: ChatSessionEntity) = chatDao.insertSession(session)
    suspend fun updateSession(session: ChatSessionEntity) = chatDao.updateSession(session)
    suspend fun deleteSession(session: ChatSessionEntity) = chatDao.deleteSessionWithMessages(session)
    suspend fun insertMessage(message: ChatMessageEntity) = chatDao.insertMessage(message)
    suspend fun getSessionById(id: String): ChatSessionEntity? = chatDao.getSessionById(id)
}