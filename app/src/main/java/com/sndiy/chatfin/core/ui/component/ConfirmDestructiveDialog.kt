// app/src/main/java/com/sndiy/chatfin/core/ui/component/ConfirmDestructiveDialog.kt

package com.sndiy.chatfin.core.ui.component

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Dialog konfirmasi untuk aksi destruktif (hapus dsb), diekstrak dari pola yang
 * sudah dipakai berulang di codebase ini — AlertDialog + icon Warning bertinta
 * error + tombol confirm berwarna error + TextButton "Batal" — supaya dialog
 * baru tidak ditulis dari nol tiap kali. Lihat TransactionListScreen (dialog
 * hapus transaksi) dan ChatComponents.ClearChatDialog untuk pola aslinya.
 */
@Composable
fun ConfirmDestructiveDialog(
    title: String,
    body: String,
    confirmLabel: String = "Hapus",
    dismissLabel: String = "Batal",
    icon: ImageVector? = Icons.Default.Warning,
    useErrorContainer: Boolean = false,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = icon?.let { { Icon(it, null, tint = MaterialTheme.colorScheme.error) } },
        title = { Text(title) },
        text = { Text(body, style = MaterialTheme.typography.bodyMedium) },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors  = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) { Text(confirmLabel, color = Color.White) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(dismissLabel) }
        },
        containerColor = if (useErrorContainer)
            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.95f)
        else MaterialTheme.colorScheme.surface
    )
}
