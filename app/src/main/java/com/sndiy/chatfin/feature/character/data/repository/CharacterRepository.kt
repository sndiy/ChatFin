// app/src/main/java/com/sndiy/chatfin/feature/character/data/repository/CharacterRepository.kt

package com.sndiy.chatfin.feature.character.data.repository

import com.sndiy.chatfin.core.data.local.dao.CharacterDao
import com.sndiy.chatfin.core.data.local.entity.CharacterProfileEntity
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CharacterRepository @Inject constructor(
    private val characterDao: CharacterDao
) {
    fun getAllCharacters(): Flow<List<CharacterProfileEntity>> =
        characterDao.getAllCharacters()

    fun getActiveCharacter(): Flow<CharacterProfileEntity?> =
        characterDao.getActiveCharacter()

    suspend fun getCharacterById(id: String): CharacterProfileEntity? =
        characterDao.getCharacterById(id)

    suspend fun createCharacter(
        name: String,
        personalityDesc: String,
        origin: String? = null,
        speechStyle: String? = null,
        likes: String? = null,
        dislikes: String? = null,
        catchphrase: String? = null,
        restrictions: String? = null,
        language: String = "INDONESIAN",
        responseLength: String = "MEDIUM",
        useEmoji: Boolean = false,
        isPreset: Boolean = false
    ): String {
        val id = UUID.randomUUID().toString()
        characterDao.insertCharacter(
            CharacterProfileEntity(
                id              = id,
                name            = name,
                origin          = origin,
                personalityDesc = personalityDesc,
                speechStyle     = speechStyle,
                likes           = likes,
                dislikes        = dislikes,
                catchphrase     = catchphrase,
                restrictions    = restrictions,
                language        = language,
                responseLength  = responseLength,
                useEmoji        = useEmoji,
                isPreset        = isPreset,
                isActive        = false
            )
        )
        return id
    }

    suspend fun updateCharacter(character: CharacterProfileEntity) =
        characterDao.updateCharacter(character)

    suspend fun deleteCharacter(character: CharacterProfileEntity) =
        characterDao.deleteCharacter(character)

    suspend fun switchActiveCharacter(id: String) =
        characterDao.switchActiveCharacter(id)

    // Seed karakter preset bawaan jika belum ada
    suspend fun seedDefaultCharactersIfEmpty() {
        val existing = characterDao.getAllCharacters()
        // Cek via one-shot query
        val count = characterDao.getCharacterById("preset_assistant")
        if (count != null) return  // sudah ada, skip

        // Seed 1: Asisten Default (selalu ada)
        val defaultId = "preset_assistant"
        characterDao.insertCharacter(
            CharacterProfileEntity(
                id              = defaultId,
                name            = "Fin",
                origin          = "ChatFin AI",
                personalityDesc = "Asisten keuangan yang ramah, profesional, dan selalu siap membantu mencatat keuanganmu dengan ceria.",
                speechStyle     = "Sopan tapi santai, kadang pakai kata 'nih', 'yuk', 'sip'",
                likes           = "Keuangan sehat, nabung, investasi",
                dislikes        = "Boros tanpa rencana",
                catchphrase     = "Yuk catat keuanganmu bareng aku!",
                language        = "INDONESIAN",
                responseLength  = "MEDIUM",
                useEmoji        = true,
                isPreset        = true,
                isActive        = true   // aktif by default
            )
        )
    }
}