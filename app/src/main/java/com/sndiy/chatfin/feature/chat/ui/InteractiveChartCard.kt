package com.sndiy.chatfin.feature.chat.ui

import android.graphics.Picture
import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.draw
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberBottom
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberStart
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.core.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.core.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.columnSeries
import com.patrykandpatrick.vico.core.cartesian.data.lineSeries
import com.sndiy.chatfin.core.data.local.entity.TransactionEntity
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.time.LocalDate
import java.util.Locale

enum class ChartType(val label: String) {
    BAR("Bar Chart"),
    LINE("Line Chart"),
    PIE("Pie Chart"),
    DONUT("Donut Chart")
}

enum class DateRangePeriod(val label: String) {
    THIS_MONTH("Bulan Ini"),
    LAST_30_DAYS("30 Hari"),
    LAST_3_MONTHS("3 Bulan"),
    THIS_YEAR("Tahun Ini")
}

enum class ChartTheme(val label: String, val colors: List<Color>) {
    SHEETS_CLASSIC(
        "Sheets Classic",
        listOf(Color(0xFF4285F4), Color(0xFF34A853), Color(0xFFFBBC05), Color(0xFFEA4335), Color(0xFFAB47BC), Color(0xFF00ACC1))
    ),
    MAI_PURPLE(
        "Mai Purple",
        listOf(Color(0xFF7E57C2), Color(0xFF5B6EF5), Color(0xFF9C27B0), Color(0xFF3F51B5), Color(0xFFBA68C8), Color(0xFF7C4DFF))
    ),
    EMERALD(
        "Emerald Green",
        listOf(Color(0xFF26A69A), Color(0xFF4CAF50), Color(0xFF009688), Color(0xFF81C784), Color(0xFF00E676), Color(0xFF66BB6A))
    ),
    OCEAN(
        "Ocean Blue",
        listOf(Color(0xFF0288D1), Color(0xFF0097A7), Color(0xFF00BCD4), Color(0xFF4FC3F7), Color(0xFF29B6F6), Color(0xFF80DEEA))
    ),
    SUNSET(
        "Sunset Amber",
        listOf(Color(0xFFFF7043), Color(0xFFFFB74D), Color(0xFFEC407A), Color(0xFFAB47BC), Color(0xFFFF5722), Color(0xFFFF8A65))
    )
}

