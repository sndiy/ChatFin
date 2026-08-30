package com.sndiy.chatfin.core.data.sync

/**
 * Model statistik audit hasil sinkronisasi dan rekonsiliasi data.
 */
data class SyncStats(
    val downloadedAccounts: Int = 0,
    val downloadedWallets: Int = 0,
    val downloadedCategories: Int = 0,
    val downloadedTransactions: Int = 0,
    val downloadedBudgets: Int = 0,
    val uploadedAccounts: Int = 0,
    val uploadedWallets: Int = 0,
    val uploadedCategories: Int = 0,
    val uploadedTransactions: Int = 0,
    val uploadedBudgets: Int = 0,
    val skippedCorruptedRecords: Int = 0,
    val reconciledWallets: Int = 0
) {
    val totalDownloaded: Int get() = downloadedAccounts + downloadedWallets + downloadedCategories + downloadedTransactions + downloadedBudgets
    val totalUploaded: Int get() = uploadedAccounts + uploadedWallets + uploadedCategories + uploadedTransactions + uploadedBudgets
    val hasChanges: Boolean get() = totalDownloaded > 0 || totalUploaded > 0 || reconciledWallets > 0
}
