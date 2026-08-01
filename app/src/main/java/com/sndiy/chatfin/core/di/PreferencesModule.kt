// app/src/main/java/com/sndiy/chatfin/core/di/PreferencesModule.kt

package com.sndiy.chatfin.core.di

import android.content.Context
import com.sndiy.chatfin.core.data.security.SecureStorage
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

// Catatan: provideDataStore + DataStore "chatfin_preferences" dihapus di M0.
// Tidak ada satu pun kelas yang meng-inject DataStore<Preferences>, jadi instance
// itu tidak pernah terpakai. DataStore yang benar-benar aktif dideklarasikan
// langsung di kelas pemakainya:
//   - AppPreferences    → "chatfin_prefs"
//   - ThemePreferences  → "theme_prefs"

@Module
@InstallIn(SingletonComponent::class)
object PreferencesModule {

    // SecureStorage untuk menyimpan data sensitif (API Key)
    @Provides
    @Singleton
    fun provideSecureStorage(
        @ApplicationContext context: Context
    ): SecureStorage = SecureStorage(context)
}