// app/src/main/java/com/sndiy/chatfin/core/data/security/SecureStorage.kt

package com.sndiy.chatfin.core.data.security

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import javax.inject.Inject

// Penyimpanan terenkripsi untuk data sensitif
// Menggunakan AES256-GCM (enkripsi) + AES256-SIV (enkripsi key)
class SecureStorage @Inject constructor(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "chatfin_secure_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    companion object {
        private const val KEY_API_KEY            = "gemini_api_key"
        private const val KEY_ACTIVE_ACCOUNT_ID  = "active_account_id"
    }

    // API Key Gemini — dimasukkan user dari Settings
    var geminiApiKey: String?
        get()      = prefs.getString(KEY_API_KEY, null)
        set(value) = prefs.edit().putString(KEY_API_KEY, value).apply()

    // ID akun yang sedang aktif — persist walau app ditutup
    var activeAccountId: String?
        get()      = prefs.getString(KEY_ACTIVE_ACCOUNT_ID, null)
        set(value) = prefs.edit().putString(KEY_ACTIVE_ACCOUNT_ID, value).apply()

    // Hapus semua data (dipakai saat reset app)
    fun clearAll() = prefs.edit().clear().apply()
}