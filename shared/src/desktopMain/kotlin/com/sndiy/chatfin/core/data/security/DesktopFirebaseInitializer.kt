package com.sndiy.chatfin.core.data.security

import com.google.firebase.FirebasePlatform
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.initialize

/**
 * Inisialisasi Firebase JVM yang tahan terhadap kegagalan (Fail-Safe).
 *
 * Menjamin aplikasi tidak crash saat offline atau startup tanpa internet,
 * sesuai prinsip Offline-First & Persona A pada AGENTS.md Bagian 1 & 5.
 */
object DesktopFirebaseInitializer {

    var isInitialized: Boolean = false
        private set

    var initErrorMessage: String? = null
        private set

    fun initialize(secureStorage: DesktopSecureStorage) {
        try {
            // 1. Hubungkan persistence layer ke DesktopSecureStorage (Encrypted vault)
            FirebasePlatform.initializeFirebasePlatform(DesktopFirebasePlatform(secureStorage))

            // 2. Inisialisasi Firebase JVM runtime
            Firebase.initialize(
                context = android.app.Application(),
                options = DesktopFirebaseConfig.getFirebaseOptions()
            )
            isInitialized = true
            initErrorMessage = null
            println("[DesktopFirebaseInitializer] Firebase Desktop JVM initialized successfully.")
        } catch (e: Throwable) {
            isInitialized = false
            val errorDetails = "${e::class.qualifiedName ?: e.javaClass.name}: ${e.message}"
            initErrorMessage = errorDetails
            System.err.println("[DesktopFirebaseInitializer] ERROR: Failed to initialize Firebase for Desktop JVM!")
            System.err.println("  Exception: ${e::class.qualifiedName ?: e.javaClass.name}")
            System.err.println("  Message: ${e.message}")
            System.err.println("  Cause: ${e.cause}")
            e.printStackTrace(System.err)
            // Jangan throw exception agar aplikasi tetap berjalan offline-first (AGENTS.md Bagian 1)
        }
    }
}
