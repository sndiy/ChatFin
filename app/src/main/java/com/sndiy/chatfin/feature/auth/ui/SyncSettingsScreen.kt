package com.sndiy.chatfin.feature.auth.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncSettingsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToAuth: () -> Unit,
    onLoggedOut: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel()
) {
    val uiState       by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarState = remember { SnackbarHostState() }
    var showLogoutDialog by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.syncState) {
        when (val s = uiState.syncState) {
            is SyncState.Done -> {
                snackbarState.showSnackbar(
                    "Selesai — ${s.stats.transactions} transaksi, ${s.stats.wallets} dompet"
                )
                viewModel.clearSyncState()
            }
            is SyncState.Error -> {
                snackbarState.showSnackbar(s.message)
                viewModel.clearSyncState()
            }
            else -> {}
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Akun & Sinkronisasi", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Kembali")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarState) }
    ) { padding ->
        Column(
            modifier            = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── Info akun ─────────────────────────────────────────────────────
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Row(
                    modifier          = Modifier.padding(16.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.AccountCircle, null,
                        modifier = Modifier.size(40.dp),
                        tint     = MaterialTheme.colorScheme.primary
                    )
                    Column {
                        Text(
                            uiState.currentUser?.email ?: "Tidak ada akun",
                            style      = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            if (uiState.currentUser != null) "Akun aktif" else "Belum login",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(0.7f)
                        )
                    }
                }
            }

            if (uiState.currentUser != null) {
                // ── Upload ────────────────────────────────────────────────────
                SyncActionCard(
                    icon        = Icons.Default.CloudUpload,
                    title       = "Upload ke Cloud",
                    subtitle    = "Simpan data lokal ke Firebase",
                    buttonLabel = "Upload Sekarang",
                    isLoading   = uiState.syncState is SyncState.Syncing,
                    onClick     = viewModel::syncUpload
                )

                // ── Download ──────────────────────────────────────────────────
                SyncActionCard(
                    icon        = Icons.Default.CloudDownload,
                    title       = "Download dari Cloud",
                    subtitle    = "Pulihkan data dari Firebase ke perangkat ini",
                    buttonLabel = "Download Sekarang",
                    isLoading   = uiState.syncState is SyncState.Syncing,
                    onClick     = viewModel::syncDownload
                )

                // ── Loading indicator ─────────────────────────────────────────
                if (uiState.syncState is SyncState.Syncing) {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier              = Modifier.padding(16.dp).fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment     = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            Text("Sedang sinkronisasi...", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }

                Spacer(Modifier.weight(1f))

                // ── Logout ────────────────────────────────────────────────────
                OutlinedButton(
                    onClick  = { showLogoutDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors   = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(Icons.AutoMirrored.Filled.Logout, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Keluar dari Akun")
                }
            } else {
                // Belum login
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors   = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier            = Modifier.padding(24.dp).fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Default.CloudOff, null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Belum terhubung ke cloud", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Login untuk menyinkronkan data ke Firebase dan mengaksesnya dari perangkat lain",
                            style     = MaterialTheme.typography.bodySmall,
                            color     = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(onClick = onNavigateToAuth) {
                            Text("Login Sekarang")
                        }
                    }
                }
            }
        }
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title   = { Text("Keluar?") },
            text    = { Text("Data lokal tetap tersimpan. Kamu bisa login kembali kapan saja.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.logout()
                    showLogoutDialog = false
                    onLoggedOut()
                }) {
                    Text("Keluar", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) { Text("Batal") }
            }
        )
    }
}

@Composable
private fun SyncActionCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    buttonLabel: String,
    isLoading: Boolean,
    onClick: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier            = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                Column {
                    Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Button(
                onClick  = onClick,
                enabled  = !isLoading,
                modifier = Modifier.fillMaxWidth()
            ) { Text(buttonLabel) }
        }
    }
}