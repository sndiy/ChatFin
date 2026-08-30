package com.sndiy.chatfin.feature.settings.backup

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
                            onClick  = { authViewModel.syncSmart() },
                            enabled  = !isSyncing
                        ) {
                            Icon(Icons.Default.Sync, "Sinkronkan dua arah")
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
            SectionHeader("Akun Cloud")

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
                                "Sinkronisasi real-time aktif",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(0.7f)
                            )
                        }
                    }
                }

                // Loading indicator sync
                AnimatedVisibility(
                    visible = isSyncing,
                    enter   = fadeIn() + expandVertically(),
                    exit    = fadeOut() + shrinkVertically()
                ) {
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
                            "Login untuk mengaktifkan sinkronisasi real-time ke Firebase",
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

            // ── Section: Pencadangan Lokal ────────────────────────────────────
            SectionHeader("Pencadangan Lokal")

            // Card cadangkan sekarang (backup ke file JSON + update timestamp)
            val lastBackupText = if (backupState.lastBackupTimestamp > 0L) {
                val dt = java.time.LocalDateTime.ofInstant(
                    java.time.Instant.ofEpochMilli(backupState.lastBackupTimestamp),
                    java.time.ZoneId.systemDefault()
                )
                dt.format(java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm"))
            } else {
                "Belum pernah"
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Backup, null,
                            tint     = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Cadangkan ke Perangkat",
                                style      = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                "Pencadangan terakhir: $lastBackupText",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Button(
                        onClick  = { backupViewModel.runManualBackupNow() },
                        enabled  = !backupState.isLoading && !isSyncing,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Save, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("CADANGKAN SEKARANG")
                    }
                }
            }

            HorizontalDivider()

            // ── Section: Backup File JSON ─────────────────────────────────────
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

            AnimatedVisibility(
                visible = backupState.isLoading,
                enter   = fadeIn() + expandVertically(),
                exit    = fadeOut() + shrinkVertically()
            ) {
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
            title   = { Text("Keluar dari Akun?", fontWeight = FontWeight.Bold) },
            text    = { Text("Data keuangan di perangkat ini akan dibersihkan demi keamanan dan privasi. Seluruh data kamu tetap tersimpan aman di cloud dan akan otomatis sinkron kembali saat kamu login.") },
            confirmButton = {
                TextButton(onClick = {
                    authViewModel.logout()
                    showLogoutDialog = false
                    onLoggedOut()
                }) {
                    Text("Keluar & Bersihkan", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
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