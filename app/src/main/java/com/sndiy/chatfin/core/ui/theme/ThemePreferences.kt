package com.sndiy.chatfin.core.ui.theme

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

private val Context.themeDataStore: DataStore<Preferences> by preferencesDataStore(name = "theme_prefs")

data class ThemeConfig(
    val isDark: Boolean  = false,
    val accentKey: String = "Indigo"
)

@Singleton
class ThemePreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val IS_DARK   = booleanPreferencesKey("is_dark")
    private val ACCENT    = stringPreferencesKey("accent")

    val themeConfig: Flow<ThemeConfig> = context.themeDataStore.data.map { prefs ->
        ThemeConfig(
            isDark    = prefs[IS_DARK] ?: false,
            accentKey = prefs[ACCENT]  ?: "Indigo"
        )
    }

    suspend fun setDark(isDark: Boolean) {
        context.themeDataStore.edit { it[IS_DARK] = isDark }
    }

    suspend fun setAccent(accentKey: String) {
        context.themeDataStore.edit { it[ACCENT] = accentKey }
    }
}