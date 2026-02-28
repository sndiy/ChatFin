// app/src/main/java/com/sndiy/chatfin/core/di/PreferencesModule.kt

package com.sndiy.chatfin.core.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.sndiy.chatfin.core.data.security.SecureStorage
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

// Extension property untuk DataStore (hanya boleh ada satu per nama)
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
    name = "chatfin_preferences"
)

@Module
@InstallIn(SingletonComponent::class)
object PreferencesModule {

    // DataStore untuk menyimpan preferensi non-sensitif (tema, bahasa, dll)
    @Provides
    @Singleton
    fun provideDataStore(
        @ApplicationContext context: Context
    ): DataStore<Preferences> = context.dataStore

    // SecureStorage untuk menyimpan data sensitif (API Key, PIN hash)
    @Provides
    @Singleton
    fun provideSecureStorage(
        @ApplicationContext context: Context
    ): SecureStorage = SecureStorage(context)
}