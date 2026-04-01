// app/src/main/java/com/sndiy/chatfin/core/data/local/dao/ChatDao.kt

package com.sndiy.chatfin.core.data.local.dao

import androidx.room.*
import com.sndiy.chatfin.core.data.local.entity.ChatMessageEntity
import com.sndiy.chatfin.core.data.local.entity.ChatSessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatDao {

    // ── Session ───────────────────────────────────────────────────────────────

    // Ambil semua sesi chat (terbaru di atas)
    @Query("SELECT * FROM chat_sessions ORDER BY updatedAt DESC")
    fun getAllSessions(): Flow<List<ChatSessionEntity>>

    // Ambil satu sesi berdasarkan ID
    @Query("SELECT * FROM chat_sessions WHERE id = :id")
    suspend fun getSessionById(id: String): ChatSessionEntity?

    // Tambah sesi baru
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: ChatSessionEntity)

    // Update sesi (contoh: update judul atau updatedAt)
    @Update
    suspend fun updateSession(session: ChatSessionEntity)

    // Hapus sesi
    @Delete
    suspend fun deleteSession(session: ChatSessionEntity)

    // ── Message ───────────────────────────────────────────────────────────────

    // Ambil semua pesan dalam satu sesi (urutan kronologis)
    @Query("SELECT * FROM chat_messages WHERE sessionId = :sessionId ORDER BY createdAt ASC")
    fun getMessagesBySession(sessionId: String): Flow<List<ChatMessageEntity>>

    // Tambah satu pesan
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ChatMessageEntity)

    // Hapus satu pesan
    @Delete
    suspend fun deleteMessage(message: ChatMessageEntity)

    // Hapus semua pesan dalam satu sesi (saat sesi dihapus)
    @Query("DELETE FROM chat_messages WHERE sessionId = :sessionId")
    suspend fun deleteMessagesBySession(sessionId: String)

    // Hapus sesi beserta semua pesannya sekaligus
    @Transaction
    suspend fun deleteSessionWithMessages(session: ChatSessionEntity) {
        deleteMessagesBySession(session.id)
        deleteSession(session)
    }
}