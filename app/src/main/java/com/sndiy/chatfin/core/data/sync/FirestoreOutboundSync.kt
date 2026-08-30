package com.sndiy.chatfin.core.data.sync

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.sndiy.chatfin.core.data.auth.AuthRepository
import com.sndiy.chatfin.core.data.local.dao.WalletDao
import com.sndiy.chatfin.core.data.local.entity.BudgetEntity
import com.sndiy.chatfin.core.data.local.entity.CategoryEntity
import com.sndiy.chatfin.core.data.local.entity.FinanceAccountEntity
import com.sndiy.chatfin.core.data.local.entity.TransactionEntity
import com.sndiy.chatfin.core.data.local.entity.TransactionItemEntity
import com.sndiy.chatfin.core.data.local.entity.WalletEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "FirestoreOutboundSync"

/**
 * Pengelola push data ke Firestore secara real-time saat terjadi perubahan di lokal (Room).
 *
 * Sifat:
 * 1. Non-blocking & aman: kegagalan network/cloud tidak membatalkan operasi Room lokal (offline-first).
 * 2. Menggunakan Dispatchers.IO sesuai AGENTS.md Bagian 2.5.
 * 3. Jika user belum login, seluruh pemanggilan langsung no-op tanpa error.
 */
@Singleton
class FirestoreOutboundSync @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val authRepo: AuthRepository,
    private val walletDao: WalletDao
) : OutboundSync {
    private fun col(uid: String, name: String) =
        firestore.collection("users").document(uid).collection(name)

    // ── Transaksi ────────────────────────────────────────────────────────────

    override suspend fun pushTransaction(
        tx: TransactionEntity,
        items: List<TransactionItemEntity>
    ) = withContext(Dispatchers.IO) {
        val uid = authRepo.currentUser?.uid ?: return@withContext
        try {
            col(uid, "transactions").document(tx.id).set(tx.toMap(items)).await()
            // Mutasi saldo dompet ikut terkirim ke cloud
            pushWalletById(tx.walletId)
            tx.toWalletId?.let { pushWalletById(it) }
        } catch (e: Exception) {
            Log.w(TAG, "pushTransaction failed for ${tx.id}: ${e.message}")
        }
    }

    override suspend fun deleteTransaction(
        txId: String,
        walletId: String?,
        toWalletId: String?
    ) = withContext(Dispatchers.IO) {
        val uid = authRepo.currentUser?.uid ?: return@withContext
        try {
            col(uid, "transactions").document(txId).delete().await()
            walletId?.let { pushWalletById(it) }
            toWalletId?.let { pushWalletById(it) }
        } catch (e: Exception) {
            Log.w(TAG, "deleteTransaction failed for $txId: ${e.message}")
        }
    }

    // ── Dompet (Wallet) ──────────────────────────────────────────────────────

    override suspend fun pushWallet(wallet: WalletEntity) = withContext(Dispatchers.IO) {
        val uid = authRepo.currentUser?.uid ?: return@withContext
        try {
            col(uid, "wallets").document(wallet.id).set(wallet.toMap()).await()
        } catch (e: Exception) {
            Log.w(TAG, "pushWallet failed for ${wallet.id}: ${e.message}")
        }
    }

    override suspend fun pushWalletById(walletId: String) = withContext(Dispatchers.IO) {
        try {
            val wallet = walletDao.getWalletById(walletId) ?: return@withContext
            pushWallet(wallet)
        } catch (e: Exception) {
            Log.w(TAG, "pushWalletById failed for $walletId: ${e.message}")
        }
    }

    override suspend fun deleteWallet(walletId: String) = withContext(Dispatchers.IO) {
        val uid = authRepo.currentUser?.uid ?: return@withContext
        try {
            col(uid, "wallets").document(walletId).delete().await()
        } catch (e: Exception) {
            Log.w(TAG, "deleteWallet failed for $walletId: ${e.message}")
        }
    }

    // ── Kategori ─────────────────────────────────────────────────────────────

    override suspend fun pushCategory(category: CategoryEntity) = withContext(Dispatchers.IO) {
        val uid = authRepo.currentUser?.uid ?: return@withContext
        try {
            col(uid, "categories").document(category.id).set(category.toMap()).await()
        } catch (e: Exception) {
            Log.w(TAG, "pushCategory failed for ${category.id}: ${e.message}")
        }
    }

    override suspend fun deleteCategory(categoryId: String) = withContext(Dispatchers.IO) {
        val uid = authRepo.currentUser?.uid ?: return@withContext
        try {
            col(uid, "categories").document(categoryId).delete().await()
        } catch (e: Exception) {
            Log.w(TAG, "deleteCategory failed for $categoryId: ${e.message}")
        }
    }

    // ── Akun Finansial ───────────────────────────────────────────────────────

    override suspend fun pushAccount(account: FinanceAccountEntity) = withContext(Dispatchers.IO) {
        val uid = authRepo.currentUser?.uid ?: return@withContext
        try {
            col(uid, "accounts").document(account.id).set(account.toMap()).await()
        } catch (e: Exception) {
            Log.w(TAG, "pushAccount failed for ${account.id}: ${e.message}")
        }
    }

    override suspend fun deleteAccount(accountId: String) = withContext(Dispatchers.IO) {
        val uid = authRepo.currentUser?.uid ?: return@withContext
        try {
            col(uid, "accounts").document(accountId).delete().await()
        } catch (e: Exception) {
            Log.w(TAG, "deleteAccount failed for $accountId: ${e.message}")
        }
    }

    // ── Budget ───────────────────────────────────────────────────────────────

    override suspend fun pushBudget(budget: BudgetEntity) = withContext(Dispatchers.IO) {
        val uid = authRepo.currentUser?.uid ?: return@withContext
        try {
            col(uid, "budgets").document(budget.id).set(budget.toMap()).await()
        } catch (e: Exception) {
            Log.w(TAG, "pushBudget failed for ${budget.id}: ${e.message}")
        }
    }

    override suspend fun deleteBudget(budgetId: String) = withContext(Dispatchers.IO) {
        val uid = authRepo.currentUser?.uid ?: return@withContext
        try {
            col(uid, "budgets").document(budgetId).delete().await()
        } catch (e: Exception) {
            Log.w(TAG, "deleteBudget failed for $budgetId: ${e.message}")
        }
    }

    // ── Serialization Helpers ────────────────────────────────────────────────

    private fun FinanceAccountEntity.toMap(): Map<String, Any?> = mapOf(
        "id"                 to id,
        "name"               to name,
        "iconName"           to iconName,
        "colorHex"           to colorHex,
        "currency"           to currency,
        "description"        to (description ?: ""),
        "isPinProtected"     to isPinProtected,
        "pinHash"            to (pinHash ?: ""),
        "isBiometricEnabled" to isBiometricEnabled,
        "isActive"           to isActive,
        "sortOrder"          to sortOrder,
        "createdAt"          to createdAt,
        "updatedAt"          to updatedAt
    )

    private fun WalletEntity.toMap(): Map<String, Any?> = mapOf(
        "id"            to id,
        "accountId"     to accountId,
        "name"          to name,
        "type"          to type,
        "balance"       to balance,
        "currency"      to currency,
        "bankName"      to (bankName ?: ""),
        "accountNumber" to (accountNumber ?: ""),
        "iconName"      to iconName,
        "colorHex"      to colorHex,
        "isDefault"     to isDefault,
        "sortOrder"     to sortOrder,
        "createdAt"     to createdAt,
        "updatedAt"     to updatedAt
    )

    private fun CategoryEntity.toMap(): Map<String, Any?> = mapOf(
        "id"        to id,
        "accountId" to (accountId ?: ""),
        "name"      to name,
        "type"      to type,
        "iconName"  to iconName,
        "colorHex"  to colorHex,
        "isCustom"  to isCustom,
        "sortOrder" to sortOrder,
        "updatedAt" to updatedAt
    )

    private fun TransactionEntity.toMap(
        items: List<TransactionItemEntity> = emptyList()
    ): Map<String, Any?> = mapOf(
        "id"                to id,
        "accountId"         to accountId,
        "type"              to type,
        "amount"            to amount,
        "categoryId"        to categoryId,
        "walletId"          to walletId,
        "toWalletId"        to (toWalletId ?: ""),
        "note"              to (note ?: ""),
        "receiptImageUri"   to (receiptImageUri ?: ""),
        "date"              to date,
        "time"              to time,
        "transferPairId"    to (transferPairId ?: ""),
        "isInitialBalance"  to isInitialBalance,
        "createdAt"         to createdAt,
        "updatedAt"         to updatedAt,
        "items"             to items.map { item ->
            mapOf(
                "id"       to item.id,
                "name"     to item.name,
                "price"    to item.price,
                "quantity" to item.quantity
            )
        }
    )

    private fun BudgetEntity.toMap(): Map<String, Any?> = mapOf(
        "id"          to id,
        "accountId"   to accountId,
        "categoryId"  to categoryId,
        "limitAmount" to limitAmount,
        "period"      to period,
        "month"       to (month ?: 0),
        "year"        to year,
        "createdAt"   to createdAt,
        "updatedAt"   to createdAt
    )
}
