// app/src/main/java/com/sndiy/chatfin/feature/finance/transaction/ui/WalletScreen.kt

package com.sndiy.chatfin.feature.finance.transaction.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sndiy.chatfin.core.data.local.entity.WalletEntity
import java.text.NumberFormat
import java.util.Locale

// ── ViewModel khusus Wallet ───────────────────────────────────────────────────

data class WalletFormState(
    val id: String? = null,
    val name: String = "",
    val type: String = "CASH",
    val balance: String = "0",
    val colorHex: String = "#0061A4",
    val nameError: String? = null,
    val isLoading: Boolean = false,
    val isSaved: Boolean = false
)

// ── Daftar Dompet ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WalletListScreen(
    onNavigateBack: () -> Unit,
    onNavigateToAdd: () -> Unit,
    viewModel: TransactionViewModel = hiltViewModel()
) {
    val listState by viewModel.listState.collectAsStateWithLifecycle()
    var walletToDelete by remember { mutableStateOf<WalletEntity?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Dompet & Rekening") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Kembali")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onNavigateToAdd) {
                Icon(Icons.Default.Add, "Tambah dompet")
            }
        }
    ) { padding ->

        if (listState.wallets.isEmpty()) {
            Column(
                modifier            = Modifier.padding(padding).fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(Icons.Default.AccountBalanceWallet, null, modifier = Modifier.size(72.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(16.dp))
                Text("Belum ada dompet", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(24.dp))
                Button(onClick = onNavigateToAdd) {
                    Icon(Icons.Default.Add, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Tambah Dompet")
                }
            }
        } else {
            LazyColumn(
                modifier       = Modifier.padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Summary total saldo
                item {
                    TotalBalanceCard(wallets = listState.wallets)
                }
                items(listState.wallets, key = { it.id }) { wallet ->
                    WalletCard(
                        wallet   = wallet,
                        onDelete = { walletToDelete = wallet }
                    )
                }
            }
        }
    }

    // ── Dialog konfirmasi hapus ────────────────────────────────────────────────
    walletToDelete?.let { wallet ->
        AlertDialog(
            onDismissRequest = { walletToDelete = null },
            title   = { Text("Hapus Dompet?") },
            text    = { Text("\"${wallet.name}\" akan dihapus permanen.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteWallet(wallet)
                    walletToDelete = null
                }) {
                    Text("Hapus", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { walletToDelete = null }) { Text("Batal") }
            }
        )
    }
}

// ── Card total saldo semua dompet ─────────────────────────────────────────────
@Composable
private fun TotalBalanceCard(wallets: List<WalletEntity>) {
    val total = wallets.sumOf { it.balance }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors   = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(
            modifier            = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text  = "Total Saldo",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Text(
                text       = "Rp ${NumberFormat.getNumberInstance(Locale("id","ID")).format(total)}",
                style      = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color      = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

// ── Card satu dompet ──────────────────────────────────────────────────────────
@Composable
private fun WalletCard(wallet: WalletEntity, onDelete: () -> Unit) {
    val color = runCatching {
        Color(android.graphics.Color.parseColor(wallet.colorHex))
    }.getOrElse { MaterialTheme.colorScheme.primary }

    var showMenu by remember { mutableStateOf(false) }

    val typeLabel = when (wallet.type) {
        "BANK"        -> "Bank"
        "E_WALLET"    -> "E-Wallet"
        "CREDIT_CARD" -> "Kartu Kredit"
        else          -> "Kas"
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier          = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier         = Modifier.size(48.dp).clip(CircleShape).background(color),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when (wallet.type) {
                        "BANK"        -> Icons.Default.AccountBalance
                        "E_WALLET"    -> Icons.Default.PhoneAndroid
                        "CREDIT_CARD" -> Icons.Default.CreditCard
                        else          -> Icons.Default.Payments
                    },
                    contentDescription = null,
                    tint     = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(wallet.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(typeLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text       = "Rp ${NumberFormat.getNumberInstance(Locale("id","ID")).format(wallet.balance)}",
                    style      = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color      = if (wallet.balance >= 0) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.error
                )
                Box {
                    IconButton(onClick = { showMenu = true }, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.MoreVert, null, modifier = Modifier.size(16.dp))
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(
                            text        = { Text("Hapus", color = MaterialTheme.colorScheme.error) },
                            leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
                            onClick     = { showMenu = false; onDelete() }
                        )
                    }
                }
            }
        }
    }
}

// ── Form Tambah Dompet ────────────────────────────────────────────────────────

private val walletTypes = listOf(
    "CASH"        to "Kas",
    "BANK"        to "Bank",
    "E_WALLET"    to "E-Wallet",
    "CREDIT_CARD" to "Kartu Kredit"
)

private val walletColors = listOf(
    "#0061A4", "#1B8A4C", "#BF5B00", "#7B3294",
    "#BA1A1A", "#006874", "#9A4521", "#005FAD"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WalletFormScreen(
    onNavigateBack: () -> Unit,
    viewModel: WalletViewModel = hiltViewModel()
) {
    val formState by viewModel.formState.collectAsStateWithLifecycle()

    LaunchedEffect(formState.isSaved) {
        if (formState.isSaved) onNavigateBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Tambah Dompet") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Kembali")
                    }
                },
                actions = {
                    TextButton(onClick = viewModel::saveWallet, enabled = !formState.isLoading) {
                        Text("Simpan", fontWeight = FontWeight.SemiBold)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(
                value         = formState.name,
                onValueChange = viewModel::onNameChange,
                label         = { Text("Nama Dompet") },
                placeholder   = { Text("Contoh: BCA, GoPay, Dompet Harian") },
                isError       = formState.nameError != null,
                supportingText = formState.nameError?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
                singleLine    = true,
                modifier      = Modifier.fillMaxWidth()
            )

            // Saldo Awal
            OutlinedTextField(
                value         = formState.balance,
                onValueChange = viewModel::onBalanceChange,
                label         = { Text("Saldo Awal") },
                prefix        = { Text("Rp ") },
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                ),
                singleLine    = true,
                modifier      = Modifier.fillMaxWidth()
            )

            // Tipe Dompet
            Text("Tipe Dompet", style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                walletTypes.forEach { (value, label) ->
                    FilterChip(
                        selected = formState.type == value,
                        onClick  = { viewModel.onTypeChange(value) },
                        label    = { Text(label) }
                    )
                }
            }

            // Warna
            Text("Warna", style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                walletColors.forEach { hex ->
                    val c = runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrElse { Color.Gray }
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(c)
                            .then(
                                if (formState.colorHex == hex)
                                    Modifier.padding(3.dp)
                                else Modifier
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (formState.colorHex == hex) {
                            Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
        }
    }
}