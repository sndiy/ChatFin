package com.sndiy.chatfin.core.data.security

import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.test.*

class DesktopSecureStorageTest {

    private lateinit var tempDir: File
    private lateinit var storage: DesktopSecureStorage

    @BeforeTest
    fun setUp() {
        tempDir = File.createTempFile("chatfin_test_vault", "").apply {
            delete()
            mkdirs()
        }
        storage = DesktopSecureStorage(baseDir = tempDir)
    }

    @AfterTest
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun testSaveAndReadApiKey() = runTest {
        assertNull(storage.getGeminiApiKey(), "API key awal harus null")

        val secretKey = "AIzaSyD-TestSecretKey123456789"
        storage.setGeminiApiKey(secretKey)

        val retrieved = storage.getGeminiApiKey()
        assertEquals(secretKey, retrieved, "API key yang disimpan harus sesuai saat dibaca kembali")
    }

    @Test
    fun testEncryptedDataAtRest() = runTest {
        val secretKey = "AIzaSySuperSecretKeyThatMustNotBeInPlainText"
        storage.setGeminiApiKey(secretKey)

        val vaultFile = File(tempDir, "secure_vault.enc")
        assertTrue(vaultFile.exists(), "Berkas vault terenkripsi harus dibuat")

        val rawBytes = vaultFile.readBytes()
        val rawContent = rawBytes.decodeToString()

        // Verifikasi bahwa plaintext string tidak pernah muncul di berkas disk
        assertFalse(
            rawContent.contains(secretKey),
            "API key plain text TIDAK BOLEH muncul di dalam berkas vault!"
        )
        assertFalse(
            rawContent.contains("AIzaSy"),
            "Prefix API key tidak boleh bocor dalam bentuk plain text!"
        )
    }

    @Test
    fun testDynamicPepperIsGeneratedAndPersisted() = runTest {
        storage.setGeminiApiKey("AIzaSyTest123")

        val pepperFile = File(tempDir, "vault.pepper")
        val saltFile = File(tempDir, "vault.salt")

        assertTrue(pepperFile.exists(), "Berkas vault.pepper harus di-generate")
        assertTrue(saltFile.exists(), "Berkas vault.salt harus di-generate")
        assertEquals(32, pepperFile.length(), "Ukuran dynamic pepper harus 32 bytes (256-bit)")
        assertEquals(16, saltFile.length(), "Ukuran salt harus 16 bytes")

        // Buat instance baru dengan direktori yang sama, harus bisa membaca kembali
        val newStorageInstance = DesktopSecureStorage(baseDir = tempDir)
        assertEquals("AIzaSyTest123", newStorageInstance.getGeminiApiKey())
    }

    @Test
    fun testDifferentDirectoriesHaveDistinctPeppers() = runTest {
        storage.setGeminiApiKey("AIzaSyKey1")

        val otherDir = File.createTempFile("chatfin_test_other", "").apply {
            delete()
            mkdirs()
        }
        try {
            val otherStorage = DesktopSecureStorage(baseDir = otherDir)
            otherStorage.setGeminiApiKey("AIzaSyOther")

            val pepper1 = File(tempDir, "vault.pepper").readBytes()
            val pepper2 = File(otherDir, "vault.pepper").readBytes()

            assertFalse(
                pepper1.contentEquals(pepper2),
                "Dua instalasi yang berbeda harus menghasilkan dynamic random pepper yang berbeda"
            )
        } finally {
            otherDir.deleteRecursively()
        }
    }

    @Test
    fun testDeleteApiKey() = runTest {
        storage.setGeminiApiKey("AIzaSyDummyKey")
        assertEquals("AIzaSyDummyKey", storage.getGeminiApiKey())

        storage.setGeminiApiKey(null)
        assertNull(storage.getGeminiApiKey(), "API key harus null setelah dihapus")
    }

    @Test
    fun testActiveAccountId() = runTest {
        assertNull(storage.getActiveAccountId())

        storage.setActiveAccountId("acc-uuid-1234")
        assertEquals("acc-uuid-1234", storage.getActiveAccountId())

        storage.setActiveAccountId(null)
        assertNull(storage.getActiveAccountId())
    }

    @Test
    fun testOverwriteApiKey() = runTest {
        storage.setGeminiApiKey("key_v1")
        assertEquals("key_v1", storage.getGeminiApiKey())

        storage.setGeminiApiKey("key_v2_updated")
        assertEquals("key_v2_updated", storage.getGeminiApiKey())
    }

    @Test
    fun testCorruptedVaultGracefulFallback() = runTest {
        storage.setGeminiApiKey("valid_key")
        val vaultFile = File(tempDir, "secure_vault.enc")
        assertTrue(vaultFile.exists())

        // Rusak file vault secara sengaja
        vaultFile.writeBytes(byteArrayOf(0x00, 0x01, 0x02, 0x03, 0x04))

        // SecureStorage harus menangani dengan aman (return null, tanpa crash)
        val result = storage.getGeminiApiKey()
        assertNull(result, "File vault yang rusak harus ditangani secara aman dengan return null")
    }

    @Test
    fun testNoHardcodedSecretsInSourceCode() {
        // Cari file sumber DesktopSecureStorage.kt
        val sourceFile = File("src/desktopMain/kotlin/com/sndiy/chatfin/core/data/security/DesktopSecureStorage.kt")
        if (sourceFile.exists()) {
            val content = sourceFile.readText()
            assertFalse(
                content.contains("STATIC_PEPPER"),
                "Source code DesktopSecureStorage.kt TIDAK BOLEH mengandung konstanta STATIC_PEPPER hardcoded!"
            )
            assertFalse(
                content.contains("ChatFin_Mai_Sakurajima"),
                "Source code DesktopSecureStorage.kt TIDAK BOLEH mengandung static pepper string hardcoded!"
            )
        }
    }
}
