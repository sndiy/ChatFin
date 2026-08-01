package com.sndiy.chatfin.feature.settings.persona

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sndiy.chatfin.R
import com.sndiy.chatfin.core.persona.PersonaId
import com.sndiy.chatfin.core.persona.PersonaPreset
import com.sndiy.chatfin.core.persona.PersonaPresets
import com.sndiy.chatfin.core.ui.animation.StaggeredEntrance
import com.sndiy.chatfin.core.ui.animation.pressScale
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonaScreen(
    onNavigateBack: () -> Unit,
    viewModel: PersonaViewModel = hiltViewModel()
) {
    val activePersonaId by viewModel.activePersonaId.collectAsStateWithLifecycle()
    val savedCustomText by viewModel.customPersonaText.collectAsStateWithLifecycle()
    val snackbarState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val savedMessage = stringResource(R.string.persona_custom_saved)

    // Draft lokal supaya user bisa mengetik tanpa menulis DataStore per huruf —
    // disinkron ulang dari nilai tersimpan setiap kali layar ini pertama kali
    // menampilkan teks tersimpan (key = savedCustomText, bukan Unit), supaya
    // tidak menimpa draft yang sedang diketik user kalau composable recompose
    // karena alasan lain.
    var customDraft by remember(savedCustomText) { mutableStateOf(savedCustomText) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.persona_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
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
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                stringResource(R.string.persona_intro),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))
            PersonaPresets.all.forEachIndexed { idx, preset ->
                StaggeredEntrance(index = idx) {
                    PersonaCard(
                        preset   = preset,
                        selected = preset.id == activePersonaId,
                        onClick  = { viewModel.selectPersona(preset.id) }
                    )
                }

                if (preset.id == PersonaId.CUSTOM && activePersonaId == PersonaId.CUSTOM) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = customDraft,
                            onValueChange = { customDraft = it },
                            label = { Text(stringResource(R.string.persona_custom_label)) },
                            placeholder = { Text(stringResource(R.string.persona_custom_placeholder)) },
                            minLines = 4,
                            maxLines = 8,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            stringResource(R.string.persona_custom_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(
                            onClick = {
                                viewModel.saveCustomText(customDraft)
                                scope.launch { snackbarState.showSnackbar(savedMessage) }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.persona_custom_save))
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun PersonaCard(
    preset: PersonaPreset,
    selected: Boolean,
    onClick: () -> Unit
) {
    val borderColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
    val bgColor     = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
    val interactionSource = remember { MutableInteractionSource() }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .pressScale(interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication         = LocalIndication.current,
                onClick            = onClick
            )
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = borderColor,
                shape = MaterialTheme.shapes.medium
            ),
        colors = CardDefaults.cardColors(containerColor = bgColor)
    ) {
        Row(
            modifier              = Modifier.padding(16.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    preset.displayName,
                    style      = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color      = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    preset.tagline,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (selected) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
