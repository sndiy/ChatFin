package com.sndiy.chatfin.feature.finance.dashboard.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberBottom
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberStart
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoZoomState
import com.patrykandpatrick.vico.compose.common.component.rememberLineComponent
import com.patrykandpatrick.vico.compose.common.fill
import com.patrykandpatrick.vico.core.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.core.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.columnSeries
import com.patrykandpatrick.vico.core.cartesian.data.lineSeries
import com.patrykandpatrick.vico.core.cartesian.layer.ColumnCartesianLayer
import com.patrykandpatrick.vico.core.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.core.common.component.TextComponent
import com.patrykandpatrick.vico.core.common.shader.ShaderProvider
import com.patrykandpatrick.vico.core.common.shape.CorneredShape
import com.sndiy.chatfin.core.data.local.entity.WalletEntity
import com.sndiy.chatfin.core.ui.theme.ExpenseRed
import com.sndiy.chatfin.core.ui.theme.IncomeGreen
import com.sndiy.chatfin.feature.finance.analytics.ui.AnalyticsPeriod
import com.sndiy.chatfin.feature.finance.analytics.ui.CategorySlice
import com.sndiy.chatfin.feature.finance.analytics.ui.DailyExpensePoint
import com.sndiy.chatfin.feature.finance.analytics.ui.MonthlyBarEntry
import java.text.NumberFormat
import java.util.Locale

