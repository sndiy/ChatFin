// app/src/main/java/com/sndiy/chatfin/feature/finance/transaction/ui/TransactionFormScreen.kt

package com.sndiy.chatfin.feature.finance.transaction.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sndiy.chatfin.core.data.local.entity.CategoryEntity
import com.sndiy.chatfin.core.data.local.entity.WalletEntity
import java.text.NumberFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionFormScreen(
    onNavigateBack: () -> Unit,
    viewModel: TransactionViewModel = hiltViewModel()
) {
    val formState by viewModel.formState.collectAsStateWithLifecycle()
    val listState by viewModel.listState.collectAsStateWithLifecycle()

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
                title = { Text("Tambah Transaksi") },
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

            // ── Input Nominal ─────────────────────────────────────────────────
            AmountInput(
                amount      = formState.amount,
                type        = formState.type,
                error       = formState.amountError,
                onAmountChange = viewModel::onAmountChange
            )

            Spacer(modifier = Modifier.height(8.dp))

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

                // ── Catatan ───────────────────────────────────────────────────
                OutlinedTextField(
                    value         = formState.note,
                    onValueChange = viewModel::onNoteChange,
                    label         = { Text("Catatan (opsional)") },
                    maxLines      = 3,
                    modifier      = Modifier.fillMaxWidth()
                )

                // ── Recurring ─────────────────────────────────────────────────
                RecurringToggle(
                    isRecurring       = formState.isRecurring,
                    interval          = formState.recurringInterval,
                    onToggle          = viewModel::onRecurringChange,
                    onIntervalChange  = viewModel::onRecurringIntervalChange
                )

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

            Surface(
                modifier      = Modifier
                    .weight(1f)
                    .clickable { onTypeChange(type) },
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

// ── Input nominal besar di tengah ─────────────────────────────────────────────
@Composable
private fun AmountInput(
    amount: String,
    type: TransactionType,
    error: String?,
    onAmountChange: (String) -> Unit
) {
    val amountColor = when (type) {
        TransactionType.EXPENSE  -> MaterialTheme.colorScheme.error
        TransactionType.INCOME   -> Color(0xFF1B8A4C)
        TransactionType.TRANSFER -> MaterialTheme.colorScheme.primary
    }

    val formatted = amount.toLongOrNull()?.let {
        NumberFormat.getNumberInstance(Locale("id", "ID")).format(it)
    } ?: ""

    Column(
        modifier            = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text  = "Rp",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        OutlinedTextField(
            value         = amount,
            onValueChange = onAmountChange,
            textStyle     = MaterialTheme.typography.displaySmall.copy(
                textAlign  = TextAlign.Center,
                color      = amountColor,
                fontWeight = FontWeight.Bold
            ),
            placeholder   = {
                Text(
                    text      = "0",
                    style     = MaterialTheme.typography.displaySmall,
                    textAlign = TextAlign.Center,
                    modifier  = Modifier.fillMaxWidth(),
                    color     = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            },
            isError       = error != null,
            supportingText = error?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
            ),
            colors        = OutlinedTextFieldDefaults.colors(
                focusedBorderColor   = Color.Transparent,
                unfocusedBorderColor = Color.Transparent
            ),
            modifier      = Modifier.fillMaxWidth()
        )
        if (formatted.isNotEmpty()) {
            Text(
                text  = "Rp $formatted",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

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

                Column(
                    modifier            = Modifier
                        .weight(1f)
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
                        .clickable { onSelect(category) }
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

// ── Toggle transaksi berulang ─────────────────────────────────────────────────
@Composable
private fun RecurringToggle(
    isRecurring: Boolean,
    interval: String?,
    onToggle: (Boolean) -> Unit,
    onIntervalChange: (String) -> Unit
) {
    val intervals = listOf("DAILY" to "Harian", "WEEKLY" to "Mingguan", "MONTHLY" to "Bulanan")

    Column {
        Row(
            modifier          = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text("Transaksi Berulang", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "Otomatis dicatat secara berkala",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(checked = isRecurring, onCheckedChange = onToggle)
        }

        if (isRecurring) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                intervals.forEach { (value, label) ->
                    FilterChip(
                        selected = interval == value,
                        onClick  = { onIntervalChange(value) },
                        label    = { Text(label) }
                    )
                }
            }
        }
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