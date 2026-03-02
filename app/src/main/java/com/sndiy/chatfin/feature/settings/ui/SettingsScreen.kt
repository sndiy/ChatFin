// app/src/main/java/com/sndiy/chatfin/feature/settings/ui/SettingsScreen.kt

package com.sndiy.chatfin.feature.settings.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.sndiy.chatfin.core.ui.navigation.Screen

@Composable
fun SettingsScreen(
    navController: NavController,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        // ── Header ─────────────────────────────────────────────────────────────
        Text(
            text     = "Setelan",
            style    = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
        )

        // ── AI & Karakter ──────────────────────────────────────────────────────
        SettingsSection(title = "AI & Karakter") {
            SettingsItem(
                icon    = Icons.Default.SmartToy,
                title   = "Kelola Karakter",
                subtitle = "Buat & pilih karakter AI kamu",
                onClick = { navController.navigate(Screen.CharacterList.route) }
            )
            SettingsItem(
                icon    = Icons.Default.Key,
                title   = "API Key Gemini",
                subtitle = if (uiState.apiKeySet) "✅ API Key sudah diset" else "⚠️ Belum diset — chat tidak bisa jalan",
                subtitleColor = if (uiState.apiKeySet)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.error,
                onClick = { viewModel.showApiKeyDialog() }
            )
        }

        // ── Akun Keuangan ─────────────────────────────────────────────────────
        SettingsSection(title = "Akun Keuangan") {
            SettingsItem(
                icon    = Icons.Default.AccountBalance,
                title   = "Kelola Akun",
                subtitle = "Tambah, edit, atau hapus akun",
                onClick = { navController.navigate(Screen.AccountList.route) }
            )
            SettingsItem(
                icon    = Icons.Default.AccountBalanceWallet,
                title   = "Dompet & Rekening",
                subtitle = "Atur dompet di akun aktif",
                onClick = { navController.navigate(Screen.WalletList.route) }
            )
            SettingsItem(
                icon    = Icons.Default.Category,
                title   = "Kategori",
                subtitle = "Kelola kategori transaksi",
                onClick = { navController.navigate(Screen.CategoryList.route) }
            )
        }

        // ── Tampilan ───────────────────────────────────────────────────────────
        SettingsSection(title = "Tampilan") {
            SettingsItem(
                icon    = Icons.Default.Palette,
                title   = "Tema",
                subtitle = "Atur warna dan tampilan app",
                onClick = { navController.navigate(Screen.SettingsTheme.route) }
            )
        }

        // ── Lainnya ───────────────────────────────────────────────────────────
        SettingsSection(title = "Lainnya") {
            SettingsItem(
                icon    = Icons.Default.Backup,
                title   = "Backup & Restore",
                subtitle = "Simpan data ke cloud",
                onClick = { navController.navigate(Screen.SettingsBackup.route) }
            )
            SettingsItem(
                icon    = Icons.Default.Info,
                title   = "Tentang ChatFin",
                subtitle = "Versi 1.0.0",
                onClick = { navController.navigate(Screen.SettingsAbout.route) }
            )
        }

        Spacer(modifier = Modifier.height(32.dp))
    }

    // ── Dialog input API Key ────────────────────────────────────────────────
    if (uiState.showApiKeyDialog) {
        ApiKeyDialog(
            currentKey = uiState.apiKeyInput,
            onKeyChange = viewModel::onApiKeyChange,
            onDismiss   = viewModel::hideApiKeyDialog,
            onSave      = viewModel::saveApiKey
        )
    }
}

// ── Dialog API Key ─────────────────────────────────────────────────────────────
@Composable
private fun ApiKeyDialog(
    currentKey: String,
    onKeyChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit
) {
    var showKey by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("API Key Gemini") },
        text  = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text  = "Dapatkan API Key gratis di aistudio.google.com",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value         = currentKey,
                    onValueChange = onKeyChange,
                    label         = { Text("Masukkan API Key") },
                    placeholder   = { Text("AIza...") },
                    singleLine    = true,
                    visualTransformation = if (showKey)
                        VisualTransformation.None
                    else
                        PasswordVisualTransformation(),
                    trailingIcon  = {
                        IconButton(onClick = { showKey = !showKey }) {
                            Icon(
                                if (showKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (showKey) "Sembunyikan" else "Tampilkan"
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick  = onSave,
                enabled  = currentKey.isNotBlank()
            ) {
                Text("Simpan")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Batal") }
        }
    )
}

// ── Section header ────────────────────────────────────────────────────────────
@Composable
private fun SettingsSection(
    title: String,
    content: @Composable () -> Unit
) {
    Column {
        Text(
            text     = title,
            style    = MaterialTheme.typography.labelLarge,
            color    = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        content()
        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
    }
}

// ── Satu item settings ─────────────────────────────────────────────────────────
@Composable
private fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    subtitleColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurfaceVariant,
    onClick: () -> Unit
) {
    ListItem(
        headlineContent   = { Text(title, style = MaterialTheme.typography.bodyLarge) },
        supportingContent = {
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = subtitleColor)
        },
        leadingContent    = {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        },
        trailingContent   = {
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
        },
        modifier = Modifier.clickable(onClick = onClick)
    )
}