data class ChartDataItem(
    val label: String,
    val value: Long,
    val category: String = ""
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InteractiveChartCard(
    title: String,
    transactions: List<TransactionEntity>,
    initialType: ChartType = ChartType.BAR,
    initialPeriod: DateRangePeriod = DateRangePeriod.THIS_MONTH,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val picture = remember { Picture() }

    var selectedType by remember { mutableStateOf(initialType) }
    var selectedPeriod by remember { mutableStateOf(initialPeriod) }
    var selectedTheme by remember { mutableStateOf(ChartTheme.SHEETS_CLASSIC) }
    var showLegend by remember { mutableStateOf(true) }
    var showValues by remember { mutableStateOf(true) }
    var isExporting by remember { mutableStateOf(false) }

    val chartData = remember(transactions, selectedPeriod) {
        aggregateTransactionsForChart(transactions, selectedPeriod)
    }

    val fmt = remember { NumberFormat.getNumberInstance(Locale("id", "ID")) }

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
            // Header: Title & Download PNG Action
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
                        imageVector = Icons.Outlined.BarChart,
                        contentDescription = null,
                        tint = selectedTheme.colors.first()
                    )
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                FilledTonalIconButton(
                    onClick = {
                        coroutineScope.launch {
                            isExporting = true
                            try {
                                val bitmap = ChartExportUtil.createBitmapFromPicture(picture)
                                ChartExportUtil.saveAndShareBitmap(context, bitmap, title)
                            } catch (e: Exception) {
                                // Fallback
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

            // Controls 1: Chart Type Buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ChartType.values().forEach { type ->
                    FilterChip(
                        selected = (selectedType == type),
                        onClick = { selectedType = type },
                        label = { Text(type.label, fontSize = 12.sp) },
                        leadingIcon = {
                            val icon = when (type) {
                                ChartType.BAR -> Icons.Default.BarChart
                                ChartType.LINE -> Icons.Default.ShowChart
                                ChartType.PIE -> Icons.Default.PieChart
                                ChartType.DONUT -> Icons.Default.DonutLarge
                            }
                            Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp))
                        }
                    )
                }
            }

            // Controls 2: Date Range Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                DateRangePeriod.values().forEach { period ->
                    ElevatedFilterChip(
                        selected = (selectedPeriod == period),
                        onClick = { selectedPeriod = period },
                        label = { Text(period.label, fontSize = 11.sp) }
                    )
                }
            }

            // Controls 3: Theme Color Picker & Toggles
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Tema:",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    ChartTheme.values().forEach { theme ->
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.linearGradient(listOf(theme.colors[0], theme.colors[1]))
                                )
                                .border(
                                    width = if (selectedTheme == theme) 2.dp else 0.dp,
                                    color = if (selectedTheme == theme) MaterialTheme.colorScheme.primary else Color.Transparent,
                                    shape = CircleShape
                                )
                                .clickable { selectedTheme = theme }
                        )
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { showLegend = !showLegend },
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(
                            imageVector = if (showLegend) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = "Toggle Legend",
                            tint = if (showLegend) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(
                        onClick = { showValues = !showValues },
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(
                            imageVector = if (showValues) Icons.Default.Numbers else Icons.Outlined.Numbers,
                            contentDescription = "Toggle Values",
                            tint = if (showValues) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            // Chart Canvas Container (Recorded precisely into Picture for clean PNG export)
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
                    .padding(12.dp)
            ) {
                if (chartData.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "Belum ada data transaksi pada rentang ${selectedPeriod.label}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        when (selectedType) {
                            ChartType.BAR -> RenderBarChart(chartData, selectedTheme, showValues, fmt)
                            ChartType.LINE -> RenderLineChart(chartData, selectedTheme, showValues, fmt)
                            ChartType.PIE -> RenderPieDonutChart(chartData, selectedTheme, showValues, fmt, isDonut = false)
                            ChartType.DONUT -> RenderPieDonutChart(chartData, selectedTheme, showValues, fmt, isDonut = true)
                        }

                        if (showLegend && (selectedType == ChartType.PIE || selectedType == ChartType.DONUT)) {
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                chartData.forEachIndexed { idx, item ->
                                    val color = selectedTheme.colors[idx % selectedTheme.colors.size]
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(10.dp)
                                                .clip(CircleShape)
                                                .background(color)
                                        )
                                        Text(
                                            text = item.label,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RenderBarChart(
    items: List<ChartDataItem>,
    theme: ChartTheme,
    showValues: Boolean,
    fmt: NumberFormat
) {
    val modelProducer = remember { CartesianChartModelProducer() }
    LaunchedEffect(items) {
        val values = items.map { it.value.toFloat() }
        modelProducer.runTransaction {
            columnSeries { series(values) }
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        CartesianChartHost(
            chart = rememberCartesianChart(
                rememberColumnCartesianLayer(),
                startAxis = VerticalAxis.rememberStart(),
                bottomAxis = HorizontalAxis.rememberBottom()
            ),
            modelProducer = modelProducer,
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
        )

        if (showValues) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                items.forEach { item ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    ) {
                        Text(
                            item.label,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            "Rp ${fmt.format(item.value)}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = theme.colors.first()
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RenderLineChart(
    items: List<ChartDataItem>,
    theme: ChartTheme,
    showValues: Boolean,
    fmt: NumberFormat
) {
    val modelProducer = remember { CartesianChartModelProducer() }
    LaunchedEffect(items) {
        val values = items.map { it.value.toFloat() }
        modelProducer.runTransaction {
            lineSeries { series(values) }
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        CartesianChartHost(
            chart = rememberCartesianChart(
                rememberLineCartesianLayer(),
                startAxis = VerticalAxis.rememberStart(),
                bottomAxis = HorizontalAxis.rememberBottom()
            ),
            modelProducer = modelProducer,
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
        )

        if (showValues) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                items.forEach { item ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    ) {
                        Text(
                            item.label,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            "Rp ${fmt.format(item.value)}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = theme.colors.first()
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RenderPieDonutChart(
    items: List<ChartDataItem>,
    theme: ChartTheme,
    showValues: Boolean,
    fmt: NumberFormat,
    isDonut: Boolean
) {
    val total = remember(items) { items.sumOf { it.value }.coerceAtLeast(1L) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(210.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier
                .size(180.dp)
                .padding(8.dp)
        ) {
            val canvasSize = size.minDimension
            val radius = canvasSize / 2f
            val center = Offset(size.width / 2f, size.height / 2f)

            var startAngle = -90f

            items.forEachIndexed { index, item ->
                val sweepAngle = (item.value.toFloat() / total.toFloat()) * 360f
                val color = theme.colors[index % theme.colors.size]

                if (isDonut) {
                    drawArc(
                        color = color,
                        startAngle = startAngle,
                        sweepAngle = sweepAngle - 2f,
                        useCenter = false,
                        topLeft = Offset(center.x - radius + 20f, center.y - radius + 20f),
                        size = Size((radius - 20f) * 2, (radius - 20f) * 2),
                        style = Stroke(width = 36f)
                    )
                } else {
                    drawArc(
                        color = color,
                        startAngle = startAngle,
                        sweepAngle = sweepAngle - 1f,
                        useCenter = true,
                        topLeft = Offset(center.x - radius, center.y - radius),
                        size = Size(radius * 2, radius * 2)
                    )
                }

                startAngle += sweepAngle
            }
        }

        if (isDonut) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "Total",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "Rp ${fmt.format(total)}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = theme.colors.first()
                )
            }
        }
    }
}

private fun aggregateTransactionsForChart(
    transactions: List<TransactionEntity>,
    period: DateRangePeriod
): List<ChartDataItem> {
    val now = LocalDate.now()
    val filtered = transactions.filter { tx ->
        val date = try { LocalDate.parse(tx.date) } catch (e: Exception) { null }
        if (date == null) false
        else when (period) {
            DateRangePeriod.THIS_MONTH -> date.month == now.month && date.year == now.year
            DateRangePeriod.LAST_30_DAYS -> !date.isBefore(now.minusDays(30))
            DateRangePeriod.LAST_3_MONTHS -> !date.isBefore(now.minusMonths(3))
            DateRangePeriod.THIS_YEAR -> date.year == now.year
        }
    }

    if (filtered.isEmpty()) {
        return emptyList()
    }

    val grouped = filtered.groupBy { tx -> tx.note?.takeIf { it.isNotBlank() } ?: "Pengeluaran" }
    return grouped.map { (labelName, list) ->
        val sum = list.sumOf { it.amount }
        ChartDataItem(
            label = labelName,
            value = sum,
            category = labelName
        )
    }.sortedByDescending { it.value }.take(6)
}
