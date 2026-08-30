package com.sndiy.chatfin.core.data.auth

import androidx.room.withTransaction
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.sndiy.chatfin.core.data.local.ChatFinDatabase
import com.sndiy.chatfin.core.data.local.entity.FinanceAccountEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    private val auth: FirebaseAuth,
    private val db: ChatFinDatabase
) {
    val currentUser: FirebaseUser? get() = auth.currentUser
    val isLoggedIn: Boolean get() = auth.currentUser != null

    val authState: Flow<FirebaseUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { trySend(it.currentUser) }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    suspend fun loginWithGoogle(idToken: String): Result<FirebaseUser> = withContext(Dispatchers.IO) {
        try {
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            val result     = auth.signInWithCredential(credential).await()
            Result.success(result.user!!)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun loginWithEmail(email: String, password: String): Result<FirebaseUser> = withContext(Dispatchers.IO) {
        try {
            val result = auth.signInWithEmailAndPassword(email, password).await()
            Result.success(result.user!!)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun registerWithEmail(email: String, password: String): Result<FirebaseUser> = withContext(Dispatchers.IO) {
        try {
            val result = auth.createUserWithEmailAndPassword(email, password).await()
            Result.success(result.user!!)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun sendPasswordReset(email: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            auth.sendPasswordResetEmail(email).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun logout(): Unit = withContext(Dispatchers.IO) {
        try {
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
            android.util.Log.e("AuthRepo", "Error clearing local DB on logout: ${e.message}", e)
        }
    }
}