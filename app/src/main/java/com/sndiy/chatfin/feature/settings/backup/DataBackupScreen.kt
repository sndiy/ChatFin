package com.sndiy.chatfin.feature.settings.backup

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.sndiy.chatfin.feature.auth.ui.AuthViewModel
import com.sndiy.chatfin.feature.auth.ui.SyncState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataBackupScreen(
    onNavigateBack: () -> Unit,
    onNavigateToAuth: () -> Unit,
    onLoggedOut: () -> Unit,
    backupViewModel: BackupViewModel = hiltViewModel(),
    authViewModel: AuthViewModel    = hiltViewModel()
) {
    val backupState   by backupViewModel.uiState.collectAsStateWithLifecycle()
    val authState     by authViewModel.uiState.collectAsStateWithLifecycle()
    val snackbarState = remember { SnackbarHostState() }
    var showLogoutDialog by remember { mutableStateOf(false) }

    val isLoggedIn = authState.currentUser != null
    val isSyncing  = authState.syncState is SyncState.Syncing

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        uri?.let { backupViewModel.exportBackup(it) }
        backupViewModel.refreshFileName()
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { backupViewModel.importBackup(it) }
    }

    // Snackbar untuk backup
    LaunchedEffect(backupState.successMessage, backupState.errorMessage) {
        backupState.successMessage?.let {
            snackbarState.showSnackbar(it, duration = SnackbarDuration.Long)
            backupViewModel.clearMessages()
        }
        backupState.errorMessage?.let {
            snackbarState.showSnackbar(it, duration = SnackbarDuration.Long)
            backupViewModel.clearMessages()
        }
    }

    // Snackbar untuk sync
    LaunchedEffect(authState.syncState) {
        when (val s = authState.syncState) {
            is SyncState.Done -> {
                snackbarState.showSnackbar(
                    "Selesai — ${s.stats.transactions} transaksi, ${s.stats.wallets} dompet"
                )
                authViewModel.clearSyncState()
            }
            is SyncState.Error -> {
                snackbarState.showSnackbar("Error: ${s.message}")
                authViewModel.clearSyncState()
            }
            else -> {}
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Data & Backup", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Kembali")
                    }
                },
                actions = {
                    if (isLoggedIn) {
                        IconButton(
                            onClick  = { authViewModel.syncDownload() },
                            enabled  = !isSyncing
                        ) {
                            Icon(Icons.Default.Refresh, "Refresh dari cloud")
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // ── Section: Akun Cloud ───────────────────────────────────────────
            SectionHeader("Akun & Sinkronisasi Cloud")

            if (isLoggedIn) {
                // Info akun
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Row(
                        modifier              = Modifier.padding(16.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.AccountCircle, null,
                            modifier = Modifier.size(40.dp),
                            tint     = MaterialTheme.colorScheme.primary
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                authState.currentUser?.email ?: "",
                                style      = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                "Akun aktif",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(0.7f)
                            )
                        }
                    }
                }

                // Loading indicator sync
                if (isSyncing) {
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

                // Upload & Download cloud
                DataActionCard(
                    icon        = Icons.Default.CloudUpload,
                    title       = "Upload ke Cloud",
                    subtitle    = "Simpan semua data lokal ke Firebase",
                    buttonLabel = "Upload Sekarang",
                    buttonIcon  = Icons.Default.CloudUpload,
                    enabled     = !isSyncing && !backupState.isLoading,
                    onClick     = { authViewModel.syncUpload() }
                )
                DataActionCard(
                    icon        = Icons.Default.CloudDownload,
                    title       = "Download dari Cloud",
                    subtitle    = "Pulihkan semua data dari Firebase ke perangkat ini",
                    buttonLabel = "Download Sekarang",
                    buttonIcon  = Icons.Default.CloudDownload,
                    enabled     = !isSyncing && !backupState.isLoading,
                    onClick     = { authViewModel.syncDownload() }
                )

                // Logout
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
                        Icon(
                            Icons.Default.CloudOff, null,
                            modifier = Modifier.size(48.dp),
                            tint     = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text("Belum terhubung ke cloud", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Login untuk menyinkronkan data ke Firebase",
                            style     = MaterialTheme.typography.bodySmall,
                            color     = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(onClick = onNavigateToAuth) {
                            Text("Login Sekarang")
                        }
                    }
                }
            }

            HorizontalDivider()

            // ── Section: Backup Lokal ─────────────────────────────────────────
            SectionHeader("Backup Lokal (File JSON)")

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Row(
                    modifier              = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment     = Alignment.Top
                ) {
                    Icon(
                        Icons.Default.Info, null,
                        tint     = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        "Data disimpan ke file JSON di perangkat. Import tidak menghapus data yang sudah ada.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                }
            }

            DataActionCard(
                icon        = Icons.Default.Upload,
                title       = "Export ke File",
                subtitle    = "Simpan semua data ke file JSON lokal",
                buttonLabel = "Pilih Lokasi Simpan",
                buttonIcon  = Icons.Default.FolderOpen,
                enabled     = !backupState.isLoading && !isSyncing,
                onClick     = { exportLauncher.launch(backupState.fileName) }
            )

            DataActionCard(
                icon        = Icons.Default.Download,
                title       = "Import dari File",
                subtitle    = "Pulihkan data dari file backup JSON",
                buttonLabel = "Pilih File Backup",
                buttonIcon  = Icons.Default.FileOpen,
                enabled     = !backupState.isLoading && !isSyncing,
                onClick     = { importLauncher.launch(arrayOf("application/json", "*/*")) }
            )

            if (backupState.isLoading) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier              = Modifier.padding(16.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        Text("Sedang memproses...", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title   = { Text("Keluar?") },
            text    = { Text("Data lokal tetap tersimpan. Kamu bisa login kembali kapan saja.") },
            confirmButton = {
                TextButton(onClick = {
                    authViewModel.logout()
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
private fun SectionHeader(title: String) {
    Text(
        title,
        style      = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color      = MaterialTheme.colorScheme.primary
    )
}

@Composable
private fun DataActionCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    buttonLabel: String,
    buttonIcon: androidx.compose.ui.graphics.vector.ImageVector,
    enabled: Boolean,
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
                enabled  = enabled,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(buttonIcon, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(buttonLabel)
            }
        }
    }
}