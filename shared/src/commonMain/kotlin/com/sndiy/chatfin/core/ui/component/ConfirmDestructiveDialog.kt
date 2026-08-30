package com.sndiy.chatfin.core.ui.component

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

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
