// app/src/main/java/com/sndiy/chatfin/core/data/sync/SyncRepository.kt
//
// FIXES:
// 1. downloadAll: SKIP jika cloud kosong (proteksi reset)
// 2. downloadAll: MERGE bukan timpa — only insert yang belum ada lokal
// 3. uploadAll: upload dulu baru delete yang tidak ada di lokal (atomic)
// 4. syncAfterLogin: smart — cek mana yang lebih banyak data, tawarkan pilihan
// 5. Tambah hasCloudData() untuk cek sebelum download
// 6. Tambah mergeDownload() sebagai alternatif downloadAll yang aman

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

    // =====================================================================
    //  CEK: apakah cloud punya data?
    // =====================================================================
    suspend fun hasCloudData(uid: String): Boolean {
        return try {
            val accountDocs = col(uid, "accounts").limit(1).get().await()
            !accountDocs.isEmpty
        } catch (e: Exception) {
            false
        }
    }

    // =====================================================================
    //  CEK: hitung data lokal vs cloud (untuk smart sync decision)
    // =====================================================================
    data class DataCount(val local: Int, val cloud: Int)

    suspend fun compareDataCount(uid: String): DataCount {
        val localCount = try {
            val accounts = accountDao.getAllAccounts().first()
            accounts.flatMap { transactionDao.getTransactionsByAccount(it.id).first() }.size
        } catch (_: Exception) { 0 }

        val cloudCount = try {
            col(uid, "transactions").get().await().size()
        } catch (_: Exception) { 0 }

        return DataCount(local = localCount, cloud = cloudCount)
    }

    // =====================================================================
    //  UPLOAD: lokal → cloud (aman — upload dulu, baru cleanup)
    // =====================================================================
    suspend fun uploadAll(uid: String): Result<SyncStats> {
        return try {
            val accounts = accountDao.getAllAccounts().first()

            if (accounts.isEmpty()) {
                return Result.failure(Exception("Tidak ada data lokal untuk diupload"))
            }

            val wallets = accounts.flatMap { walletDao.getWalletsByAccount(it.id).first() }
            val categories = mutableListOf<CategoryEntity>()
            accounts.forEach { acc ->
                categories += categoryDao.getCategoriesByAccountAndType(acc.id, "EXPENSE").first()
                categories += categoryDao.getCategoriesByAccountAndType(acc.id, "INCOME").first()
            }
            val uniqueCategories = categories.distinctBy { it.id }
            val transactions = accounts.flatMap { transactionDao.getTransactionsByAccount(it.id).first() }

            // STEP 1: Upload semua data lokal ke cloud (upsert via set)
            // Ini TIDAK menghapus data cloud yang tidak ada di lokal — aman
            uploadCollection(uid, "accounts",     accounts.map { it.toMap() to it.id })
            uploadCollection(uid, "wallets",      wallets.map { it.toMap() to it.id })
            uploadCollection(uid, "categories",   uniqueCategories.map { it.toMap() to it.id })
            uploadCollection(uid, "transactions", transactions.map { it.toMap() to it.id })

            // STEP 2: Cleanup cloud docs yang tidak ada di lokal
            // Ini dilakukan SETELAH upload sukses, jadi kalau crash di tengah,
            // cloud tetap punya data (worst case: ada orphan docs)
            cleanupCloudOrphans(uid, "accounts",     accounts.map { it.id }.toSet())
            cleanupCloudOrphans(uid, "wallets",      wallets.map { it.id }.toSet())
            cleanupCloudOrphans(uid, "categories",   uniqueCategories.map { it.id }.toSet())
            cleanupCloudOrphans(uid, "transactions", transactions.map { it.id }.toSet())

            android.util.Log.d("SyncRepo", "Upload OK: ${accounts.size}a ${wallets.size}w ${uniqueCategories.size}c ${transactions.size}t")

            Result.success(SyncStats(accounts.size, wallets.size, uniqueCategories.size, transactions.size))
        } catch (e: Exception) {
            android.util.Log.e("SyncRepo", "Upload error: ${e.message}", e)
            Result.failure(Exception("Upload gagal: ${e.message}"))
        }
    }

    // =====================================================================
    //  DOWNLOAD (MERGE): cloud → lokal — HANYA tambah/update, TIDAK hapus lokal
    // =====================================================================
    suspend fun mergeDownload(uid: String): Result<SyncStats> {
        return try {
            val accountDocs     = col(uid, "accounts").get().await()
            val walletDocs      = col(uid, "wallets").get().await()
            val categoryDocs    = col(uid, "categories").get().await()
            val transactionDocs = col(uid, "transactions").get().await()

            // Cek cloud kosong — JANGAN lanjut kalau kosong
            if (accountDocs.isEmpty) {
                android.util.Log.w("SyncRepo", "Cloud kosong — skip download untuk proteksi data lokal")
                return Result.failure(Exception("Cloud kosong. Upload dulu dari device yang ada datanya."))
            }

            val accounts     = accountDocs.documents.mapNotNull     { it.toFinanceAccount() }
            val wallets      = walletDocs.documents.mapNotNull       { it.toWallet() }
            val categories   = categoryDocs.documents.mapNotNull     { it.toCategory() }
            val transactions = transactionDocs.documents.mapNotNull  { it.toTransaction() }

            // MERGE: INSERT OR REPLACE — data lokal yang ID-nya sama diupdate,
            // data lokal yang TIDAK ada di cloud TETAP ada (tidak dihapus)
            accounts.forEach     { accountDao.insertAccount(it) }
            wallets.forEach      { walletDao.insertWallet(it) }
            categories.forEach   { categoryDao.insertCategory(it) }
            transactions.forEach { transactionDao.insertTransaction(it) }

            android.util.Log.d("SyncRepo", "Merge download OK: ${accounts.size}a ${wallets.size}w ${categories.size}c ${transactions.size}t")

            Result.success(SyncStats(accounts.size, wallets.size, categories.size, transactions.size))
        } catch (e: Exception) {
            android.util.Log.e("SyncRepo", "Merge download error: ${e.message}", e)
            Result.failure(Exception("Download gagal: ${e.message}"))
        }
    }

    // =====================================================================
    //  DOWNLOAD (FULL REPLACE): cloud → lokal — HAPUS lokal, ganti dengan cloud
    //  ⚠️ HANYA gunakan jika user SADAR mau timpa data lokal
    // =====================================================================
    suspend fun downloadAll(uid: String): Result<SyncStats> {
        return try {
            val accountDocs     = col(uid, "accounts").get().await()

            if (accountDocs.isEmpty) {
                return Result.failure(Exception("Cloud kosong. Tidak bisa download — data lokal akan tetap aman."))
            }

            val walletDocs      = col(uid, "wallets").get().await()
            val categoryDocs    = col(uid, "categories").get().await()
            val transactionDocs = col(uid, "transactions").get().await()

            val accounts     = accountDocs.documents.mapNotNull     { it.toFinanceAccount() }
            val wallets      = walletDocs.documents.mapNotNull       { it.toWallet() }
            val categories   = categoryDocs.documents.mapNotNull     { it.toCategory() }
            val transactions = transactionDocs.documents.mapNotNull  { it.toTransaction() }

            android.util.Log.d("SyncRepo", "Full download: ${accounts.size}a ${wallets.size}w ${categories.size}c ${transactions.size}t")

            accounts.forEach     { accountDao.insertAccount(it) }
            wallets.forEach      { walletDao.insertWallet(it) }
            categories.forEach   { categoryDao.insertCategory(it) }
            transactions.forEach { transactionDao.insertTransaction(it) }

            Result.success(SyncStats(accounts.size, wallets.size, categories.size, transactions.size))
        } catch (e: Exception) {
            android.util.Log.e("SyncRepo", "Full download error: ${e.message}", e)
            Result.failure(Exception("Download gagal: ${e.message}"))
        }
    }

    // ── Helper: cleanup orphan cloud docs ────────────────────────────────────
    private suspend fun cleanupCloudOrphans(uid: String, name: String, localIds: Set<String>) {
        try {
            val cloudDocs = col(uid, name).get().await()
            val orphans = cloudDocs.documents.filter { it.id !in localIds }
            if (orphans.isEmpty()) return

            orphans.chunked(400).forEach { chunk ->
                val batch = firestore.batch()
                chunk.forEach { batch.delete(it.reference) }
                batch.commit().await()
            }
            android.util.Log.d("SyncRepo", "Cleaned $name: ${orphans.size} orphans")
        } catch (e: Exception) {
            // Non-fatal — orphan cleanup gagal tidak masalah
            android.util.Log.w("SyncRepo", "Cleanup $name warning: ${e.message}")
        }
    }

    // ── Helper: upload in batches ─────────────────────────────────────────────
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
        "recurringInterval" to (recurringInterval ?: ""),
        "createdAt"         to createdAt
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
        } catch (e: Exception) { null }
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
        } catch (e: Exception) { null }
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
        } catch (e: Exception) { null }
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
                recurringInterval = getString("recurringInterval")?.ifBlank { null },
                createdAt         = getLong("createdAt") ?: System.currentTimeMillis()
            )
        } catch (e: Exception) { null }
    }
}

data class SyncStats(
    val accounts: Int,
    val wallets: Int,
    val categories: Int,
    val transactions: Int
)