// feature/finance/analytics/ui/AnalyticsScreen.kt

package com.sndiy.chatfin.feature.finance.analytics.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.sndiy.chatfin.core.ui.theme.ExpenseRed
import com.sndiy.chatfin.core.ui.theme.IncomeGreen
import java.text.NumberFormat
import java.util.Locale

// ── Warna palette donut chart ─────────────────────────────────────────────────
private val donutColors = listOf(
    Color(0xFF5B6EF5),
    Color(0xFF7E57C2),
    Color(0xFF26A69A),
    Color(0xFFEF6C00),
    Color(0xFFEC407A),
    Color(0xFF78909C)
)

// ── Main Screen ───────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsScreen(
    onNavigateBack: () -> Unit = {},
    viewModel: AnalyticsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Analitik", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali")
                    }
                }
            )
        }
    ) { padding ->
        if (uiState.isLoading) {
            Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item { PeriodSelector(uiState.selectedPeriod, viewModel::selectPeriod) }

                item {
                    SummaryRow(
                        income  = uiState.totalIncome,
                        expense = uiState.totalExpense,
                        net     = uiState.netBalance
                    )
                }

                if (uiState.dailyExpensePoints.isNotEmpty()) {
                    item {
                        ChartCard(title = "Pengeluaran Harian") {
                            DailyLineChart(points = uiState.dailyExpensePoints)
                        }
                    }
                }

                if (uiState.categorySlices.isNotEmpty()) {
                    item {
                        ChartCard(title = "Pengeluaran per Kategori") {
                            DonutChartWithLegend(slices = uiState.categorySlices)
                        }
                    }
                }

                if (uiState.monthlyBarEntries.isNotEmpty()) {
                    item {
                        ChartCard(title = "Pemasukan vs Pengeluaran (6 Bulan)") {
                            MonthlyBarChart(entries = uiState.monthlyBarEntries)
                        }
                    }
                }

                if (uiState.dailyExpensePoints.isEmpty() && uiState.categorySlices.isEmpty()) {
                    item { EmptyAnalyticsState() }
                }
            }
        }
    }
}

// ── Period Selector ───────────────────────────────────────────────────────────

