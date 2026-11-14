package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.EmojiEmotions
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.StickyNote2
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ChatMessage

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WhatsAppComposer(
    text: String,
    replyTo: ChatMessage?,
    editingMessage: ChatMessage?,
    uploadProgress: Float?,
    isRecording: Boolean,
    recordingDurationMs: Long,
    liveWaveform: List<Int>,
    onTextChanged: (String) -> Unit,
    onSendText: () -> Unit,
    onSendSticker: (String) -> Unit,
    onCancelReplyOrEdit: () -> Unit,
    onPickPhoto: () -> Unit,
    onPickVideo: () -> Unit,
    onPickDocument: () -> Unit,
    onPickAudio: () -> Unit,
    onOpenCamera: () -> Unit,
    onStartRecording: () -> Unit,
    onCancelRecording: () -> Unit,
    onFinishAndSendRecording: () -> Unit
) {
    var showEmojiStickerTray by remember { mutableStateOf(false) }
    var trayTab by remember { mutableIntStateOf(0) } // 0 = Emoji, 1 = Stickers
    var showAttachmentSheet by remember { mutableStateOf(false) }

    val quickEmojis = remember {
        listOf(
            "\uD83D\uDE00", "\uD83D\uDE02", "\uD83E\uDD70", "\uD83D\uDE0E", "\uD83E\uDD14",
            "\uD83D\uDE4F", "\u2764\uFE0F", "\uD83D\uDD25", "\uD83D\uDC4D", "\uD83D\uDC4F",
            "\uD83C\uDF89", "\uD83D\uDE2D", "\uD83E\uDD1D", "\u2705", "\uD83D\uDCAF",
            "\uD83D\uDE0A", "\uD83E\uDD29", "\uD83D\uDE2E", "\uD83D\uDE4C", "\uD83D\uDCAA"
        )
    }
    val quickStickers = remember {
        listOf(
            "\uD83D\uDC4B\uD83C\uDFFB", "\uD83E\uDEF6", "\uD83D\uDE80", "\uD83C\uDF1F",
            "\uD83E\uDD81", "\uD83C\uDFC6", "\u2615", "\uD83D\uDC9A",
            "\uD83D\uDC8E", "\uD83C\uDFAF", "\uD83E\uDDD1\u200D\uD83D\uDCBB", "\uD83E\uDD42"
        )
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        if (uploadProgress != null) {
            LinearProgressIndicator(
                progress = { uploadProgress },
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (replyTo != null || editingMessage != null) {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (editingMessage != null) "Editing message" else "Replying to ${replyTo?.senderName}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = editingMessage?.text ?: replyTo?.text.orEmpty().ifBlank { "[${replyTo?.type}]" },
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(onClick = onCancelReplyOrEdit, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.Rounded.Close, contentDescription = "Cancel")
                    }
                }
            }
        }

        AnimatedVisibility(visible = showAttachmentSheet) {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 4.dp,
                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp, horizontal = 12.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    AttachmentTile("Gallery", Icons.Rounded.Image) {
                        showAttachmentSheet = false; onPickPhoto()
                    }
                    AttachmentTile("Video", Icons.Rounded.Videocam) {
                        showAttachmentSheet = false; onPickVideo()
                    }
                    AttachmentTile("Document", Icons.Rounded.Description) {
                        showAttachmentSheet = false; onPickDocument()
                    }
                    AttachmentTile("Audio", Icons.Rounded.Headphones) {
                        showAttachmentSheet = false; onPickAudio()
                    }
                }
            }
        }

        if (isRecording) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 2.dp,
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Rounded.Mic,
                            contentDescription = "Recording",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = formatDurationMillis(recordingDurationMs),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.width(10.dp))
                        Row(
                            modifier = Modifier.weight(1f).height(24.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            liveWaveform.takeLast(18).forEach { amp ->
                                Box(
                                    modifier = Modifier
                                        .width(3.dp)
                                        .height(((amp / 100f) * 20f).coerceIn(4f, 20f).dp)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(MaterialTheme.colorScheme.primary)
                                )
                            }
                        }
                        IconButton(onClick = onCancelRecording, modifier = Modifier.size(44.dp)) {
                            Icon(Icons.Rounded.Delete, contentDescription = "Cancel recording")
                        }
                    }
                }
                Spacer(Modifier.width(8.dp))
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier
                        .size(50.dp)
                        .clip(CircleShape)
                        .clickable { onFinishAndSendRecording() }
                        .testTag("send_voice_button")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = "Send voice message")
                    }
                }
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 2.dp,
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        IconButton(
                            onClick = {
                                showAttachmentSheet = false
                                trayTab = 0
                                showEmojiStickerTray = !showEmojiStickerTray
                            },
                            modifier = Modifier
                                .size(42.dp)
                                .testTag("composer_emoji_button")
                        ) {
                            Icon(
                                Icons.Rounded.EmojiEmotions,
                                contentDescription = "Emoji",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .padding(vertical = 10.dp)
                        ) {
                            if (text.isEmpty()) {
                                Text(
                                    text = "Message",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            BasicTextField(
                                value = text,
                                onValueChange = onTextChanged,
                                textStyle = MaterialTheme.typography.bodyLarge.copy(
                                    color = MaterialTheme.colorScheme.onSurface
                                ),
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                minLines = 1,
                                maxLines = 5,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("chat_message_input")
                            )
                        }

                        IconButton(
                            onClick = {
                                showAttachmentSheet = false
                                trayTab = 1
                                showEmojiStickerTray = !showEmojiStickerTray
                            },
                            modifier = Modifier
                                .size(40.dp)
                                .testTag("composer_sticker_button")
                        ) {
                            Icon(
                                Icons.Rounded.StickyNote2,
                                contentDescription = "Stickers",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        IconButton(
                            onClick = {
                                showEmojiStickerTray = false
                                showAttachmentSheet = !showAttachmentSheet
                            },
                            modifier = Modifier
                                .size(40.dp)
                                .testTag("composer_attach_button")
                        ) {
                            Icon(
                                Icons.Rounded.AttachFile,
                                contentDescription = "Attach file",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (text.isBlank()) {
                            IconButton(
                                onClick = {
                                    showEmojiStickerTray = false
                                    showAttachmentSheet = false
                                    onOpenCamera()
                                },
                                modifier = Modifier
                                    .size(40.dp)
                                    .testTag("composer_camera_button")
                            ) {
                                Icon(
                                    Icons.Rounded.CameraAlt,
                                    contentDescription = "Camera",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.width(6.dp))

                if (text.isNotBlank()) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .clickable { onSendText() }
                            .testTag("chat_send_button")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = "Send")
                        }
                    }
                } else {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onTap = { onStartRecording() },
                                    onLongPress = { onStartRecording() }
                                )
                            }
                            .testTag("chat_mic_button")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.Mic, contentDescription = "Record voice message")
                        }
                    }
                }
            }
        }

        AnimatedVisibility(visible = showEmojiStickerTray) {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    TabRow(selectedTabIndex = trayTab) {
                        Tab(
                            selected = trayTab == 0,
                            onClick = { trayTab = 0 },
                            text = { Text("Emoji") }
                        )
                        Tab(
                            selected = trayTab == 1,
                            onClick = { trayTab = 1 },
                            text = { Text("Stickers") }
                        )
                    }
                    FlowRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (trayTab == 0) {
                            quickEmojis.forEach { emoji ->
                                Text(
                                    text = emoji,
                                    fontSize = 26.sp,
                                    modifier = Modifier
                                        .clickable { onTextChanged(text + emoji) }
                                        .padding(4.dp)
                                )
                            }
                        } else {
                            quickStickers.forEach { sticker ->
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier.clickable {
                                        onSendSticker(sticker)
                                        showEmojiStickerTray = false
                                    }
                                ) {
                                    Text(
                                        text = sticker,
                                        fontSize = 36.sp,
                                        modifier = Modifier.padding(10.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AttachmentTile(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = label, tint = MaterialTheme.colorScheme.onPrimaryContainer)
        }
        Spacer(Modifier.height(6.dp))
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}
