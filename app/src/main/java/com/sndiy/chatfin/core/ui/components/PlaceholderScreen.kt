// app/src/main/java/com/sndiy/chatfin/core/ui/components/PlaceholderScreen.kt

package com.sndiy.chatfin.core.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Construction
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

// Placeholder untuk screen yang belum diimplementasi
// Akan diganti satu per satu di fase berikutnya
@Composable
fun PlaceholderScreen(screenName: String) {
    Column(
        modifier              = Modifier.fillMaxSize(),
        horizontalAlignment   = Alignment.CenterHorizontally,
        verticalArrangement   = Arrangement.Center
    ) {
        Icon(
            imageVector        = Icons.Default.Construction,
            contentDescription = null,
            modifier           = Modifier.size(64.dp),
            tint               = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text  = screenName,
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            text  = "Akan hadir di fase berikutnya",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}