// app/src/main/java/com/sndiy/chatfin/feature/finance/transaction/ui/TransactionListScreen.kt

package com.sndiy.chatfin.feature.finance.transaction.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sndiy.chatfin.core.data.local.entity.CategoryEntity
import com.sndiy.chatfin.core.data.local.entity.TransactionEntity
import java.text.NumberFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionListScreen(
    onNavigateBack: () -> Unit,
    onNavigateToAdd: () -> Unit,
    viewModel: TransactionViewModel = hiltViewModel()
) {
    val listState        by viewModel.listState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // Konfirmasi hapus
    var transactionToDelete by remember { mutableStateOf<TransactionEntity?>(null) }

    LaunchedEffect(listState.errorMessage, listState.successMessage) {
        listState.errorMessage?.let { snackbarHostState.showSnackbar(it); viewModel.clearMessages() }
        listState.successMessage?.let { snackbarHostState.showSnackbar(it); viewModel.clearMessages() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Transaksi") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Kembali")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onNavigateToAdd) {
                Icon(Icons.Default.Add, "Tambah transaksi")
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->

        if (listState.transactions.isEmpty()) {
            EmptyTransactionState(
                modifier = Modifier.padding(padding),
                onAdd    = onNavigateToAdd
            )
        } else {
            LazyColumn(
                modifier       = Modifier.padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Kelompokkan transaksi per tanggal
                val grouped = listState.transactions.groupBy { it.date }
                grouped.forEach { (date, txList) ->
                    item {
                        Text(
                            text     = formatDateHeader(date),
                            style    = MaterialTheme.typography.labelLarge,
                            color    = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                    items(txList, key = { it.id }) { transaction ->
                        val category = (listState.expenseCategories + listState.incomeCategories)
                            .find { it.id == transaction.categoryId }
                        val wallet = listState.wallets.find { it.id == transaction.walletId }

                        TransactionItem(
                            transaction = transaction,
                            category    = category,
                            walletName  = wallet?.name ?: "-",
                            onDelete    = { transactionToDelete = transaction }
                        )
                    }
                }
            }
        }
    }

    // Dialog konfirmasi hapus
    transactionToDelete?.let { tx ->
        AlertDialog(
            onDismissRequest = { transactionToDelete = null },
            title   = { Text("Hapus Transaksi?") },
            text    = { Text("Saldo dompet akan dikembalikan. Tindakan ini tidak bisa dibatalkan.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteTransaction(tx)
                    transactionToDelete = null
                }) {
                    Text("Hapus", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { transactionToDelete = null }) { Text("Batal") }
            }
        )
    }
}

// ── Satu item transaksi ───────────────────────────────────────────────────────
@Composable
private fun TransactionItem(
    transaction: TransactionEntity,
    category: CategoryEntity?,
    walletName: String,
    onDelete: () -> Unit
) {
    val isExpense  = transaction.type == "EXPENSE"
    val isIncome   = transaction.type == "INCOME"
    val isTransfer = transaction.type == "TRANSFER"

    val amountColor = when {
        isIncome   -> Color(0xFF1B8A4C)
        isExpense  -> MaterialTheme.colorScheme.error
        else       -> MaterialTheme.colorScheme.primary
    }
    val amountPrefix = when {
        isIncome  -> "+ "
        isExpense -> "- "
        else      -> ""
    }

    val categoryColor = category?.colorHex?.let {
        runCatching { Color(android.graphics.Color.parseColor(it)) }.getOrElse { Color.Gray }
    } ?: Color.Gray

    var showMenu by remember { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier          = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Ikon kategori
            Box(
                modifier         = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(categoryColor),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text  = if (isTransfer) "↔" else category?.name?.take(1) ?: "?",
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text       = if (isTransfer) "Transfer" else category?.name ?: "Tidak diketahui",
                    style      = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text  = "$walletName • ${transaction.time}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (!transaction.note.isNullOrBlank()) {
                    Text(
                        text  = transaction.note,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text       = "$amountPrefix Rp ${NumberFormat.getNumberInstance(Locale("id","ID")).format(transaction.amount)}",
                    style      = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color      = amountColor
                )
                Box {
                    IconButton(
                        onClick  = { showMenu = true },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = "Opsi",
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    DropdownMenu(
                        expanded         = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text        = { Text("Hapus", color = MaterialTheme.colorScheme.error) },
                            leadingIcon = {
                                Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error)
                            },
                            onClick     = { showMenu = false; onDelete() }
                        )
                    }
                }
            }
        }
    }
}

// ── State kosong ──────────────────────────────────────────────────────────────
@Composable
private fun EmptyTransactionState(modifier: Modifier = Modifier, onAdd: () -> Unit) {
    Column(
        modifier            = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.ReceiptLong,
            contentDescription = null,
            modifier = Modifier.size(72.dp),
            tint     = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text("Belum ada transaksi", style = MaterialTheme.typography.titleMedium)
        Text(
            "Tap tombol + untuk mencatat transaksi pertamamu",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onAdd) {
            Icon(Icons.Default.Add, null)
            Spacer(Modifier.width(8.dp))
            Text("Tambah Transaksi")
        }
    }
}

// ── Format tanggal jadi header yang ramah ─────────────────────────────────────
private fun formatDateHeader(date: String): String {
    return try {
        val parts = date.split("-")
        val months = listOf(
            "", "Jan", "Feb", "Mar", "Apr", "Mei", "Jun",
            "Jul", "Agu", "Sep", "Okt", "Nov", "Des"
        )
        "${parts[2]} ${months[parts[1].toInt()]} ${parts[0]}"
    } catch (e: Exception) {
        date
    }
}