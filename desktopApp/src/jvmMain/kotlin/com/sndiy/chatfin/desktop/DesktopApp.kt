package com.sndiy.chatfin.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sndiy.chatfin.core.data.local.entity.FinanceAccountEntity
import com.sndiy.chatfin.core.data.local.entity.TransactionEntity
import com.sndiy.chatfin.core.data.local.entity.WalletEntity
import com.sndiy.chatfin.core.domain.LocalInsightEngine
import com.sndiy.chatfin.core.ui.component.*
import com.sndiy.chatfin.core.ui.theme.*
import com.sndiy.chatfin.core.ui.util.formatRupiah
import com.sndiy.chatfin.core.ui.util.toRpString
import com.sndiy.chatfin.feature.finance.account.data.repository.AccountRepository
import com.sndiy.chatfin.feature.finance.budget.data.repository.BudgetRepository
import com.sndiy.chatfin.feature.finance.budget.data.repository.BudgetWithSpent
import com.sndiy.chatfin.feature.finance.transaction.data.repository.CategoryRepository
import com.sndiy.chatfin.feature.finance.transaction.data.repository.TransactionRepository
import com.sndiy.chatfin.feature.finance.transaction.data.repository.WalletRepository
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import java.time.LocalDate
import java.time.format.DateTimeFormatter

import com.sndiy.chatfin.core.data.auth.DesktopAuthRepository
import com.sndiy.chatfin.core.data.auth.AuthUser
import com.sndiy.chatfin.core.data.sync.DesktopSyncOrchestrator
import com.sndiy.chatfin.core.data.sync.SyncStats
import com.sndiy.chatfin.core.data.sync.SyncStatus
import com.sndiy.chatfin.core.data.sync.SyncStatusRepository
import com.sndiy.chatfin.desktop.ui.AuthDialog

enum class DesktopNavTab(val label: String) {
    DASHBOARD("Dashboard"),
    TRANSACTIONS("Transaksi"),
    WALLETS("Dompet"),
    BUDGETS("Anggaran")
}

