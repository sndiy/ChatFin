package com.sndiy.chatfin.core.data.security

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SecureStorage @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "chatfin_secure",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    var geminiApiKey: String?
        get() = prefs.getString(KEY_GEMINI_API, null)
        set(value) = prefs.edit().let {
            if (value.isNullOrBlank()) it.remove(KEY_GEMINI_API)
            else it.putString(KEY_GEMINI_API, value)
            it.apply()
        }

    var activeAccountId: String?
        get() = prefs.getString(KEY_ACTIVE_ACCOUNT, null)
        set(value) = prefs.edit().let {
            if (value.isNullOrBlank()) it.remove(KEY_ACTIVE_ACCOUNT)
            else it.putString(KEY_ACTIVE_ACCOUNT, value)
            it.apply()
        }

    companion object {
        private const val KEY_GEMINI_API     = "gemini_api_key"
        private const val KEY_ACTIVE_ACCOUNT = "active_account_id"
    }
}