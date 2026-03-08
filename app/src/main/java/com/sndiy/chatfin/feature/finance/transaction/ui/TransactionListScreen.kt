package com.sndiy.chatfin.feature.finance.transaction.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.KeyboardType
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
    val listState         by viewModel.listState.collectAsStateWithLifecycle()
    val formState         by viewModel.formState.collectAsStateWithLifecycle()
    val snackbarHostState  = remember { SnackbarHostState() }

    var transactionToDelete by remember { mutableStateOf<TransactionEntity?>(null) }
    var showEditSheet       by remember { mutableStateOf(false) }

    // Tutup sheet dan reset form setelah saved
    LaunchedEffect(formState.isSaved) {
        if (formState.isSaved) {
            showEditSheet = false
            viewModel.resetForm()
        }
    }

    LaunchedEffect(listState.errorMessage, listState.successMessage) {
        listState.errorMessage?.let   { snackbarHostState.showSnackbar(it); viewModel.clearMessages() }
        listState.successMessage?.let { snackbarHostState.showSnackbar(it); viewModel.clearMessages() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Riwayat Transaksi") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Kembali")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        if (listState.transactions.isEmpty()) {
            EmptyTransactionState(modifier = Modifier.padding(padding), onAdd = onNavigateToAdd)
        } else {
            LazyColumn(
                modifier            = Modifier.padding(padding),
                contentPadding      = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
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
                            onEdit      = {
                                viewModel.loadForEdit(transaction)
                                showEditSheet = true
                            },
                            onDelete    = { transactionToDelete = transaction }
                        )
                    }
                }
            }
        }
    }

    // ── Dialog hapus ──────────────────────────────────────────────────────────
    transactionToDelete?.let { tx ->
        AlertDialog(
            onDismissRequest = { transactionToDelete = null },
            title   = { Text("Hapus Transaksi?") },
            text    = { Text("Saldo dompet akan dikembalikan. Tindakan ini tidak bisa dibatalkan.") },
            confirmButton = {
                TextButton(onClick = { viewModel.deleteTransaction(tx); transactionToDelete = null }) {
                    Text("Hapus", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { transactionToDelete = null }) { Text("Batal") }
            }
        )
    }

    // ── Bottom sheet edit ─────────────────────────────────────────────────────
    if (showEditSheet) {
        ModalBottomSheet(
            onDismissRequest = { showEditSheet = false; viewModel.resetForm() },
            sheetState       = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            EditTransactionSheet(
                formState         = formState,
                expenseCategories = listState.expenseCategories,
                incomeCategories  = listState.incomeCategories,
                wallets           = listState.wallets,
                onAmountChange    = viewModel::onAmountChange,
                onNoteChange      = viewModel::onNoteChange,
                onCategorySelect  = viewModel::onCategorySelect,
                onWalletSelect    = viewModel::onWalletSelect,
                onSave            = viewModel::saveTransaction,
                onDismiss         = { showEditSheet = false; viewModel.resetForm() }
            )
        }
    }
}

// ── Bottom sheet konten edit ──────────────────────────────────────────────────
@Composable
private fun EditTransactionSheet(
    formState: TransactionFormState,
    expenseCategories: List<CategoryEntity>,
    incomeCategories: List<CategoryEntity>,
    wallets: List<com.sndiy.chatfin.core.data.local.entity.WalletEntity>,
    onAmountChange: (String) -> Unit,
    onNoteChange: (String) -> Unit,
    onCategorySelect: (CategoryEntity) -> Unit,
    onWalletSelect: (com.sndiy.chatfin.core.data.local.entity.WalletEntity) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit
) {
    val categories = if (formState.type == TransactionType.INCOME) incomeCategories else expenseCategories

    Column(
        modifier            = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Text("Edit Transaksi", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, contentDescription = "Tutup")
            }
        }

        HorizontalDivider()

        // Nominal
        OutlinedTextField(
            value         = formState.amount,
            onValueChange = onAmountChange,
            label         = { Text("Nominal") },
            prefix        = { Text("Rp ") },
            isError       = formState.amountError != null,
            supportingText = formState.amountError?.let { { Text(it) } },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine    = true,
            modifier      = Modifier.fillMaxWidth()
        )

        // Judul/Note
        OutlinedTextField(
            value         = formState.note,
            onValueChange = onNoteChange,
            label         = { Text("Judul (opsional)") },
            singleLine    = true,
            modifier      = Modifier.fillMaxWidth()
        )

        // Pilih Kategori
        Text("Kategori", style = MaterialTheme.typography.labelMedium)
        androidx.compose.foundation.lazy.LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(categories) { cat ->
                val selected = formState.selectedCategory?.id == cat.id
                FilterChip(
                    selected = selected,
                    onClick  = { onCategorySelect(cat) },
                    label    = { Text(cat.name) }
                )
            }
        }
        formState.categoryError?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
        }

        // Pilih Dompet
        Text("Dompet", style = MaterialTheme.typography.labelMedium)
        androidx.compose.foundation.lazy.LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(wallets) { wallet ->
                val selected = formState.selectedWallet?.id == wallet.id
                FilterChip(
                    selected = selected,
                    onClick  = { onWalletSelect(wallet) },
                    label    = { Text(wallet.name) }
                )
            }
        }
        formState.walletError?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
        }

        Button(
            onClick  = onSave,
            enabled  = !formState.isLoading,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (formState.isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            } else {
                Text("Simpan Perubahan")
            }
        }
    }
}

