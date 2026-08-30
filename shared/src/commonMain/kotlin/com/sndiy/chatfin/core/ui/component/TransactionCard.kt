package com.sndiy.chatfin.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sndiy.chatfin.core.data.local.entity.TransactionEntity
import com.sndiy.chatfin.core.ui.theme.ExpenseRed
import com.sndiy.chatfin.core.ui.theme.IncomeGreen
import com.sndiy.chatfin.core.ui.theme.TransferBlue
import com.sndiy.chatfin.core.ui.util.formatRupiah

@Composable
fun TransactionCard(
    transaction: TransactionEntity,
    categoryName: String = transaction.categoryId,
    walletName: String = "Dompet",
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val isExpense = transaction.type == "EXPENSE"
    val isIncome = transaction.type == "INCOME"
    val amountColor = when (transaction.type) {
        "INCOME" -> IncomeGreen
        "EXPENSE" -> ExpenseRed
        else -> TransferBlue
    }
    val amountPrefix = when (transaction.type) {
        "INCOME" -> "+Rp "
        "EXPENSE" -> "-Rp "
        else -> "Rp "
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(amountColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when (transaction.type) {
                        "INCOME" -> Icons.AutoMirrored.Filled.TrendingUp
                        "EXPENSE" -> Icons.AutoMirrored.Filled.TrendingDown
                        else -> Icons.Default.SwapHoriz
                    },
                    contentDescription = transaction.type,
                    tint = amountColor,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = transaction.note?.ifBlank { categoryName } ?: categoryName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = walletName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = " • ${transaction.date} ${transaction.time}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }

            Text(
                text = "$amountPrefix${transaction.amount.formatRupiah()}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = amountColor
            )
        }
    }
}
