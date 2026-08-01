package com.sndiy.chatfin.core.persona

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.personaDataStore: DataStore<Preferences> by preferencesDataStore(name = "persona_prefs")

@Singleton
class PersonaPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val PERSONA_ID = stringPreferencesKey("persona_id")
    private val CUSTOM_TEXT = stringPreferencesKey("custom_persona_text")

    // runCatching menjaga dari nilai tersimpan yang sudah tidak valid lagi
    // (mis. build lama menyimpan id yang di build baru sudah dihapus) — jangan
    // sampai layar chat crash gara-gara DataStore, cukup jatuh ke default Mai.
    val activePersonaId: Flow<PersonaId> = context.personaDataStore.data.map { prefs ->
        val stored = prefs[PERSONA_ID]
        runCatching { PersonaId.valueOf(stored ?: PersonaId.MAI.name) }.getOrDefault(PersonaId.MAI)
    }

    // Teks bebas untuk PersonaId.CUSTOM — kosong kalau user belum pernah
    // menulisnya (PersonaPreset.CUSTOM sudah punya fallback netral untuk itu).
    val customPersonaText: Flow<String> = context.personaDataStore.data.map { prefs ->
        prefs[CUSTOM_TEXT] ?: ""
    }

    suspend fun setPersona(id: PersonaId) {
        context.personaDataStore.edit { it[PERSONA_ID] = id.name }
    }

    suspend fun setCustomText(text: String) {
        context.personaDataStore.edit { it[CUSTOM_TEXT] = text }
    }
}
