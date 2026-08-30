package com.sndiy.chatfin.core.data.security

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Implementasi SecureStorage untuk platform Desktop (JVM).
 *
 * Fitur Keamanan (Sesuai AGENTS.md Bagian 2.8):
 * 1. Enkripsi AES-256-GCM (otentikasi terenkripsi dengan 128-bit tag & 12-byte random IV per write).
 * 2. Key Derivation via PBKDF2WithHmacSHA256 (65.536 iterasi) menggunakan 16-byte random salt.
 * 3. Dynamic Random Pepper (256-bit) di-generate acak per instalasi di berkas lokal terpisah (vault.pepper)
 *    dengan proteksi file permission OS — TIDAK ADA rahasia hardcoded di source code.
 * 4. Data di disk (secure_vault.enc) 100% berupa ciphertext, tidak pernah plain text.
 * 5. Atomic write melalui berkas sementara (.tmp) dan thread-safe coroutine Mutex untuk mencegah race condition.
 * 6. Graceful fallback: Jika dekripsi gagal (misal file korup / pepper hilang), sistem mengembalikan null
 *    tanpa melempar exception mentah atau mematikan aplikasi.
 */
class DesktopSecureStorage(
    private val baseDir: File = File(System.getProperty("user.home"), ".chatfin")
) : SecureStorage {

    private val mutex = Mutex()
    private val secureRandom = SecureRandom()
    private val json = Json { ignoreUnknownKeys = true }

    private val saltFile = File(baseDir, "vault.salt")
    private val pepperFile = File(baseDir, "vault.pepper")
    private val vaultFile = File(baseDir, "secure_vault.enc")

    @Volatile
    private var cachedKey: SecretKey? = null

    init {
        if (!baseDir.exists()) {
            baseDir.mkdirs()
            restrictFilePermissions(baseDir)
        }
    }

    override suspend fun getGeminiApiKey(): String? = withContext(Dispatchers.IO) {
        mutex.withLock {
            readEntries()[KEY_GEMINI_API]
        }
    }

    override suspend fun setGeminiApiKey(value: String?) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val entries = readEntries().toMutableMap()
            if (value.isNullOrBlank()) {
                entries.remove(KEY_GEMINI_API)
            } else {
                entries[KEY_GEMINI_API] = value
            }
            writeEntries(entries)
        }
    }

    override suspend fun getActiveAccountId(): String? = withContext(Dispatchers.IO) {
        mutex.withLock {
            readEntries()[KEY_ACTIVE_ACCOUNT]
        }
    }

    override suspend fun setActiveAccountId(value: String?) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val entries = readEntries().toMutableMap()
            if (value.isNullOrBlank()) {
                entries.remove(KEY_ACTIVE_ACCOUNT)
            } else {
                entries[KEY_ACTIVE_ACCOUNT] = value
            }
            writeEntries(entries)
        }
    }

    // ── Internal Crypto Operations ───────────────────────────────────────────

    private fun getOrCreateSalt(): ByteArray {
        if (saltFile.exists() && saltFile.length() == SALT_LENGTH.toLong()) {
            return saltFile.readBytes()
        }
        val newSalt = ByteArray(SALT_LENGTH)
        secureRandom.nextBytes(newSalt)
        atomicWriteFile(saltFile, "vault.salt.tmp", newSalt)
        return newSalt
    }

    private fun getOrCreatePepper(): ByteArray {
        if (pepperFile.exists() && pepperFile.length() == PEPPER_LENGTH.toLong()) {
            return pepperFile.readBytes()
        }
        val newPepper = ByteArray(PEPPER_LENGTH)
        secureRandom.nextBytes(newPepper)
        atomicWriteFile(pepperFile, "vault.pepper.tmp", newPepper)
        return newPepper
    }

    private fun getDerivedKey(): SecretKey {
        cachedKey?.let { return it }

        val salt = getOrCreateSalt()
        val dynamicPepper = getOrCreatePepper()

        // Gabungkan identitas user OS, hardware environment, dan dynamic random pepper
        val machineEntropy = buildString {
            append(System.getProperty("user.name", "unknown_user"))
            append(":")
            append(System.getProperty("user.home", "unknown_home"))
            append(":")
            append(System.getProperty("os.name", "unknown_os"))
            append(":")
            append(System.getProperty("os.arch", "unknown_arch"))
            append(":")
            append(dynamicPepper.joinToString("") { "%02x".format(it) })
        }

        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(machineEntropy.toCharArray(), salt, ITERATION_COUNT, KEY_LENGTH_BITS)
        val tmpKey = factory.generateSecret(spec)
        val secretKey = SecretKeySpec(tmpKey.encoded, "AES")
        cachedKey = secretKey
        return secretKey
    }

    private fun readEntries(): Map<String, String> {
        if (!vaultFile.exists() || vaultFile.length() < HEADER_SIZE + IV_LENGTH + TAG_LENGTH_BYTES) {
            return emptyMap()
        }

        return try {
            val fileBytes = vaultFile.readBytes()
            // Validasi header
            if (fileBytes[0] != MAGIC_BYTE_0 || fileBytes[1] != MAGIC_BYTE_1 || fileBytes[2] != VERSION) {
                return emptyMap()
            }

            val iv = fileBytes.copyOfRange(HEADER_SIZE, HEADER_SIZE + IV_LENGTH)
            val ciphertext = fileBytes.copyOfRange(HEADER_SIZE + IV_LENGTH, fileBytes.size)

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val gcmSpec = GCMParameterSpec(TAG_LENGTH_BITS, iv)
            cipher.init(Cipher.DECRYPT_MODE, getDerivedKey(), gcmSpec)

            val plaintextBytes = cipher.doFinal(ciphertext)
            val jsonString = plaintextBytes.decodeToString()
            json.decodeFromString<Map<String, String>>(jsonString)
        } catch (_: Exception) {
            // Graceful fallback: jika dekripsi gagal (misal kunci berbeda / berkas korup),
            // kembalikan emptyMap agar user diminta input ulang tanpa crash aplikasi.
            emptyMap()
        }
    }

    private fun writeEntries(entries: Map<String, String>) {
        val jsonString = json.encodeToString(entries)
        val plaintextBytes = jsonString.encodeToByteArray()

        val iv = ByteArray(IV_LENGTH)
        secureRandom.nextBytes(iv)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val gcmSpec = GCMParameterSpec(TAG_LENGTH_BITS, iv)
        cipher.init(Cipher.ENCRYPT_MODE, getDerivedKey(), gcmSpec)

        val ciphertext = cipher.doFinal(plaintextBytes)

        val output = ByteArray(HEADER_SIZE + IV_LENGTH + ciphertext.size)
        output[0] = MAGIC_BYTE_0
        output[1] = MAGIC_BYTE_1
        output[2] = VERSION
        System.arraycopy(iv, 0, output, HEADER_SIZE, IV_LENGTH)
        System.arraycopy(ciphertext, 0, output, HEADER_SIZE + IV_LENGTH, ciphertext.size)

        atomicWriteFile(vaultFile, "secure_vault.tmp", output)
    }

    private fun atomicWriteFile(targetFile: File, tempName: String, data: ByteArray) {
        val tempFile = File(baseDir, tempName)
        tempFile.writeBytes(data)
        restrictFilePermissions(tempFile)
        try {
            Files.move(
                tempFile.toPath(),
                targetFile.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING
            )
        } catch (_: Exception) {
            Files.move(
                tempFile.toPath(),
                targetFile.toPath(),
                StandardCopyOption.REPLACE_EXISTING
            )
        }
        restrictFilePermissions(targetFile)
    }

    private fun restrictFilePermissions(file: File) {
        try {
            file.setReadable(false, false)
            file.setReadable(true, true)
            file.setWritable(false, false)
            file.setWritable(true, true)
            file.setExecutable(false, false)
        } catch (_: Exception) {
            // Best effort on file systems that do not support permission manipulation
        }
    }

    companion object {
        private const val KEY_GEMINI_API = "gemini_api_key"
        private const val KEY_ACTIVE_ACCOUNT = "active_account_id"

        private const val ITERATION_COUNT = 65536
        private const val KEY_LENGTH_BITS = 256
        private const val SALT_LENGTH = 16
        private const val PEPPER_LENGTH = 32
        private const val IV_LENGTH = 12
        private const val TAG_LENGTH_BITS = 128
        private const val TAG_LENGTH_BYTES = 16

        private const val MAGIC_BYTE_0 = 0x43.toByte() // 'C'
        private const val MAGIC_BYTE_1 = 0x46.toByte() // 'F'
        private const val VERSION = 0x01.toByte()
        private const val HEADER_SIZE = 3
    }
}
