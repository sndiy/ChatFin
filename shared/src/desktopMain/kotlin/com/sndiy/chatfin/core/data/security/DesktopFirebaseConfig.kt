package com.sndiy.chatfin.core.data.security

import dev.gitlive.firebase.FirebaseOptions
import java.io.File

/**
 * Konfigurasi client publik Firebase untuk platform Desktop.
 *
 * Catatan: Parameter di bawah ini merupakan client identification publik Firebase
 * (bukan secret server / API Key Gemini), setara dengan konfigurasi google-services.json di Android.
 */
object DesktopFirebaseConfig {

    private const val DEFAULT_PROJECT_ID = "chatfin-ada27"
    private const val DEFAULT_API_KEY = "AIzaSyBUpp6uNim218D7VOezk50aiHv0q5fJfFs"
    private const val DEFAULT_APP_ID = "1:117617139696:android:4cc191f7f1e46261794735"
    private const val DEFAULT_STORAGE_BUCKET = "chatfin-ada27.firebasestorage.app"

    fun getFirebaseOptions(): FirebaseOptions {
        // 1. Prioritas Utama: Cek file konfigurasi Web/Desktop khusus (desktopApp/firebase-config.json)
        val desktopConfigFiles = listOf(
            File("desktopApp/firebase-config.json"),
            File("firebase-config.json"),
            File("desktop-firebase-config.json")
        )

        for (file in desktopConfigFiles) {
            if (file.exists()) {
                try {
                    val content = file.readText()
                    val apiKey = extractJsonField(content, "\"apiKey\": \"", "\"")
                        ?: extractJsonField(content, "\"apiKey\":\"", "\"")
                    val appId = extractJsonField(content, "\"appId\": \"", "\"")
                        ?: extractJsonField(content, "\"appId\":\"", "\"")
                    val projectId = extractJsonField(content, "\"projectId\": \"", "\"")
                        ?: extractJsonField(content, "\"projectId\":\"", "\"") ?: DEFAULT_PROJECT_ID
                    val storageBucket = extractJsonField(content, "\"storageBucket\": \"", "\"")
                        ?: extractJsonField(content, "\"storageBucket\":\"", "\"") ?: DEFAULT_STORAGE_BUCKET
                    val authDomain = extractJsonField(content, "\"authDomain\": \"", "\"")
                        ?: extractJsonField(content, "\"authDomain\":\"", "\"")

                    if (!apiKey.isNullOrBlank() && !appId.isNullOrBlank()) {
                        println("[DesktopFirebaseConfig] Memuat konfigurasi Firebase dari: ${file.name}")
                        return FirebaseOptions(
                            applicationId = appId,
                            apiKey = apiKey,
                            projectId = projectId,
                            storageBucket = storageBucket,
                            authDomain = authDomain
                        )
                    }
                } catch (_: Exception) {}
            }
        }

        // 2. Fallback: Cek app/google-services.json
        try {
            val jsonFile = File("app/google-services.json")
            if (jsonFile.exists()) {
                val content = jsonFile.readText()
                val projectId = extractJsonField(content, "\"project_id\": \"", "\"") ?: DEFAULT_PROJECT_ID
                val apiKey = extractJsonField(content, "\"current_key\": \"", "\"") ?: DEFAULT_API_KEY
                val appId = extractJsonField(content, "\"mobilesdk_app_id\": \"", "\"") ?: DEFAULT_APP_ID
                val storageBucket = extractJsonField(content, "\"storage_bucket\": \"", "\"") ?: DEFAULT_STORAGE_BUCKET

                return FirebaseOptions(
                    applicationId = appId,
                    apiKey = apiKey,
                    projectId = projectId,
                    storageBucket = storageBucket
                )
            }
        } catch (_: Exception) {
            // Fallback ke konstanta default
        }

        return FirebaseOptions(
            applicationId = DEFAULT_APP_ID,
            apiKey = DEFAULT_API_KEY,
            projectId = DEFAULT_PROJECT_ID,
            storageBucket = DEFAULT_STORAGE_BUCKET
        )
    }

    private fun extractJsonField(json: String, prefix: String, suffix: String): String? {
        val startIndex = json.indexOf(prefix)
        if (startIndex == -1) return null
        val valueStart = startIndex + prefix.length
        val endIndex = json.indexOf(suffix, valueStart)
        if (endIndex == -1) return null
        return json.substring(valueStart, endIndex)
    }
}
