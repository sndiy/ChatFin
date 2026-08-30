package com.sndiy.chatfin.core.data.auth

import com.sndiy.chatfin.core.data.local.ChatFinDatabase
import com.sndiy.chatfin.core.data.local.entity.FinanceAccountEntity
import com.sndiy.chatfin.core.data.local.withTransaction
import com.sndiy.chatfin.core.data.security.DesktopSecureStorage
import dev.gitlive.firebase.auth.FirebaseAuth
import dev.gitlive.firebase.auth.FirebaseUser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Implementasi DesktopAuthRepository untuk platform Desktop menggunakan GitLive Firebase SDK.
 */
class DesktopAuthRepositoryImpl(
    private val auth: FirebaseAuth,
    private val secureStorage: DesktopSecureStorage,
    private val db: ChatFinDatabase
) : DesktopAuthRepository {

    override val currentUser: AuthUser?
        get() = try {
            auth.currentUser?.toDomainAuthUser()
        } catch (_: Throwable) {
            null
        }

    override val isLoggedIn: Boolean
        get() = currentUser != null

    override val authState: Flow<AuthUser?> = auth.authStateChanged.map { firebaseUser ->
        try {
            firebaseUser?.toDomainAuthUser()
        } catch (_: Throwable) {
            null
        }
    }

    override suspend fun loginWithEmail(email: String, password: String): Result<AuthUser> = withContext(Dispatchers.IO) {
        try {
            val result = auth.signInWithEmailAndPassword(email.trim(), password)
            val trimmedEmail = email.trim()
            secureStorage.setSync("firebase_user_email", trimmedEmail)
            val user = result.user?.toDomainAuthUser(trimmedEmail)
                ?: return@withContext Result.failure(IllegalStateException("Login berhasil namun data user kosong"))
            Result.success(user)
        } catch (e: Throwable) {
            System.err.println("[DesktopAuthRepository] loginWithEmail FAILED: ${e::class.qualifiedName ?: e.javaClass.name}: ${e.message}")
            e.printStackTrace(System.err)
            Result.failure(Exception(friendlyAuthErrorMessage(e)))
        }
    }

    override suspend fun registerWithEmail(email: String, password: String): Result<AuthUser> = withContext(Dispatchers.IO) {
        try {
            val result = auth.createUserWithEmailAndPassword(email.trim(), password)
            val trimmedEmail = email.trim()
            secureStorage.setSync("firebase_user_email", trimmedEmail)
            val user = result.user?.toDomainAuthUser(trimmedEmail)
                ?: return@withContext Result.failure(IllegalStateException("Registrasi berhasil namun data user kosong"))
            Result.success(user)
        } catch (e: Throwable) {
            System.err.println("[DesktopAuthRepository] registerWithEmail FAILED: ${e::class.qualifiedName ?: e.javaClass.name}: ${e.message}")
            e.printStackTrace(System.err)
            Result.failure(Exception(friendlyAuthErrorMessage(e)))
        }
    }

    override suspend fun sendPasswordReset(email: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            auth.sendPasswordResetEmail(email.trim())
            Result.success(Unit)
        } catch (e: Throwable) {
            System.err.println("[DesktopAuthRepository] sendPasswordReset FAILED: ${e::class.qualifiedName ?: e.javaClass.name}: ${e.message}")
            e.printStackTrace(System.err)
            Result.failure(Exception(friendlyAuthErrorMessage(e)))
        }
    }

    override suspend fun logout(): Unit = withContext(Dispatchers.IO) {
        try {
            secureStorage.removeSync("firebase_user_email")
            auth.signOut()
        } catch (_: Exception) {}

        try {
            // Bersihkan data Room lokal secara atomik saat logout (data cloud Firestore tetap utuh 100%)
            db.withTransaction {
                db.transactionDao().deleteAllTransactionItems()
                db.transactionDao().deleteAllTransactions()
                db.walletDao().deleteAllWallets()
                db.budgetDao().deleteAllBudgets()
                db.categoryDao().deleteAllCategories()
                db.chatDao().deleteAllChatMessages()
                db.chatDao().deleteAllChatSessions()
                db.accountDao().deleteAllAccounts()

                // Inisialisasi ulang akun lokal default yang bersih untuk mode offline
                val newAccId = "acc_" + System.currentTimeMillis()
                db.accountDao().insertAccount(
                    FinanceAccountEntity(
                        id = newAccId,
                        name = "Pribadi",
                        iconName = "account_balance_wallet",
                        colorHex = "#6750A4",
                        currency = "IDR",
                        isActive = true,
                        createdAt = System.currentTimeMillis(),
                        updatedAt = System.currentTimeMillis()
                    )
                )
            }
        } catch (e: Exception) {
            System.err.println("[DesktopAuthRepository] Error clearing local database on logout: ${e.message}")
        }
    }

    private fun FirebaseUser.toDomainAuthUser(emailOverride: String? = null): AuthUser {
        val safeEmail = emailOverride ?: secureStorage.getSync("firebase_user_email") ?: try { email } catch (_: Throwable) { null }
        val safeDisplayName = try { displayName } catch (_: Throwable) { null }
        val safeIsAnonymous = try { isAnonymous } catch (_: Throwable) { false }
        return AuthUser(
            uid = uid,
            email = safeEmail,
            displayName = safeDisplayName,
            isAnonymous = safeIsAnonymous
        )
    }

    private fun friendlyAuthErrorMessage(e: Throwable): String {
        val msg = e.message.orEmpty()
        return when {
            msg.contains("user-not-found", ignoreCase = true) ->
                "Akun tidak ditemukan. Silakan periksa kembali email kamu atau daftar akun baru."
            msg.contains("wrong-password", ignoreCase = true) || msg.contains("invalid-credential", ignoreCase = true) ->
                "Kata sandi yang kamu masukkan salah. Silakan coba lagi."
            msg.contains("email-already-in-use", ignoreCase = true) ->
                "Email ini sudah terdaftar. Silakan pilih tab 'Masuk' dengan akun tersebut."
            msg.contains("invalid-email", ignoreCase = true) ->
                "Format email tidak valid. Pastikan penulisan email sudah benar."
            msg.contains("weak-password", ignoreCase = true) ->
                "Kata sandi terlalu pendek. Gunakan minimal 6 karakter."
            msg.contains("network", ignoreCase = true) || msg.contains("timeout", ignoreCase = true) || msg.contains("unavailable", ignoreCase = true) ->
                "Tidak dapat terhubung ke server cloud. Periksa koneksi internet kamu atau lanjutkan secara offline."
            else ->
                "Gagal menghubungi server cloud. Periksa koneksi internet kamu atau coba beberapa saat lagi."
        }
    }
}
