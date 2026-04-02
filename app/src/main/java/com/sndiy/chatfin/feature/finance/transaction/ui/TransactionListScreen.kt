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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import com.sndiy.chatfin.core.data.local.entity.CategoryEntity
import com.sndiy.chatfin.core.data.local.entity.TransactionEntity
import com.sndiy.chatfin.core.ui.theme.ExpenseRed
import com.sndiy.chatfin.core.ui.theme.IncomeGreen
import java.text.NumberFormat
import java.time.LocalDate
import java.util.Locale

private enum class TxFilter(val label: String) {
    ALL("Semua"), INCOME("Pemasukan"), EXPENSE("Pengeluaran")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionListScreen(
    onNavigateBack: () -> Unit,
    onNavigateToAdd: () -> Unit,
    viewModel: TransactionViewModel = hiltViewModel()
) {
    val listState         by viewModel.listState.collectAsStateWithLifecycle()
    val formState         by viewModel.formState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var transactionToDelete by remember { mutableStateOf<TransactionEntity?>(null) }
    var showEditSheet       by remember { mutableStateOf(false) }
    var activeFilter        by remember { mutableStateOf(TxFilter.ALL) }
    var visibleCount        by remember { mutableIntStateOf(10) }
    var searchQuery         by remember { mutableStateOf("") }
    var isSearchActive      by remember { mutableStateOf(false) }
    var showDateFilter      by remember { mutableStateOf(false) }

    LaunchedEffect(activeFilter, searchQuery, listState.dateFilter) { visibleCount = 10 }

    LaunchedEffect(formState.isSaved) {
        if (formState.isSaved) { showEditSheet = false; viewModel.resetForm() }
    }

    LaunchedEffect(listState.errorMessage, listState.successMessage) {
        listState.errorMessage?.let   { snackbarHostState.showSnackbar(it); viewModel.clearMessages() }
        listState.successMessage?.let { snackbarHostState.showSnackbar(it); viewModel.clearMessages() }
    }

    val allCategories = listState.expenseCategories + listState.incomeCategories

    val filteredTx = remember(listState.transactions, activeFilter, searchQuery, listState.dateFilter) {
        listState.transactions
            .let { list ->
                when (activeFilter) {
                    TxFilter.ALL     -> list
                    TxFilter.INCOME  -> list.filter { it.type == "INCOME" }
                    TxFilter.EXPENSE -> list.filter { it.type == "EXPENSE" }
                }
            }
            .let { list ->
                // Filter tanggal
                val df = listState.dateFilter
                if (!df.isActive) list
                else list.filter { tx ->
                    val txDate = runCatching { LocalDate.parse(tx.date) }.getOrNull() ?: return@filter true
                    val afterStart  = df.startDate?.let { !txDate.isBefore(it) } ?: true
                    val beforeEnd   = df.endDate?.let { !txDate.isAfter(it) } ?: true
                    afterStart && beforeEnd
                }
            }
            .let { list ->
                if (searchQuery.isBlank()) list
                else {
                    val q = searchQuery.trim().lowercase()
                    list.filter { tx ->
                        tx.note?.lowercase()?.contains(q) == true ||
                                allCategories.find { it.id == tx.categoryId }
                                    ?.name?.lowercase()?.contains(q) == true ||
                                listState.wallets.find { it.id == tx.walletId }
                                    ?.name?.lowercase()?.contains(q) == true ||
                                tx.amount.toString().contains(q)
                    }
                }
            }
    }

    val visibleTx      = filteredTx.take(visibleCount)
    val hasMore        = filteredTx.size > visibleCount
    val remainingCount = (filteredTx.size - visibleCount).coerceAtLeast(0)

    Scaffold(
        topBar = {
            if (isSearchActive) {
                SearchTopBar(
                    query         = searchQuery,
                    onQueryChange = { searchQuery = it },
                    onClose       = { isSearchActive = false; searchQuery = "" }
                )
            } else {
                TopAppBar(
                    title = { Text("Riwayat", fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Kembali")
                        }
                    },
                    actions = {
                        // Tombol filter tanggal — berwarna kalau aktif
                        IconButton(onClick = { showDateFilter = true }) {
                            Icon(
                                Icons.Default.DateRange,
                                "Filter tanggal",
                                tint = if (listState.dateFilter.isActive)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { isSearchActive = true }) {
                            Icon(Icons.Default.Search, "Cari")
                        }
                    }
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        LazyColumn(
            modifier            = Modifier.fillMaxSize().padding(padding),
            contentPadding      = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // ── Filter chips ──────────────────────────────────────────────────
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier              = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        TxFilter.entries.forEach { filter ->
                            FilterChip(
                                selected = activeFilter == filter,
                                onClick  = { activeFilter = filter },
                                label    = {
                                    val count = when (filter) {
                                        TxFilter.ALL     -> listState.transactions.size
                                        TxFilter.INCOME  -> listState.transactions.count { it.type == "INCOME" }
                                        TxFilter.EXPENSE -> listState.transactions.count { it.type == "EXPENSE" }
                                    }
                                    Text("${filter.label} ($count)", style = MaterialTheme.typography.labelMedium)
                                },
                                leadingIcon = when (filter) {
                                    TxFilter.ALL     -> null
                                    TxFilter.INCOME  -> { { Icon(Icons.Default.TrendingUp,   null, Modifier.size(14.dp), tint = IncomeGreen) } }
                                    TxFilter.EXPENSE -> { { Icon(Icons.Default.TrendingDown, null, Modifier.size(14.dp), tint = ExpenseRed)  } }
                                }
                            )
                        }
                    }

                    // Tampilkan chip filter tanggal kalau aktif
                    if (listState.dateFilter.isActive) {
                        Row(
                            verticalAlignment     = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Default.DateRange, null,
                                modifier = Modifier.size(16.dp),
                                tint     = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                listState.dateFilter.label,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            TextButton(
                                onClick      = { viewModel.clearDateFilter() },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                            ) {
                                Icon(Icons.Default.Close, null, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(2.dp))
                                Text("Hapus filter", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }

            // ── List transaksi / empty state ──────────────────────────────────
            if (filteredTx.isEmpty()) {
                if (searchQuery.isNotBlank() || listState.dateFilter.isActive) {
                    item {
                        Box(
                            Modifier.fillMaxWidth().padding(vertical = 48.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    if (listState.dateFilter.isActive) Icons.Default.DateRange
                                    else Icons.Default.SearchOff,
                                    null,
                                    Modifier.size(64.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text("Tidak ditemukan", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    if (listState.dateFilter.isActive)
                                        "Tidak ada transaksi di rentang tanggal ini"
                                    else
                                        "Tidak ada transaksi yang cocok\ndengan \"$searchQuery\"",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (listState.dateFilter.isActive) {
                                    TextButton(onClick = { viewModel.clearDateFilter() }) {
                                        Text("Hapus filter tanggal")
                                    }
                                }
                            }
                        }
                    }
                } else {
                    item { EmptyTransactionState(onAdd = onNavigateToAdd) }
                }
            } else {
                if (searchQuery.isNotBlank()) {
                    item {
                        Text(
                            "${filteredTx.size} hasil untuk \"$searchQuery\"",
                            style    = MaterialTheme.typography.labelMedium,
                            color    = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                }

                val grouped = visibleTx.groupBy { it.date }
                grouped.forEach { (date, txList) ->
                    item(key = "header_$date") {
                        Text(
                            text       = formatDateHeader(date),
                            style      = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color      = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier   = Modifier.padding(top = 8.dp, bottom = 2.dp)
                        )
                    }
                    items(txList, key = { it.id }) { transaction ->
                        val category = allCategories.find { it.id == transaction.categoryId }
                        val wallet   = listState.wallets.find { it.id == transaction.walletId }
                        TransactionItem(
                            transaction  = transaction,
                            category     = category,
                            walletName   = wallet?.name ?: "-",
                            searchQuery  = searchQuery,
                            onEdit       = { viewModel.loadForEdit(transaction); showEditSheet = true },
                            onDelete     = { transactionToDelete = transaction }
                        )
                    }
                }

                if (hasMore) {
                    item(key = "show_more") {
                        OutlinedButton(
                            onClick  = { visibleCount += 10 },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        ) {
                            Icon(Icons.Default.ExpandMore, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "Tampilkan ${minOf(10, remainingCount)} lagi  •  sisa $remainingCount transaksi",
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                } else if (filteredTx.size > 10) {
                    item(key = "all_shown") {
                        Box(
                            Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "Semua ${filteredTx.size} transaksi ditampilkan",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(16.dp)) }
        }
    }

    // ── Dialog filter tanggal ─────────────────────────────────────────────────
    if (showDateFilter) {
        DateFilterDialog(
            currentFilter = listState.dateFilter,
            onApply       = { start, end ->
                viewModel.setDateFilter(start, end)
                showDateFilter = false
            },
            onDismiss     = { showDateFilter = false }
        )
    }

    // ── Dialog hapus ──────────────────────────────────────────────────────────
    transactionToDelete?.let { tx ->
        val fmt       = NumberFormat.getNumberInstance(Locale("id", "ID"))
        val isLarge   = tx.amount >= 500_000
        AlertDialog(
            onDismissRequest = { transactionToDelete = null },
            icon    = if (isLarge) {
                { Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error) }
            } else null,
            title   = { Text(if (isLarge) "⚠️ Hapus Transaksi Besar?" else "Hapus Transaksi?") },
            text    = {
                Text(if (isLarge)
                    "Transaksi ini bernilai Rp ${fmt.format(tx.amount)}.\nSaldo dompet akan dikembalikan. Tindakan ini tidak bisa dibatalkan."
                else
                    "Saldo dompet akan dikembalikan. Tindakan ini tidak bisa dibatalkan."
                )
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.deleteTransaction(tx); transactionToDelete = null },
                    colors  = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text(if (isLarge) "Ya, Hapus" else "Hapus", color = Color.White) }
            },
            dismissButton = {
                TextButton(onClick = { transactionToDelete = null }) { Text("Batal") }
            },
            containerColor = if (isLarge)
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.95f)
            else MaterialTheme.colorScheme.surface
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

// ── Date Filter Dialog ────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateFilterDialog(
    currentFilter: DateFilter,
    onApply: (LocalDate?, LocalDate?) -> Unit,
    onDismiss: () -> Unit
) {
    var startDate by remember { mutableStateOf(currentFilter.startDate) }
    var endDate   by remember { mutableStateOf(currentFilter.endDate) }
    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker   by remember { mutableStateOf(false) }

    val dateFmt = java.time.format.DateTimeFormatter.ofPattern("dd MMM yyyy",
        java.util.Locale("id", "ID"))

    // Preset ranges
    val today     = LocalDate.now()
    val presets   = listOf(
        "Hari ini"      to (today to today),
        "7 hari"        to (today.minusDays(6) to today),
        "Bulan ini"     to (today.withDayOfMonth(1) to today),
        "Bulan lalu"    to (today.minusMonths(1).withDayOfMonth(1) to
                today.minusMonths(1).withDayOfMonth(today.minusMonths(1).lengthOfMonth())),
        "3 bulan"       to (today.minusMonths(2).withDayOfMonth(1) to today),
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title   = { Text("Filter Tanggal", fontWeight = FontWeight.Bold) },
        text    = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {

                // Preset chips
                Text("Pilih cepat:", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(presets.size) { i ->
                        val (label, range) = presets[i]
                        FilterChip(
                            selected = startDate == range.first && endDate == range.second,
                            onClick  = { startDate = range.first; endDate = range.second },
                            label    = { Text(label, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }

                HorizontalDivider()

                // Manual input
                Text("Atau pilih manual:", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)

                // Tanggal mulai
                OutlinedCard(
                    onClick = { showStartPicker = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier              = Modifier.padding(12.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Dari", style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                startDate?.format(dateFmt) ?: "Pilih tanggal",
                                style      = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color      = if (startDate != null) MaterialTheme.colorScheme.onSurface
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(Icons.Default.CalendarMonth, null,
                            tint = MaterialTheme.colorScheme.primary)
                    }
                }

                // Tanggal akhir
                OutlinedCard(
                    onClick = { showEndPicker = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier              = Modifier.padding(12.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Sampai", style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                endDate?.format(dateFmt) ?: "Pilih tanggal",
                                style      = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color      = if (endDate != null) MaterialTheme.colorScheme.onSurface
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(Icons.Default.CalendarMonth, null,
                            tint = MaterialTheme.colorScheme.primary)
                    }
                }

                // Validasi
                if (startDate != null && endDate != null && startDate!!.isAfter(endDate)) {
                    Text(
                        "Tanggal mulai tidak boleh setelah tanggal akhir",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            val isValid = startDate == null || endDate == null || !startDate!!.isAfter(endDate)
            Button(
                onClick  = { onApply(startDate, endDate) },
                enabled  = isValid && (startDate != null || endDate != null)
            ) { Text("Terapkan") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { startDate = null; endDate = null }) {
                    Text("Reset")
                }
                TextButton(onClick = onDismiss) { Text("Batal") }
            }
        }
    )

    // ── Date Pickers ──────────────────────────────────────────────────────────
    if (showStartPicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = startDate?.toEpochDay()?.times(86400000)
        )
        DatePickerDialog(
            onDismissRequest = { showStartPicker = false },
            confirmButton    = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { millis ->
                        startDate = LocalDate.ofEpochDay(millis / 86400000)
                    }
                    showStartPicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showStartPicker = false }) { Text("Batal") }
            }
        ) { DatePicker(state = pickerState) }
    }

    if (showEndPicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = endDate?.toEpochDay()?.times(86400000)
        )
        DatePickerDialog(
            onDismissRequest = { showEndPicker = false },
            confirmButton    = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { millis ->
                        endDate = LocalDate.ofEpochDay(millis / 86400000)
                    }
                    showEndPicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showEndPicker = false }) { Text("Batal") }
            }
        ) { DatePicker(state = pickerState) }
    }
}

// ── Search top bar ────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchTopBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    TopAppBar(
        navigationIcon = {
            IconButton(onClick = onClose) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Tutup pencarian")
            }
        },
        title = {
            OutlinedTextField(
                value         = query,
                onValueChange = onQueryChange,
                placeholder   = { Text("Judul, kategori, dompet, nominal...") },
                singleLine    = true,
                modifier      = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
                shape  = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                ),
                trailingIcon = {
                    if (query.isNotBlank()) {
                        IconButton(onClick = { onQueryChange("") }) {
                            Icon(Icons.Default.Clear, "Hapus")
                        }
                    }
                }
            )
        }
    )
}

// ── Edit sheet ────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
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
            IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, null) }
        }
        HorizontalDivider()
        OutlinedTextField(
            value           = formState.amount,
            onValueChange   = onAmountChange,
            label           = { Text("Nominal") },
            prefix          = { Text("Rp ") },
            isError         = formState.amountError != null,
            supportingText  = formState.amountError?.let { { Text(it) } },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine      = true,
            modifier        = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value         = formState.note,
            onValueChange = onNoteChange,
            label         = { Text("Judul (opsional)") },
            singleLine    = true,
            modifier      = Modifier.fillMaxWidth()
        )
        Text("Kategori", style = MaterialTheme.typography.labelMedium)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(categories) { cat ->
                FilterChip(
                    selected = formState.selectedCategory?.id == cat.id,
                    onClick  = { onCategorySelect(cat) },
                    label    = { Text(cat.name) }
                )
            }
        }
        formState.categoryError?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
        }
        Text("Dompet", style = MaterialTheme.typography.labelMedium)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(wallets) { w ->
                FilterChip(
                    selected = formState.selectedWallet?.id == w.id,
                    onClick  = { onWalletSelect(w) },
                    label    = { Text(w.name) }
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
            if (formState.isLoading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            else Text("Simpan Perubahan")
        }
    }
}

// ── Transaction item ──────────────────────────────────────────────────────────
@Composable
private fun TransactionItem(
    transaction: TransactionEntity,
    category: CategoryEntity?,
    walletName: String,
    searchQuery: String = "",
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val isIncome   = transaction.type == "INCOME"
    val isTransfer = transaction.type == "TRANSFER"
    val amountColor = when {
        isIncome   -> IncomeGreen
        isTransfer -> MaterialTheme.colorScheme.primary
        else       -> MaterialTheme.colorScheme.error
    }
    val amountPrefix = when {
        isIncome   -> "+ "
        isTransfer -> ""
        else       -> "- "
    }
    val categoryColor = category?.colorHex?.let {
        runCatching { Color(android.graphics.Color.parseColor(it)) }.getOrElse { Color.Gray }
    } ?: Color.Gray
    var showMenu by remember { mutableStateOf(false) }

    val cardColor = if (searchQuery.isNotBlank())
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
    else
        MaterialTheme.colorScheme.surface

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors   = CardDefaults.cardColors(containerColor = cardColor)
    ) {
        Row(
            modifier              = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier         = Modifier.size(48.dp).clip(CircleShape).background(categoryColor),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (isTransfer) "↔" else category?.name?.take(1) ?: "?",
                    style      = MaterialTheme.typography.titleMedium,
                    color      = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    transaction.note?.takeIf { it.isNotBlank() }
                        ?: if (isTransfer) "Transfer" else category?.name ?: "?",
                    style      = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines   = 1,
                    overflow   = TextOverflow.Ellipsis
                )
                Text(
                    buildString {
                        append(if (isTransfer) "Transfer" else category?.name ?: "?")
                        append(" · "); append(walletName)
                        append(" · "); append(transaction.time)
                    },
                    style    = MaterialTheme.typography.bodySmall,
                    color    = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    "$amountPrefix Rp ${NumberFormat.getNumberInstance(Locale("id", "ID")).format(transaction.amount)}",
                    style      = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color      = amountColor
                )
                Box {
                    IconButton(onClick = { showMenu = true }, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.MoreVert, null, Modifier.size(18.dp))
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

// ── Empty state ───────────────────────────────────────────────────────────────
@Composable
private fun EmptyTransactionState(onAdd: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().padding(vertical = 48.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(Icons.Default.ReceiptLong, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Belum ada transaksi", style = MaterialTheme.typography.titleMedium)
            Text(
                "Tap tombol + untuk mencatat transaksi pertamamu",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(onClick = onAdd) {
                Icon(Icons.Default.Add, null)
                Spacer(Modifier.width(8.dp))
                Text("Tambah Transaksi")
            }
        }
    }
}

// ── Helper ────────────────────────────────────────────────────────────────────
private fun formatDateHeader(date: String): String {
    return try {
        val parts  = date.split("-")
        val months = listOf("", "Jan", "Feb", "Mar", "Apr", "Mei", "Jun", "Jul", "Agu", "Sep", "Okt", "Nov", "Des")
        "${parts[2]} ${months[parts[1].toInt()]} ${parts[0]}"
    } catch (e: Exception) { date }
}