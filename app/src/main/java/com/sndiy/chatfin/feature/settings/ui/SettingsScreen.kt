package com.sndiy.chatfin.feature.settings.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.sndiy.chatfin.core.ui.navigation.Screen
import com.sndiy.chatfin.feature.auth.ui.AuthViewModel

@Composable
fun SettingsScreen(
    navController: NavController,
    authViewModel: AuthViewModel = hiltViewModel()
) {
    val authState by authViewModel.uiState.collectAsStateWithLifecycle()
    val isLoggedIn = authState.currentUser != null
    val userEmail  = authState.currentUser?.email ?: ""

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = "Setelan",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
        )

        SettingsSection(title = "Akun Keuangan") {
            SettingsItem(
                icon = Icons.Default.AccountBalance,
                title = "Kelola Akun",
                subtitle = "Tambah, edit, atau hapus akun",
                onClick = { navController.navigate(Screen.AccountList.route) }
            )
            SettingsItem(
                icon = Icons.Default.AccountBalanceWallet,
                title = "Dompet & Rekening",
                subtitle = "Atur dompet di akun aktif",
                onClick = { navController.navigate(Screen.WalletList.route) }
            )
            SettingsItem(
                icon = Icons.Default.Category,
                title = "Kategori",
                subtitle = "Kelola kategori transaksi",
                onClick = { navController.navigate(Screen.CategoryList.route) }
            )
            SettingsItem(
                icon = Icons.Default.PieChart,
                title = "Budget",
                subtitle = "Atur batas pengeluaran per kategori",
                onClick = { navController.navigate(Screen.BudgetList.route) }
            )
        }

        SettingsSection(title = "Tampilan") {
            SettingsItem(
                icon = Icons.Default.Palette,
                title = "Tema",
                subtitle = "Atur warna dan tampilan app",
                onClick = { navController.navigate(Screen.SettingsTheme.route) }
            )
        }

        SettingsSection(title = "Lainnya") {
            SettingsItem(
                icon = Icons.Default.FileDownload,
                title = "Export Laporan",
                subtitle = "Download CSV atau PDF transaksi",
                onClick = { navController.navigate(Screen.Export.route) }
            )
            SettingsItem(
                icon = Icons.Default.CloudSync,
                title = "Akun & Sinkronisasi",
                subtitle = if (isLoggedIn) "Login sebagai $userEmail" else "Belum login",
                onClick = { navController.navigate(Screen.SyncSettings.route) }
            )
            SettingsItem(
                icon     = Icons.Default.SaveAlt,
                title    = "Data & Backup",
                subtitle = if (isLoggedIn) "Login sebagai $userEmail" else "Backup lokal & sinkronisasi cloud",
                onClick  = { navController.navigate(Screen.SettingsBackup.route) }
            )
            SettingsItem(
                icon = Icons.Default.Info,
                title = "Tentang ChatFin",
                subtitle = "Versi 1.6.0",
                onClick = { navController.navigate(Screen.SettingsAbout.route) }
            )
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable () -> Unit) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        content()
        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
    }
}

@Composable
private fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    subtitleColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurfaceVariant,
    onClick: () -> Unit
) {
    ListItem(
        headlineContent = { Text(title, style = MaterialTheme.typography.bodyLarge) },
        supportingContent = {
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = subtitleColor)
        },
        leadingContent = {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        },
        trailingContent = {
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
        },
        modifier = Modifier.clickable(onClick = onClick)
    )
}
