package com.sndiy.chatfin.core.data.security

import com.google.firebase.FirebasePlatform

/**
 * Bridge antara FirebasePlatform internal (firebase-java-sdk) ke DesktopSecureStorage.
 *
 * Seluruh token autentikasi Firebase ("com.google.firebase.auth.FIREBASE_USER...")
 * akan disimpan dan dibaca secara terenkripsi AES-256-GCM pada ~/.chatfin/secure_vault.enc.
 */
class DesktopFirebasePlatform(
    private val secureStorage: DesktopSecureStorage
) : FirebasePlatform() {

    override fun store(key: String, value: String) {
        secureStorage.setSync(key, value)
    }

    override fun retrieve(key: String): String? {
        return secureStorage.getSync(key)
    }

    override fun clear(key: String) {
        secureStorage.removeSync(key)
    }

    override fun log(msg: String) {
        // Dilarang mencatat log berisi data finansial / kredensial (AGENTS.md Bagian 2.8)
    }
}
