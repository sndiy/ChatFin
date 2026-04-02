// app/src/main/java/com/sndiy/chatfin/feature/finance/budget/ui/BudgetScreen.kt

package com.sndiy.chatfin.feature.finance.budget.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sndiy.chatfin.core.data.local.entity.BudgetEntity
import com.sndiy.chatfin.core.data.local.entity.CategoryEntity
import com.sndiy.chatfin.core.ui.theme.ExpenseRed
import com.sndiy.chatfin.core.ui.theme.IncomeGreen
import com.sndiy.chatfin.feature.finance.budget.data.repository.BudgetWithSpent
import java.text.NumberFormat
import java.time.Month
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetScreen(
    onNavigateBack: () -> Unit,
    viewModel: BudgetViewModel = hiltViewModel()
) {
    val uiState           by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val fmt               = NumberFormat.getNumberInstance(Locale("id", "ID"))

    LaunchedEffect(uiState.errorMessage, uiState.successMessage) {
        uiState.errorMessage?.let   { snackbarHostState.showSnackbar(it); viewModel.clearMessages() }
        uiState.successMessage?.let { snackbarHostState.showSnackbar(it); viewModel.clearMessages() }
    }

    var budgetToDelete by remember { mutableStateOf<BudgetEntity?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Budget", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Kembali")
                    }
                },
                actions = {
                    IconButton(
                        onClick = viewModel::showAddDialog,
                        enabled = uiState.availableCategories.isNotEmpty()
                    ) {
                        Icon(Icons.Default.Add, "Tambah Budget")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        LazyColumn(
            modifier            = Modifier.fillMaxSize().padding(padding),
            contentPadding      = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ── Month Navigator ──────────────────────────────────────────────
            item {
                MonthNavigator(
                    month     = uiState.month,
                    year      = uiState.year,
                    onPrev    = { viewModel.navigateMonth(-1) },
                    onNext    = { viewModel.navigateMonth(1) }
                )
            }

            // ── Summary Card ─────────────────────────────────────────────────
            item {
                BudgetSummaryCard(
                    totalBudget = uiState.totalBudget,
                    totalSpent  = uiState.totalSpent,
                    fmt         = fmt
                )
            }

            // ── Budget List ──────────────────────────────────────────────────
            if (uiState.isLoading) {
                item {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            } else if (uiState.budgets.isEmpty()) {
                item {
                    EmptyBudgetState(
                        hasCategories = uiState.availableCategories.isNotEmpty(),
                        onAdd         = viewModel::showAddDialog
                    )
                }
            } else {
                items(uiState.budgets, key = { it.budget.id }) { budgetWithSpent ->
                    BudgetItem(
                        item     = budgetWithSpent,
                        fmt      = fmt,
                        onEdit   = { viewModel.showEditDialog(budgetWithSpent.budget) },
                        onDelete = { budgetToDelete = budgetWithSpent.budget }
                    )
                }
            }

            item { Spacer(Modifier.height(16.dp)) }
        }
    }

    // ── Add/Edit Dialog ──────────────────────────────────────────────────────
    if (uiState.showAddDialog) {
        BudgetFormDialog(
            editing             = uiState.editingBudget,
            availableCategories = uiState.availableCategories,
            allBudgets          = uiState.budgets,
            onSave              = viewModel::saveBudget,
            onDismiss           = viewModel::dismissDialog
        )
    }

    // ── Delete Confirmation ──────────────────────────────────────────────────
    budgetToDelete?.let { budget ->
        AlertDialog(
            onDismissRequest = { budgetToDelete = null },
            title   = { Text("Hapus Budget?") },
            text    = { Text("Budget untuk kategori ini akan dihapus. Transaksi yang sudah tercatat tidak terpengaruh.") },
            confirmButton = {
                Button(
                    onClick = { viewModel.deleteBudget(budget); budgetToDelete = null },
                    colors  = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Hapus", color = Color.White) }
            },
            dismissButton = {
                TextButton(onClick = { budgetToDelete = null }) { Text("Batal") }
            }
        )
    }
}

// ── Month Navigator ──────────────────────────────────────────────────────────

@Composable
private fun MonthNavigator(month: Int, year: Int, onPrev: () -> Unit, onNext: () -> Unit) {
    val monthName = Month.of(month).getDisplayName(TextStyle.FULL, Locale("id", "ID"))
    val isCurrentMonth = month == java.time.LocalDate.now().monthValue && year == java.time.LocalDate.now().year

    Row(
        modifier              = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically
    ) {
        IconButton(onClick = onPrev) {
            Icon(Icons.Default.ChevronLeft, "Bulan sebelumnya")
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                monthName.replaceFirstChar { it.uppercase() },
                style      = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                if (isCurrentMonth) "Bulan ini" else year.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = if (isCurrentMonth) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = onNext) {
            Icon(Icons.Default.ChevronRight, "Bulan berikutnya")
        }
    }
}

// ── Summary Card ─────────────────────────────────────────────────────────────

@Composable
private fun BudgetSummaryCard(totalBudget: Long, totalSpent: Long, fmt: NumberFormat) {
    val percentage = if (totalBudget > 0) (totalSpent.toFloat() / totalBudget).coerceIn(0f, 1f) else 0f
    val animatedProgress by animateFloatAsState(
        targetValue   = percentage,
        animationSpec = tween(800, easing = FastOutSlowInEasing),
        label         = "summary_progress"
    )
    val progressColor = when {
        percentage > 1f  -> ExpenseRed
        percentage > 0.8f -> Color(0xFFE65100) // orange
        else -> IncomeGreen
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors   = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text(
                        "Total Terpakai",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        "Rp ${fmt.format(totalSpent)}",
                        style      = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color      = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        "dari Budget",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                    Text(
                        "Rp ${fmt.format(totalBudget)}",
                        style      = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color      = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            LinearProgressIndicator(
                progress  = { animatedProgress },
                modifier  = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                color     = progressColor,
                trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.15f)
            )

            val remaining = totalBudget - totalSpent
            Text(
                text = if (remaining >= 0) "Sisa: Rp ${fmt.format(remaining)}"
                       else "Melebihi budget: Rp ${fmt.format(-remaining)}",
                style = MaterialTheme.typography.labelMedium,
                color = if (remaining >= 0) IncomeGreen else ExpenseRed
            )
        }
    }
}

// ── Budget Item ──────────────────────────────────────────────────────────────

@Composable
private fun BudgetItem(
    item: BudgetWithSpent,
    fmt: NumberFormat,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val animatedProgress by animateFloatAsState(
        targetValue   = (item.percentage / 100f).coerceIn(0f, 1f),
        animationSpec = tween(600, easing = FastOutSlowInEasing),
        label         = "budget_item_progress"
    )
    val progressColor = when {
        item.isOverBudget -> ExpenseRed
        item.isNearLimit  -> Color(0xFFE65100)
        else              -> IncomeGreen
    }
    val categoryColor = runCatching {
        Color(android.graphics.Color.parseColor(item.categoryColor))
    }.getOrElse { Color.Gray }

    var showMenu by remember { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth().animateContentSize()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Box(
                        modifier         = Modifier.size(40.dp).clip(CircleShape).background(categoryColor),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            item.categoryName.take(1),
                            color      = Color.White,
                            fontWeight = FontWeight.Bold,
                            style      = MaterialTheme.typography.titleSmall
                        )
                    }
                    Column {
                        Text(
                            item.categoryName,
                            style      = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            maxLines   = 1,
                            overflow   = TextOverflow.Ellipsis
                        )
                        Text(
                            "Rp ${fmt.format(item.spent)} / Rp ${fmt.format(item.budget.limitAmount)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Box {
                    IconButton(onClick = { showMenu = true }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.MoreVert, null, Modifier.size(18.dp))
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(
                            text        = { Text("Edit") },
                            leadingIcon = { Icon(Icons.Default.Edit, null) },
                            onClick     = { showMenu = false; onEdit() }
                        )
                        DropdownMenuItem(
                            text        = { Text("Hapus", color = MaterialTheme.colorScheme.error) },
                            leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
                            onClick     = { showMenu = false; onDelete() }
                        )
                    }
                }
            }

            LinearProgressIndicator(
                progress   = { animatedProgress },
                modifier   = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                color      = progressColor,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    "${"%.0f".format(item.percentage)}%",
                    style      = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color      = progressColor
                )
                Text(
                    text = when {
                        item.isOverBudget -> "Melebihi Rp ${fmt.format(-item.remaining)}"
                        else              -> "Sisa Rp ${fmt.format(item.remaining)}"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (item.isOverBudget) ExpenseRed
                            else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ── Add/Edit Dialog ──────────────────────────────────────────────────────────

@Composable
private fun BudgetFormDialog(
    editing: BudgetEntity?,
    availableCategories: List<CategoryEntity>,
    allBudgets: List<BudgetWithSpent>,
    onSave: (categoryId: String, limitAmount: Long) -> Unit,
    onDismiss: () -> Unit
) {
    // Jika editing, category sudah dipilih
    val editingCategory = if (editing != null) {
        allBudgets.find { it.budget.id == editing.id }?.let {
            CategoryEntity(
                id       = editing.categoryId,
                name     = it.categoryName,
                type     = "EXPENSE",
                colorHex = it.categoryColor
            )
        }
    } else null

    val allOptions = if (editingCategory != null) listOf(editingCategory) + availableCategories
                     else availableCategories

    var selectedCategory by remember { mutableStateOf(editingCategory ?: allOptions.firstOrNull()) }
    var amount by remember { mutableStateOf(editing?.limitAmount?.toString() ?: "") }
    var amountError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (editing != null) "Edit Budget" else "Tambah Budget", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                // Kategori selector
                if (editing == null && allOptions.size > 1) {
                    Text("Kategori", style = MaterialTheme.typography.labelMedium)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        allOptions.forEach { cat ->
                            val isSelected = selectedCategory?.id == cat.id
                            val catColor = runCatching {
                                Color(android.graphics.Color.parseColor(cat.colorHex))
                            }.getOrElse { Color.Gray }

                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedCategory = cat },
                                color = if (isSelected) catColor.copy(alpha = 0.15f)
                                        else MaterialTheme.colorScheme.surfaceVariant,
                                shape = MaterialTheme.shapes.small
                            ) {
                                Row(
                                    Modifier.padding(12.dp),
                                    verticalAlignment     = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Box(
                                        Modifier.size(24.dp).clip(CircleShape).background(catColor),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(cat.name.take(1), color = Color.White, style = MaterialTheme.typography.labelSmall)
                                    }
                                    Text(cat.name, style = MaterialTheme.typography.bodyMedium)
                                    if (isSelected) {
                                        Spacer(Modifier.weight(1f))
                                        Icon(Icons.Default.Check, null, Modifier.size(18.dp), tint = catColor)
                                    }
                                }
                            }
                        }
                    }
                } else if (editingCategory != null) {
                    Text("Kategori: ${editingCategory.name}", style = MaterialTheme.typography.bodyMedium)
                }

                // Amount input
                OutlinedTextField(
                    value           = amount,
                    onValueChange   = { amount = it.filter { c -> c.isDigit() }; amountError = null },
                    label           = { Text("Batas Budget") },
                    prefix          = { Text("Rp ") },
                    isError         = amountError != null,
                    supportingText  = amountError?.let { { Text(it) } },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine      = true,
                    modifier        = Modifier.fillMaxWidth()
                )

                // Quick amount chips
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(100_000L, 500_000L, 1_000_000L).forEach { preset ->
                        val label = when {
                            preset >= 1_000_000 -> "${preset / 1_000_000}jt"
                            else                -> "${preset / 1_000}rb"
                        }
                        FilterChip(
                            selected = amount == preset.toString(),
                            onClick  = { amount = preset.toString(); amountError = null },
                            label    = { Text(label) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val parsedAmount = amount.toLongOrNull()
                    val catId = selectedCategory?.id
                    when {
                        catId == null              -> amountError = "Pilih kategori"
                        parsedAmount == null || parsedAmount <= 0 -> amountError = "Nominal tidak valid"
                        else -> onSave(catId, parsedAmount)
                    }
                }
            ) { Text(if (editing != null) "Simpan" else "Tambah") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Batal") }
        }
    )
}

// ── Empty State ──────────────────────────────────────────────────────────────

@Composable
private fun EmptyBudgetState(hasCategories: Boolean, onAdd: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().padding(vertical = 48.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                Icons.Default.PieChart, null,
                Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text("Belum ada budget", style = MaterialTheme.typography.titleMedium)
            Text(
                "Atur batas pengeluaran per kategori\nagar keuanganmu lebih terkontrol",
                style     = MaterialTheme.typography.bodyMedium,
                color     = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            if (hasCategories) {
                Button(onClick = onAdd) {
                    Icon(Icons.Default.Add, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Tambah Budget")
                }
            }
        }
    }
}