private val donutColors = listOf(
    Color(0xFF5B6EF5), Color(0xFF7E57C2), Color(0xFF26A69A),
    Color(0xFFEF6C00), Color(0xFFEC407A), Color(0xFF78909C)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onNavigateToChat: () -> Unit = {},
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val uiState        by viewModel.uiState.collectAsStateWithLifecycle()
    var showOnboarding by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.isLoading, uiState.isOnboarded) {
        if (!uiState.isLoading && !uiState.isOnboarded) showOnboarding = true
    }

    if (showOnboarding) {
        OnboardingDialog(
            onConfirm = { name ->
                viewModel.setupInitialData(name)
                showOnboarding = false
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Beranda", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = onNavigateToChat) {
                        Icon(Icons.Default.Chat, contentDescription = "Chat dengan Mai")
                    }
                }
            )
        }
    ) { padding ->
        if (uiState.isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier            = Modifier.fillMaxSize().padding(padding),
                contentPadding      = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // ── Summary saldo ─────────────────────────────────────────────
                item {
                    BalanceSummaryCard(
                        totalBalance   = uiState.totalBalance,
                        monthlyIncome  = uiState.monthlyIncome,
                        monthlyExpense = uiState.monthlyExpense
                    )
                }

                // ── Dompet ────────────────────────────────────────────────────
                item { WalletsSection(wallets = uiState.wallets) }

                // ── Transaksi terbaru ─────────────────────────────────────────
                if (uiState.recentTransactions.isNotEmpty()) {
                    item {
                        Text(
                            "Transaksi Terbaru",
                            style      = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    items(uiState.recentTransactions, key = { it.id }) { tx ->
                        TransactionItem(tx = tx)
                    }
                } else {
                    item {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Box(
                                Modifier.fillMaxWidth().padding(32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        Icons.Default.Chat, null,
                                        modifier = Modifier.size(48.dp),
                                        tint     = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    Text("Belum ada transaksi", style = MaterialTheme.typography.bodyLarge)
                                    Text(
                                        "Ceritakan ke Mai pengeluaranmu hari ini!",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                // ── Divider Analitik ──────────────────────────────────────────
                item {
                    Spacer(Modifier.height(4.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Analitik",
                        style      = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                // ── Period selector ───────────────────────────────────────────
                item {
                    Row(
                        modifier              = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AnalyticsPeriod.entries.forEach { period ->
                            FilterChip(
                                selected = uiState.selectedPeriod == period,
                                onClick  = { viewModel.selectPeriod(period) },
                                label    = { Text(period.label, style = MaterialTheme.typography.labelMedium) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                // ── Summary analytics ─────────────────────────────────────────
                item {
                    val fmt = NumberFormat.getNumberInstance(Locale("id", "ID"))
                    val net = uiState.analyticsNet
                    Row(
                        modifier              = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AnalyticsMiniCard(
                            modifier = Modifier.weight(1f),
                            label    = "Pemasukan",
                            value    = "Rp ${fmt.format(uiState.analyticsIncome)}",
                            color    = IncomeGreen
                        )
                        AnalyticsMiniCard(
                            modifier = Modifier.weight(1f),
                            label    = "Pengeluaran",
                            value    = "Rp ${fmt.format(uiState.analyticsExpense)}",
                            color    = ExpenseRed
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors   = CardDefaults.cardColors(
                            containerColor = if (net >= 0) IncomeGreen.copy(alpha = 0.1f)
                            else ExpenseRed.copy(alpha = 0.1f)
                        )
                    ) {
                        Row(
                            modifier              = Modifier
                                .padding(horizontal = 16.dp, vertical = 10.dp)
                                .fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment     = Alignment.CenterVertically
                        ) {
                            Text("Selisih Bersih", style = MaterialTheme.typography.labelLarge)
                            Text(
                                "${if (net >= 0) "+" else ""}Rp ${fmt.format(net)}",
                                style      = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color      = if (net >= 0) IncomeGreen else ExpenseRed
                            )
                        }
                    }
                }

                // ── Charts ────────────────────────────────────────────────────
                if (uiState.analyticsLoading) {
                    item {
                        Box(
                            Modifier.fillMaxWidth().padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) { CircularProgressIndicator() }
                    }
                } else {
                    if (uiState.dailyExpensePoints.isNotEmpty()) {
                        item {
                            AnalyticsCard("Pengeluaran Harian") {
                                DailyLineChart(uiState.dailyExpensePoints)
                            }
                        }
                    }
                    if (uiState.categorySlices.isNotEmpty()) {
                        item {
                            AnalyticsCard("Pengeluaran per Kategori") {
                                DonutChartWithLegend(uiState.categorySlices)
                            }
                        }
                    }
                    if (uiState.monthlyBarEntries.isNotEmpty()) {
                        item {
                            AnalyticsCard("Pemasukan vs Pengeluaran (6 Bulan)") {
                                MonthlyBarChart(uiState.monthlyBarEntries)
                            }
                        }
                    }
                    if (uiState.dailyExpensePoints.isEmpty() && uiState.categorySlices.isEmpty()) {
                        item {
                            Box(
                                Modifier.fillMaxWidth().padding(vertical = 24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "Belum ada data grafik untuk periode ini",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }
}

// ── Komponen UI ───────────────────────────────────────────────────────────────

@Composable
private fun AnalyticsMiniCard(modifier: Modifier, label: String, value: String, color: Color) {
    Card(
        modifier = modifier,
        colors   = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.08f))
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = color)
            Text(
                value,
                style      = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines   = 1,
                overflow   = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun AnalyticsCard(title: String, content: @Composable () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}

@Composable
private fun BalanceSummaryCard(totalBalance: Long, monthlyIncome: Long, monthlyExpense: Long) {
    val fmt = NumberFormat.getNumberInstance(Locale("id", "ID"))
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors   = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(
                "Total Saldo",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Text(
                "Rp ${fmt.format(totalBalance)}",
                style      = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color      = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text(
                        "Pemasukan Bulan Ini",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(0.7f)
                    )
                    Text(
                        "+ Rp ${fmt.format(monthlyIncome)}",
                        fontWeight = FontWeight.SemiBold,
                        color      = IncomeGreen
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        "Pengeluaran Bulan Ini",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(0.7f)
                    )
                    Text(
                        "- Rp ${fmt.format(monthlyExpense)}",
                        fontWeight = FontWeight.SemiBold,
                        color      = ExpenseRed
                    )
                }
            }
        }
    }
}

@Composable
private fun WalletsSection(wallets: List<WalletEntity>) {
    if (wallets.isEmpty()) return
    val fmt = NumberFormat.getNumberInstance(Locale("id", "ID"))
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "Dompet & Rekening",
            style      = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        wallets.forEach { wallet ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    Modifier.padding(16.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.AccountBalanceWallet, null, tint = MaterialTheme.colorScheme.primary)
                        Column {
                            Text(wallet.name, fontWeight = FontWeight.Medium)
                            Text(
                                wallet.type,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Text("Rp ${fmt.format(wallet.balance)}", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun TransactionItem(tx: TransactionDisplay) {
    val fmt = NumberFormat.getNumberInstance(Locale("id", "ID"))
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(12.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Icon(
                    if (tx.type == "INCOME") Icons.Default.TrendingUp else Icons.Default.TrendingDown,
                    contentDescription = null,
                    tint = if (tx.type == "INCOME") IncomeGreen else ExpenseRed
                )
                Column {
                    Text(
                        text       = tx.note?.takeIf { it.isNotBlank() } ?: tx.categoryName,
                        fontWeight = FontWeight.Medium,
                        style      = MaterialTheme.typography.bodyMedium,
                        maxLines   = 1
                    )
                    Text(
                        text = buildString {
                            append(tx.categoryName)
                            append(" · "); append(tx.walletName)
                            if (tx.time.isNotBlank()) { append(" · "); append(tx.time) }
                        },
                        style    = MaterialTheme.typography.labelSmall,
                        color    = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
            Text(
                "${if (tx.type == "INCOME") "+" else "-"}Rp ${fmt.format(tx.amount)}",
                fontWeight = FontWeight.SemiBold,
                color      = if (tx.type == "INCOME") IncomeGreen else ExpenseRed
            )
        }
    }
}

@Composable
private fun OnboardingDialog(onConfirm: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = {},
        title = { Text("Selamat Datang di ChatFin! 👋") },
        text  = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Mai akan membantumu mengelola keuangan. Mulai dengan memberi nama profilmu.")
                OutlinedTextField(
                    value         = name,
                    onValueChange = { name = it },
                    label         = { Text("Nama profil (contoh: Keuangan Pribadi)") },
                    singleLine    = true,
                    modifier      = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { if (name.isNotBlank()) onConfirm(name.trim()) },
                enabled = name.isNotBlank()
            ) { Text("Mulai") }
        }
    )
}

// ── Charts (sama persis dari AnalyticsScreen) ─────────────────────────────────

@Composable
private fun DailyLineChart(points: List<DailyExpensePoint>) {
    val primaryColor  = MaterialTheme.colorScheme.primary
    val primaryArgb   = primaryColor.toArgb()
    val modelProducer = remember { CartesianChartModelProducer() }
    LaunchedEffect(points) {
        modelProducer.runTransaction { lineSeries { series(points.map { it.amount.toFloat() }) } }
    }
    val labels = remember(points) { points.map { it.dayLabel } }
    val line   = remember(primaryColor) {
        LineCartesianLayer.Line(
            fill = LineCartesianLayer.LineFill.single(
                com.patrykandpatrick.vico.core.common.Fill(primaryArgb)
            ),
            areaFill = LineCartesianLayer.AreaFill.single(
                com.patrykandpatrick.vico.core.common.Fill(
                    ShaderProvider.verticalGradient(intArrayOf(
                        android.graphics.Color.argb(76,
                            android.graphics.Color.red(primaryArgb),
                            android.graphics.Color.green(primaryArgb),
                            android.graphics.Color.blue(primaryArgb)
                        ),
                        android.graphics.Color.TRANSPARENT
                    ))
                )
            )
        )
    }
    CartesianChartHost(
        modifier      = Modifier.fillMaxWidth().height(180.dp),
        chart         = rememberCartesianChart(
            rememberLineCartesianLayer(LineCartesianLayer.LineProvider.series(line)),
            startAxis  = VerticalAxis.rememberStart(
                label          = TextComponent(color = android.graphics.Color.GRAY, textSizeSp = 9f),
                valueFormatter = { _, v, _ -> val x = v.toLong(); when { x >= 1_000_000 -> "${x/1_000_000}jt"; x >= 1_000 -> "${x/1_000}rb"; else -> "$x" } }
            ),
            bottomAxis = HorizontalAxis.rememberBottom(
                label          = TextComponent(color = android.graphics.Color.GRAY, textSizeSp = 9f),
                valueFormatter = { _, v, _ -> labels.getOrElse(v.toInt()) { "" } }
            )
        ),
        modelProducer = modelProducer,
        zoomState     = rememberVicoZoomState(zoomEnabled = false)
    )
}

@Composable
private fun DonutChartWithLegend(slices: List<CategorySlice>) {
    val animProgress by animateFloatAsState(
        targetValue   = 1f,
        animationSpec = tween(800, easing = FastOutSlowInEasing),
        label         = "donut"
    )
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(140.dp).padding(8.dp)) { drawDonut(slices, animProgress) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            slices.forEachIndexed { idx, slice ->
                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(donutColors.getOrElse(idx) { Color.Gray }))
                    Text(slice.categoryName, style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${"%.1f".format(slice.percentage)}%", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

private fun DrawScope.drawDonut(slices: List<CategorySlice>, progress: Float) {
    val stroke = 44f
    val total  = slices.sumOf { it.amount }.toFloat().takeIf { it > 0 } ?: 1f
    val cs     = size.minDimension
    val tl     = Offset((size.width - cs) / 2f, (size.height - cs) / 2f)
    val arcTl  = Offset(tl.x + stroke / 2f, tl.y + stroke / 2f)
    var angle  = -90f
    slices.forEachIndexed { idx, slice ->
        val sweep = slice.amount.toFloat() / total * 360f * progress
        drawArc(donutColors.getOrElse(idx) { Color.Gray }, angle, sweep, false, arcTl, Size(cs - stroke, cs - stroke), style = Stroke(stroke, cap = StrokeCap.Butt))
        angle += sweep
    }
}

@Composable
private fun MonthlyBarChart(entries: List<MonthlyBarEntry>) {
    val modelProducer = remember { CartesianChartModelProducer() }
    LaunchedEffect(entries) {
        modelProducer.runTransaction {
            columnSeries {
                series(entries.map { it.income.toFloat() })
                series(entries.map { it.expense.toFloat() })
            }
        }
    }
    val labels     = remember(entries) { entries.map { it.monthLabel } }
    val incomeCol  = rememberLineComponent(fill = fill(IncomeGreen), shape = CorneredShape.rounded(topLeftPercent = 4, topRightPercent = 4), thickness = 16.dp)
    val expenseCol = rememberLineComponent(fill = fill(ExpenseRed),  shape = CorneredShape.rounded(topLeftPercent = 4, topRightPercent = 4), thickness = 16.dp)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(IncomeGreen)); Spacer(Modifier.width(4.dp)); Text("Pemasukan", style = MaterialTheme.typography.labelSmall)
            Spacer(Modifier.width(12.dp))
            Box(Modifier.size(8.dp).clip(CircleShape).background(ExpenseRed)); Spacer(Modifier.width(4.dp)); Text("Pengeluaran", style = MaterialTheme.typography.labelSmall)
        }
        CartesianChartHost(
            modifier      = Modifier.fillMaxWidth().height(180.dp),
            chart         = rememberCartesianChart(
                rememberColumnCartesianLayer(ColumnCartesianLayer.ColumnProvider.series(incomeCol, expenseCol)),
                startAxis  = VerticalAxis.rememberStart(
                    label          = TextComponent(color = android.graphics.Color.GRAY, textSizeSp = 9f),
                    valueFormatter = { _, v, _ -> val x = v.toLong(); when { x >= 1_000_000 -> "${x/1_000_000}jt"; x >= 1_000 -> "${x/1_000}rb"; else -> "$x" } }
                ),
                bottomAxis = HorizontalAxis.rememberBottom(
                    label          = TextComponent(color = android.graphics.Color.GRAY, textSizeSp = 9f),
                    valueFormatter = { _, v, _ -> labels.getOrElse(v.toInt()) { "" } }
                )
            ),
            modelProducer = modelProducer,
            zoomState     = rememberVicoZoomState(zoomEnabled = false)
        )
    }
}