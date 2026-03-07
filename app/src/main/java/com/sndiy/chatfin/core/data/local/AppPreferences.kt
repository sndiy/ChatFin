// app/src/main/java/com/sndiy/chatfin/core/data/local/AppPreferences.kt

package com.sndiy.chatfin.core.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "chatfin_prefs")

@Singleton
class AppPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val ACTIVE_ACCOUNT_ID  = stringPreferencesKey("active_account_id")
        val ACTIVE_SESSION_ID  = stringPreferencesKey("active_session_id")
        val ONBOARDING_DONE    = booleanPreferencesKey("onboarding_done")
        val THEME_MODE         = stringPreferencesKey("theme_mode") // LIGHT | DARK | SYSTEM
        val CURRENCY           = stringPreferencesKey("currency")
    }

    val activeAccountId: Flow<String?> = context.dataStore.data.map { it[Keys.ACTIVE_ACCOUNT_ID] }
    val activeSessionId: Flow<String?> = context.dataStore.data.map { it[Keys.ACTIVE_SESSION_ID] }
    val onboardingDone: Flow<Boolean>  = context.dataStore.data.map { it[Keys.ONBOARDING_DONE] ?: false }
    val themeMode: Flow<String>        = context.dataStore.data.map { it[Keys.THEME_MODE] ?: "SYSTEM" }

    suspend fun setActiveAccountId(id: String) {
        context.dataStore.edit { it[Keys.ACTIVE_ACCOUNT_ID] = id }
    }

    suspend fun setActiveSessionId(id: String) {
        context.dataStore.edit { it[Keys.ACTIVE_SESSION_ID] = id }
    }

    suspend fun setOnboardingDone(done: Boolean) {
        context.dataStore.edit { it[Keys.ONBOARDING_DONE] = done }
    }

    suspend fun setThemeMode(mode: String) {
        context.dataStore.edit { it[Keys.THEME_MODE] = mode }
    }
}
