// app/src/main/java/com/sndiy/chatfin/feature/finance/dashboard/ui/BudgetOverviewSection.kt
//
// Widget budget overview untuk Dashboard.
// Tambahkan di DashboardScreen.kt setelah WalletsSection dan sebelum Transaksi Terbaru:
//
//   item {
//       BudgetOverviewSection(
//           budgets           = uiState.budgetOverview,
//           hasBudgets        = uiState.hasBudgets,
//           onNavigateToBudget = onNavigateToBudget
//       )
//   }
//
// Dan update parameter DashboardScreen:
//   fun DashboardScreen(
//       onNavigateToChat: () -> Unit = {},
//       onNavigateToBudget: () -> Unit = {},   // <-- TAMBAH INI
//       viewModel: DashboardViewModel = hiltViewModel()
//   )

package com.sndiy.chatfin.feature.finance.dashboard.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sndiy.chatfin.core.ui.theme.ExpenseRed
import com.sndiy.chatfin.core.ui.theme.IncomeGreen
import com.sndiy.chatfin.feature.finance.budget.data.repository.BudgetWithSpent
import java.text.NumberFormat
import java.util.Locale

@Composable
fun BudgetOverviewSection(
    budgets: List<BudgetWithSpent>,
    hasBudgets: Boolean,
    onNavigateToBudget: () -> Unit
) {
    val fmt = NumberFormat.getNumberInstance(Locale("id", "ID"))

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Text(
                "Budget Bulan Ini",
                style      = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            TextButton(onClick = onNavigateToBudget) {
                Text(if (hasBudgets) "Lihat Semua" else "Atur Budget")
                Spacer(Modifier.width(4.dp))
                Icon(Icons.Default.ChevronRight, null, Modifier.size(16.dp))
            }
        }

        if (!hasBudgets) {
            // Empty state mini
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors   = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Row(
                    modifier              = Modifier.padding(16.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.PieChart, null,
                        tint     = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    )
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Belum ada budget",
                            style      = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            "Atur batas pengeluaran per kategori",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            budgets.forEach { item ->
                BudgetMiniCard(item = item, fmt = fmt)
            }
        }
    }
}

@Composable
private fun BudgetMiniCard(item: BudgetWithSpent, fmt: NumberFormat) {
    val animatedProgress by animateFloatAsState(
        targetValue   = (item.percentage / 100f).coerceIn(0f, 1f),
        animationSpec = tween(600, easing = FastOutSlowInEasing),
        label         = "mini_budget_progress"
    )
    val progressColor = when {
        item.isOverBudget -> ExpenseRed
        item.isNearLimit  -> Color(0xFFE65100)
        else              -> IncomeGreen
    }
    val categoryColor = runCatching {
        Color(android.graphics.Color.parseColor(item.categoryColor))
    }.getOrElse { Color.Gray }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Box(
                        modifier         = Modifier.size(28.dp).clip(CircleShape).background(categoryColor),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            item.categoryName.take(1),
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        item.categoryName,
                        style    = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    "${"%.0f".format(item.percentage)}%",
                    style      = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color      = progressColor
                )
            }

            LinearProgressIndicator(
                progress   = { animatedProgress },
                modifier   = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                color      = progressColor,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )

            Text(
                "Rp ${fmt.format(item.spent)} / Rp ${fmt.format(item.budget.limitAmount)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