// ── Item transaksi ────────────────────────────────────────────────────────────
@Composable
private fun TransactionItem(
    transaction: TransactionEntity,
    category: CategoryEntity?,
    walletName: String,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val isIncome   = transaction.type == "INCOME"
    val isTransfer = transaction.type == "TRANSFER"
    val amountColor  = when {
        isIncome   -> Color(0xFF1B8A4C)
        isTransfer -> MaterialTheme.colorScheme.primary
        else       -> MaterialTheme.colorScheme.error
    }
    val amountPrefix = when { isIncome -> "+ "; isTransfer -> ""; else -> "- " }
    val categoryColor = category?.colorHex?.let {
        runCatching { Color(android.graphics.Color.parseColor(it)) }.getOrElse { Color.Gray }
    } ?: Color.Gray

    var showMenu by remember { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier              = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier         = Modifier.size(40.dp).clip(CircleShape).background(categoryColor),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text       = if (isTransfer) "↔" else category?.name?.take(1) ?: "?",
                    color      = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                // Judul: note kalau ada, fallback ke nama kategori
                Text(
                    text       = transaction.note?.takeIf { it.isNotBlank() }
                        ?: if (isTransfer) "Transfer" else category?.name ?: "?",
                    style      = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines   = 1
                )
                Text(
                    text  = buildString {
                        append(if (isTransfer) "Transfer" else category?.name ?: "?")
                        append(" · ")
                        append(walletName)
                        append(" · ")
                        append(transaction.time)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text       = "$amountPrefix Rp ${NumberFormat.getNumberInstance(Locale("id","ID")).format(transaction.amount)}",
                    style      = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color      = amountColor
                )
                Box {
                    IconButton(onClick = { showMenu = true }, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.MoreVert, null, modifier = Modifier.size(16.dp))
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(
                            text        = { Text("Edit") },
                            leadingIcon = { Icon(Icons.Default.Edit, null) },
                            onClick     = { showMenu = false; onEdit() }
                        )
                        DropdownMenuItem(
                            text        = { Text("Hapus", color = MaterialTheme.colorScheme.error) },
                            leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
                            onClick     = { showMenu = false; onDelete() }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyTransactionState(modifier: Modifier = Modifier, onAdd: () -> Unit) {
    Column(
        modifier            = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.ReceiptLong, null, modifier = Modifier.size(72.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(16.dp))
        Text("Belum ada transaksi", style = MaterialTheme.typography.titleMedium)
        Text("Chat dengan Mai untuk mencatat transaksi", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(24.dp))
        Button(onClick = onAdd) {
            Icon(Icons.Default.Chat, null)
            Spacer(Modifier.width(8.dp))
            Text("Chat dengan Mai")
        }
    }
}

private fun formatDateHeader(date: String): String {
    return try {
        val parts  = date.split("-")
        val months = listOf("","Jan","Feb","Mar","Apr","Mei","Jun","Jul","Agu","Sep","Okt","Nov","Des")
        "${parts[2]} ${months[parts[1].toInt()]} ${parts[0]}"
    } catch (e: Exception) { date }
}