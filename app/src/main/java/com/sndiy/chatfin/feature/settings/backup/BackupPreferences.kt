package com.sndiy.chatfin.feature.settings.backup

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.backupDataStore: DataStore<Preferences> by preferencesDataStore(name = "auto_backup_prefs")

@Singleton
class BackupPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val LAST_BACKUP_TIME = longPreferencesKey("last_backup_timestamp")
    }

    val lastBackupTimestamp: Flow<Long> = context.backupDataStore.data
        .map { prefs -> prefs[Keys.LAST_BACKUP_TIME] ?: 0L }

    suspend fun setLastBackupTimestamp(timestamp: Long) {
        context.backupDataStore.edit { prefs ->
            prefs[Keys.LAST_BACKUP_TIME] = timestamp
        }
    }
}
