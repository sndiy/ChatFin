// app/src/main/java/com/sndiy/chatfin/feature/chat/ui/QuickEditTransactionSheet.kt

package com.sndiy.chatfin.feature.chat.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sndiy.chatfin.R
import com.sndiy.chatfin.core.data.local.entity.CategoryEntity
import com.sndiy.chatfin.core.data.local.entity.TransactionEntity
import com.sndiy.chatfin.core.data.local.entity.WalletEntity
import com.sndiy.chatfin.core.ui.component.NumpadAmountDisplay
import com.sndiy.chatfin.core.ui.component.NumpadKeyboard
import com.sndiy.chatfin.core.ui.component.NumpadPresetChips
import com.sndiy.chatfin.core.ui.theme.ExpenseRed
import com.sndiy.chatfin.core.ui.theme.IncomeGreen
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Sheet edit cepat untuk transaksi yang SUDAH tersimpan, dipicu dari tombol
 * Edit di kartu daftar transaksi chat. Dimodelkan dari QuickAddSheet (numpad +
 * chip kategori/dompet), tapi terisi dari [transaction] dan menyimpan lewat
 * update, bukan insert. Kategori/dompet yang ditampilkan sudah scoped per akun
 * aktif + tipe transaksi (list yang sama dipakai alur tambah-transaksi).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickEditTransactionSheet(
    transaction: TransactionEntity,
    expenseCategories: List<CategoryEntity>,
    incomeCategories: List<CategoryEntity>,
    wallets: List<WalletEntity>,
    onSave: (type: String, amount: Long, categoryId: String, walletId: String, date: LocalDate) -> Unit,
    onDismiss: () -> Unit,
    onFullEdit: (String) -> Unit
) {
    val fmt         = remember { NumberFormat.getNumberInstance(Locale("id", "ID")) }
    val dateFmt     = remember { DateTimeFormatter.ofPattern("dd MMM yyyy", Locale("id", "ID")) }
    val scrollState = rememberScrollState()

    var isExpense        by remember { mutableStateOf(transaction.type != "INCOME") }
    var rawDigits         by remember { mutableStateOf(transaction.amount.toString()) }
    var selectedCategory  by remember {
        mutableStateOf((if (isExpense) expenseCategories else incomeCategories).find { it.id == transaction.categoryId })
    }
    var selectedWallet    by remember(wallets) {
        mutableStateOf(wallets.find { it.id == transaction.walletId } ?: wallets.firstOrNull())
    }
    var selectedDate      by remember {
        mutableStateOf(runCatching { LocalDate.parse(transaction.date) }.getOrElse { LocalDate.now() })
    }
    var showDatePicker    by remember { mutableStateOf(false) }
    var amountError       by remember { mutableStateOf(false) }
    var categoryError     by remember { mutableStateOf(false) }

    val categories = if (isExpense) expenseCategories else incomeCategories

    val formattedAmount = remember(rawDigits) {
        val num = rawDigits.toLongOrNull()
        if (num != null && num > 0) fmt.format(num) else rawDigits.ifBlank { "" }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState       = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier            = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // ── Header ──────────────────────────────────────────────────────────
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.quick_edit_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                TextButton(onClick = { onDismiss(); onFullEdit(transaction.id) }) {
                    Text(stringResource(R.string.quick_edit_full_edit))
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.Default.OpenInNew, null, Modifier.size(16.dp))
                }
            }

            // ── Tipe toggle: Pengeluaran / Pemasukan ─────────────────────────
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    modifier = Modifier.weight(1f).clickable {
                        isExpense = true
                        selectedCategory = expenseCategories.find { it.id == transaction.categoryId }
                        categoryError = false
                    },
                    color    = if (isExpense) ExpenseRed.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant,
                    shape    = MaterialTheme.shapes.medium
                ) {
                    Text(
                        stringResource(R.string.quick_edit_expense),
                        modifier   = Modifier.padding(vertical = 12.dp),
                        textAlign  = TextAlign.Center,
                        style      = MaterialTheme.typography.labelLarge,
                        color      = if (isExpense) ExpenseRed else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (isExpense) FontWeight.Bold else FontWeight.Normal
                    )
                }
                Surface(
                    modifier = Modifier.weight(1f).clickable {
                        isExpense = false
                        selectedCategory = incomeCategories.find { it.id == transaction.categoryId }
                        categoryError = false
                    },
                    color    = if (!isExpense) IncomeGreen.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant,
                    shape    = MaterialTheme.shapes.medium
                ) {
                    Text(
                        stringResource(R.string.quick_edit_income),
                        modifier   = Modifier.padding(vertical = 12.dp),
                        textAlign  = TextAlign.Center,
                        style      = MaterialTheme.typography.labelLarge,
                        color      = if (!isExpense) IncomeGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (!isExpense) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }

            // ── Display nominal ─────────────────────────────────────────────
            NumpadAmountDisplay(
                formattedAmount = formattedAmount,
                currencyPrefix  = "Rp",
                error           = if (amountError) stringResource(R.string.quick_edit_amount_error) else null
            )

            NumpadPresetChips(
                presets     = listOf(5_000L, 10_000L, 20_000L, 50_000L, 100_000L, 200_000L),
                currentRaw  = rawDigits,
                onSelect    = { preset -> rawDigits = preset.toString(); amountError = false }
            )

            NumpadKeyboard(
                rawDigits   = rawDigits,
                onDigit     = { key ->
                    rawDigits = (rawDigits + key).trimStart('0').take(12).ifBlank { key }
                    amountError = false
                },
                onBackspace = { if (rawDigits.isNotEmpty()) rawDigits = rawDigits.dropLast(1) },
                onClear     = { rawDigits = "" },
                buttonSize  = 68.dp
            )

            HorizontalDivider()

            // ── Kategori chip ─────────────────────────────────────────────────
            Text(
                text  = stringResource(if (categoryError) R.string.quick_edit_category_error else R.string.quick_edit_category_label),
                style = MaterialTheme.typography.labelMedium,
                color = if (categoryError) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(categories) { cat ->
                    val isSelected = selectedCategory?.id == cat.id
                    val catColor = runCatching {
                        Color(android.graphics.Color.parseColor(cat.colorHex))
                    }.getOrElse { Color.Gray }

                    FilterChip(
                        selected    = isSelected,
                        onClick     = { selectedCategory = cat; categoryError = false },
                        label       = { Text(cat.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        leadingIcon = { Box(Modifier.size(8.dp).clip(CircleShape).background(catColor)) }
                    )
                }
            }

            // ── Dompet chip ───────────────────────────────────────────────────
            if (wallets.size > 1) {
                Text(stringResource(R.string.quick_edit_wallet_label), style = MaterialTheme.typography.labelMedium)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(wallets) { w ->
                        FilterChip(
                            selected = selectedWallet?.id == w.id,
                            onClick  = { selectedWallet = w },
                            label    = { Text(w.name) }
                        )
                    }
                }
            }

            // ── Tanggal ───────────────────────────────────────────────────────
            Text(stringResource(R.string.quick_edit_date_label), style = MaterialTheme.typography.labelMedium)
            OutlinedCard(onClick = { showDatePicker = true }, modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier              = Modifier.padding(12.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Text(selectedDate.format(dateFmt), style = MaterialTheme.typography.bodyMedium)
                    Icon(Icons.Default.CalendarMonth, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            // ── Tombol Simpan ────────────────────────────────────────────────
            Button(
                onClick = {
                    val parsedAmount = rawDigits.toLongOrNull()
                    if (parsedAmount == null || parsedAmount <= 0) {
                        amountError = true
                        return@Button
                    }
                    val cat = selectedCategory
                    if (cat == null) {
                        categoryError = true
                        return@Button
                    }
                    val wal = selectedWallet ?: return@Button

                    onSave(
                        if (isExpense) "EXPENSE" else "INCOME",
                        parsedAmount,
                        cat.id,
                        wal.id,
                        selectedDate
                    )
                },
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                Icon(Icons.Default.Check, null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.quick_edit_save), fontWeight = FontWeight.SemiBold)
            }
        }
    }

    if (showDatePicker) {
        val dpState = rememberDatePickerState(
            initialSelectedDateMillis = selectedDate.toEpochDay() * 86_400_000L
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    dpState.selectedDateMillis?.let { millis ->
                        selectedDate = LocalDate.ofEpochDay(millis / 86_400_000L)
                    }
                    showDatePicker = false
                }) { Text(stringResource(R.string.quick_edit_date_ok)) }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text(stringResource(R.string.quick_edit_date_cancel)) } }
        ) { DatePicker(state = dpState) }
    }
}