@Composable
fun DesktopApp() {
    val accountRepo: AccountRepository = koinInject()
    val walletRepo: WalletRepository = koinInject()
    val transactionRepo: TransactionRepository = koinInject()
    val budgetRepo: BudgetRepository = koinInject()
    val categoryRepo: CategoryRepository = koinInject()
    val authRepo: DesktopAuthRepository = koinInject()
    val syncStatusRepo: SyncStatusRepository = koinInject()
    val syncOrchestrator: DesktopSyncOrchestrator = koinInject()

    val coroutineScope = rememberCoroutineScope()
    var selectedTab by remember { mutableStateOf(DesktopNavTab.DASHBOARD) }

    // Start background sync orchestrator
    LaunchedEffect(Unit) {
        syncOrchestrator.start(this)
    }

    // Auth & Sync State
    val authUser by authRepo.authState.collectAsState(initial = authRepo.currentUser)
    val syncStatus by syncStatusRepo.status.collectAsState()
    val lastActivationStats by syncOrchestrator.lastActivationStats.collectAsState()
    var showAuthDialog by remember { mutableStateOf(false) }
    var showLogoutConfirmDialog by remember { mutableStateOf(false) }

    // Account & Data State
    val accounts by accountRepo.getAllAccounts().collectAsState(initial = emptyList())
    val activeAccount = remember(accounts) {
        accounts.find { it.isActive } ?: accounts.firstOrNull()
    }

    LaunchedEffect(accounts) {
        if (accounts.isEmpty()) {
            val id = accountRepo.createAccount("Pribadi")
            accountRepo.switchActiveAccount(id)
        }
    }

    val accountId = activeAccount?.id ?: ""
    val wallets by walletRepo.getWalletsByAccount(accountId).collectAsState(initial = emptyList())
    val totalBalance by walletRepo.getTotalBalanceByAccount(accountId).collectAsState(initial = 0L)
    val transactions by transactionRepo.getTransactionsByAccount(accountId).collectAsState(initial = emptyList())

    val today = remember { LocalDate.now() }
    val startOfMonth = remember { today.withDayOfMonth(1) }
    val endOfMonth = remember { today.withDayOfMonth(today.lengthOfMonth()) }
    val incomeThisMonth by transactionRepo.getTotalIncome(accountId, startOfMonth, endOfMonth).collectAsState(initial = 0L)
    val expenseThisMonth by transactionRepo.getTotalExpense(accountId, startOfMonth, endOfMonth).collectAsState(initial = 0L)

    var showAddTxDialog by remember { mutableStateOf(false) }
    var showAddWalletDialog by remember { mutableStateOf(false) }

    ChatFinTheme(darkTheme = false, accentKey = "Ungu") {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Row(modifier = Modifier.fillMaxSize()) {
                // ── Sidebar Navigation ───────────────────────────────────────
                NavigationRail(
                    modifier = Modifier.width(220.dp).fillMaxHeight(),
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    header = {
                        Column(
                            modifier = Modifier.padding(vertical = 20.dp, horizontal = 16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(MaiPurple),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AccountBalanceWallet,
                                    contentDescription = "ChatFin",
                                    tint = Color.White,
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "ChatFin",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaiPurple
                            )
                            Text(
                                text = "Sakurajima Mai",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            // ── Account Selector Dropdown ─────────────────────────
                            if (accounts.isNotEmpty()) {
                                var accountMenuExpanded by remember { mutableStateOf(false) }
                                Spacer(modifier = Modifier.height(10.dp))
                                Box {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.surface,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable { accountMenuExpanded = true }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Person,
                                                contentDescription = "Akun",
                                                modifier = Modifier.size(16.dp),
                                                tint = MaiPurple
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = activeAccount?.name ?: "Pilih Akun",
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Icon(
                                                imageVector = Icons.Default.ArrowDropDown,
                                                contentDescription = "Pilih Akun",
                                                modifier = Modifier.size(16.dp),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    DropdownMenu(
                                        expanded = accountMenuExpanded,
                                        onDismissRequest = { accountMenuExpanded = false }
                                    ) {
                                        accounts.forEach { acc ->
                                            DropdownMenuItem(
                                                text = {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Text(
                                                            text = acc.name,
                                                            fontWeight = if (acc.id == accountId) FontWeight.Bold else FontWeight.Normal
                                                        )
                                                        if (acc.id == accountId) {
                                                            Spacer(modifier = Modifier.width(8.dp))
                                                            Icon(
                                                                imageVector = Icons.Default.Check,
                                                                contentDescription = "Aktif",
                                                                modifier = Modifier.size(16.dp),
                                                                tint = MaiPurple
                                                            )
                                                        }
                                                    }
                                                },
                                                onClick = {
                                                    coroutineScope.launch {
                                                        accountRepo.switchActiveAccount(acc.id)
                                                    }
                                                    accountMenuExpanded = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                ) {
                    Spacer(modifier = Modifier.height(16.dp))
                    DesktopNavTab.entries.forEach { tab ->
                        NavigationRailItem(
                            selected = selectedTab == tab,
                            onClick = { selectedTab = tab },
                            icon = {
                                Icon(
                                    imageVector = when (tab) {
                                        DesktopNavTab.DASHBOARD -> Icons.Default.Dashboard
                                        DesktopNavTab.TRANSACTIONS -> Icons.Default.ReceiptLong
                                        DesktopNavTab.WALLETS -> Icons.Default.AccountBalanceWallet
                                        DesktopNavTab.BUDGETS -> Icons.Default.PieChart
                                    },
                                    contentDescription = tab.label
                                )
                            },
                            label = { Text(tab.label) },
                            colors = NavigationRailItemDefaults.colors(
                                selectedIconColor = MaiPurple,
                                selectedTextColor = MaiPurple,
                                indicatorColor = MaiPurple.copy(alpha = 0.15f)
                            )
                        )
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    // ── Profile & Sync Status Widget ─────────────────────────
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                if (authUser == null) showAuthDialog = true
                            },
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(
                                            when (syncStatus) {
                                                SyncStatus.IN_SYNC -> Color(0xFF2E7D32)
                                                SyncStatus.SYNCING -> Color(0xFFF57C00)
                                                SyncStatus.OFFLINE -> Color(0xFF757575)
                                                SyncStatus.IDLE    -> Color(0xFF9E9E9E)
                                            }
                                        )
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = when (syncStatus) {
                                        SyncStatus.IN_SYNC -> "Tersinkron"
                                        SyncStatus.SYNCING -> "Menyinkronkan..."
                                        SyncStatus.OFFLINE -> "Offline"
                                        SyncStatus.IDLE    -> "Mode Offline"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = authUser?.email ?: "Klik untuk Login",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1
                            )
                            if (authUser != null) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Keluar (Logout)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.clickable {
                                        showLogoutConfirmDialog = true
                                    }
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                VerticalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                // ── Main Content Area ────────────────────────────────────────
                Box(modifier = Modifier.weight(1f).fillMaxHeight().padding(24.dp)) {
                    when (selectedTab) {
                        DesktopNavTab.DASHBOARD -> DesktopDashboardView(
                            totalBalance = totalBalance ?: 0L,
                            incomeThisMonth = incomeThisMonth ?: 0L,
                            expenseThisMonth = expenseThisMonth ?: 0L,
                            wallets = wallets,
                            transactions = transactions,
                            onAddTransaction = { showAddTxDialog = true }
                        )
                        DesktopNavTab.TRANSACTIONS -> DesktopTransactionsView(
                            transactions = transactions,
                            wallets = wallets,
                            onAddTransaction = { showAddTxDialog = true },
                            onDeleteTransaction = { tx ->
                                coroutineScope.launch { transactionRepo.deleteTransaction(tx) }
                            }
                        )
                        DesktopNavTab.WALLETS -> DesktopWalletsView(
                            wallets = wallets,
                            onAddWallet = { showAddWalletDialog = true },
                            onDeleteWallet = { wallet ->
                                coroutineScope.launch { walletRepo.deleteWallet(wallet) }
                            }
                        )
                        DesktopNavTab.BUDGETS -> DesktopBudgetsView(
                            budgetRepo = budgetRepo,
                            categoryRepo = categoryRepo,
                            accountId = accountId
                        )
                    }
                }
            }

            // ── Dialog Tambah Transaksi ──────────────────────────────────────
            if (showAddTxDialog && wallets.isNotEmpty()) {
                AddTransactionDialog(
                    wallets = wallets,
                    onDismiss = { showAddTxDialog = false },
                    onConfirm = { type, amount, note, walletId ->
                        coroutineScope.launch {
                            transactionRepo.addTransaction(
                                accountId = accountId,
                                type = type,
                                amount = amount,
                                categoryId = if (type == "INCOME") "inc_salary" else "exp_food",
                                walletId = walletId,
                                note = note
                            )
                            showAddTxDialog = false
                        }
                    }
                )
            }

            // ── Dialog Tambah Dompet ─────────────────────────────────────────
            if (showAddWalletDialog) {
                AddWalletDialog(
                    onDismiss = { showAddWalletDialog = false },
                    onConfirm = { name, type, balance ->
                        coroutineScope.launch {
                            walletRepo.createWallet(
                                accountId = accountId,
                                name = name,
                                type = type,
                                balance = balance,
                                currency = "IDR",
                                colorHex = "#9C27B0",
                                iconName = "account_balance_wallet"
                            )
                            showAddWalletDialog = false
                        }
                    }
                )
            }

            // ── Dialog Autentikasi Cloud ─────────────────────────────────────
            if (showAuthDialog) {
                AuthDialog(
                    authRepo = authRepo,
                    onDismiss = { showAuthDialog = false },
                    onSuccess = { showAuthDialog = false }
                )
            }

            // ── Dialog Konfirmasi Logout & Pembersihan Data Lokal ────────────
            if (showLogoutConfirmDialog) {
                AlertDialog(
                    onDismissRequest = { showLogoutConfirmDialog = false },
                    title = { Text("Keluar dari Akun?", fontWeight = FontWeight.Bold) },
                    text = {
                        Text("Data keuangan di perangkat ini akan dibersihkan demi keamanan dan privasi. Seluruh data kamu tetap tersimpan aman di cloud dan akan otomatis tersinkronkan kembali saat kamu masuk.")
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                coroutineScope.launch {
                                    authRepo.logout()
                                    showLogoutConfirmDialog = false
                                }
                            }
                        ) {
                            Text("Keluar & Bersihkan Data Lokal", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showLogoutConfirmDialog = false }) {
                            Text("Batal")
                        }
                    }
                )
            }

            // ── Dialog Ringkasan Hasil Sync Pertama (First-Activation Audit) ─
            lastActivationStats?.let { stats ->
                AlertDialog(
                    onDismissRequest = { syncOrchestrator.dismissActivationStats() },
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.CloudDone,
                                contentDescription = null,
                                tint = Color(0xFF2E7D32),
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Sinkronisasi Selesai", fontWeight = FontWeight.Bold)
                        }
                    },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "Data cloud dan lokal berhasil disinkronkan:",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text("• Diunduh dari Cloud: ${stats.totalDownloaded} data (${stats.downloadedTransactions} transaksi, ${stats.downloadedWallets} dompet)")
                            Text("• Diunggah ke Cloud: ${stats.totalUploaded} data")
                            if (stats.reconciledWallets > 0) {
                                Text("• Dompet Direkonsiliasi: ${stats.reconciledWallets} dompet")
                            }
                            if (stats.skippedCorruptedRecords > 0) {
                                Text(
                                    text = "• Data Rusak Di-skip: ${stats.skippedCorruptedRecords} data (nominal tidak valid)",
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = { syncOrchestrator.dismissActivationStats() },
                            colors = ButtonDefaults.buttonColors(containerColor = MaiPurple)
                        ) {
                            Text("Mengerti")
                        }
                    }
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Dashboard View
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun DesktopDashboardView(
    totalBalance: Long,
    incomeThisMonth: Long,
    expenseThisMonth: Long,
    wallets: List<WalletEntity>,
    transactions: List<TransactionEntity>,
    onAddTransaction: () -> Unit
) {
    val insight = remember(expenseThisMonth, incomeThisMonth, totalBalance) {
        LocalInsightEngine.spendingInsight(
            balance = totalBalance,
            income = incomeThisMonth,
            expense = expenseThisMonth
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Ringkasan Finansial",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Pantau saldo dan mutasi kas secara offline-first",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Button(
                    onClick = onAddTransaction,
                    colors = ButtonDefaults.buttonColors(containerColor = MaiPurple)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Catat Transaksi")
                }
            }
        }

        // Kartu Saldo & Metrik
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                MetricCard(
                    title = "Total Saldo",
                    amount = totalBalance.toRpString(),
                    color = MaiPurple,
                    icon = Icons.Default.AccountBalanceWallet,
                    modifier = Modifier.weight(1f)
                )
                MetricCard(
                    title = "Pemasukan Bulan Ini",
                    amount = "+${incomeThisMonth.toRpString()}",
                    color = IncomeGreen,
                    icon = Icons.AutoMirrored.Filled.TrendingUp,
                    modifier = Modifier.weight(1f)
                )
                MetricCard(
                    title = "Pengeluaran Bulan Ini",
                    amount = "-${expenseThisMonth.toRpString()}",
                    color = ExpenseRed,
                    icon = Icons.AutoMirrored.Filled.TrendingDown,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Mai Local Insight Card (Lapis 2 - Progressive Disclosure)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaiPurple.copy(alpha = 0.08f))
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(MaiPurple),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Catatan Mai",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaiPurple
                        )
                        Text(
                            text = insight,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        // Transaksi Terbaru
        item {
            Text(
                text = "Transaksi Terbaru",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }

        if (transactions.isEmpty()) {
            item {
                EmptyStateView(
                    title = "Belum Ada Transaksi",
                    description = "Catat pemasukan atau pengeluaran pertamamu untuk mulai memantau keuangan.",
                    actionLabel = "Tambah Transaksi",
                    onAction = onAddTransaction
                )
            }
        } else {
            items(transactions.take(5)) { tx ->
                TransactionCard(
                    transaction = tx,
                    walletName = wallets.find { it.id == tx.walletId }?.name ?: "Dompet"
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Transactions View
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun DesktopTransactionsView(
    transactions: List<TransactionEntity>,
    wallets: List<WalletEntity>,
    onAddTransaction: () -> Unit,
    onDeleteTransaction: (TransactionEntity) -> Unit
) {
    var deletingTx by remember { mutableStateOf<TransactionEntity?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Daftar Transaksi",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Semua riwayat mutasi kas yang tersimpan di perangkat ini",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Button(
                onClick = onAddTransaction,
                colors = ButtonDefaults.buttonColors(containerColor = MaiPurple)
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Tambah Transaksi")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (transactions.isEmpty()) {
            EmptyStateView(
                title = "Belum Ada Transaksi",
                description = "Transaksi yang kamu catat akan muncul di sini beserta rincian tanggal dan dompetnya.",
                actionLabel = "Catat Transaksi Sekarang",
                onAction = onAddTransaction,
                modifier = Modifier.weight(1f)
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(transactions) { tx ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TransactionCard(
                            transaction = tx,
                            walletName = wallets.find { it.id == tx.walletId }?.name ?: "Dompet",
                            modifier = Modifier.weight(1f)
                        )
                        if (!tx.isInitialBalance) {
                            IconButton(onClick = { deletingTx = tx }) {
                                Icon(Icons.Default.Delete, contentDescription = "Hapus", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
    }

    deletingTx?.let { tx ->
        ConfirmDestructiveDialog(
            title = "Hapus Transaksi?",
            body = "Transaksi sebesar ${tx.amount.toRpString()} akan dihapus dan saldo dompet akan dikembalikan.",
            onConfirm = {
                onDeleteTransaction(tx)
                deletingTx = null
            },
            onDismiss = { deletingTx = null }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Wallets View
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun DesktopWalletsView(
    wallets: List<WalletEntity>,
    onAddWallet: () -> Unit,
    onDeleteWallet: (WalletEntity) -> Unit
) {
    var deletingWallet by remember { mutableStateOf<WalletEntity?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Daftar Dompet",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Kelola rekening bank, dompet digital, dan uang tunai",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Button(
                onClick = onAddWallet,
                colors = ButtonDefaults.buttonColors(containerColor = MaiPurple)
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Tambah Dompet")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (wallets.isEmpty()) {
            EmptyStateView(
                title = "Belum Ada Dompet",
                description = "Buat dompet untuk memisahkan saldo tunai, rekening bank, atau e-wallet kamu.",
                actionLabel = "Buat Dompet Baru",
                onAction = onAddWallet,
                modifier = Modifier.weight(1f)
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(wallets) { wallet ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        WalletCard(
                            wallet = wallet,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { deletingWallet = wallet }) {
                            Icon(Icons.Default.Delete, contentDescription = "Hapus", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }

    deletingWallet?.let { wallet ->
        ConfirmDestructiveDialog(
            title = "Hapus Dompet ${wallet.name}?",
            body = "Dompet dan seluruh transaksi terkait akan dihapus. Pastikan saldo sudah dipindahkan jika perlu.",
            onConfirm = {
                onDeleteWallet(wallet)
                deletingWallet = null
            },
            onDismiss = { deletingWallet = null }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Budgets View
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun DesktopBudgetsView(
    budgetRepo: BudgetRepository,
    categoryRepo: CategoryRepository,
    accountId: String
) {
    val coroutineScope = rememberCoroutineScope()
    val now = remember { LocalDate.now() }
    val rawBudgets by budgetRepo.getBudgetsByAccountAndPeriod(accountId, now.year, now.monthValue).collectAsState(initial = emptyList())
    var budgetsWithSpent by remember { mutableStateOf<List<BudgetWithSpent>>(emptyList()) }

    LaunchedEffect(rawBudgets) {
        val start = now.withDayOfMonth(1)
        val end = now.withDayOfMonth(now.lengthOfMonth())
        val list = rawBudgets.map { b ->
            val spent = budgetRepo.getSpentForCategory(accountId, b.categoryId, start, end)
            BudgetWithSpent(
                budget = b,
                spent = spent,
                categoryName = b.categoryId.replace("exp_", "").replaceFirstChar { it.uppercase() },
                categoryColor = "#9C27B0"
            )
        }
        budgetsWithSpent = list
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "Anggaran Bulanan (${now.month.name.lowercase().replaceFirstChar { it.uppercase() }} ${now.year})",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Batasi pengeluaran per kategori agar tidak boros",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (budgetsWithSpent.isEmpty()) {
            EmptyStateView(
                title = "Belum Ada Budget Bulan Ini",
                description = "Budget membantu membatasi pengeluaran per kategori, misalnya maksimal Rp 1.000.000 untuk Makanan per bulan.",
                actionLabel = "Buat Budget Makanan (Rp 1.000.000)",
                onAction = {
                    coroutineScope.launch {
                        budgetRepo.createBudget(
                            accountId = accountId,
                            categoryId = "exp_food",
                            limitAmount = 1_000_000L,
                            year = now.year,
                            month = now.monthValue
                        )
                    }
                },
                modifier = Modifier.weight(1f)
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(budgetsWithSpent) { item ->
                    BudgetCard(item = item)
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Components & Dialogs
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun MetricCard(
    title: String,
    amount: String,
    color: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(24.dp))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(text = title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(text = amount, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = color)
            }
        }
    }
}

@Composable
private fun AddTransactionDialog(
    wallets: List<WalletEntity>,
    onDismiss: () -> Unit,
    onConfirm: (type: String, amount: Long, note: String, walletId: String) -> Unit
) {
    var type by remember { mutableStateOf("EXPENSE") }
    var amountText by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var selectedWalletId by remember { mutableStateOf(wallets.first().id) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Catat Transaksi Baru") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = type == "EXPENSE",
                        onClick = { type = "EXPENSE" },
                        label = { Text("Pengeluaran") }
                    )
                    FilterChip(
                        selected = type == "INCOME",
                        onClick = { type = "INCOME" },
                        label = { Text("Pemasukan") }
                    )
                }
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it.filter { c -> c.isDigit() } },
                    label = { Text("Nominal (Rp)") },
                    placeholder = { Text("0") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Catatan / Keterangan") },
                    placeholder = { Text("Misal: Makan Siang") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amount = amountText.toLongOrNull() ?: 0L
                    if (amount > 0) {
                        onConfirm(type, amount, note, selectedWalletId)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaiPurple)
            ) {
                Text("Simpan")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Batal") }
        }
    )
}

@Composable
private fun AddWalletDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, type: String, balance: Long) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("BANK") }
    var balanceText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Tambah Dompet Baru") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nama Dompet") },
                    placeholder = { Text("BCA, GoPay, Tunai, dll.") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("BANK", "E-WALLET", "CASH").forEach { t ->
                        FilterChip(
                            selected = type == t,
                            onClick = { type = t },
                            label = { Text(t) }
                        )
                    }
                }
                OutlinedTextField(
                    value = balanceText,
                    onValueChange = { balanceText = it.filter { c -> c.isDigit() } },
                    label = { Text("Saldo Awal (Rp)") },
                    placeholder = { Text("0") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val balance = balanceText.toLongOrNull() ?: 0L
                    if (name.isNotBlank()) {
                        onConfirm(name, type, balance)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaiPurple)
            ) {
                Text("Simpan")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Batal") }
        }
    )
}
