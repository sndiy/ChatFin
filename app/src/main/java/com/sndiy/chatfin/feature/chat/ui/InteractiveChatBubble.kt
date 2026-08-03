package com.sndiy.chatfin.feature.chat.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.EaseOutBack
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Reply
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.sndiy.chatfin.R
import com.sndiy.chatfin.core.ui.theme.MaiPurple

/**
 * Reusable wrapper composable untuk bubble chat / kartu transaksi yang menambahkan:
 * 1. Gestur Long-Press + Haptic Feedback (Getar Halus).
 * 2. Menu Aksi (Context Menu) beranimasi Scale + Fade (mirip WhatsApp/Telegram).
 * 3. Mode Inline Edit in-place tanpa berpindah halaman.
 * 4. Pilihan Aksi: Salin teks, Edit (khusus user), Balas, dan Hapus.
 */
@Composable
fun InteractiveChatBubble(
    messageId: String,
    text: String,
    isUser: Boolean,
    modifier: Modifier = Modifier,
    isEditable: Boolean = isUser,
    onCopyText: (String) -> Unit = {},
    onEditMessage: (String, String) -> Unit = { _, _ -> },
    onDeleteMessage: (String) -> Unit = {},
    onReplyMessage: (String, String) -> Unit = { _, _ -> },
    content: @Composable () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    var showMenu by remember { mutableStateOf(false) }
    var isEditing by remember { mutableStateOf(false) }
    var editText by remember(text) { mutableStateOf(text) }

    Box(modifier = modifier) {
        if (isEditing) {
            // ── Inline Edit Mode ──────────────────────────────────────────────
            InlineEditBubble(
                isUser = isUser,
                editText = editText,
                onValueChange = { editText = it },
                onCancel = {
                    editText = text
                    isEditing = false
                },
                onSave = {
                    if (editText.isNotBlank() && editText != text) {
                        onEditMessage(messageId, editText)
                    }
                    isEditing = false
                }
            )
        } else {
            // ── Normal Bubble Display with Long-Press Handler ─────────────────
            Box(
                modifier = Modifier.pointerInput(messageId, text) {
                    detectTapGestures(
                        onLongPress = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            showMenu = true
                        }
                    )
                }
            ) {
                content()
            }
        }

        // ── Animated Context Menu (Scale + Fade) ──────────────────────────────
        if (showMenu) {
            val density = androidx.compose.ui.platform.LocalDensity.current
            val offsetX = with(density) { (if (isUser) (-8).dp else 8.dp).roundToPx() }
            val offsetY = with(density) { 4.dp.roundToPx() }

            Popup(
                alignment = if (isUser) Alignment.TopEnd else Alignment.TopStart,
                offset = androidx.compose.ui.unit.IntOffset(offsetX, offsetY),
                onDismissRequest = { showMenu = false },
                properties = PopupProperties(focusable = true)
            ) {
                AnimatedVisibility(
                    visible = showMenu,
                    enter = fadeIn(animationSpec = tween(180)) + scaleIn(
                        animationSpec = tween(180, easing = EaseOutBack),
                        initialScale = 0.8f
                    ),
                    exit = fadeOut(animationSpec = tween(120)) + scaleOut(
                        animationSpec = tween(120),
                        targetScale = 0.8f
                    )
                ) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 8.dp,
                        shadowElevation = 8.dp,
                        modifier = Modifier
                            .width(180.dp)
                            .shadow(12.dp, RoundedCornerShape(16.dp))
                    ) {
                        Column(modifier = Modifier.padding(vertical = 6.dp)) {
                            // Salin Teks
                            ContextMenuItem(
                                icon = Icons.Outlined.ContentCopy,
                                label = stringResource(R.string.chat_action_copy),
                                onClick = {
                                    showMenu = false
                                    onCopyText(text)
                                }
                            )

                            // Edit (Hanya muncul jika isEditable = true)
                            if (isEditable) {
                                ContextMenuItem(
                                    icon = Icons.Outlined.Edit,
                                    label = stringResource(R.string.chat_action_edit),
                                    onClick = {
                                        showMenu = false
                                        isEditing = true
                                    }
                                )
                            }

                            // Balas
                            ContextMenuItem(
                                icon = Icons.Outlined.Reply,
                                label = stringResource(R.string.chat_action_reply),
                                onClick = {
                                    showMenu = false
                                    onReplyMessage(messageId, text)
                                }
                            )

                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 4.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            )

                            // Hapus
                            ContextMenuItem(
                                icon = Icons.Outlined.Delete,
                                label = stringResource(R.string.chat_action_delete),
                                tint = MaterialTheme.colorScheme.error,
                                onClick = {
                                    showMenu = false
                                    onDeleteMessage(messageId)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── Item Menu Aksi Popup ──────────────────────────────────────────────────────
@Composable
private fun ContextMenuItem(
    icon: ImageVector,
    label: String,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = tint
        )
    }
}

// ── Inline Edit Bubble Component ──────────────────────────────────────────────
@Composable
private fun InlineEditBubble(
    isUser: Boolean,
    editText: String,
    onValueChange: (String) -> Unit,
    onCancel: () -> Unit,
    onSave: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            tonalElevation = 4.dp,
            modifier = Modifier.widthIn(max = 320.dp)
        ) {
            Column(modifier = Modifier.padding(8.dp)) {
                OutlinedTextField(
                    value = editText,
                    onValueChange = onValueChange,
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = MaterialTheme.typography.bodyMedium,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { onSave() }),
                    maxLines = 4,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaiPurple,
                        cursorColor = MaiPurple
                    )
                )

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Tombol Batal
                    IconButton(
                        onClick = onCancel,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.chat_edit_cancel),
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    // Tombol Simpan
                    IconButton(
                        onClick = onSave,
                        enabled = editText.isNotBlank(),
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (editText.isNotBlank()) MaiPurple
                                else MaiPurple.copy(alpha = 0.3f)
                            )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = stringResource(R.string.chat_edit_save),
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}
