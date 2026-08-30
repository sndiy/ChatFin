package com.sndiy.chatfin.core.data.security

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AndroidSecureStorage(
    private val context: Context
) : SecureStorage {
    private val masterKey by lazy {
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
    }

    private val prefs: SharedPreferences by lazy {
        EncryptedSharedPreferences.create(
            context,
            "chatfin_secure",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    override suspend fun getGeminiApiKey(): String? = withContext(Dispatchers.IO) {
        prefs.getString(KEY_GEMINI_API, null)
    }

    override suspend fun setGeminiApiKey(value: String?) = withContext(Dispatchers.IO) {
        prefs.edit().apply {
            if (value.isNullOrBlank()) remove(KEY_GEMINI_API)
            else putString(KEY_GEMINI_API, value)
        }.apply()
    }

    override suspend fun getActiveAccountId(): String? = withContext(Dispatchers.IO) {
        prefs.getString(KEY_ACTIVE_ACCOUNT, null)
    }

    override suspend fun setActiveAccountId(value: String?) = withContext(Dispatchers.IO) {
        prefs.edit().apply {
            if (value.isNullOrBlank()) remove(KEY_ACTIVE_ACCOUNT)
            else putString(KEY_ACTIVE_ACCOUNT, value)
        }.apply()
    }

    companion object {
        private const val KEY_GEMINI_API = "gemini_api_key"
        private const val KEY_ACTIVE_ACCOUNT = "active_account_id"
    }
}