@Composable
private fun PeriodSelector(
    selected: AnalyticsPeriod,
    onSelect: (AnalyticsPeriod) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        AnalyticsPeriod.entries.forEach { period ->
            FilterChip(
                selected = selected == period,
                onClick  = { onSelect(period) },
                label    = { Text(period.label, style = MaterialTheme.typography.labelMedium) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

// ── Summary Row ───────────────────────────────────────────────────────────────

@Composable
private fun SummaryRow(income: Long, expense: Long, net: Long) {
    val fmt = NumberFormat.getNumberInstance(Locale("id", "ID"))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SummaryChip(
            modifier = Modifier.weight(1f),
            label    = "Pemasukan",
            value    = "Rp ${fmt.format(income)}",
            color    = IncomeGreen,
            icon     = Icons.AutoMirrored.Filled.TrendingUp
        )
        SummaryChip(
            modifier = Modifier.weight(1f),
            label    = "Pengeluaran",
            value    = "Rp ${fmt.format(expense)}",
            color    = ExpenseRed,
            icon     = Icons.AutoMirrored.Filled.TrendingDown
        )
    }
    Spacer(Modifier.height(4.dp))
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors   = CardDefaults.cardColors(
            containerColor = if (net >= 0) IncomeGreen.copy(alpha = 0.1f)
            else ExpenseRed.copy(alpha = 0.1f)
        )
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 10.dp).fillMaxWidth(),
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

@Composable
private fun SummaryChip(
    modifier: Modifier,
    label: String,
    value: String,
    color: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Card(
        modifier = modifier,
        colors   = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.08f))
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Icon(icon, null, tint = color, modifier = Modifier.size(14.dp))
                Text(label, style = MaterialTheme.typography.labelSmall, color = color)
            }
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

// ── Chart Card wrapper ────────────────────────────────────────────────────────

@Composable
private fun ChartCard(title: String, content: @Composable () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}

// ── 1. Line Chart — Pengeluaran Harian (Vico) ─────────────────────────────────

@Composable
private fun DailyLineChart(points: List<DailyExpensePoint>) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val primaryArgb  = primaryColor.toArgb()
    val modelProducer = remember { CartesianChartModelProducer() }

    LaunchedEffect(points) {
        modelProducer.runTransaction {
            lineSeries { series(points.map { it.amount.toFloat() }) }
        }
    }

    val labels = remember(points) { points.map { it.dayLabel } }

    // Gunakan LineCartesianLayer.Line() constructor — tidak butuh @Composable context
    val line = remember(primaryColor) {
        LineCartesianLayer.Line(
            fill = LineCartesianLayer.LineFill.single(
                com.patrykandpatrick.vico.core.common.Fill(primaryColor.toArgb())
            ),
            areaFill = LineCartesianLayer.AreaFill.single(
                com.patrykandpatrick.vico.core.common.Fill(
                    ShaderProvider.verticalGradient(
                        intArrayOf(
                            android.graphics.Color.argb(
                                76,
                                android.graphics.Color.red(primaryArgb),
                                android.graphics.Color.green(primaryArgb),
                                android.graphics.Color.blue(primaryArgb)
                            ),
                            android.graphics.Color.TRANSPARENT
                        )
                    )
                )
            )
        )
    }

    val lineLayer = rememberLineCartesianLayer(
        lineProvider = LineCartesianLayer.LineProvider.series(line)
    )

    CartesianChartHost(
        modifier = Modifier.fillMaxWidth().height(200.dp),
        chart = rememberCartesianChart(
            lineLayer,
            startAxis = VerticalAxis.rememberStart(
                label = TextComponent(color = android.graphics.Color.GRAY, textSizeSp = 9f),
                valueFormatter = { _, value, _ ->
                    val v = value.toLong()
                    when {
                        v >= 1_000_000 -> "${v / 1_000_000}jt"
                        v >= 1_000     -> "${v / 1_000}rb"
                        else           -> "$v"
                    }
                }
            ),
            bottomAxis = HorizontalAxis.rememberBottom(
                label = TextComponent(color = android.graphics.Color.GRAY, textSizeSp = 9f),
                valueFormatter = { _, value, _ ->
                    labels.getOrElse(value.toInt()) { "" }
                }
            )
        ),
        modelProducer = modelProducer,
        zoomState     = rememberVicoZoomState(zoomEnabled = false)
    )
}

// ── 2. Donut Chart — Per Kategori (Canvas) ────────────────────────────────────

@Composable
private fun DonutChartWithLegend(slices: List<CategorySlice>) {
    val animProgress by animateFloatAsState(
        targetValue   = 1f,
        animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing),
        label         = "donut"
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Canvas(modifier = Modifier.size(160.dp).padding(8.dp)) {
            drawDonut(slices, animProgress)
        }
        Spacer(Modifier.width(16.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            slices.forEachIndexed { idx, slice ->
                DonutLegendItem(
                    color      = donutColors.getOrElse(idx) { Color.Gray },
                    label      = slice.categoryName,
                    percentage = slice.percentage
                )
            }
        }
    }
}

private fun DrawScope.drawDonut(slices: List<CategorySlice>, progress: Float) {
    val stroke     = 48f
    val total      = slices.sumOf { it.amount }.toFloat().takeIf { it > 0 } ?: 1f
    val canvasSize = size.minDimension
    val topLeft    = Offset((size.width - canvasSize) / 2f, (size.height - canvasSize) / 2f)
    val arcSize    = Size(canvasSize - stroke, canvasSize - stroke)
    val arcTopLeft = Offset(topLeft.x + stroke / 2f, topLeft.y + stroke / 2f)
    var startAngle = -90f

    slices.forEachIndexed { idx, slice ->
        val sweep = (slice.amount.toFloat() / total) * 360f * progress
        drawArc(
            color      = donutColors.getOrElse(idx) { Color.Gray },
            startAngle = startAngle,
            sweepAngle = sweep,
            useCenter  = false,
            topLeft    = arcTopLeft,
            size       = arcSize,
            style      = Stroke(width = stroke, cap = StrokeCap.Butt)
        )
        startAngle += sweep
    }
}

@Composable
private fun DonutLegendItem(color: Color, label: String, percentage: Float) {
    Row(
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
        Text(
            label,
            style    = MaterialTheme.typography.labelSmall,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            "${"%.1f".format(percentage)}%",
            style      = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color      = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ── 3. Bar Chart — Monthly Income vs Expense (Vico) ───────────────────────────

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

    val labels = remember(entries) { entries.map { it.monthLabel } }

    val incomeColumn = rememberLineComponent(
        fill      = fill(IncomeGreen),
        shape     = CorneredShape.rounded(topLeftPercent = 4, topRightPercent = 4),
        thickness = 16.dp
    )
    val expenseColumn = rememberLineComponent(
        fill      = fill(ExpenseRed),
        shape     = CorneredShape.rounded(topLeftPercent = 4, topRightPercent = 4),
        thickness = 16.dp
    )

    val columnLayer = rememberColumnCartesianLayer(
        columnProvider = ColumnCartesianLayer.ColumnProvider.series(incomeColumn, expenseColumn)
    )

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            LegendDot(color = IncomeGreen, label = "Pemasukan")
            Spacer(Modifier.width(16.dp))
            LegendDot(color = ExpenseRed, label = "Pengeluaran")
        }

        CartesianChartHost(
            modifier = Modifier.fillMaxWidth().height(200.dp),
            chart = rememberCartesianChart(
                columnLayer,
                startAxis = VerticalAxis.rememberStart(
                    label = TextComponent(color = android.graphics.Color.GRAY, textSizeSp = 9f),
                    valueFormatter = { _, value, _ ->
                        val v = value.toLong()
                        when {
                            v >= 1_000_000 -> "${v / 1_000_000}jt"
                            v >= 1_000     -> "${v / 1_000}rb"
                            else           -> "$v"
                        }
                    }
                ),
                bottomAxis = HorizontalAxis.rememberBottom(
                    label = TextComponent(color = android.graphics.Color.GRAY, textSizeSp = 9f),
                    valueFormatter = { _, value, _ ->
                        labels.getOrElse(value.toInt()) { "" }
                    }
                )
            ),
            modelProducer = modelProducer,
            zoomState     = rememberVicoZoomState(zoomEnabled = false)
        )
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

// ── Empty state ───────────────────────────────────────────────────────────────

@Composable
private fun EmptyAnalyticsState() {
    Card(modifier = Modifier.fillMaxWidth()) {
        Box(
            Modifier.fillMaxWidth().padding(40.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("📊", fontSize = 48.sp)
                Text(
                    "Belum ada data untuk periode ini",
                    style      = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    "Tambahkan transaksi untuk melihat analitik keuanganmu",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}