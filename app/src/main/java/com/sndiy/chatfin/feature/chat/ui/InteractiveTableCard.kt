package com.sndiy.chatfin.feature.chat.ui

import android.graphics.Picture
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Compare
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.draw
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sndiy.chatfin.core.data.local.entity.CategoryEntity
import com.sndiy.chatfin.core.data.local.entity.TransactionEntity
import com.sndiy.chatfin.core.data.local.entity.WalletEntity
import com.sndiy.chatfin.core.ui.theme.ExpenseRed
import com.sndiy.chatfin.core.ui.theme.IncomeGreen
import com.sndiy.chatfin.core.ui.theme.MaiPurple
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class TableTemplate(val label: String) {
    CATEGORY_SUMMARY("Ringkasan Kategori"),
    DAILY_DETAILS("Rincian Harian"),
    MONTHLY_COMPARISON("Perbandingan Bulan"),
    AUTO_AI("Biarkan AI Desain")
}

data class CategorySummaryRow(
    val categoryName: String,
    val count: Int,
    val totalAmount: Long,
    val percentage: Float
)

data class MonthlyComparisonRow(
    val monthName: String,
    val totalExpense: Long,
    val totalIncome: Long,
    val netBalance: Long
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InteractiveTableCard(
    title: String,
    transactions: List<TransactionEntity>,
    categories: List<CategoryEntity> = emptyList(),
    wallets: List<WalletEntity> = emptyList(),
    initialTemplate: TableTemplate = TableTemplate.CATEGORY_SUMMARY,
    categoryNames: List<String> = emptyList(),
    walletNames: List<String> = emptyList(),
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val picture = remember { Picture() }

    var selectedTemplate by remember { mutableStateOf(initialTemplate) }
    var isExporting by remember { mutableStateOf(false) }

    val fmt = remember { NumberFormat.getNumberInstance(Locale("id", "ID")) }

    // Nama tidak ketemu di data akun = tidak difilter, sama seperti
    // TransactionListCard/InteractiveChartCard — salah eja dari AI tidak
    // membuat tabel diam-diam kosong.
    val categoryIds = remember(categoryNames, categories) {
        categoryNames.takeIf { it.isNotEmpty() }
            ?.let { names -> categories.filter { c -> names.any { n -> n.equals(c.name, true) } }.map { it.id }.toSet() }
            ?.takeIf { it.isNotEmpty() }
    }
    val walletIds = remember(walletNames, wallets) {
        walletNames.takeIf { it.isNotEmpty() }
            ?.let { names -> wallets.filter { w -> names.any { n -> n.equals(w.name, true) } }.map { it.id }.toSet() }
            ?.takeIf { it.isNotEmpty() }
    }
    val activeFilters = listOfNotNull(
        categoryIds?.let { categoryNames.joinToString(", ") },
        walletIds?.let { walletNames.joinToString(", ") }
    )
    // Filter kategori/dompet diterapkan SEKALI di sini, diteruskan ke ketiga
    // renderer di bawah. txType SENGAJA tidak diterapkan di titik ini —
    // RenderCategorySummaryTable mempertahankan filter EXPENSE internalnya
    // sendiri (labelnya "TOTAL PENGELUARAN"), sedangkan DailyDetails/
    // MonthlyComparison memang dirancang menampilkan kedua tipe berdampingan.
    val filteredTransactions = remember(transactions, categoryIds, walletIds) {
        transactions
            .filter { categoryIds == null || it.categoryId in categoryIds }
            .filter { walletIds == null || it.walletId in walletIds }
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header Section
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.TableChart,
                        contentDescription = null,
                        tint = MaiPurple
                    )
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                // PNG Download Button
                FilledTonalIconButton(
                    onClick = {
                        coroutineScope.launch {
                            isExporting = true
                            try {
                                val bitmap = ChartExportUtil.createBitmapFromPicture(picture)
                                ChartExportUtil.saveAndShareBitmap(context, bitmap, title)
                            } catch (e: Exception) {
                                // Capture fallback
                            } finally {
                                isExporting = false
                            }
                        }
                    },
                    modifier = Modifier.size(36.dp)
                ) {
                    if (isExporting) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = "Download PNG",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            if (activeFilters.isNotEmpty()) {
                Text(
                    "Filter: ${activeFilters.joinToString(" · ")}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaiPurple
                )
            }

            // Template Selector Buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                TableTemplate.values().forEach { template ->
                    FilterChip(
                        selected = (selectedTemplate == template),
                        onClick = { selectedTemplate = template },
                        label = { Text(template.label, fontSize = 12.sp) },
                        leadingIcon = {
                            val icon = when (template) {
                                TableTemplate.CATEGORY_SUMMARY -> Icons.Outlined.Category
                                TableTemplate.DAILY_DETAILS -> Icons.Outlined.DateRange
                                TableTemplate.MONTHLY_COMPARISON -> Icons.Outlined.Compare
                                TableTemplate.AUTO_AI -> Icons.Default.AutoAwesome
                            }
                            Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp))
                        }
                    )
                }
            }

            Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            // Professional Rendered Table Area (Recorded into Picture)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .drawWithContent {
                        val width = size.width.toInt().coerceAtLeast(1)
                        val height = size.height.toInt().coerceAtLeast(1)
                        val pictureCanvas = androidx.compose.ui.graphics.Canvas(
                            picture.beginRecording(width, height)
                        )
                        draw(this, layoutDirection, pictureCanvas, size) {
                            this@drawWithContent.drawContent()
                        }
                        picture.endRecording()
                        drawContent()
                    }
                    .padding(8.dp)
            ) {
                if (filteredTransactions.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "Belum ada data transaksi untuk dibuatkan tabel.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    when (selectedTemplate) {
                        TableTemplate.CATEGORY_SUMMARY -> RenderCategorySummaryTable(filteredTransactions, categories, fmt)
                        TableTemplate.DAILY_DETAILS -> RenderDailyDetailsTable(filteredTransactions, fmt)
                        TableTemplate.MONTHLY_COMPARISON -> RenderMonthlyComparisonTable(filteredTransactions, fmt)
                        TableTemplate.AUTO_AI -> RenderCategorySummaryTable(filteredTransactions, categories, fmt)
                    }
                }
            }
        }
    }
}

