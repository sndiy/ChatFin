// app/src/main/java/com/sndiy/chatfin/feature/finance/transaction/ui/TransactionFormScreen.kt

package com.sndiy.chatfin.feature.finance.transaction.ui

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sndiy.chatfin.R
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sndiy.chatfin.core.data.local.entity.CategoryEntity
import com.sndiy.chatfin.core.data.local.entity.WalletEntity
import com.sndiy.chatfin.core.ui.animation.pressScale
import com.sndiy.chatfin.core.ui.component.NumpadAmountDisplay
import com.sndiy.chatfin.core.ui.component.NumpadKeyboard
import com.sndiy.chatfin.core.ui.component.NumpadPresetChips
import java.text.NumberFormat
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val formNotePresets = listOf(
    "Makan", "Minum", "Kopi", "Belanja", "Bensin",
    "Parkir", "Transportasi", "Listrik", "Air", "Internet",
    "Hiburan", "Kesehatan", "Gaji", "Bonus"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionFormScreen(
    onNavigateBack: () -> Unit,
    transactionId: String? = null,
    viewModel: TransactionViewModel = hiltViewModel()
) {
    val formState by viewModel.formState.collectAsStateWithLifecycle()
    val listState by viewModel.listState.collectAsStateWithLifecycle()

    // Dibuka dengan id (mis. tombol "Edit lengkap" dari chat) → muat transaksi
    // yang bersangkutan begitu layar ini pertama kali tampil.
    LaunchedEffect(transactionId) {
        transactionId?.let { viewModel.loadForEditById(it) }
    }

    // Navigasi kembali setelah berhasil simpan
    LaunchedEffect(formState.isSaved) {
        if (formState.isSaved) {
            viewModel.resetForm()
            onNavigateBack()
        }
    }

    // Kategori sesuai tipe yang dipilih
    val categories = when (formState.type) {
        TransactionType.INCOME   -> listState.incomeCategories
        TransactionType.EXPENSE  -> listState.expenseCategories
        TransactionType.TRANSFER -> emptyList()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (formState.editingId != null) R.string.transaction_form_title_edit else R.string.transaction_form_title_add)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Kembali")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {

            // ── Pilih Tipe Transaksi ──────────────────────────────────────────
            TypeSelector(
                selectedType = formState.type,
                onTypeChange = viewModel::onTypeChange
            )

            // ── Nominal Display (di-update oleh numpad di bawah) ─────────────
            val formattedAmt = remember(formState.amount) {
                val n = formState.amount.toLongOrNull()
                if (n != null && n > 0) NumberFormat.getNumberInstance(Locale("id", "ID")).format(n) else formState.amount.ifBlank { "" }
            }
            NumpadAmountDisplay(
                formattedAmount = formattedAmt,
                currencyPrefix  = "Rp",
                error           = formState.amountError,
                modifier        = Modifier.padding(horizontal = 16.dp)
            )

            // ── Preset nominal chip ───────────────────────────────────────────
            Row(modifier = Modifier.padding(horizontal = 16.dp)) {
                NumpadPresetChips(
                    presets    = listOf(5_000L, 10_000L, 20_000L, 50_000L, 100_000L, 200_000L, 500_000L),
                    currentRaw = formState.amount,
                    onSelect   = { viewModel.onAmountChange(it.toString()) }
                )
            }

            // ── Numpad Visual ─────────────────────────────────────────────────
            NumpadKeyboard(
                rawDigits   = formState.amount,
                onDigit     = { key ->
                    val current = formState.amount
                    val next = (current + key).trimStart('0').take(12).ifBlank { key }
                    viewModel.onAmountChange(next)
                },
                onBackspace = {
                    if (formState.amount.isNotEmpty()) viewModel.onAmountChange(formState.amount.dropLast(1))
                },
                onClear     = { viewModel.onAmountChange("") },
                buttonSize  = 68.dp,
                modifier    = Modifier.padding(horizontal = 8.dp)
            )

            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

            Column(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {

                // ── Pilih Dompet Sumber ───────────────────────────────────────
                SectionLabel("Dompet")
                WalletSelector(
                    wallets        = listState.wallets,
                    selectedWallet = formState.selectedWallet,
                    error          = formState.walletError,
                    onSelect       = viewModel::onWalletSelect
                )

                // ── Pilih Dompet Tujuan (khusus Transfer) ─────────────────────
                if (formState.type == TransactionType.TRANSFER) {
                    SectionLabel("Dompet Tujuan")
                    WalletSelector(
                        wallets        = listState.wallets.filter {
                            it.id != formState.selectedWallet?.id
                        },
                        selectedWallet = formState.selectedToWallet,
                        error          = null,
                        onSelect       = viewModel::onToWalletSelect
                    )
                }

                // ── Pilih Kategori (selain Transfer) ──────────────────────────
                if (formState.type != TransactionType.TRANSFER) {
                    SectionLabel("Kategori")
                    if (formState.categoryError != null) {
                        Text(
                            text  = formState.categoryError!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    CategoryGrid(
                        categories       = categories,
                        selectedCategory = formState.selectedCategory,
                        onSelect         = viewModel::onCategorySelect
                    )
                }

                // ── Tanggal & Waktu (DatePicker & TimePicker interaktif) ───────
                var showDatePicker by remember { mutableStateOf(false) }
                var showTimePicker by remember { mutableStateOf(false) }
                val formattedDate = remember(formState.date) {
                    formState.date.format(DateTimeFormatter.ofPattern("dd MMM yyyy", Locale("id", "ID")))
                }
                val formattedTime = remember(formState.time) {
                    formState.time.format(DateTimeFormatter.ofPattern("HH:mm"))
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(modifier = Modifier.weight(1.5f).clickable { showDatePicker = true }) {
                        OutlinedTextField(
                            value          = formattedDate,
                            onValueChange  = {},
                            readOnly       = true,
                            enabled        = false,
                            label          = { Text("Tanggal") },
                            leadingIcon    = { Icon(Icons.Default.CalendarToday, null) },
                            trailingIcon   = { Icon(Icons.Default.ArrowDropDown, null) },
                            colors         = OutlinedTextFieldDefaults.colors(
                                disabledTextColor        = MaterialTheme.colorScheme.onSurface,
                                disabledBorderColor      = MaterialTheme.colorScheme.outline,
                                disabledLabelColor       = MaterialTheme.colorScheme.onSurfaceVariant,
                                disabledLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            modifier       = Modifier.fillMaxWidth(),
                            singleLine     = true
                        )
                    }
                    Box(modifier = Modifier.weight(1f).clickable { showTimePicker = true }) {
                        OutlinedTextField(
                            value          = formattedTime,
                            onValueChange  = {},
                            readOnly       = true,
                            enabled        = false,
                            label          = { Text("Waktu") },
                            leadingIcon    = { Icon(Icons.Default.Schedule, null) },
                            trailingIcon   = { Icon(Icons.Default.ArrowDropDown, null) },
                            colors         = OutlinedTextFieldDefaults.colors(
                                disabledTextColor        = MaterialTheme.colorScheme.onSurface,
                                disabledBorderColor      = MaterialTheme.colorScheme.outline,
                                disabledLabelColor       = MaterialTheme.colorScheme.onSurfaceVariant,
                                disabledLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            modifier       = Modifier.fillMaxWidth(),
                            singleLine     = true
                        )
                    }
                }

                if (showDatePicker) {
                    val dpState = rememberDatePickerState(
                        initialSelectedDateMillis = formState.date.toEpochDay() * 86400000L
                    )
                    DatePickerDialog(
                        onDismissRequest = { showDatePicker = false },
                        confirmButton = {
                            TextButton(onClick = {
                                dpState.selectedDateMillis?.let { millis ->
                                    viewModel.onDateChange(LocalDate.ofEpochDay(millis / 86400000L))
                                }
                                showDatePicker = false
                            }) { Text("OK") }
                        },
                        dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Batal") } }
                    ) { DatePicker(state = dpState) }
                }

                if (showTimePicker) {
                    val tpState = rememberTimePickerState(
                        initialHour = formState.time.hour,
                        initialMinute = formState.time.minute,
                        is24Hour = true
                    )
                    AlertDialog(
                        onDismissRequest = { showTimePicker = false },
                        confirmButton = {
                            TextButton(onClick = {
                                viewModel.onTimeChange(LocalTime.of(tpState.hour, tpState.minute))
                                showTimePicker = false
                            }) { Text("OK") }
                        },
                        dismissButton = { TextButton(onClick = { showTimePicker = false }) { Text("Batal") } },
                        text = { TimePicker(state = tpState) }
                    )
                }

                // ── Catatan via Chip Preset ───────────────────────────────────
                var showCustomNoteField by remember { mutableStateOf(true) }
                SectionLabel("Catatan (opsional)")
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(formNotePresets) { preset ->
                        val isSelected = formState.note == preset && !showCustomNoteField
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                if (preset == "Lainnya") {
                                    showCustomNoteField = true
                                    viewModel.onNoteChange("")
                                } else {
                                    viewModel.onNoteChange(if (isSelected) "" else preset)
                                    showCustomNoteField = false
                                }
                            },
                            label = { Text(preset) },
                            leadingIcon = if (isSelected) { { Icon(Icons.Default.Check, null, Modifier.size(14.dp)) } } else null
                        )
                    }
                    item {
                        FilterChip(
                            selected = showCustomNoteField,
                            onClick  = { showCustomNoteField = !showCustomNoteField },
                            label    = { Text("Lainnya") },
                            leadingIcon = if (showCustomNoteField) { { Icon(Icons.Default.Check, null, Modifier.size(14.dp)) } } else null
                        )
                    }
                }
                if (showCustomNoteField) {
                    OutlinedTextField(
                        value         = formState.note,
                        onValueChange = viewModel::onNoteChange,
                        label         = { Text("Catatan custom") },
                        leadingIcon   = { Icon(Icons.Default.Edit, null) },
                        trailingIcon  = if (formState.note.isNotEmpty()) {
                            { IconButton(onClick = { viewModel.onNoteChange("") }) { Icon(Icons.Default.Clear, null) } }
                        } else null,
                        maxLines      = 2,
                        modifier      = Modifier.fillMaxWidth()
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // ── Tombol Simpan ─────────────────────────────────────────────
                Button(
                    onClick  = viewModel::saveTransaction,
                    enabled  = !formState.isLoading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                ) {
                    if (formState.isLoading) {
                        CircularProgressIndicator(
                            modifier    = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color       = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Text("Simpan Transaksi", fontWeight = FontWeight.SemiBold)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

// ── Selector tipe transaksi (Pengeluaran / Pemasukan / Transfer) ──────────────
@Composable
private fun TypeSelector(
    selectedType: TransactionType,
    onTypeChange: (TransactionType) -> Unit
) {
    val types = TransactionType.entries

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        types.forEach { type ->
            val isSelected = type == selectedType
            val containerColor = when {
                !isSelected              -> MaterialTheme.colorScheme.surfaceVariant
                type == TransactionType.EXPENSE  -> MaterialTheme.colorScheme.errorContainer
                type == TransactionType.INCOME   -> Color(0xFF1B8A4C).copy(alpha = 0.2f)
                else                             -> MaterialTheme.colorScheme.primaryContainer
            }
            val contentColor = when {
                !isSelected              -> MaterialTheme.colorScheme.onSurfaceVariant
                type == TransactionType.EXPENSE  -> MaterialTheme.colorScheme.error
                type == TransactionType.INCOME   -> Color(0xFF1B8A4C)
                else                             -> MaterialTheme.colorScheme.primary
            }

            val interactionSource = remember { MutableInteractionSource() }
            Surface(
                modifier      = Modifier
                    .weight(1f)
                    .pressScale(interactionSource)
                    .clickable(
                        interactionSource = interactionSource,
                        indication         = LocalIndication.current
                    ) { onTypeChange(type) },
                color         = containerColor,
                shape         = MaterialTheme.shapes.medium
            ) {
                Text(
                    text      = type.label,
                    modifier  = Modifier.padding(vertical = 10.dp),
                    textAlign = TextAlign.Center,
                    style     = MaterialTheme.typography.labelLarge,
                    color     = contentColor,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                )
            }
        }
    }
}

// AmountInput — dihapus, digantikan oleh NumpadAmountDisplay + NumpadKeyboard inline

// ── Selector dompet ───────────────────────────────────────────────────────────
@Composable
private fun WalletSelector(
    wallets: List<WalletEntity>,
    selectedWallet: WalletEntity?,
    error: String?,
    onSelect: (WalletEntity) -> Unit
) {
    if (error != null) {
        Text(text = error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
    }

    Row(
        modifier              = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        wallets.forEach { wallet ->
            val isSelected = wallet.id == selectedWallet?.id
            val color = runCatching {
                Color(android.graphics.Color.parseColor(wallet.colorHex))
            }.getOrElse { MaterialTheme.colorScheme.primary }

            FilterChip(
                selected  = isSelected,
                onClick   = { onSelect(wallet) },
                label     = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(wallet.name, style = MaterialTheme.typography.labelMedium)
                        Text(
                            text  = "Rp ${NumberFormat.getNumberInstance(Locale("id","ID")).format(wallet.balance)}",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                },
                leadingIcon = {
                    Box(
                        modifier         = Modifier.size(8.dp).clip(CircleShape).background(color)
                    )
                }
            )
        }
    }
}

// ── Grid kategori ─────────────────────────────────────────────────────────────
@Composable
private fun CategoryGrid(
    categories: List<CategoryEntity>,
    selectedCategory: CategoryEntity?,
    onSelect: (CategoryEntity) -> Unit
) {
    val chunked = categories.chunked(4)
    chunked.forEach { row ->
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            row.forEach { category ->
                val isSelected = category.id == selectedCategory?.id
                val color = runCatching {
                    Color(android.graphics.Color.parseColor(category.colorHex))
                }.getOrElse { MaterialTheme.colorScheme.primary }
                val interactionSource = remember { MutableInteractionSource() }

                Column(
                    modifier            = Modifier
                        .weight(1f)
                        .pressScale(interactionSource)
                        .clip(MaterialTheme.shapes.medium)
                        .background(
                            if (isSelected) color.copy(alpha = 0.2f)
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                        .border(
                            width = if (isSelected) 2.dp else 0.dp,
                            color = if (isSelected) color else Color.Transparent,
                            shape = MaterialTheme.shapes.medium
                        )
                        .clickable(
                            interactionSource = interactionSource,
                            indication         = LocalIndication.current
                        ) { onSelect(category) }
                        .padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier         = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(color),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text  = category.name.take(1),
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize   = 14.sp
                        )
                    }
                    Text(
                        text      = category.name,
                        style     = MaterialTheme.typography.labelSmall,
                        textAlign = TextAlign.Center,
                        maxLines  = 2
                    )
                }
            }
            // Isi sisa kolom kosong agar grid rata
            repeat(4 - row.size) {
                Spacer(modifier = Modifier.weight(1f))
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
    }
}

// ── Label section ─────────────────────────────────────────────────────────────
@Composable
private fun SectionLabel(text: String) {
    Text(
        text  = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}