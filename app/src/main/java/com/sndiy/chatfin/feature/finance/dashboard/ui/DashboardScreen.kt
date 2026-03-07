package com.sndiy.chatfin.feature.finance.dashboard.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sndiy.chatfin.core.data.local.entity.WalletEntity
import java.text.NumberFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onNavigateToChat: () -> Unit = {},
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val uiState       by viewModel.uiState.collectAsStateWithLifecycle()
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
                title = { Text("Dashboard", fontWeight = FontWeight.Bold) },
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
                modifier        = Modifier.fillMaxSize().padding(padding),
                contentPadding  = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    BalanceSummaryCard(
                        totalBalance   = uiState.totalBalance,
                        monthlyIncome  = uiState.monthlyIncome,
                        monthlyExpense = uiState.monthlyExpense
                    )
                }
                item { WalletsSection(wallets = uiState.wallets) }

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
            }
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
                        color      = Color(0xFF1B8A4C)
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
                        color      = Color(0xFFE53935)
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
                        Icon(
                            Icons.Default.AccountBalanceWallet, null,
                            tint = MaterialTheme.colorScheme.primary
                        )
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
                    tint = if (tx.type == "INCOME") Color(0xFF1B8A4C) else Color(0xFFE53935)
                )
                Column {
                    Text(
                        tx.categoryName,
                        fontWeight = FontWeight.Medium,
                        style      = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        if (tx.note.isNullOrBlank()) tx.date else "${tx.date} · ${tx.note}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Text(
                "${if (tx.type == "INCOME") "+" else "-"} Rp ${fmt.format(tx.amount)}",
                fontWeight = FontWeight.SemiBold,
                color      = if (tx.type == "INCOME") Color(0xFF1B8A4C) else Color(0xFFE53935)
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
                onClick  = { if (name.isNotBlank()) onConfirm(name.trim()) },
                enabled  = name.isNotBlank()
            ) { Text("Mulai") }
        }
    )
}