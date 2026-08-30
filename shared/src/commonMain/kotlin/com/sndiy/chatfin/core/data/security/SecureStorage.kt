package com.sndiy.chatfin.core.data.security

/**
 * Interface abstraksi SecureStorage untuk menyimpan data sensitif secara terenkripsi (KMP-ready).
 */
interface SecureStorage {
    suspend fun getGeminiApiKey(): String?
    suspend fun setGeminiApiKey(value: String?)
    suspend fun getActiveAccountId(): String?
    suspend fun setActiveAccountId(value: String?)
}
