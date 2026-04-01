// app/src/main/java/com/sndiy/chatfin/core/data/local/dao/CharacterDao.kt

package com.sndiy.chatfin.core.data.local.dao

import androidx.room.*
import com.sndiy.chatfin.core.data.local.entity.CharacterProfileEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CharacterDao {

    // Ambil semua karakter yang tersimpan
    @Query("SELECT * FROM character_profiles ORDER BY isPreset DESC, createdAt DESC")
    fun getAllCharacters(): Flow<List<CharacterProfileEntity>>

    // Ambil karakter yang sedang aktif dipakai
    @Query("SELECT * FROM character_profiles WHERE isActive = 1 LIMIT 1")
    fun getActiveCharacter(): Flow<CharacterProfileEntity?>

    // Ambil satu karakter berdasarkan ID
    @Query("SELECT * FROM character_profiles WHERE id = :id")
    suspend fun getCharacterById(id: String): CharacterProfileEntity?

    // Tambah karakter baru
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCharacter(character: CharacterProfileEntity)

    // Update karakter
    @Update
    suspend fun updateCharacter(character: CharacterProfileEntity)

    // Hapus karakter
    @Delete
    suspend fun deleteCharacter(character: CharacterProfileEntity)

    // Set semua karakter jadi tidak aktif
    @Query("UPDATE character_profiles SET isActive = 0")
    suspend fun deactivateAllCharacters()

    // Set satu karakter jadi aktif
    @Query("UPDATE character_profiles SET isActive = 1 WHERE id = :id")
    suspend fun activateCharacter(id: String)

    // Ganti karakter aktif
    @Transaction
    suspend fun switchActiveCharacter(id: String) {
        deactivateAllCharacters()
        activateCharacter(id)
    }
}