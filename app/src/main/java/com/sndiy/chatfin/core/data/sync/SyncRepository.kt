// app/src/main/java/com/sndiy/chatfin/core/data/sync/SyncRepository.kt
//
// Bi-directional Non-Destructive Merge (Penggabungan Dua Arah Paralel & Cepat):
// 1. mergeDownload:
//    - Mengunduh 5 koleksi Firestore secara PARALEL (coroutineScope async) -> 1 roundtrip network.
//    - Cloud -> Local: Masukkan atau perbarui data dari cloud jika cloud lebih baru (updatedAt).
//    - Local -> Cloud: Unggah data lokal baru tanpa query ulang ke Firestore (menggunakan snapshot yang sudah diunduh).
//    - Conflict resolution berbasis ID (UUID) + timestamp updatedAt (Last-Write-Wins).
// 2. Computed balance:
//    - Hitung ulang saldo setiap wallet dari penjumlahan seluruh transaksi (INCOME - EXPENSE + TRANSFER).
// 3. Smart active account:
//    - Beralih ke akun cloud aktif jika akun lokal saat ini adalah akun baru/kosong tanpa transaksi.

package com.sndiy.chatfin.core.data.sync

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.QuerySnapshot
import com.sndiy.chatfin.core.data.local.dao.AccountDao
import com.sndiy.chatfin.core.data.local.dao.BudgetDao
import com.sndiy.chatfin.core.data.local.dao.CategoryDao
import com.sndiy.chatfin.core.data.local.dao.TransactionDao
import com.sndiy.chatfin.core.data.local.dao.WalletDao
import com.sndiy.chatfin.core.data.local.entity.BudgetEntity
import com.sndiy.chatfin.core.data.local.entity.CategoryEntity
import com.sndiy.chatfin.core.data.local.entity.FinanceAccountEntity
import com.sndiy.chatfin.core.data.local.entity.TransactionEntity
import com.sndiy.chatfin.core.data.local.entity.TransactionItemEntity
import com.sndiy.chatfin.core.data.local.entity.WalletEntity
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
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
    private val transactionDao: TransactionDao,
    private val budgetDao: BudgetDao
) {
    private fun col(uid: String, name: String) =
        firestore.collection("users").document(uid).collection(name)

    // =====================================================================
    //  CEK: apakah cloud punya data?
    // =====================================================================
    suspend fun hasCloudData(uid: String): Boolean {
        return try {
            val accountDocs = col(uid, "accounts").limit(1).get().await()
            if (!accountDocs.isEmpty) return true
            val txDocs = col(uid, "transactions").limit(1).get().await()
            if (!txDocs.isEmpty) return true
            val walletDocs = col(uid, "wallets").limit(1).get().await()
            !walletDocs.isEmpty
        } catch (e: Exception) {
            false
        }
    }

    // =====================================================================
    //  UPLOAD: lokal → cloud (dengan conflict resolution berbasis updatedAt)
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
            val budgets = accounts.flatMap { budgetDao.getBudgetsByAccount(it.id).first() }

            val txItemsMap = mutableMapOf<String, List<TransactionItemEntity>>()
            transactions.forEach { tx ->
                val withItems = transactionDao.getTransactionWithItemsById(tx.id)
                if (withItems != null && withItems.items.isNotEmpty()) {
                    txItemsMap[tx.id] = withItems.items
                }
            }

            // Upload paralel
            coroutineScope {
                val accJob = async { uploadItemsDirect(uid, "accounts", accounts.map { Triple(it.toMap(), it.id, it.updatedAt) }) }
                val wltJob = async { uploadItemsDirect(uid, "wallets", wallets.map { Triple(it.toMap(), it.id, it.updatedAt) }) }
                val catJob = async { uploadItemsDirect(uid, "categories", uniqueCategories.map { Triple(it.toMap(), it.id, it.updatedAt) }) }
                val txJob  = async {
                    uploadItemsDirect(
                        uid, "transactions",
                        transactions.map { tx ->
                            val map = tx.toMap(txItemsMap[tx.id] ?: emptyList())
                            Triple(map, tx.id, tx.updatedAt)
                        }
                    )
                }
                val budJob = async { uploadItemsDirect(uid, "budgets", budgets.map { Triple(it.toMap(), it.id, it.createdAt) }) }

                accJob.await(); wltJob.await(); catJob.await(); txJob.await(); budJob.await()
            }

            android.util.Log.d("SyncRepo", "Upload OK: ${accounts.size}a ${wallets.size}w ${uniqueCategories.size}c ${transactions.size}t")

            Result.success(SyncStats(accounts.size, wallets.size, uniqueCategories.size, transactions.size))
        } catch (e: Exception) {
            android.util.Log.e("SyncRepo", "Upload error: ${e.message}", e)
            Result.failure(Exception("Upload gagal: ${e.message}"))
        }
    }

    // =====================================================================
    //  DOWNLOAD & MERGE: Penggabungan Dua Arah Cepat & Paralel (Cloud <-> Local)
    // =====================================================================
    suspend fun mergeDownload(uid: String): Result<SyncStats> {
        return try {
            // Unduh semua koleksi cloud secara paralel
            val (accountDocs, walletDocs, categoryDocs, transactionDocs, budgetDocs) = coroutineScope {
                val accDeferred = async { try { col(uid, "accounts").get().await() } catch (_: Exception) { null } }
                val wltDeferred = async { try { col(uid, "wallets").get().await() } catch (_: Exception) { null } }
                val catDeferred = async { try { col(uid, "categories").get().await() } catch (_: Exception) { null } }
                val txDeferred  = async { try { col(uid, "transactions").get().await() } catch (_: Exception) { null } }
                val budDeferred = async { try { col(uid, "budgets").get().await() } catch (_: Exception) { null } }

                FetchResults(
                    accDeferred.await(),
                    wltDeferred.await(),
                    catDeferred.await(),
                    txDeferred.await(),
                    budDeferred.await()
                )
            }

            val cloudAccounts     = accountDocs?.documents?.mapNotNull     { it.toFinanceAccount() } ?: emptyList()
            val cloudWallets      = walletDocs?.documents?.mapNotNull       { it.toWallet() } ?: emptyList()
            val cloudCategories   = categoryDocs?.documents?.mapNotNull     { it.toCategory() } ?: emptyList()
            val cloudTransactions = transactionDocs?.documents?.mapNotNull  { it.toTransaction() } ?: emptyList()
            val cloudBudgets      = budgetDocs?.documents?.mapNotNull     { it.toBudget() } ?: emptyList()
            val cloudTxItems      = transactionDocs?.documents?.associate   { doc ->
                val docId = doc.getString("id") ?: doc.id
                docId to doc.toTransactionItems(docId)
            } ?: emptyMap()

            // ── STEP 1: MERGE ACCOUNTS ──────────────────────────────────────
            var mergedAccounts = 0
            val allLocalBefore = accountDao.getAllAccounts().first()
            cloudAccounts.forEach { cloud ->
                val local = accountDao.getAccountById(cloud.id)
                val shouldBeActive = local?.isActive ?: false
                if (local == null || cloud.updatedAt >= local.updatedAt) {
                    accountDao.insertAccount(cloud.copy(isActive = shouldBeActive))
                    mergedAccounts++
                }
            }

            // ── STEP 2: MERGE CATEGORIES ────────────────────────────────────
            var mergedCategories = 0
            cloudCategories.forEach { cloud ->
                val local = categoryDao.getCategoryById(cloud.id)
                if (local == null || cloud.updatedAt >= local.updatedAt) {
                    categoryDao.insertCategory(cloud)
                    mergedCategories++
                }
            }

            // ── STEP 3: MERGE WALLETS ───────────────────────────────────────
            var mergedWallets = 0
            cloudWallets.forEach { cloud ->
                val local = walletDao.getWalletById(cloud.id)
                if (local == null) {
                    walletDao.insertWallet(cloud)
                    mergedWallets++
                } else if (cloud.updatedAt >= local.updatedAt) {
                    walletDao.insertWallet(cloud.copy(balance = local.balance))
                    mergedWallets++
                }
            }

            // ── STEP 4: MERGE TRANSACTIONS ──────────────────────────────────
            var mergedTransactions = 0
            cloudTransactions.forEach { cloud ->
                val local = transactionDao.getTransactionById(cloud.id)
                if (local == null || cloud.updatedAt >= local.updatedAt) {
                    transactionDao.insertTransaction(cloud)
                    val items = cloudTxItems[cloud.id] ?: emptyList()
                    if (items.isNotEmpty()) {
                        transactionDao.deleteTransactionItemsByTransactionId(cloud.id)
                        transactionDao.insertTransactionItems(items)
                    }
                    mergedTransactions++
                }
            }

            // ── STEP 5: MERGE BUDGETS ───────────────────────────────────────
            cloudBudgets.forEach { cloud ->
                val local = budgetDao.getBudgetById(cloud.id)
                if (local == null || cloud.createdAt >= local.createdAt) {
                    budgetDao.insertBudget(cloud)
                }
            }

            // ── STEP 6: DEDUPLIKASI AKUN DAN REKONSILIASI ───────────────────
            deduplicateAndReconcileAccounts(uid)

            // ── STEP 7: TWO-WAY MERGE — UNGGAH DATA LOKAL YANG BELUM ADA DI CLOUD ──
            val allLocalAccounts = accountDao.getAllAccounts().first()
            val allLocalWallets = allLocalAccounts.flatMap { walletDao.getWalletsByAccount(it.id).first() }
            val allLocalCategories = mutableListOf<CategoryEntity>()
            allLocalAccounts.forEach { acc ->
                allLocalCategories += categoryDao.getCategoriesByAccountAndType(acc.id, "EXPENSE").first()
                allLocalCategories += categoryDao.getCategoriesByAccountAndType(acc.id, "INCOME").first()
            }
            val allLocalTransactions = allLocalAccounts.flatMap { transactionDao.getTransactionsByAccount(it.id).first() }
            val allLocalBudgets = allLocalAccounts.flatMap { budgetDao.getBudgetsByAccount(it.id).first() }

            val localTxItemsMap = mutableMapOf<String, List<TransactionItemEntity>>()
            allLocalTransactions.forEach { tx ->
                val withItems = transactionDao.getTransactionWithItemsById(tx.id)
                if (withItems != null && withItems.items.isNotEmpty()) {
                    localTxItemsMap[tx.id] = withItems.items
                }
            }

            // Map updatedAt dokumen cloud yang sudah ada
            val cloudAccMap = accountDocs?.documents?.associate { it.id to (it.getLong("updatedAt") ?: 0L) } ?: emptyMap()
            val cloudWltMap = walletDocs?.documents?.associate  { it.id to (it.getLong("updatedAt") ?: 0L) } ?: emptyMap()
            val cloudCatMap = categoryDocs?.documents?.associate{ it.id to (it.getLong("updatedAt") ?: 0L) } ?: emptyMap()
            val cloudTxMap  = transactionDocs?.documents?.associate { it.id to (it.getLong("updatedAt") ?: 0L) } ?: emptyMap()
            val cloudBudMap = budgetDocs?.documents?.associate  { it.id to (it.getLong("createdAt") ?: 0L) } ?: emptyMap()

            coroutineScope {
                val uAcc = async { uploadItemsWithKnownCloud(uid, "accounts", allLocalAccounts.map { Triple(it.toMap(), it.id, it.updatedAt) }, cloudAccMap) }
                val uWlt = async { uploadItemsWithKnownCloud(uid, "wallets", allLocalWallets.map { Triple(it.toMap(), it.id, it.updatedAt) }, cloudWltMap) }
                val uCat = async { uploadItemsWithKnownCloud(uid, "categories", allLocalCategories.distinctBy { it.id }.map { Triple(it.toMap(), it.id, it.updatedAt) }, cloudCatMap) }
                val uTx  = async {
                    uploadItemsWithKnownCloud(
                        uid, "transactions",
                        allLocalTransactions.map { tx ->
                            val map = tx.toMap(localTxItemsMap[tx.id] ?: emptyList())
                            Triple(map, tx.id, tx.updatedAt)
                        },
                        cloudTxMap
                    )
                }
                val uBud = async { uploadItemsWithKnownCloud(uid, "budgets", allLocalBudgets.map { Triple(it.toMap(), it.id, it.createdAt) }, cloudBudMap) }

                uAcc.await(); uWlt.await(); uCat.await(); uTx.await(); uBud.await()
            }

            // ── STEP 8: RECOMPUTE WALLET BALANCES SECARA MATEMATIS ──────────
            recomputeWalletBalances()

            android.util.Log.d("SyncRepo",
                "Merge download OK: ${mergedAccounts}a ${mergedWallets}w ${mergedCategories}c ${mergedTransactions}t")

            Result.success(SyncStats(mergedAccounts, mergedWallets, mergedCategories, mergedTransactions))
        } catch (e: Exception) {
            android.util.Log.e("SyncRepo", "Merge download error: ${e.message}", e)
            Result.failure(Exception("Download gagal: ${e.message}"))
        }
    }

    // =====================================================================
    //  DEDUPLIKASI AKUN: Bersihkan akun duplikat (misal 2 akun 'Utama')
    // =====================================================================
    private suspend fun deduplicateAndReconcileAccounts(uid: String) {
        try {
            val allAccounts = accountDao.getAllAccounts().first()
            if (allAccounts.size <= 1) {
                if (allAccounts.size == 1 && !allAccounts.first().isActive) {
                    accountDao.switchActiveAccount(allAccounts.first().id)
                }
                return
            }

            val groupsByName = allAccounts.groupBy { it.name.trim().lowercase() }
            for ((_, group) in groupsByName) {
                if (group.size > 1) {
                    val accountsWithStats = group.map { acc ->
                        val txs = transactionDao.getTransactionsByAccount(acc.id).first()
                        val wallets = walletDao.getWalletsByAccount(acc.id).first()
                        Triple(acc, txs, wallets)
                    }.sortedWith(
                        compareByDescending<Triple<FinanceAccountEntity, List<TransactionEntity>, List<WalletEntity>>> { it.second.size }
                            .thenByDescending { it.third.size }
                            .thenBy { it.first.createdAt }
                    )

                    val primary = accountsWithStats.first().first
                    val duplicates = accountsWithStats.drop(1)

                    duplicates.forEach { (dupAcc, dupTxs, dupWallets) ->
                        val primaryWallets = walletDao.getWalletsByAccount(primary.id).first()
                        val fallbackWallet = primaryWallets.firstOrNull()

                        if (dupTxs.isNotEmpty()) {
                            dupTxs.forEach { tx ->
                                val targetWalletId = if (primaryWallets.any { it.id == tx.walletId }) tx.walletId
                                                     else fallbackWallet?.id ?: tx.walletId
                                transactionDao.updateTransaction(tx.copy(accountId = primary.id, walletId = targetWalletId))
                            }
                        }

                        dupWallets.forEach { wlt ->
                            val hasTx = transactionDao.getTransactionsByAccount(primary.id).first().any { it.walletId == wlt.id }
                            if (hasTx) {
                                walletDao.updateWallet(wlt.copy(accountId = primary.id))
                            } else {
                                walletDao.deleteWallet(wlt)
                                try { col(uid, "wallets").document(wlt.id).delete() } catch (_: Exception) {}
                            }
                        }

                        accountDao.deleteAccount(dupAcc)
                        try {
                            col(uid, "accounts").document(dupAcc.id).delete()
                        } catch (_: Exception) {}
                    }

                    accountDao.switchActiveAccount(primary.id)
                }
            }

            val finalAccounts = accountDao.getAllAccounts().first()
            val activeAcc = finalAccounts.find { it.isActive }
            if (activeAcc == null && finalAccounts.isNotEmpty()) {
                val withData = finalAccounts.find { acc ->
                    transactionDao.getTransactionsByAccount(acc.id).first().isNotEmpty()
                } ?: finalAccounts.first()
                accountDao.switchActiveAccount(withData.id)
            }
        } catch (e: Exception) {
            android.util.Log.e("SyncRepo", "deduplicateAndReconcileAccounts error: ${e.message}", e)
        }
    }

    // =====================================================================
    //  RECOMPUTE WALLET BALANCES: hitung saldo dari seluruh transaksi
    // =====================================================================
    private suspend fun recomputeWalletBalances() {
        try {
            val allAccounts     = accountDao.getAllAccounts().first()
            val allWallets      = allAccounts.flatMap { walletDao.getWalletsByAccount(it.id).first() }
            val allTransactions = allAccounts.flatMap { transactionDao.getTransactionsByAccount(it.id).first() }

            val balanceMap  = mutableMapOf<String, Long>()
            val walletHasTx = mutableSetOf<String>()
            allWallets.forEach { balanceMap[it.id] = 0L }

            allTransactions.forEach { tx ->
                walletHasTx.add(tx.walletId)
                when (tx.type) {
                    "INCOME"   -> balanceMap[tx.walletId] = (balanceMap[tx.walletId] ?: 0L) + tx.amount
                    "EXPENSE"  -> balanceMap[tx.walletId] = (balanceMap[tx.walletId] ?: 0L) - tx.amount
                    "TRANSFER" -> {
                        balanceMap[tx.walletId] = (balanceMap[tx.walletId] ?: 0L) - tx.amount
                        tx.toWalletId?.let {
                            walletHasTx.add(it)
                            balanceMap[it] = (balanceMap[it] ?: 0L) + tx.amount
                        }
                    }
                }
            }

            allWallets.forEach { wallet ->
                val computed = if (wallet.id in walletHasTx) balanceMap[wallet.id] ?: 0L
                               else wallet.balance
                if (wallet.balance != computed) {
                    walletDao.updateWallet(wallet.copy(balance = computed, updatedAt = System.currentTimeMillis()))
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("SyncRepo", "recomputeWalletBalances: ${e.message}", e)
        }
    }

    // ── Helper: upload langsung dengan batch ──────────────────────────────────
    private suspend fun uploadItemsDirect(
        uid: String,
        name: String,
        items: List<Triple<Map<String, Any?>, String, Long>>
    ) {
        if (items.isEmpty()) return
        items.chunked(500).forEach { chunk ->
            val batch = firestore.batch()
            chunk.forEach { (map, id, _) ->
                batch.set(col(uid, name).document(id), map)
            }
            batch.commit().await()
        }
    }

    // ── Helper: upload hanya jika lokal lebih baru atau belum ada di cloud ────
    private suspend fun uploadItemsWithKnownCloud(
        uid: String,
        name: String,
        items: List<Triple<Map<String, Any?>, String, Long>>,
        cloudMap: Map<String, Long>
    ) {
        val toUpload = items.filter { (_, id, localUpdatedAt) ->
            val cloudUpdatedAt = cloudMap[id]
            cloudUpdatedAt == null || localUpdatedAt > cloudUpdatedAt
        }

        if (toUpload.isEmpty()) return

        toUpload.chunked(500).forEach { chunk ->
            val batch = firestore.batch()
            chunk.forEach { (map, id, _) ->
                batch.set(col(uid, name).document(id), map)
            }
            batch.commit().await()
        }
    }

    // ── Helper: safely read Long from Firestore ───────────────────────────────
    private fun DocumentSnapshot.getFirestoreLong(field: String): Long? {
        return try {
            getLong(field)
        } catch (e: Exception) {
            try {
                getDouble(field)?.toLong()
            } catch (e2: Exception) {
                null
            }
        }
    }

    // ── Entity → Map ──────────────────────────────────────────────────────────
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

    private fun BudgetEntity.toMap(): Map<String, Any?> = mapOf(
        "id"          to id,
        "accountId"   to accountId,
        "categoryId"  to categoryId,
        "limitAmount" to limitAmount,
        "period"      to period,
        "month"       to (month ?: 0),
        "year"        to year,
        "createdAt"   to createdAt
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

    // ── Map → Entity ──────────────────────────────────────────────────────────
    private fun DocumentSnapshot.toFinanceAccount(): FinanceAccountEntity? {
        return try {
            val createdAtVal = getLong("createdAt") ?: System.currentTimeMillis()
            FinanceAccountEntity(
                id                 = getString("id") ?: id,
                name               = getString("name") ?: return null,
                iconName           = getString("iconName") ?: "account_balance_wallet",
                colorHex           = getString("colorHex") ?: "#0061A4",
                currency           = getString("currency") ?: "IDR",
                description        = getString("description")?.ifBlank { null },
                isPinProtected     = getBoolean("isPinProtected") ?: false,
                pinHash            = getString("pinHash")?.ifBlank { null },
                isBiometricEnabled = getBoolean("isBiometricEnabled") ?: false,
                isActive           = getBoolean("isActive") ?: false,
                sortOrder          = getLong("sortOrder")?.toInt() ?: 0,
                createdAt          = createdAtVal,
                updatedAt          = getLong("updatedAt") ?: createdAtVal
            )
        } catch (e: Exception) {
            android.util.Log.w("SyncRepo", "Skip account doc ${id}: ${e.message}")
            null
        }
    }

    private fun DocumentSnapshot.toWallet(): WalletEntity? {
        return try {
            val createdAtVal = getLong("createdAt") ?: System.currentTimeMillis()
            WalletEntity(
                id            = getString("id") ?: id,
                accountId     = getString("accountId") ?: return null,
                name          = getString("name") ?: return null,
                type          = getString("type") ?: "CASH",
                balance       = getLong("balance") ?: 0L,
                currency      = getString("currency") ?: "IDR",
                bankName      = getString("bankName")?.ifBlank { null },
                accountNumber = getString("accountNumber")?.ifBlank { null },
                iconName      = getString("iconName") ?: "payments",
                colorHex      = getString("colorHex") ?: "#1B8A4C",
                isDefault     = getBoolean("isDefault") ?: false,
                sortOrder     = getLong("sortOrder")?.toInt() ?: 0,
                createdAt     = createdAtVal,
                updatedAt     = getLong("updatedAt") ?: createdAtVal
            )
        } catch (e: Exception) {
            android.util.Log.w("SyncRepo", "Skip wallet doc ${id}: ${e.message}")
            null
        }
    }

    private fun DocumentSnapshot.toCategory(): CategoryEntity? {
        return try {
            CategoryEntity(
                id        = getString("id") ?: id,
                accountId = getString("accountId")?.ifBlank { null },
                name      = nameOrBlank(getString("name")),
                type      = getString("type") ?: return null,
                iconName  = getString("iconName") ?: "category",
                colorHex  = getString("colorHex") ?: "#757575",
                isCustom  = getBoolean("isCustom") ?: true,
                sortOrder = getLong("sortOrder")?.toInt() ?: 0,
                updatedAt = getLong("updatedAt") ?: 0L
            )
        } catch (e: Exception) {
            android.util.Log.w("SyncRepo", "Skip category doc ${id}: ${e.message}")
            null
        }
    }

    private fun nameOrBlank(name: String?): String {
        return if (name.isNullOrBlank()) "Lainnya" else name
    }

    private fun DocumentSnapshot.toBudget(): BudgetEntity? {
        return try {
            val accountId   = getString("accountId") ?: return null
            val categoryId  = getString("categoryId") ?: return null
            val limitAmount = getFirestoreLong("limitAmount") ?: return null
            val year        = getLong("year")?.toInt() ?: return null
            val month       = getLong("month")?.toInt()
            BudgetEntity(
                id          = getString("id") ?: id,
                accountId   = accountId,
                categoryId  = categoryId,
                limitAmount = limitAmount,
                period      = getString("period") ?: "MONTHLY",
                month       = if (month == 0) null else month,
                year        = year,
                createdAt   = getLong("createdAt") ?: System.currentTimeMillis()
            )
        } catch (e: Exception) {
            android.util.Log.w("SyncRepo", "Skip budget doc ${id}: ${e.message}")
            null
        }
    }

    private fun DocumentSnapshot.toTransaction(): TransactionEntity? {
        return try {
            val createdAtVal = getLong("createdAt") ?: System.currentTimeMillis()
            TransactionEntity(
                id                = getString("id") ?: id,
                accountId         = getString("accountId") ?: return null,
                type              = getString("type") ?: return null,
                amount            = getFirestoreLong("amount") ?: return null,
                categoryId        = getString("categoryId") ?: return null,
                walletId          = getString("walletId") ?: return null,
                toWalletId        = getString("toWalletId")?.ifBlank { null },
                note              = getString("note")?.ifBlank { null },
                receiptImageUri   = getString("receiptImageUri")?.ifBlank { null },
                date              = getString("date") ?: return null,
                time              = getString("time") ?: return null,
                transferPairId    = getString("transferPairId")?.ifBlank { null },
                isInitialBalance  = getBoolean("isInitialBalance") ?: false,
                createdAt         = createdAtVal,
                updatedAt         = getLong("updatedAt") ?: createdAtVal
            )
        } catch (e: Exception) {
            android.util.Log.w("SyncRepo", "Skip transaction doc ${id}: ${e.message}")
            null
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun DocumentSnapshot.toTransactionItems(
        transactionId: String
    ): List<TransactionItemEntity> {
        return try {
            val itemsList = get("items") as? List<Map<String, Any?>> ?: return emptyList()
            itemsList.mapNotNull { map ->
                try {
                    TransactionItemEntity(
                        id            = map["id"] as? String ?: return@mapNotNull null,
                        transactionId = transactionId,
                        name          = map["name"] as? String ?: return@mapNotNull null,
                        price         = (map["price"] as? Long)
                            ?: (map["price"] as? Double)?.toLong()
                            ?: return@mapNotNull null,
                        quantity      = (map["quantity"] as? Long)?.toInt()
                            ?: (map["quantity"] as? Double)?.toInt()
                            ?: 1
                    )
                } catch (e: Exception) {
                    android.util.Log.w("SyncRepo", "Skip transaction item: ${e.message}")
                    null
                }
            }
        } catch (e: Exception) {
            android.util.Log.w("SyncRepo", "Parse items error for $transactionId: ${e.message}")
            emptyList()
        }
    }
}

private data class FetchResults(
    val accounts: QuerySnapshot?,
    val wallets: QuerySnapshot?,
    val categories: QuerySnapshot?,
    val transactions: QuerySnapshot?,
    val budgets: QuerySnapshot?
)

data class SyncStats(
    val accounts: Int,
    val wallets: Int,
    val categories: Int,
    val transactions: Int
)