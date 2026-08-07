package com.sndiy.chatfin.feature.chat.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sndiy.chatfin.R
import com.sndiy.chatfin.core.data.local.entity.CategoryEntity
import com.sndiy.chatfin.core.data.local.entity.TransactionEntity
import com.sndiy.chatfin.core.data.local.entity.WalletEntity
import com.sndiy.chatfin.core.ui.theme.ExpenseRed
import com.sndiy.chatfin.core.ui.theme.IncomeGreen
import com.sndiy.chatfin.core.ui.theme.MaiPurple
import java.text.NumberFormat
import java.util.Locale

/** Maks item ditampilkan langsung di kartu chat — sisanya lewat "Lihat semua". */
private const val MAX_VISIBLE_ITEMS = 15

/**
 * Kartu daftar transaksi periode tertentu di dalam chat. Data difilter dari
 * [transactions] yang sudah ada di [ChatUiState] (sama seperti InteractiveChartCard/
 * InteractiveTableCard) — tidak ada query DB baru. Tanpa elevation/shadow,
 * mengikuti pola TransactionItem yang sudah ada di TransactionListScreen.
 */
@Composable
internal fun TransactionListCard(
    periodLabel: String,
    startDate: String,
    endDate: String,
    limit: Int?,
    categoryName: String?,
    walletName: String?,
    type: String?,
    transactions: List<TransactionEntity>,
    categories: List<CategoryEntity>,
    wallets: List<WalletEntity>,
    onEditQuick: (TransactionEntity) -> Unit,
    onDeleteRequest: (TransactionEntity) -> Unit
) {
    // "transaksi terakhir" membawa limit-nya sendiri; permintaan berbasis
    // periode tidak, jadi jatuh ke batas tampilan default kartu.
    val visibleCount = limit ?: MAX_VISIBLE_ITEMS

    // Nama diterjemahkan ke id di sini. Nama yang tidak cocok dengan data akun
    // (mis. AI salah eja) sengaja jatuh ke "tanpa filter", bukan ke hasil
    // kosong — daftar penuh masih berguna, layar kosong tanpa penjelasan tidak.
    val categoryIds = categoryName
        ?.let { name -> categories.filter { it.name.equals(name, true) }.map { it.id }.toSet() }
        ?.takeIf { it.isNotEmpty() }
    val walletIds = walletName
        ?.let { name -> wallets.filter { it.name.equals(name, true) }.map { it.id }.toSet() }
        ?.takeIf { it.isNotEmpty() }
    val typeFilter = type?.takeIf { it == "INCOME" || it == "EXPENSE" }

    val activeFilters = listOfNotNull(
        categoryIds?.let { categoryName },
        walletIds?.let { walletName },
        when (typeFilter) {
            "INCOME"  -> "Pemasukan"
            "EXPENSE" -> "Pengeluaran"
            else      -> null
        }
    )

    val filtered = transactions
        .filter { it.date in startDate..endDate }
        .filter { categoryIds == null || it.categoryId in categoryIds }
        .filter { walletIds == null || it.walletId in walletIds }
        .filter { typeFilter == null || it.type == typeFilter }
        .sortedWith(compareByDescending<TransactionEntity> { it.date }.thenByDescending { it.time })

    Card(
        modifier = Modifier.widthIn(max = 320.dp),
        border   = BorderStroke(1.dp, MaiPurple.copy(alpha = 0.3f)),
        colors   = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Receipt, null, tint = MaiPurple, modifier = Modifier.size(18.dp))
                Text(
                    stringResource(R.string.chat_tx_card_title, periodLabel),
                    style      = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier   = Modifier.weight(1f)
                )
                Text(
                    "${filtered.size}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (activeFilters.isNotEmpty()) {
                Text(
                    stringResource(R.string.chat_tx_card_filter, activeFilters.joinToString(" · ")),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaiPurple
                )
            }
            HorizontalDivider(color = MaiPurple.copy(alpha = 0.15f))

            if (filtered.isEmpty()) {
                Row(
                    modifier              = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.EventBusy, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                    Text(
                        stringResource(R.string.chat_tx_card_empty),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    filtered.take(visibleCount).forEach { tx ->
                        MiniTransactionRow(
                            transaction     = tx,
                            category        = categories.find { it.id == tx.categoryId },
                            walletName      = wallets.find { it.id == tx.walletId }?.name ?: "—",
                            onEditQuick     = { onEditQuick(tx) },
                            onDeleteRequest = { onDeleteRequest(tx) }
                        )
                    }
                }
                if (filtered.size > visibleCount) {
                    HorizontalDivider(color = MaiPurple.copy(alpha = 0.15f))
                    Text(
                        stringResource(R.string.chat_tx_card_more, filtered.size - visibleCount),
                        style    = MaterialTheme.typography.labelSmall,
                        color    = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun MiniTransactionRow(
    transaction: TransactionEntity,
    category: CategoryEntity?,
    walletName: String,
    onEditQuick: () -> Unit,
    onDeleteRequest: () -> Unit
) {
    val fmt = remember { NumberFormat.getNumberInstance(Locale("id", "ID")) }
    val catColor = remember(category?.colorHex) {
        runCatching { Color(android.graphics.Color.parseColor(category?.colorHex ?: "#0061A4")) }
            .getOrElse { MaiPurple }
    }
    val (amountPrefix, amountColor) = when (transaction.type) {
        "INCOME"   -> "+" to IncomeGreen
        "TRANSFER" -> "" to MaterialTheme.colorScheme.primary
        else       -> "-" to ExpenseRed
    }

    Row(
        modifier              = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier.size(32.dp).clip(CircleShape).background(catColor.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                (category?.name?.firstOrNull() ?: '?').uppercase(),
                style      = MaterialTheme.typography.labelMedium,
                color      = catColor,
                fontWeight = FontWeight.Bold
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                category?.name ?: "Tanpa kategori",
                style    = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "$walletName · ${transaction.time}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Text(
            "$amountPrefix Rp ${fmt.format(transaction.amount)}",
            style      = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color      = amountColor
        )

        IconButton(onClick = onEditQuick, modifier = Modifier.size(28.dp)) {
            Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.chat_tx_edit_action), modifier = Modifier.size(16.dp))
        }
        IconButton(onClick = onDeleteRequest, modifier = Modifier.size(28.dp)) {
            Icon(
                Icons.Default.Delete,
                contentDescription = stringResource(R.string.chat_tx_delete_action),
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}
