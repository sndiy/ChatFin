package com.sndiy.chatfin.core.data.security

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.prefs.Preferences

class DesktopSecureStorage : SecureStorage {
    private val prefs = Preferences.userNodeForPackage(DesktopSecureStorage::class.java)

    override suspend fun getGeminiApiKey(): String? = withContext(Dispatchers.IO) {
        prefs.get(KEY_GEMINI_API, null)
    }

    override suspend fun setGeminiApiKey(value: String?) = withContext(Dispatchers.IO) {
        if (value.isNullOrBlank()) {
            prefs.remove(KEY_GEMINI_API)
        } else {
            prefs.put(KEY_GEMINI_API, value)
        }
    }

    override suspend fun getActiveAccountId(): String? = withContext(Dispatchers.IO) {
        prefs.get(KEY_ACTIVE_ACCOUNT, null)
    }

    override suspend fun setActiveAccountId(value: String?) = withContext(Dispatchers.IO) {
        if (value.isNullOrBlank()) {
            prefs.remove(KEY_ACTIVE_ACCOUNT)
        } else {
            prefs.put(KEY_ACTIVE_ACCOUNT, value)
        }
    }

    companion object {
        private const val KEY_GEMINI_API = "gemini_api_key"
        private const val KEY_ACTIVE_ACCOUNT = "active_account_id"
    }
}
