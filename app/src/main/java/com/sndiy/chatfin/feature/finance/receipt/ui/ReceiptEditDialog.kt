package com.sndiy.chatfin.feature.finance.receipt.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sndiy.chatfin.core.data.local.entity.CategoryEntity
import com.sndiy.chatfin.core.data.local.entity.WalletEntity
import com.sndiy.chatfin.core.ocr.ParsedReceipt
import com.sndiy.chatfin.core.ocr.ParsedReceiptItem
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val merchantPresets = listOf(
    "Indomaret", "Alfamart", "Superindo", "Transmart",
    "Pertamina", "Starbucks", "Kopi Kenangan", "Janji Jiwa",
    "McDonald's", "KFC", "ShopeePay", "Gojek", "Grab"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceiptEditDialog(
    parsedReceipt: ParsedReceipt,
    wallets: List<WalletEntity>,
    categories: List<CategoryEntity>,
    onDismiss: () -> Unit,
    onSaveTransaction: (
        merchant: String,
        date: String,
        time: String,
        totalAmount: Long,
        walletId: String,
        categoryId: String,
        itemsSummary: String,
        itemsList: List<ParsedReceiptItem>
    ) -> Unit,
    modifier: Modifier = Modifier
) {
    val rupiahFmt = remember { NumberFormat.getNumberInstance(Locale("id", "ID")) }

    var merchant by remember(parsedReceipt) { mutableStateOf(parsedReceipt.merchant.orEmpty()) }
    var dateStr by remember(parsedReceipt) { mutableStateOf(parsedReceipt.date ?: LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)) }
    var timeStr by remember(parsedReceipt) { mutableStateOf(parsedReceipt.time ?: "12:00") }

    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    var items by remember(parsedReceipt) { mutableStateOf(parsedReceipt.items) }

    val hasItems = items.isNotEmpty()
    val itemsSum = items.sumOf { it.price }

    val formattedDateLabel = remember(dateStr) {
        runCatching {
            val parsed = LocalDate.parse(dateStr)
            parsed.format(DateTimeFormatter.ofPattern("dd MMM yyyy", Locale("id", "ID")))
        }.getOrDefault(dateStr)
    }

    // Jika ada daftar item, total nominal dikunci otomatis dari penjumlahan item
    var totalText by remember(parsedReceipt, itemsSum) {
        val calculated = if (hasItems && itemsSum > 0L) itemsSum else (parsedReceipt.totalAmount ?: 0L)
        mutableStateOf(if (calculated > 0L) rupiahFmt.format(calculated) else "")
    }

    var selectedWallet by remember(wallets) { mutableStateOf(wallets.firstOrNull()) }
    var selectedCategory by remember(categories) { mutableStateOf(categories.firstOrNull { it.name.contains("Belanja", ignoreCase = true) || it.name.contains("Makanan", ignoreCase = true) } ?: categories.firstOrNull()) }

    var amountError by remember { mutableStateOf<String?>(null) }
    var walletError by remember { mutableStateOf<String?>(null) }

    val scrollState = rememberScrollState()

    // Singkronisasi otomatis: setiap kali daftar item diubah/ditambah/dihapus, total nominal langsung diperbarui
    LaunchedEffect(items) {
        if (items.isNotEmpty()) {
            val sum = items.sumOf { it.price }
            totalText = if (sum > 0L) rupiahFmt.format(sum) else ""
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header Dialog
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Konfirmasi & Edit Struk",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Periksa dan lengkapi data struk sebelum disimpan",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Tutup")
                }
            }

            // Warning Banner jika ada field buram/gagal terbaca
            if (parsedReceipt.hasLowConfidenceField) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Peringatan",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Beberapa field (ditandai ikon peringatan) buram/gagal terbaca otomatis. Silakan isi atau perbaiki manual.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }

            // 1. Nama Merchant
            OutlinedTextField(
                value = merchant,
                onValueChange = { merchant = it },
                label = {
                    Text(if (parsedReceipt.isMerchantLowConfidence) "Nama Merchant / Toko (Isi Manual)" else "Nama Merchant / Toko")
                },
                leadingIcon = {
                    if (parsedReceipt.isMerchantLowConfidence) {
                        Icon(Icons.Default.Warning, contentDescription = "Perlu Perhatian", tint = MaterialTheme.colorScheme.error)
                    } else {
                        Icon(Icons.Default.Store, contentDescription = "Merchant")
                    }
                },
                trailingIcon = {
                    if (merchant.isNotEmpty()) {
                        IconButton(onClick = { merchant = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Hapus")
                        }
                    }
                },
                isError = parsedReceipt.isMerchantLowConfidence && merchant.isBlank(),
                colors = if (parsedReceipt.isMerchantLowConfidence) {
                    OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.error,
                        unfocusedBorderColor = MaterialTheme.colorScheme.error.copy(alpha = 0.6f)
                    )
                } else OutlinedTextFieldDefaults.colors(),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            // Saran Merchant Populer (Tap-first)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(merchantPresets) { preset ->
                    val isSelected = merchant.equals(preset, ignoreCase = true)
                    FilterChip(
                        selected = isSelected,
                        onClick = { merchant = if (isSelected) "" else preset },
                        label = { Text(preset, style = MaterialTheme.typography.labelMedium) },
                        leadingIcon = if (isSelected) {
                            { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp)) }
                        } else null
                    )
                }
            }

            // 2. Tanggal & Waktu Transaksi (Interaktif Kalender & Waktu)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1.5f)
                        .clickable { showDatePicker = true }
                ) {
                    OutlinedTextField(
                        value = formattedDateLabel,
                        onValueChange = {},
                        readOnly = true,
                        enabled = false,
                        label = { Text("Tanggal") },
                        leadingIcon = {
                            if (parsedReceipt.isDateLowConfidence) {
                                Icon(Icons.Default.Warning, contentDescription = "Perlu Perhatian", tint = MaterialTheme.colorScheme.error)
                            } else {
                                Icon(Icons.Default.CalendarToday, contentDescription = "Tanggal")
                            }
                        },
                        trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = "Pilih Tanggal") },
                        colors = OutlinedTextFieldDefaults.colors(
                            disabledTextColor = MaterialTheme.colorScheme.onSurface,
                            disabledBorderColor = MaterialTheme.colorScheme.outline,
                            disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            disabledLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { showTimePicker = true }
                ) {
                    OutlinedTextField(
                        value = timeStr,
                        onValueChange = {},
                        readOnly = true,
                        enabled = false,
                        label = { Text("Waktu") },
                        leadingIcon = { Icon(Icons.Default.Schedule, contentDescription = "Waktu") },
                        trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = "Pilih Waktu") },
                        colors = OutlinedTextFieldDefaults.colors(
                            disabledTextColor = MaterialTheme.colorScheme.onSurface,
                            disabledBorderColor = MaterialTheme.colorScheme.outline,
                            disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            disabledLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            }

            // 3. Daftar Item Belanja (Dapat Diedit / Ditambah / Dihapus & Format Rupiah)
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Daftar Item (${items.size})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        TextButton(
                            onClick = {
                                items = items + ParsedReceiptItem(name = "", price = 0L)
                            }
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Tambah Item")
                        }
                    }

                    if (items.isEmpty()) {
                        Text(
                            text = "Belum ada item terdeteksi. Anda bisa menambahkan item manual di atas.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        items.forEachIndexed { index, item ->
                            val itemPriceFormatted = if (item.price > 0L) rupiahFmt.format(item.price) else ""
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = item.name,
                                    onValueChange = { updatedName ->
                                        items = items.toMutableList().apply {
                                            this[index] = item.copy(name = updatedName)
                                        }
                                    },
                                    placeholder = { Text("Nama item") },
                                    modifier = Modifier.weight(1.8f),
                                    singleLine = true
                                )

                                OutlinedTextField(
                                    value = itemPriceFormatted,
                                    onValueChange = { updatedPriceInput ->
                                        val newPrice = updatedPriceInput.filter { it.isDigit() }.toLongOrNull() ?: 0L
                                        items = items.toMutableList().apply {
                                            this[index] = item.copy(price = newPrice)
                                        }
                                    },
                                    prefix = { Text("Rp ", style = MaterialTheme.typography.bodySmall) },
                                    placeholder = { Text("0") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.weight(1.2f),
                                    singleLine = true
                                )

                                IconButton(
                                    onClick = {
                                        items = items.filterIndexed { i, _ -> i != index }
                                    }
                                ) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "Hapus Item",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 4. Pilih Dompet Sumber
            Text("Simpan ke Dompet", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                wallets.forEach { wallet ->
                    val isSelected = selectedWallet?.id == wallet.id
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            selectedWallet = wallet
                            walletError = null
                        },
                        label = { Text(wallet.name) },
                        leadingIcon = {
                            if (isSelected) Icon(Icons.Default.Check, contentDescription = null)
                        }
                    )
                }
            }
            if (walletError != null) {
                Text(walletError!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            // 5. Pilih Kategori Transaksi
            Text("Kategori Transaksi", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                categories.take(6).forEach { category ->
                    val isSelected = selectedCategory?.id == category.id
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedCategory = category },
                        label = { Text(category.name) },
                        leadingIcon = {
                            if (isSelected) Icon(Icons.Default.Check, contentDescription = null)
                        }
                    )
                }
            }

            // 6. TOTAL NOMINAL STRUK (DITARUH DI BAGIAN BAWAH, TULISAN BESAR, KARTU READ-ONLY)
            val digitsOnly = totalText.filter { it.isDigit() }
            val totalLong = digitsOnly.toLongOrNull() ?: 0L

            if (hasItems) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f)
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Lock,
                                    contentDescription = "Terkunci dari Item",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Total Nominal Struk",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            Text(
                                text = "Penjumlahan ${items.size} item belanja",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                            )
                        }

                        Text(
                            text = "Rp ${if (totalLong > 0L) rupiahFmt.format(totalLong) else "0"}",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            } else {
                OutlinedTextField(
                    value = totalText,
                    onValueChange = { input ->
                        val digits = input.filter { it.isDigit() }
                        val num = digits.toLongOrNull()
                        totalText = if (num != null && num > 0) rupiahFmt.format(num) else digits
                        amountError = null
                    },
                    label = { Text("Total Nominal Struk (Isi Manual)") },
                    placeholder = { Text("0") },
                    prefix = { Text("Rp ", fontWeight = FontWeight.Bold) },
                    leadingIcon = {
                        Icon(Icons.Default.Payments, contentDescription = "Total")
                    },
                    supportingText = {
                        if (amountError != null) {
                            Text(amountError!!, color = MaterialTheme.colorScheme.error)
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = amountError != null,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }

            // Action Buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Batal / Rescan")
                }

                Button(
                    onClick = {
                        val amount = totalText.filter { it.isDigit() }.toLongOrNull() ?: 0L
                        if (amount <= 0) {
                            amountError = "Nominal total harus lebih dari 0"
                            return@Button
                        }
                        val wallet = selectedWallet
                        if (wallet == null) {
                            walletError = "Pilih dompet sumber terlebih dahulu"
                            return@Button
                        }
                        val category = selectedCategory ?: categories.firstOrNull()
                        val categoryId = category?.id ?: ""

                        val validItems = items.filter { it.name.isNotBlank() }
                        val itemsSummary = if (validItems.isNotEmpty()) {
                            validItems.joinToString(", ") { "${it.name} (Rp ${rupiahFmt.format(it.price)})" }
                        } else ""

                        val finalMerchant = merchant.ifBlank { "Struk Belanja" }

                        onSaveTransaction(
                            finalMerchant,
                            dateStr,
                            timeStr,
                            amount,
                            wallet.id,
                            categoryId,
                            itemsSummary,
                            validItems
                        )
                    },
                    modifier = Modifier.weight(1.5f)
                ) {
                    Icon(Icons.Default.Save, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Simpan Transaksi")
                }
            }
        }
    }

    // Modal Date Picker
    if (showDatePicker) {
        val initialEpochMillis = remember(dateStr) {
            runCatching {
                LocalDate.parse(dateStr).toEpochDay() * 86400000L
            }.getOrDefault(System.currentTimeMillis())
        }
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = initialEpochMillis
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        dateStr = LocalDate.ofEpochDay(millis / 86400000L).format(DateTimeFormatter.ISO_LOCAL_DATE)
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Batal") }
            }
        ) { DatePicker(state = datePickerState) }
    }

    // Modal Time Picker
    if (showTimePicker) {
        val initialHour = runCatching { timeStr.split(":")[0].toInt() }.getOrDefault(12)
        val initialMinute = runCatching { timeStr.split(":")[1].toInt() }.getOrDefault(0)
        val timePickerState = rememberTimePickerState(
            initialHour = initialHour,
            initialMinute = initialMinute,
            is24Hour = true
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    timeStr = String.format(Locale.getDefault(), "%02d:%02d", timePickerState.hour, timePickerState.minute)
                    showTimePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) { Text("Batal") }
            },
            text = { TimePicker(state = timePickerState) }
        )
    }
}
