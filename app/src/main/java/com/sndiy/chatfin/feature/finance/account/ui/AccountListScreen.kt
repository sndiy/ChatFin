// app/src/main/java/com/sndiy/chatfin/feature/finance/account/ui/AccountListScreen.kt

package com.sndiy.chatfin.feature.finance.account.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import com.sndiy.chatfin.core.data.local.entity.FinanceAccountEntity
import com.sndiy.chatfin.core.ui.theme.ChatFinSpacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountListScreen(
    onNavigateBack: () -> Unit,
    onNavigateToAddAccount: () -> Unit,
    onNavigateToEditAccount: (String) -> Unit,
    viewModel: AccountViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // State konfirmasi hapus
    var accountToDelete by remember { mutableStateOf<FinanceAccountEntity?>(null) }

    // Tampilkan snackbar untuk error/success
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(uiState.errorMessage, uiState.successMessage) {
        uiState.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessages()
        }
        uiState.successMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessages()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Kelola Akun") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector        = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Kembali"
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onNavigateToAddAccount) {
                Icon(
                    imageVector        = Icons.Default.Add,
                    contentDescription = "Tambah akun"
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->

        if (uiState.accounts.isEmpty()) {
            // State kosong
            EmptyAccountState(
                modifier          = Modifier.padding(paddingValues),
                onAddAccount      = onNavigateToAddAccount
            )
        } else {
            LazyColumn(
                modifier          = Modifier.padding(paddingValues),
                contentPadding    = PaddingValues(ChatFinSpacing.md),
                verticalArrangement = Arrangement.spacedBy(ChatFinSpacing.sm)
            ) {
                items(
                    items = uiState.accounts,
                    key   = { it.id }
                ) { account ->
                    AccountListItem(
                        account    = account,
                        isActive   = account.id == uiState.activeAccount?.id,
                        onSetActive = { viewModel.switchAccount(account.id) },
                        onEdit     = { onNavigateToEditAccount(account.id) },
                        onDelete   = { accountToDelete = account }
                    )
                }
            }
        }
    }

    // ── Dialog konfirmasi hapus ────────────────────────────────────────────────
    accountToDelete?.let { account ->
        AlertDialog(
            onDismissRequest = { accountToDelete = null },
            title   = { Text("Hapus Akun?") },
            text    = {
                Text("Akun '${account.name}' dan semua data di dalamnya akan dihapus permanen. Tindakan ini tidak bisa dibatalkan.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteAccount(account)
                        accountToDelete = null
                    }
                ) {
                    Text(
                        text  = "Hapus",
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { accountToDelete = null }) {
                    Text("Batal")
                }
            }
        )
    }
}

// ── Satu item akun dalam list ─────────────────────────────────────────────────
@Composable
private fun AccountListItem(
    account: FinanceAccountEntity,
    isActive: Boolean,
    onSetActive: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val accentColor = runCatching {
        Color(android.graphics.Color.parseColor(account.colorHex))
    }.getOrElse { Color.Gray }

    var showMenu by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors   = CardDefaults.cardColors(
            containerColor = if (isActive)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
            else
                MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier          = Modifier
                .fillMaxWidth()
                .clickable(onClick = onSetActive)
                .padding(ChatFinSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ChatFinSpacing.md)
        ) {
            // Avatar
            Box(
                modifier         = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(accentColor),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text  = account.name.take(1).uppercase(),
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White
                )
            }

            // Info akun
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(ChatFinSpacing.xs)
                ) {
                    Text(
                        text       = account.name,
                        style      = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (isActive) {
                        Surface(
                            color = MaterialTheme.colorScheme.primary,
                            shape = MaterialTheme.shapes.small
                        ) {
                            Text(
                                text     = "AKTIF",
                                style    = MaterialTheme.typography.labelSmall,
                                color    = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                if (!account.description.isNullOrBlank()) {
                    Text(
                        text  = account.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text  = account.currency,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Menu opsi (titik tiga)
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(
                        imageVector        = Icons.Default.MoreVert,
                        contentDescription = "Opsi"
                    )
                }
                DropdownMenu(
                    expanded         = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text    = { Text("Edit") },
                        leadingIcon = {
                            Icon(Icons.Default.Edit, contentDescription = null)
                        },
                        onClick = {
                            showMenu = false
                            onEdit()
                        }
                    )
                    DropdownMenuItem(
                        text    = { Text("Hapus", color = MaterialTheme.colorScheme.error) },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                        },
                        onClick = {
                            showMenu = false
                            onDelete()
                        }
                    )
                }
            }
        }
    }
}

// ── Tampilan saat belum ada akun ──────────────────────────────────────────────
@Composable
private fun EmptyAccountState(
    modifier: Modifier = Modifier,
    onAddAccount: () -> Unit
) {
    Column(
        modifier              = modifier.fillMaxSize(),
        horizontalAlignment   = Alignment.CenterHorizontally,
        verticalArrangement   = Arrangement.Center
    ) {
        Icon(
            imageVector        = Icons.Default.AccountBalance,
            contentDescription = null,
            modifier           = Modifier.size(72.dp),
            tint               = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(ChatFinSpacing.md))
        Text(
            text  = "Belum ada akun",
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            text  = "Buat akun pertamamu untuk mulai mencatat keuangan",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(ChatFinSpacing.lg))
        Button(onClick = onAddAccount) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(modifier = Modifier.width(ChatFinSpacing.sm))
            Text("Buat Akun")
        }
    }
}