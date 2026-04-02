// app/src/main/java/com/sndiy/chatfin/feature/finance/transaction/ui/QuickAddSheet.kt

package com.sndiy.chatfin.feature.finance.transaction.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sndiy.chatfin.core.data.local.entity.CategoryEntity
import com.sndiy.chatfin.core.data.local.entity.WalletEntity
import com.sndiy.chatfin.core.ui.theme.ExpenseRed
import com.sndiy.chatfin.core.ui.theme.IncomeGreen
import java.text.NumberFormat
import java.util.Locale

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
    val fmt             = NumberFormat.getNumberInstance(Locale("id", "ID"))
    val focusRequester  = remember { FocusRequester() }
    val keyboard        = LocalSoftwareKeyboardController.current

    var isExpense         by remember { mutableStateOf(true) }
    var amount            by remember { mutableStateOf("") }
    var selectedCategory  by remember { mutableStateOf<CategoryEntity?>(null) }
    var selectedWallet    by remember { mutableStateOf(wallets.firstOrNull()) }
    var note              by remember { mutableStateOf("") }
    var amountError       by remember { mutableStateOf(false) }

    val categories = if (isExpense) expenseCategories else incomeCategories

    // Auto-select first category
    LaunchedEffect(isExpense) {
        selectedCategory = categories.firstOrNull()
    }

    // Auto-focus amount
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState       = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier            = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
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

            // Type toggle
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
                        modifier   = Modifier.padding(vertical = 10.dp),
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
                        modifier   = Modifier.padding(vertical = 10.dp),
                        textAlign  = TextAlign.Center,
                        style      = MaterialTheme.typography.labelLarge,
                        color      = if (!isExpense) IncomeGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (!isExpense) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }

            // Amount input
            OutlinedTextField(
                value           = amount,
                onValueChange   = { amount = it.filter { c -> c.isDigit() }; amountError = false },
                label           = { Text("Nominal") },
                prefix          = { Text("Rp ") },
                isError         = amountError,
                supportingText  = if (amountError) { { Text("Isi nominal") } } else null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { keyboard?.hide() }),
                singleLine      = true,
                modifier        = Modifier.fillMaxWidth().focusRequester(focusRequester)
            )

            // Quick amount chips
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val presets = listOf(5_000L, 10_000L, 20_000L, 50_000L, 100_000L)
                items(presets) { preset ->
                    FilterChip(
                        selected = amount == preset.toString(),
                        onClick  = { amount = preset.toString(); amountError = false },
                        label    = { Text(if (preset >= 100_000) "${preset/1000}rb" else "${fmt.format(preset)}") }
                    )
                }
            }

            // Category selector
            Text("Kategori", style = MaterialTheme.typography.labelMedium)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(categories) { cat ->
                    val isSelected = selectedCategory?.id == cat.id
                    val catColor = runCatching {
                        Color(android.graphics.Color.parseColor(cat.colorHex))
                    }.getOrElse { Color.Gray }

                    FilterChip(
                        selected    = isSelected,
                        onClick     = { selectedCategory = cat },
                        label       = { Text(cat.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        leadingIcon = {
                            Box(Modifier.size(8.dp).clip(CircleShape).background(catColor))
                        }
                    )
                }
            }

            // Wallet selector (hanya kalau lebih dari 1)
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

            // Note (optional, collapsed)
            OutlinedTextField(
                value         = note,
                onValueChange = { note = it },
                label         = { Text("Catatan (opsional)") },
                singleLine    = true,
                modifier      = Modifier.fillMaxWidth()
            )

            // Save button
            Button(
                onClick = {
                    val parsedAmount = amount.toLongOrNull()
                    if (parsedAmount == null || parsedAmount <= 0) {
                        amountError = true
                        return@Button
                    }
                    val cat = selectedCategory ?: return@Button
                    val wal = selectedWallet ?: return@Button

                    onSave(QuickAddResult(
                        type       = if (isExpense) "EXPENSE" else "INCOME",
                        amount     = parsedAmount,
                        categoryId = cat.id,
                        walletId   = wal.id,
                        note       = note.trim()
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
