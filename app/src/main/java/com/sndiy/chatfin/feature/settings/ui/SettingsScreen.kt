package com.sndiy.chatfin.feature.settings.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.sndiy.chatfin.BuildConfig
import com.sndiy.chatfin.R
import com.sndiy.chatfin.core.persona.PersonaPresets
import com.sndiy.chatfin.core.ui.animation.pressScale
import com.sndiy.chatfin.core.ui.navigation.Screen
import com.sndiy.chatfin.feature.auth.ui.AuthViewModel
import com.sndiy.chatfin.feature.settings.apikey.ApiKeyViewModel
import com.sndiy.chatfin.feature.settings.persona.PersonaViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavController,
    authViewModel: AuthViewModel = hiltViewModel(),
    apiKeyViewModel: ApiKeyViewModel = hiltViewModel(),
    personaViewModel: PersonaViewModel = hiltViewModel(),
    settingsViewModel: SettingsViewModel = hiltViewModel()
) {
    val authState by authViewModel.uiState.collectAsStateWithLifecycle()
    val apiKeyState by apiKeyViewModel.uiState.collectAsStateWithLifecycle()
    val activePersonaId by personaViewModel.activePersonaId.collectAsStateWithLifecycle()
    val bgTaskNotifEnabled by settingsViewModel.backgroundTaskNotifEnabled.collectAsStateWithLifecycle()
    val dailyReminderNotifEnabled by settingsViewModel.dailyReminderNotifEnabled.collectAsStateWithLifecycle()

    val isLoggedIn = authState.currentUser != null
    val userEmail  = authState.currentUser?.email ?: ""
    val context = LocalContext.current

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { _ -> }
    )

    fun handleNotifToggle(enabled: Boolean, onToggle: (Boolean) -> Unit) {
        if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        onToggle(enabled)
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) apiKeyViewModel.refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Setelan", fontWeight = FontWeight.Bold) }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
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

            SettingsSection(title = "Asisten AI") {
                SettingsItem(
                    icon = Icons.Default.VpnKey,
                    title = stringResource(R.string.apikey_menu_title),
                    subtitle = if (apiKeyState.hasStoredKey) {
                        stringResource(R.string.apikey_menu_subtitle_active)
                    } else {
                        stringResource(R.string.apikey_menu_subtitle_inactive)
                    },
                    onClick = { navController.navigate(Screen.SettingsApiKey.route) }
                )
                SettingsItem(
                    icon = Icons.Default.Face,
                    title = stringResource(R.string.persona_menu_title),
                    subtitle = stringResource(
                        R.string.persona_menu_subtitle,
                        PersonaPresets.byId(activePersonaId).displayName
                    ),
                    onClick = { navController.navigate(Screen.SettingsPersona.route) }
                )
            }

            SettingsSection(title = stringResource(R.string.settings_section_notification)) {
                SettingsSwitchItem(
                    icon = Icons.Default.Sync,
                    title = stringResource(R.string.settings_notif_bg_task_title),
                    subtitle = stringResource(R.string.settings_notif_bg_task_subtitle),
                    checked = bgTaskNotifEnabled,
                    onCheckedChange = { handleNotifToggle(it, settingsViewModel::toggleBackgroundTaskNotif) }
                )
                SettingsSwitchItem(
                    icon = Icons.Default.NotificationsActive,
                    title = stringResource(R.string.settings_notif_daily_reminder_title),
                    subtitle = stringResource(R.string.settings_notif_daily_reminder_subtitle),
                    checked = dailyReminderNotifEnabled,
                    onCheckedChange = { handleNotifToggle(it, settingsViewModel::toggleDailyReminderNotif) }
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
                    title = "Sinkronisasi & Backup",
                    subtitle = if (isLoggedIn) "Login sebagai $userEmail" else "Login untuk sinkronisasi cloud",
                    onClick = { navController.navigate(Screen.SettingsBackup.route) }
                )
                SettingsItem(
                    icon = Icons.Default.Info,
                    title = "Tentang ChatFin",
                    subtitle = "Versi ${BuildConfig.VERSION_NAME}",
                    onClick = { navController.navigate(Screen.SettingsAbout.route) }
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
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
    val interactionSource = remember { MutableInteractionSource() }
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
        modifier = Modifier
            .pressScale(interactionSource, pressedScale = 0.985f)
            .clickable(
                interactionSource = interactionSource,
                indication         = LocalIndication.current,
                onClick            = onClick
            )
    )
}

@Composable
private fun SettingsSwitchItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    ListItem(
        headlineContent = { Text(title, style = MaterialTheme.typography.bodyLarge) },
        supportingContent = {
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        },
        leadingContent = {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        },
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange
            )
        },
        modifier = Modifier
            .pressScale(interactionSource, pressedScale = 0.985f)
            .clickable(
                interactionSource = interactionSource,
                indication         = LocalIndication.current,
                onClick            = { onCheckedChange(!checked) }
            )
    )
}
