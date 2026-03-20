package com.sndiy.chatfin.core.data.sync

import com.google.firebase.firestore.FirebaseFirestore
import com.sndiy.chatfin.core.data.local.dao.AccountDao
import com.sndiy.chatfin.core.data.local.dao.CategoryDao
import com.sndiy.chatfin.core.data.local.dao.TransactionDao
import com.sndiy.chatfin.core.data.local.dao.WalletDao
import com.sndiy.chatfin.core.data.local.entity.CategoryEntity
import com.sndiy.chatfin.core.data.local.entity.FinanceAccountEntity
import com.sndiy.chatfin.core.data.local.entity.TransactionEntity
import com.sndiy.chatfin.core.data.local.entity.WalletEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val accountDao: AccountDao,
    private val walletDao: WalletDao,
    private val categoryDao: CategoryDao,
    private val transactionDao: TransactionDao
) {
    private fun col(uid: String, name: String) =
        firestore.collection("users").document(uid).collection(name)

    // ── Upload: lokal → cloud (timpa semua) ───────────────────────────────────
    suspend fun uploadAll(uid: String): Result<SyncStats> {
        return try {
            val accounts = accountDao.getAllAccounts().first()

            // Kumpulkan semua data lokal
            val wallets = accounts.flatMap {
                walletDao.getWalletsByAccount(it.id).first()
            }
            val categories = mutableListOf<CategoryEntity>()
            accounts.forEach { acc ->
                categories += categoryDao.getCategoriesByAccountAndType(acc.id, "EXPENSE").first()
                categories += categoryDao.getCategoriesByAccountAndType(acc.id, "INCOME").first()
            }
            val uniqueCategories = categories.distinctBy { it.id }
            val transactions = accounts.flatMap {
                transactionDao.getTransactionsByAccount(it.id).first()
            }

            // Hapus semua data lama di cloud dulu
            deleteCloudCollection(uid, "accounts")
            deleteCloudCollection(uid, "wallets")
            deleteCloudCollection(uid, "categories")
            deleteCloudCollection(uid, "transactions")

            // Upload semua data lokal ke cloud
            uploadCollection(uid, "accounts",     accounts.map { it.toMap() to it.id })
            uploadCollection(uid, "wallets",      wallets.map { it.toMap() to it.id })
            uploadCollection(uid, "categories",   uniqueCategories.map { it.toMap() to it.id })
            uploadCollection(uid, "transactions", transactions.map { it.toMap() to it.id })

            android.util.Log.d("SyncRepo", "Upload selesai: ${accounts.size} akun, ${wallets.size} dompet, ${uniqueCategories.size} kategori, ${transactions.size} transaksi")

            Result.success(SyncStats(
                accounts     = accounts.size,
                wallets      = wallets.size,
                categories   = uniqueCategories.size,
                transactions = transactions.size
            ))
        } catch (e: Exception) {
            android.util.Log.e("SyncRepo", "Upload error: ${e.message}", e)
            Result.failure(Exception("Upload gagal: ${e.message}"))
        }
    }

    // ── Download: cloud → lokal (timpa semua) ─────────────────────────────────
    suspend fun downloadAll(uid: String): Result<SyncStats> {
        return try {
            val accountDocs     = col(uid, "accounts").get().await()
            val walletDocs      = col(uid, "wallets").get().await()
            val categoryDocs    = col(uid, "categories").get().await()
            val transactionDocs = col(uid, "transactions").get().await()

            val accounts     = accountDocs.documents.mapNotNull     { it.toFinanceAccount() }
            val wallets      = walletDocs.documents.mapNotNull       { it.toWallet() }
            val categories   = categoryDocs.documents.mapNotNull     { it.toCategory() }
            val transactions = transactionDocs.documents.mapNotNull  { it.toTransaction() }

            android.util.Log.d("SyncRepo", "Download: ${accounts.size} akun, ${wallets.size} dompet, ${categories.size} kategori, ${transactions.size} transaksi")

            // Hapus semua data lokal dulu, lalu insert dari cloud
            // Gunakan REPLACE agar data terbaru dari cloud menimpa yang lama
            accounts.forEach     { accountDao.insertAccount(it) }
            wallets.forEach      { walletDao.insertWallet(it) }
            categories.forEach   { categoryDao.insertCategory(it) }
            transactions.forEach { transactionDao.insertTransaction(it) }

            Result.success(SyncStats(
                accounts     = accounts.size,
                wallets      = wallets.size,
                categories   = categories.size,
                transactions = transactions.size
            ))
        } catch (e: Exception) {
            android.util.Log.e("SyncRepo", "Download error: ${e.message}", e)
            Result.failure(Exception("Download gagal: ${e.message}"))
        }
    }

    // ── Helper: hapus semua dokumen di collection ─────────────────────────────
    private suspend fun deleteCloudCollection(uid: String, name: String) {
        val docs = col(uid, name).get().await()
        docs.documents.chunked(400).forEach { chunk ->
            val batch = firestore.batch()
            chunk.forEach { batch.delete(it.reference) }
            batch.commit().await()
        }
        android.util.Log.d("SyncRepo", "Deleted cloud $name: ${docs.size()} docs")
    }

    // ── Helper: upload collection dalam batch ─────────────────────────────────
    private suspend fun uploadCollection(
        uid: String,
        name: String,
        items: List<Pair<Map<String, Any?>, String>>
    ) {
        items.chunked(400).forEach { chunk ->
            val batch = firestore.batch()
            chunk.forEach { (data, id) ->
                batch.set(col(uid, name).document(id), data)
            }
            batch.commit().await()
        }
    }

    // ── Entity → Map ──────────────────────────────────────────────────────────
    private fun FinanceAccountEntity.toMap(): Map<String, Any?> = mapOf(
        "id"          to id,
        "name"        to name,
        "iconName"    to iconName,
        "colorHex"    to colorHex,
        "currency"    to currency,
        "description" to (description ?: ""),
        "isActive"    to isActive,
        "sortOrder"   to sortOrder
    )

    private fun WalletEntity.toMap(): Map<String, Any?> = mapOf(
        "id"        to id,
        "accountId" to accountId,
        "name"      to name,
        "type"      to type,
        "balance"   to balance,
        "currency"  to currency,
        "colorHex"  to colorHex,
        "iconName"  to iconName,
        "sortOrder" to sortOrder
    )

    private fun CategoryEntity.toMap(): Map<String, Any?> = mapOf(
        "id"        to id,
        "accountId" to (accountId ?: ""),
        "name"      to name,
        "type"      to type,
        "iconName"  to iconName,
        "colorHex"  to colorHex,
        "isCustom"  to isCustom,
        "sortOrder" to sortOrder
    )

    private fun TransactionEntity.toMap(): Map<String, Any?> = mapOf(
        "id"                to id,
        "accountId"         to accountId,
        "type"              to type,
        "amount"            to amount,
        "categoryId"        to categoryId,
        "walletId"          to walletId,
        "toWalletId"        to (toWalletId ?: ""),
        "note"              to (note ?: ""),
        "date"              to date,
        "time"              to time,
        "isRecurring"       to isRecurring,
        "recurringInterval" to (recurringInterval ?: "")
    )

    // ── Map → Entity ──────────────────────────────────────────────────────────
    private fun com.google.firebase.firestore.DocumentSnapshot.toFinanceAccount(): FinanceAccountEntity? {
        return try {
            FinanceAccountEntity(
                id          = getString("id") ?: id,
                name        = getString("name") ?: return null,
                iconName    = getString("iconName") ?: "account_balance_wallet",
                colorHex    = getString("colorHex") ?: "#0061A4",
                currency    = getString("currency") ?: "IDR",
                description = getString("description")?.ifBlank { null },
                isActive    = getBoolean("isActive") ?: false,
                sortOrder   = getLong("sortOrder")?.toInt() ?: 0
            )
        } catch (e: Exception) {
            android.util.Log.e("SyncRepo", "Parse account error: ${e.message}")
            null
        }
    }

    private fun com.google.firebase.firestore.DocumentSnapshot.toWallet(): WalletEntity? {
        return try {
            WalletEntity(
                id        = getString("id") ?: id,
                accountId = getString("accountId") ?: return null,
                name      = getString("name") ?: return null,
                type      = getString("type") ?: "CASH",
                balance   = getLong("balance") ?: 0L,
                currency  = getString("currency") ?: "IDR",
                colorHex  = getString("colorHex") ?: "#1B8A4C",
                iconName  = getString("iconName") ?: "payments",
                sortOrder = getLong("sortOrder")?.toInt() ?: 0
            )
        } catch (e: Exception) {
            android.util.Log.e("SyncRepo", "Parse wallet error: ${e.message}")
            null
        }
    }

    private fun com.google.firebase.firestore.DocumentSnapshot.toCategory(): CategoryEntity? {
        return try {
            CategoryEntity(
                id        = getString("id") ?: id,
                accountId = getString("accountId")?.ifBlank { null },
                name      = getString("name") ?: return null,
                type      = getString("type") ?: return null,
                iconName  = getString("iconName") ?: "category",
                colorHex  = getString("colorHex") ?: "#757575",
                isCustom  = getBoolean("isCustom") ?: true,
                sortOrder = getLong("sortOrder")?.toInt() ?: 0
            )
        } catch (e: Exception) {
            android.util.Log.e("SyncRepo", "Parse category error: ${e.message}")
            null
        }
    }

    private fun com.google.firebase.firestore.DocumentSnapshot.toTransaction(): TransactionEntity? {
        return try {
            TransactionEntity(
                id                = getString("id") ?: id,
                accountId         = getString("accountId") ?: return null,
                type              = getString("type") ?: return null,
                amount            = getLong("amount") ?: return null,
                categoryId        = getString("categoryId") ?: return null,
                walletId          = getString("walletId") ?: return null,
                toWalletId        = getString("toWalletId")?.ifBlank { null },
                note              = getString("note")?.ifBlank { null },
                date              = getString("date") ?: return null,
                time              = getString("time") ?: return null,
                isRecurring       = getBoolean("isRecurring") ?: false,
                recurringInterval = getString("recurringInterval")?.ifBlank { null }
            )
        } catch (e: Exception) {
            android.util.Log.e("SyncRepo", "Parse transaction error: ${e.message}")
            null
        }
    }
}

data class SyncStats(
    val accounts: Int,
    val wallets: Int,
    val categories: Int,
    val transactions: Int
)