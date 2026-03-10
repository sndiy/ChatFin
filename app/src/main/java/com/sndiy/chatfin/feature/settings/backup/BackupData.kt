package com.sndiy.chatfin.feature.settings.backup

import com.sndiy.chatfin.core.data.local.entity.CategoryEntity
import com.sndiy.chatfin.core.data.local.entity.FinanceAccountEntity
import com.sndiy.chatfin.core.data.local.entity.TransactionEntity
import com.sndiy.chatfin.core.data.local.entity.WalletEntity

data class BackupData(
    val version: Int                        = 1,
    val exportedAt: String                  = "",
    val accounts: List<FinanceAccountEntity>    = emptyList(),
    val wallets: List<WalletEntity>             = emptyList(),
    val categories: List<CategoryEntity>        = emptyList(),
    val transactions: List<TransactionEntity>   = emptyList()
)