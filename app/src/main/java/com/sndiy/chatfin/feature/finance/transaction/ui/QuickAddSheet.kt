// app/src/main/java/com/sndiy/chatfin/feature/finance/transaction/ui/QuickAddSheet.kt

package com.sndiy.chatfin.feature.finance.transaction.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sndiy.chatfin.core.data.local.entity.CategoryEntity
import com.sndiy.chatfin.core.data.local.entity.WalletEntity
import com.sndiy.chatfin.core.ui.component.NumpadAmountDisplay
import com.sndiy.chatfin.core.ui.component.NumpadKeyboard
import com.sndiy.chatfin.core.ui.component.NumpadPresetChips
import com.sndiy.chatfin.core.ui.theme.ExpenseRed
import com.sndiy.chatfin.core.ui.theme.IncomeGreen
import java.text.NumberFormat
import java.util.Locale

// Preset catatan yang bisa ditap langsung
private val notePresets = listOf(
    "Makan", "Minum", "Kopi", "Belanja", "Bensin",
    "Parkir", "Transportasi", "Listrik", "Air", "Internet",
    "Hiburan", "Kesehatan", "Gaji", "Bonus", "Lainnya"
)

data class QuickAddResult(
    val type: String,       // INCOME | EXPENSE
    val amount: Long,
    val categoryId: String,
    val walletId: String,
    val note: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickAddSheet(
    expenseCategories: List<CategoryEntity>,
    incomeCategories: List<CategoryEntity>,
    wallets: List<WalletEntity>,
    onSave: (QuickAddResult) -> Unit,
    onDismiss: () -> Unit,
    onFullForm: () -> Unit
) {
    val fmt             = remember { NumberFormat.getNumberInstance(Locale("id", "ID")) }
    val scrollState     = rememberScrollState()

    var isExpense         by remember { mutableStateOf(true) }
    var rawDigits         by remember { mutableStateOf("") }      // digit murni tanpa separator
    var selectedCategory  by remember { mutableStateOf<CategoryEntity?>(null) }
    // Berkunci pada `wallets`: tanpa key, sheet yang tersusun sebelum daftar
    // dompet sempat terbit akan memegang null selamanya dan tombol Simpan
    // tidak pernah bekerja.
    var selectedWallet    by remember(wallets) { mutableStateOf(wallets.firstOrNull()) }
    var selectedNote      by remember { mutableStateOf("") }       // catatan dari chip preset
    var showCustomNote    by remember { mutableStateOf(true) }     // otomatis aktifkan opsi 'Lainnya' / custom note
    var customNoteText    by remember { mutableStateOf("") }
    var amountError       by remember { mutableStateOf(false) }
    var categoryError     by remember { mutableStateOf(false) }

    val categories = if (isExpense) expenseCategories else incomeCategories

    // Format tampilan nominal
    val formattedAmount = remember(rawDigits) {
        val num = rawDigits.toLongOrNull()
        if (num != null && num > 0) fmt.format(num) else rawDigits.ifBlank { "" }
    }

    // Ganti tipe = mulai dari nol. Kategori SENGAJA tidak dipilihkan otomatis:
    // "Makanan & Minuman" selalu jadi kategori pertama (sortOrder 0), jadi
    // memilih categories.firstOrNull() di sini membuat setiap transaksi yang
    // user-nya tidak menyentuh baris kategori — mis. tarik tunai yang cuma
    // diketik di catatan — tersimpan sebagai makanan tanpa peringatan apa pun.
    LaunchedEffect(isExpense) {
        selectedCategory = null
        categoryError = false
        selectedNote = ""
        showCustomNote = true
        customNoteText = ""
    }

    ModalBottomSheet(
        onDismissRequest = { onDismiss() },
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
                Text("Tambah Cepat", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                TextButton(onClick = { onDismiss(); onFullForm() }) {
                    Text("Form Lengkap")
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
                    modifier = Modifier.weight(1f).clickable { isExpense = true },
                    color    = if (isExpense) ExpenseRed.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant,
                    shape    = MaterialTheme.shapes.medium
                ) {
                    Text(
                        "Pengeluaran",
                        modifier   = Modifier.padding(vertical = 12.dp),
                        textAlign  = TextAlign.Center,
                        style      = MaterialTheme.typography.labelLarge,
                        color      = if (isExpense) ExpenseRed else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (isExpense) FontWeight.Bold else FontWeight.Normal
                    )
                }
                Surface(
                    modifier = Modifier.weight(1f).clickable { isExpense = false },
                    color    = if (!isExpense) IncomeGreen.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant,
                    shape    = MaterialTheme.shapes.medium
                ) {
                    Text(
                        "Pemasukan",
                        modifier   = Modifier.padding(vertical = 12.dp),
                        textAlign  = TextAlign.Center,
                        style      = MaterialTheme.typography.labelLarge,
                        color      = if (!isExpense) IncomeGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (!isExpense) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }

            // ── Display nominal (read-only, diisi oleh numpad di bawah) ──────
            NumpadAmountDisplay(
                formattedAmount = formattedAmount,
                currencyPrefix  = "Rp",
                error           = if (amountError) "Masukkan nominal terlebih dahulu" else null
            )

            // ── Preset nominal chip ──────────────────────────────────────────
            NumpadPresetChips(
                presets     = listOf(5_000L, 10_000L, 20_000L, 50_000L, 100_000L, 200_000L),
                currentRaw  = rawDigits,
                onSelect    = { preset ->
                    rawDigits = preset.toString()
                    amountError = false
                }
            )

            // ── Numpad Visual ────────────────────────────────────────────────
            NumpadKeyboard(
                rawDigits   = rawDigits,
                onDigit     = { key ->
                    rawDigits = (rawDigits + key).trimStart('0').take(12).ifBlank { key }
                    amountError = false
                },
                onBackspace = {
                    if (rawDigits.isNotEmpty()) rawDigits = rawDigits.dropLast(1)
                },
                onClear     = { rawDigits = "" },
                buttonSize  = 68.dp
            )

            HorizontalDivider()

            // ── Kategori chip ─────────────────────────────────────────────────
            Text(
                text  = if (categoryError) "Kategori — pilih dulu" else "Kategori",
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
                        leadingIcon = {
                            Box(Modifier.size(8.dp).clip(CircleShape).background(catColor))
                        }
                    )
                }
            }

            // ── Dompet chip (jika lebih dari 1) ──────────────────────────────
            if (wallets.size > 1) {
                Text("Dompet", style = MaterialTheme.typography.labelMedium)
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

            // ── Catatan — Chip Preset (tap) ───────────────────────────────────
            Text("Catatan (opsional)", style = MaterialTheme.typography.labelMedium)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(notePresets) { preset ->
                    val isSelected = if (preset == "Lainnya") showCustomNote else (selectedNote == preset && !showCustomNote)
                    FilterChip(
                        selected = isSelected,
                        onClick  = {
                            if (preset == "Lainnya") {
                                showCustomNote = true
                                selectedNote = ""
                            } else {
                                selectedNote = if (isSelected) "" else preset
                                showCustomNote = false
                                customNoteText = ""
                            }
                        },
                        label    = { Text(preset) },
                        leadingIcon = if (isSelected) {
                            { Icon(Icons.Default.Check, null, Modifier.size(14.dp)) }
                        } else null
                    )
                }
            }

            // TextField muncul hanya jika user tap "Lainnya"
            if (showCustomNote) {
                OutlinedTextField(
                    value           = customNoteText,
                    onValueChange   = { customNoteText = it },
                    label           = { Text("Catatan custom") },
                    leadingIcon     = { Icon(Icons.Default.Edit, null) },
                    trailingIcon    = {
                        if (customNoteText.isNotEmpty()) {
                            IconButton(onClick = { customNoteText = "" }) {
                                Icon(Icons.Default.Clear, null)
                            }
                        }
                    },
                    singleLine      = true,
                    modifier        = Modifier.fillMaxWidth()
                )
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

                    val finalNote = when {
                        showCustomNote -> customNoteText.trim()
                        selectedNote.isNotBlank() -> selectedNote
                        else -> ""
                    }

                    onSave(QuickAddResult(
                        type       = if (isExpense) "EXPENSE" else "INCOME",
                        amount     = parsedAmount,
                        categoryId = cat.id,
                        walletId   = wal.id,
                        note       = finalNote
                    ))
                },
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                Icon(Icons.Default.Check, null)
                Spacer(Modifier.width(8.dp))
                Text("Simpan", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
