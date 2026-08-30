package com.sndiy.chatfin.core.data.sync

import com.sndiy.chatfin.core.data.local.ChatFinDatabase
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
import com.sndiy.chatfin.core.data.local.withTransaction
import dev.gitlive.firebase.firestore.DocumentSnapshot
import dev.gitlive.firebase.firestore.FirebaseFirestore
import dev.gitlive.firebase.firestore.QuerySnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Repository rekonsiliasi dua arah (Two-Way Non-Destructive Merge) untuk Desktop.
 *
 * Menghandle:
 * 1. Unduh 5 koleksi Firestore paralel saat first activation.
 * 2. Cloud -> Local merge dengan conflict resolution Last-Write-Wins (updatedAt).
 * 3. Local -> Cloud upload untuk data pra-sync lokal yang belum ada di cloud.
 * 4. Strict Long verification: dokumen dengan pecahan rupiah ditolak dan dicatat ke skippedCorruptedRecords.
 * 5. Rekonsiliasi saldo dompet atomik via recomputeWalletBalances().
 */
class DesktopSyncRepository(
    private val firestore: FirebaseFirestore,
    private val db: ChatFinDatabase,
    private val accountDao: AccountDao,
    private val walletDao: WalletDao,
    private val categoryDao: CategoryDao,
    private val transactionDao: TransactionDao,
    private val budgetDao: BudgetDao
) {
    private fun col(uid: String, name: String) =
        firestore.collection("users").document(uid).collection(name)

    suspend fun twoWayMerge(uid: String): Result<SyncStats> = withContext(Dispatchers.IO) {
        try {
            // ── FASE 1: Unduh 5 Koleksi Cloud secara Paralel ─────────────────────
            val (accountDocs, walletDocs, categoryDocs, transactionDocs, budgetDocs) = coroutineScope {
                val accDeferred = async { try { col(uid, "accounts").get() } catch (_: Exception) { null } }
                val wltDeferred = async { try { col(uid, "wallets").get() } catch (_: Exception) { null } }
                val catDeferred = async { try { col(uid, "categories").get() } catch (_: Exception) { null } }
                val txDeferred  = async { try { col(uid, "transactions").get() } catch (_: Exception) { null } }
                val budDeferred = async { try { col(uid, "budgets").get() } catch (_: Exception) { null } }

                FetchResults(
                    accDeferred.await(),
                    wltDeferred.await(),
                    catDeferred.await(),
                    txDeferred.await(),
                    budDeferred.await()
                )
            }

            var skippedCorrupted = 0

            // ── Parse Cloud Entities dengan Strict Validation ────────────────────
            val cloudAccounts = mutableListOf<FinanceAccountEntity>()
            accountDocs?.documents?.forEach { doc ->
                val acc = doc.toFinanceAccount()
                if (acc != null) cloudAccounts.add(acc) else skippedCorrupted++
            }

            val cloudWallets = mutableListOf<WalletEntity>()
            walletDocs?.documents?.forEach { doc ->
                val w = doc.toWallet()
                if (w != null) cloudWallets.add(w) else skippedCorrupted++
            }

            val cloudCategories = mutableListOf<CategoryEntity>()
            categoryDocs?.documents?.forEach { doc ->
                val c = doc.toCategory()
                if (c != null) cloudCategories.add(c) else skippedCorrupted++
            }

            val cloudTransactions = mutableListOf<TransactionEntity>()
            val cloudTxItems = mutableMapOf<String, List<TransactionItemEntity>>()
            transactionDocs?.documents?.forEach { doc ->
                val tx = doc.toTransaction()
                if (tx != null) {
                    cloudTransactions.add(tx)
                    val items = doc.toTransactionItems(tx.id)
                    if (items.isNotEmpty()) cloudTxItems[tx.id] = items
                } else {
                    skippedCorrupted++
                }
            }

            val cloudBudgets = mutableListOf<BudgetEntity>()
            budgetDocs?.documents?.forEach { doc ->
                val b = doc.toBudget()
                if (b != null) cloudBudgets.add(b) else skippedCorrupted++
            }

            var downloadedAccounts = 0
            var downloadedWallets = 0
            var downloadedCategories = 0
            var downloadedTransactions = 0
            var downloadedBudgets = 0

            // ── FASE 2: Cloud -> Local (Insert / Update LWW) ─────────────────────
            db.withTransaction {
                // 1. Accounts
                cloudAccounts.forEach { cloud ->
                    val local = accountDao.getAccountById(cloud.id)
                    val shouldBeActive = local?.isActive ?: false
                    if (local == null || cloud.updatedAt >= local.updatedAt) {
                        accountDao.insertAccount(cloud.copy(isActive = shouldBeActive))
                        downloadedAccounts++
                    }
                }

                // 2. Categories
                cloudCategories.forEach { cloud ->
                    val local = categoryDao.getCategoryById(cloud.id)
                    if (local == null || cloud.updatedAt >= local.updatedAt) {
                        categoryDao.insertCategory(cloud)
                        downloadedCategories++
                    }
                }

                // 3. Wallets
                cloudWallets.forEach { cloud ->
                    val local = walletDao.getWalletById(cloud.id)
                    if (local == null) {
                        walletDao.insertWallet(cloud)
                        downloadedWallets++
                    } else if (cloud.updatedAt > local.updatedAt) {
                        // Pertahankan saldo lokal, hanya perbarui metadata
                        walletDao.insertWallet(cloud.copy(balance = local.balance))
                        downloadedWallets++
                    }
                }

                // 4. Transactions + Items
                cloudTransactions.forEach { cloud ->
                    val local = transactionDao.getTransactionById(cloud.id)
                    if (local == null || cloud.updatedAt >= local.updatedAt) {
                        transactionDao.insertTransaction(cloud)
                        transactionDao.deleteTransactionItemsByTransactionId(cloud.id)
                        val items = cloudTxItems[cloud.id] ?: emptyList()
                        if (items.isNotEmpty()) transactionDao.insertTransactionItems(items)
                        downloadedTransactions++
                    }
                }

                // 5. Budgets
                cloudBudgets.forEach { cloud ->
                    val local = budgetDao.getBudgetById(cloud.id)
                    if (local == null || cloud.createdAt >= local.createdAt) {
                        budgetDao.insertBudget(cloud)
                        downloadedBudgets++
                    }
                }
            }

            // ── FASE 3: Local -> Cloud (Upload data lokal pra-sync yang baru) ────
            val localAccounts = accountDao.getAllAccounts().first()
            val localWallets = localAccounts.flatMap { walletDao.getWalletsByAccount(it.id).first() }
            val localCategories = mutableListOf<CategoryEntity>()
            localAccounts.forEach { acc ->
                localCategories += categoryDao.getCategoriesByAccountAndType(acc.id, "EXPENSE").first()
                localCategories += categoryDao.getCategoriesByAccountAndType(acc.id, "INCOME").first()
            }
            val uniqueLocalCategories = localCategories.distinctBy { it.id }
            val localTransactions = localAccounts.flatMap { transactionDao.getTransactionsByAccount(it.id).first() }
            val localBudgets = localAccounts.flatMap { budgetDao.getBudgetsByAccount(it.id).first() }

            val cloudAccMap = cloudAccounts.associateBy { it.id }
            val cloudWltMap = cloudWallets.associateBy { it.id }
            val cloudCatMap = cloudCategories.associateBy { it.id }
            val cloudTxMap  = cloudTransactions.associateBy { it.id }
            val cloudBudMap = cloudBudgets.associateBy { it.id }

            var uploadedAccounts = 0
            var uploadedWallets = 0
            var uploadedCategories = 0
            var uploadedTransactions = 0
            var uploadedBudgets = 0

            coroutineScope {
                // Upload Accounts
                localAccounts.forEach { local ->
                    val cloud = cloudAccMap[local.id]
                    if (cloud == null || local.updatedAt > cloud.updatedAt) {
                        col(uid, "accounts").document(local.id).set(local.toMap())
                        uploadedAccounts++
                    }
                }

                // Upload Categories
                uniqueLocalCategories.forEach { local ->
                    val cloud = cloudCatMap[local.id]
                    if (cloud == null || local.updatedAt > cloud.updatedAt) {
                        col(uid, "categories").document(local.id).set(local.toMap())
                        uploadedCategories++
                    }
                }

                // Upload Wallets
                localWallets.forEach { local ->
                    val cloud = cloudWltMap[local.id]
                    if (cloud == null || local.updatedAt > cloud.updatedAt) {
                        col(uid, "wallets").document(local.id).set(local.toMap())
                        uploadedWallets++
                    }
                }

                // Upload Transactions
                localTransactions.forEach { local ->
                    val cloud = cloudTxMap[local.id]
                    if (cloud == null || local.updatedAt > cloud.updatedAt) {
                        val withItems = transactionDao.getTransactionWithItemsById(local.id)
                        val items = withItems?.items ?: emptyList()
                        col(uid, "transactions").document(local.id).set(local.toMap(items))
                        uploadedTransactions++
                    }
                }

                // Upload Budgets
                localBudgets.forEach { local ->
                    val cloud = cloudBudMap[local.id]
                    if (cloud == null || local.createdAt > cloud.createdAt) {
                        col(uid, "budgets").document(local.id).set(local.toMap())
                        uploadedBudgets++
                    }
                }
            }

            // ── FASE 4: Deduplikasi Akun & Rekonsiliasi ─────────────────────────
            deduplicateAndReconcileAccounts(uid)

            // ── FASE 5: Atomic Balance Recomputation ─────────────────────────────
            val reconciledCount = recomputeWalletBalances()

            // ── FASE 6: Smart Active Account Selection ───────────────────────────
            syncSmartActiveAccount()

            val stats = SyncStats(
                downloadedAccounts = downloadedAccounts,
                downloadedWallets = downloadedWallets,
                downloadedCategories = downloadedCategories,
                downloadedTransactions = downloadedTransactions,
                downloadedBudgets = downloadedBudgets,
                uploadedAccounts = uploadedAccounts,
                uploadedWallets = uploadedWallets,
                uploadedCategories = uploadedCategories,
                uploadedTransactions = uploadedTransactions,
                uploadedBudgets = uploadedBudgets,
                skippedCorruptedRecords = skippedCorrupted,
                reconciledWallets = reconciledCount
            )

            // Log Audit (Tanpa data finansial sensitif sesuai AGENTS.md Bagian 2.8)
            println(
                "[DesktopSyncRepository] Two-Way Merge Completed: " +
                "Downloaded=${stats.totalDownloaded}, Uploaded=${stats.totalUploaded}, " +
                "SkippedCorrupted=${stats.skippedCorruptedRecords}, ReconciledWallets=${stats.reconciledWallets}"
            )

            Result.success(stats)
        } catch (e: Exception) {
            println("[DesktopSyncRepository] Two-Way Merge Error: ${e.message}")
            Result.failure(e)
        }
    }

    private suspend fun recomputeWalletBalances(): Int {
        var reconciled = 0
        try {
            val allAccounts = db.accountDao().getAllAccounts().first()
            val allWallets = allAccounts.flatMap { walletDao.getWalletsByAccount(it.id).first() }
            val allTransactions = allAccounts.flatMap { transactionDao.getTransactionsByAccount(it.id).first() }

            val balanceMap = mutableMapOf<String, Long>()
            val walletHasTx = mutableSetOf<String>()
            allWallets.forEach { balanceMap[it.id] = 0L }

            allTransactions.forEach { tx ->
                walletHasTx.add(tx.walletId)
                when (tx.type) {
                    "INCOME" -> balanceMap[tx.walletId] = (balanceMap[tx.walletId] ?: 0L) + tx.amount
                    "EXPENSE" -> balanceMap[tx.walletId] = (balanceMap[tx.walletId] ?: 0L) - tx.amount
                    "TRANSFER" -> {
                        balanceMap[tx.walletId] = (balanceMap[tx.walletId] ?: 0L) - tx.amount
                        tx.toWalletId?.let {
                            walletHasTx.add(it)
                            balanceMap[it] = (balanceMap[it] ?: 0L) + tx.amount
                        }
                    }
                }
            }

            db.withTransaction {
                allWallets.forEach { wallet ->
                    val computed = if (wallet.id in walletHasTx) balanceMap[wallet.id] ?: 0L
                    else wallet.balance
                    if (wallet.balance != computed) {
                        walletDao.updateWallet(wallet.copy(balance = computed, updatedAt = System.currentTimeMillis()))
                        reconciled++
                    }
                }
            }
        } catch (e: Exception) {
            println("[DesktopSyncRepository] recomputeWalletBalances error: ${e.message}")
        }
        return reconciled
    }

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
            println("[DesktopSyncRepository] deduplicateAndReconcileAccounts error: ${e.message}")
        }
    }

    private suspend fun syncSmartActiveAccount() {
        try {
            val accounts = accountDao.getAllAccounts().first()
            val activeAcc = accounts.find { it.isActive }
            val hasDataInActive = activeAcc != null && transactionDao.getTransactionsByAccount(activeAcc.id).first().isNotEmpty()

            if (!hasDataInActive && accounts.isNotEmpty()) {
                // Pilih akun yang memiliki transaksi terbanyak
                val best = accounts.maxByOrNull { acc ->
                    transactionDao.getTransactionsByAccount(acc.id).first().size
                } ?: accounts.first()

                if (best.id != activeAcc?.id) {
                    println("[DesktopSyncRepository] Mengaktifkan akun '${best.name}' (${best.id}) dengan data terbanyak.")
                    accountDao.switchActiveAccount(best.id)
                }
            }
        } catch (_: Exception) {}
    }

    // ── Mapping Helpers ──────────────────────────────────────────────────────

    private fun DocumentSnapshot.toFinanceAccount(): FinanceAccountEntity? {
        return try {
            val name = strictString("name") ?: return null
            val createdAtVal = firestoreStrictLong("createdAt") ?: System.currentTimeMillis()
            FinanceAccountEntity(
                id = strictString("id") ?: id,
                name = name,
                iconName = strictString("iconName") ?: "account_balance_wallet",
                colorHex = strictString("colorHex") ?: "#0061A4",
                currency = strictString("currency") ?: "IDR",
                description = strictString("description")?.ifBlank { null },
                isPinProtected = strictBoolean("isPinProtected"),
                pinHash = strictString("pinHash")?.ifBlank { null },
                isBiometricEnabled = strictBoolean("isBiometricEnabled"),
                isActive = strictBoolean("isActive"),
                sortOrder = strictInt("sortOrder"),
                createdAt = createdAtVal,
                updatedAt = firestoreStrictLong("updatedAt") ?: createdAtVal
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun DocumentSnapshot.toWallet(): WalletEntity? {
        return try {
            val accountId = strictString("accountId") ?: return null
            val name = strictString("name") ?: return null
            val balance = firestoreStrictLong("balance") ?: return null
            val createdAtVal = firestoreStrictLong("createdAt") ?: System.currentTimeMillis()
            WalletEntity(
                id = strictString("id") ?: id,
                accountId = accountId,
                name = name,
                type = strictString("type") ?: "CASH",
                balance = balance,
                currency = strictString("currency") ?: "IDR",
                bankName = strictString("bankName")?.ifBlank { null },
                accountNumber = strictString("accountNumber")?.ifBlank { null },
                iconName = strictString("iconName") ?: "payments",
                colorHex = strictString("colorHex") ?: "#1B8A4C",
                isDefault = strictBoolean("isDefault"),
                sortOrder = strictInt("sortOrder"),
                createdAt = createdAtVal,
                updatedAt = firestoreStrictLong("updatedAt") ?: createdAtVal
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun DocumentSnapshot.toCategory(): CategoryEntity? {
        return try {
            val type = strictString("type") ?: return null
            val name = strictString("name")
            CategoryEntity(
                id = strictString("id") ?: id,
                accountId = strictString("accountId")?.ifBlank { null },
                name = if (name.isNullOrBlank()) "Lainnya" else name,
                type = type,
                iconName = strictString("iconName") ?: "category",
                colorHex = strictString("colorHex") ?: "#757575",
                isCustom = strictBoolean("isCustom", true),
                sortOrder = strictInt("sortOrder"),
                updatedAt = firestoreStrictLong("updatedAt") ?: 0L
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun DocumentSnapshot.toBudget(): BudgetEntity? {
        return try {
            val accountId = strictString("accountId") ?: return null
            val categoryId = strictString("categoryId") ?: return null
            val limitAmount = firestoreStrictLong("limitAmount") ?: return null
            val year = strictInt("year", 0)
            if (year <= 0) return null
            val month = strictInt("month", 0)
            BudgetEntity(
                id = strictString("id") ?: id,
                accountId = accountId,
                categoryId = categoryId,
                limitAmount = limitAmount,
                period = strictString("period") ?: "MONTHLY",
                month = if (month == 0) null else month,
                year = year,
                createdAt = firestoreStrictLong("createdAt") ?: System.currentTimeMillis()
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun DocumentSnapshot.toTransaction(): TransactionEntity? {
        return try {
            val accountId = strictString("accountId") ?: return null
            val type = strictString("type") ?: return null
            val amount = firestoreStrictLong("amount") ?: return null
            val categoryId = strictString("categoryId") ?: return null
            val walletId = strictString("walletId") ?: return null
            val date = strictString("date") ?: return null
            val time = strictString("time") ?: return null
            val createdAtVal = firestoreStrictLong("createdAt") ?: System.currentTimeMillis()
            TransactionEntity(
                id = strictString("id") ?: id,
                accountId = accountId,
                type = type,
                amount = amount,
                categoryId = categoryId,
                walletId = walletId,
                toWalletId = strictString("toWalletId")?.ifBlank { null },
                note = strictString("note")?.ifBlank { null },
                receiptImageUri = strictString("receiptImageUri")?.ifBlank { null },
                date = date,
                time = time,
                transferPairId = strictString("transferPairId")?.ifBlank { null },
                isInitialBalance = strictBoolean("isInitialBalance"),
                createdAt = createdAtVal,
                updatedAt = firestoreStrictLong("updatedAt") ?: createdAtVal
            )
        } catch (_: Exception) {
            null
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun DocumentSnapshot.toTransactionItems(transactionId: String): List<TransactionItemEntity> {
        return try {
            val itemsList: List<Map<String, Any?>>? = get("items")
            if (itemsList == null) return emptyList()
            itemsList.mapNotNull { map ->
                try {
                    val rawPrice: Number? = map["price"] as? Number
                    val priceVal = rawPrice?.toDouble()
                    val validPrice = if (priceVal != null && priceVal % 1.0 == 0.0) priceVal.toLong() else null ?: return@mapNotNull null
                    TransactionItemEntity(
                        id = map["id"] as? String ?: return@mapNotNull null,
                        transactionId = transactionId,
                        name = map["name"] as? String ?: return@mapNotNull null,
                        price = validPrice,
                        quantity = (map["quantity"] as? Number)?.toInt() ?: 1
                    )
                } catch (_: Exception) {
                    null
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun FinanceAccountEntity.toMap(): Map<String, Any?> = mapOf(
        "id" to id,
        "name" to name,
        "iconName" to iconName,
        "colorHex" to colorHex,
        "currency" to currency,
        "description" to (description ?: ""),
        "isPinProtected" to isPinProtected,
        "pinHash" to (pinHash ?: ""),
        "isBiometricEnabled" to isBiometricEnabled,
        "isActive" to isActive,
        "sortOrder" to sortOrder,
        "createdAt" to createdAt,
        "updatedAt" to updatedAt
    )

    private fun WalletEntity.toMap(): Map<String, Any?> = mapOf(
        "id" to id,
        "accountId" to accountId,
        "name" to name,
        "type" to type,
        "balance" to balance,
        "currency" to currency,
        "bankName" to (bankName ?: ""),
        "accountNumber" to (accountNumber ?: ""),
        "iconName" to iconName,
        "colorHex" to colorHex,
        "isDefault" to isDefault,
        "sortOrder" to sortOrder,
        "createdAt" to createdAt,
        "updatedAt" to updatedAt
    )

    private fun CategoryEntity.toMap(): Map<String, Any?> = mapOf(
        "id" to id,
        "accountId" to (accountId ?: ""),
        "name" to name,
        "type" to type,
        "iconName" to iconName,
        "colorHex" to colorHex,
        "isCustom" to isCustom,
        "sortOrder" to sortOrder,
        "updatedAt" to updatedAt
    )

    private fun BudgetEntity.toMap(): Map<String, Any?> = mapOf(
        "id" to id,
        "accountId" to accountId,
        "categoryId" to categoryId,
        "limitAmount" to limitAmount,
        "period" to period,
        "month" to (month ?: 0),
        "year" to year,
        "createdAt" to createdAt
    )

    private fun TransactionEntity.toMap(
        items: List<TransactionItemEntity> = emptyList()
    ): Map<String, Any?> = mapOf(
        "id" to id,
        "accountId" to accountId,
        "type" to type,
        "amount" to amount,
        "categoryId" to categoryId,
        "walletId" to walletId,
        "toWalletId" to (toWalletId ?: ""),
        "note" to (note ?: ""),
        "receiptImageUri" to (receiptImageUri ?: ""),
        "date" to date,
        "time" to time,
        "transferPairId" to (transferPairId ?: ""),
        "isInitialBalance" to isInitialBalance,
        "createdAt" to createdAt,
        "updatedAt" to updatedAt,
        "items" to items.map { item ->
            mapOf(
                "id" to item.id,
                "name" to item.name,
                "price" to item.price,
                "quantity" to item.quantity
            )
        }
    )

    private data class FetchResults(
        val accounts: QuerySnapshot?,
        val wallets: QuerySnapshot?,
        val categories: QuerySnapshot?,
        val transactions: QuerySnapshot?,
        val budgets: QuerySnapshot?
    )
}