// ── Template 1: Ringkasan Kategori (Excel / Sheets Style) ──────────────────────
@Composable
private fun RenderCategorySummaryTable(
    transactions: List<TransactionEntity>,
    categories: List<CategoryEntity>,
    fmt: NumberFormat
) {
    val totalExpense = remember(transactions) {
        transactions.filter { it.type == "EXPENSE" }.sumOf { it.amount }.coerceAtLeast(1L)
    }

    // FIX: dulu groupBy { tx.note } — mengelompokkan per JUDUL transaksi
    // (variabel dinamai catName tapi isinya bukan kategori), jadi dua transaksi
    // kategori sama dengan judul beda jadi dua baris terpisah. Sekarang
    // benar-benar per categoryId.
    val summaryRows = remember(transactions, categories) {
        transactions.filter { it.type == "EXPENSE" }
            .groupBy { it.categoryId }
            .map { (categoryId, list) ->
                val name = categories.find { it.id == categoryId }?.name ?: "Tanpa kategori"
                val sum = list.sumOf { it.amount }
                CategorySummaryRow(
                    categoryName = name,
                    count = list.size,
                    totalAmount = sum,
                    percentage = (sum.toFloat() / totalExpense.toFloat()) * 100f
                )
            }.sortedByDescending { it.totalAmount }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaiPurple, RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                .padding(vertical = 8.dp, horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "KATEGORI / DESKRIPSI",
                modifier = Modifier.weight(1.8f),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                "JUMLAH",
                modifier = Modifier.weight(0.8f),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center
            )
            Text(
                "TOTAL (RP)",
                modifier = Modifier.weight(1.6f),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.End
            )
            Text(
                "PORSI",
                modifier = Modifier.weight(0.8f),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.End
            )
        }

        summaryRows.forEachIndexed { index, row ->
            val rowBg = if (index % 2 == 0) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceContainer
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(rowBg)
                    .padding(vertical = 8.dp, horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    row.categoryName,
                    modifier = Modifier.weight(1.8f),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "${row.count} tx",
                    modifier = Modifier.weight(0.8f),
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "Rp ${fmt.format(row.totalAmount)}",
                    modifier = Modifier.weight(1.6f),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.End
                )
                Text(
                    "${row.percentage.toInt()}%",
                    modifier = Modifier.weight(0.8f),
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.End,
                    fontWeight = FontWeight.Bold,
                    color = MaiPurple
                )
            }
            Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(vertical = 10.dp, horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "TOTAL PENGELUARAN",
                modifier = Modifier.weight(2.6f),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Rp ${fmt.format(totalExpense)}",
                modifier = Modifier.weight(1.6f),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.End,
                color = ExpenseRed
            )
            Text(
                "100%",
                modifier = Modifier.weight(0.8f),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.End
            )
        }
    }
}

