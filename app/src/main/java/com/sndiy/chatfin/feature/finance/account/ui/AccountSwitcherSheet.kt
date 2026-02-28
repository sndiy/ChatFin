// app/src/main/java/com/sndiy/chatfin/feature/finance/account/ui/AccountSwitcherSheet.kt

package com.sndiy.chatfin.feature.finance.account.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Add
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
fun AccountSwitcherSheet(
    onDismiss: () -> Unit,
    onNavigateToAllAccounts: () -> Unit,
    onNavigateToAddAccount: () -> Unit,
    viewModel: AccountViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState       = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        dragHandle       = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = ChatFinSpacing.lg)
        ) {

            // ── Header ────────────────────────────────────────────────────────
            Text(
                text     = "Pilih Akun",
                style    = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(
                    horizontal = ChatFinSpacing.md,
                    vertical   = ChatFinSpacing.sm
                )
            )

            HorizontalDivider()

            Spacer(modifier = Modifier.height(ChatFinSpacing.sm))

            // ── Daftar Akun ───────────────────────────────────────────────────
            LazyColumn {
                items(
                    items = uiState.accounts,
                    key   = { it.id }
                ) { account ->
                    AccountSwitcherItem(
                        account   = account,
                        isActive  = account.id == uiState.activeAccount?.id,
                        onClick   = {
                            viewModel.switchAccount(account.id)
                            onDismiss()
                        }
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = ChatFinSpacing.sm))

            // ── Lihat Semua Akun ──────────────────────────────────────────────
            ListItem(
                headlineContent = {
                    Text(
                        text  = "Lihat Semua Akun",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                },
                leadingContent  = {
                    Icon(
                        imageVector        = Icons.Default.AccountBalance,
                        contentDescription = null,
                        tint               = MaterialTheme.colorScheme.primary
                    )
                },
                modifier = Modifier.clickable {
                    onDismiss()
                    onNavigateToAllAccounts()
                }
            )

            // ── Tambah Akun Baru ──────────────────────────────────────────────
            ListItem(
                headlineContent = {
                    Text(
                        text  = "Tambah Akun Baru",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                },
                leadingContent  = {
                    Icon(
                        imageVector        = Icons.Outlined.Add,
                        contentDescription = null,
                        tint               = MaterialTheme.colorScheme.primary
                    )
                },
                modifier = Modifier.clickable {
                    onDismiss()
                    onNavigateToAddAccount()
                }
            )
        }
    }
}

// ── Item satu akun di dalam switcher ─────────────────────────────────────────
@Composable
private fun AccountSwitcherItem(
    account: FinanceAccountEntity,
    isActive: Boolean,
    onClick: () -> Unit
) {
    val accentColor = runCatching {
        Color(android.graphics.Color.parseColor(account.colorHex))
    }.getOrElse { MaterialTheme.colorScheme.primary }

    ListItem(
        headlineContent = {
            Text(
                text       = account.name,
                style      = MaterialTheme.typography.bodyLarge,
                fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal
            )
        },
        supportingContent = account.description?.let {
            { Text(text = it, style = MaterialTheme.typography.bodySmall) }
        },
        leadingContent = {
            // Lingkaran warna sebagai avatar akun
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(accentColor),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text  = account.name.take(1).uppercase(),
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White
                )
            }
        },
        trailingContent = {
            if (isActive) {
                Icon(
                    imageVector        = Icons.Default.CheckCircle,
                    contentDescription = "Akun aktif",
                    tint               = MaterialTheme.colorScheme.primary
                )
            }
        },
        colors = ListItemDefaults.colors(
            containerColor = if (isActive)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            else
                MaterialTheme.colorScheme.surface
        ),
        modifier = Modifier.clickable(onClick = onClick)
    )
}