// ── Template 2: Rincian Harian ──────────────────────────────────────────────────
@Composable
private fun RenderDailyDetailsTable(
    transactions: List<TransactionEntity>,
    fmt: NumberFormat
) {
    val recentList = remember(transactions) {
        transactions.sortedByDescending { it.date }.take(10)
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF0288D1), RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                .padding(vertical = 8.dp, horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "TANGGAL",
                modifier = Modifier.weight(1.2f),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                "DESKRIPSI",
                modifier = Modifier.weight(1.8f),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                "NOMINAL (RP)",
                modifier = Modifier.weight(1.6f),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.End
            )
        }

        recentList.forEachIndexed { index, tx ->
            val rowBg = if (index % 2 == 0) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceContainer
            val isIncome = tx.type == "INCOME"
            val amountColor = if (isIncome) IncomeGreen else ExpenseRed
            val prefix = if (isIncome) "+Rp " else "-Rp "

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(rowBg)
                    .padding(vertical = 8.dp, horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    tx.date.takeLast(5),
                    modifier = Modifier.weight(1.2f),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium
                )
                Column(modifier = Modifier.weight(1.8f)) {
                    Text(
                        tx.note?.takeIf { it.isNotBlank() } ?: "Transaksi",
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    "$prefix${fmt.format(tx.amount)}",
                    modifier = Modifier.weight(1.6f),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = amountColor,
                    textAlign = TextAlign.End
                )
            }
            Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
        }
    }
}

// ── Template 3: Perbandingan Bulan ────────────────────────────────────────────
@Composable
private fun RenderMonthlyComparisonTable(
    transactions: List<TransactionEntity>,
    fmt: NumberFormat
) {
    val monthlyData = remember(transactions) {
        val now = LocalDate.now()
        listOf(now, now.minusMonths(1), now.minusMonths(2)).map { monthDate ->
            val monthTxs = transactions.filter { tx ->
                try {
                    val d = LocalDate.parse(tx.date)
                    d.month == monthDate.month && d.year == monthDate.year
                } catch (e: Exception) { false }
            }
            val income = monthTxs.filter { it.type == "INCOME" }.sumOf { it.amount }
            val expense = monthTxs.filter { it.type == "EXPENSE" }.sumOf { it.amount }
            MonthlyComparisonRow(
                monthName = monthDate.format(DateTimeFormatter.ofPattern("MMM yyyy", Locale("id", "ID"))),
                totalExpense = expense,
                totalIncome = income,
                netBalance = income - expense
            )
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF26A69A), RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                .padding(vertical = 8.dp, horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "PERIODE",
                modifier = Modifier.weight(1.4f),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                "PEMASUKAN",
                modifier = Modifier.weight(1.4f),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.End
            )
            Text(
                "PENGELUARAN",
                modifier = Modifier.weight(1.4f),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.End
            )
            Text(
                "SELISIH",
                modifier = Modifier.weight(1.4f),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.End
            )
        }

        monthlyData.forEachIndexed { index, row ->
            val rowBg = if (index % 2 == 0) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceContainer
            val diffColor = if (row.netBalance >= 0) IncomeGreen else ExpenseRed

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(rowBg)
                    .padding(vertical = 8.dp, horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    row.monthName,
                    modifier = Modifier.weight(1.4f),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "Rp ${fmt.format(row.totalIncome)}",
                    modifier = Modifier.weight(1.4f),
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.End,
                    color = IncomeGreen
                )
                Text(
                    "Rp ${fmt.format(row.totalExpense)}",
                    modifier = Modifier.weight(1.4f),
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.End,
                    color = ExpenseRed
                )
                Text(
                    "Rp ${fmt.format(row.netBalance)}",
                    modifier = Modifier.weight(1.4f),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.End,
                    color = diffColor
                )
            }
            Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
        }
    }
}
