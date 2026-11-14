package com.example.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Done
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Forward
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ChatMessage
import com.example.data.remote.MediaBlobService
import com.example.media.PlaybackState
import com.example.media.VoiceRecorderManager
import com.example.ui.theme.LocalSeptisChatColors
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatMessageBubble(
    message: ChatMessage,
    isMine: Boolean,
    isGroupChat: Boolean,
    totalParticipants: Int,
    mediaService: MediaBlobService,
    voiceManager: VoiceRecorderManager,
    playbackState: PlaybackState,
    onReply: () -> Unit,
    onForward: () -> Unit,
    onCopy: () -> Unit,
    onStar: () -> Unit,
    onEdit: () -> Unit,
    onDeleteForMe: () -> Unit,
    onDeleteForEveryone: () -> Unit,
    onPreviewImageBlob: (String) -> Unit,
    onFileFeedback: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val chatColors = LocalSeptisChatColors.current
    var showMenu by remember { mutableStateOf(false) }
    var isDownloadingFile by remember { mutableStateOf(false) }

    val bubbleColor = when {
        message.type == "sticker" && !message.deletedForEveryone -> Color.Transparent
        isMine -> chatColors.outgoingBubble
        else -> chatColors.incomingBubble
    }
    val textColor = if (isMine) chatColors.onOutgoingBubble else chatColors.onIncomingBubble

    val bubbleShape = RoundedCornerShape(
        topStart = if (isMine) 16.dp else 4.dp,
        topEnd = if (isMine) 4.dp else 16.dp,
        bottomStart = 16.dp,
        bottomEnd = 16.dp
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 3.dp),
        horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start
    ) {
        Box {
            Surface(
                color = bubbleColor,
                shape = bubbleShape,
                tonalElevation = if (bubbleColor == Color.Transparent) 0.dp else 1.dp,
                modifier = Modifier
                    .widthIn(min = 84.dp, max = 300.dp)
                    .combinedClickable(
                        onClick = {},
                        onLongClick = { showMenu = true }
                    )
                    .testTag("message_bubble_${message.id}")
            ) {
                Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)) {
                    if (isGroupChat && !isMine) {
                        Text(
                            text = message.senderName,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(2.dp))
                    }

                    if (!message.forwardedFrom.isNullOrBlank() && !message.deletedForEveryone) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Rounded.Forward,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = "Forwarded",
                                style = MaterialTheme.typography.labelSmall,
                                fontStyle = FontStyle.Italic,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.height(3.dp))
                    }

                    if (!message.replyToId.isNullOrBlank() && !message.deletedForEveryone) {
                        Surface(
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)) {
                                Text(
                                    text = message.replyToSender.orEmpty(),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = message.replyToText.orEmpty(),
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    color = textColor.copy(alpha = 0.8f)
                                )
                            }
                        }
                        Spacer(Modifier.height(5.dp))
                    }

                    if (message.deletedForEveryone) {
                        Text(
                            text = "This message was deleted",
                            style = MaterialTheme.typography.bodyMedium,
                            fontStyle = FontStyle.Italic,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        when (message.type) {
                            "sticker" -> {
                                Text(
                                    text = message.text,
                                    fontSize = 52.sp
                                )
                            }
                            "image" -> {
                                val blobId = message.mediaBlobId
                                if (!blobId.isNullOrBlank()) {
                                    BlobImagePreview(
                                        blobId = blobId,
                                        mediaService = mediaService,
                                        contentDescription = "Photo attachment",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(190.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .clickable { onPreviewImageBlob(blobId) }
                                    )
                                }
                                if (message.text.isNotBlank()) {
                                    Spacer(Modifier.height(6.dp))
                                    Text(message.text, color = textColor, style = MaterialTheme.typography.bodyLarge)
                                }
                            }
                            "voice" -> {
                                VoiceMessageContent(
                                    message = message,
                                    textColor = textColor,
                                    playbackState = playbackState,
                                    isDownloading = isDownloadingFile,
                                    onTogglePlay = {
                                        val blobId = message.mediaBlobId ?: return@VoiceMessageContent
                                        scope.launch {
                                            isDownloadingFile = true
                                            val fileRes = mediaService.downloadToCache(
                                                context,
                                                blobId,
                                                message.mediaFileName ?: "voice_$blobId.m4a"
                                            )
                                            isDownloadingFile = false
                                            fileRes.onSuccess { file ->
                                                voiceManager.togglePlay(message.id, file, scope)
                                                    .onFailure { onFileFeedback(it.localizedMessage ?: "Playback failed") }
                                            }.onFailure {
                                                onFileFeedback(it.localizedMessage ?: "Audio download failed")
                                            }
                                        }
                                    },
                                    onCycleSpeed = { voiceManager.cycleSpeed() }
                                )
                            }
                            "video", "document", "audio" -> {
                                val icon = when (message.type) {
                                    "video" -> Icons.Rounded.Videocam
                                    "audio" -> Icons.Rounded.Mic
                                    else -> Icons.Rounded.Description
                                }
                                Surface(
                                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            val blobId = message.mediaBlobId ?: return@clickable
                                            scope.launch {
                                                isDownloadingFile = true
                                                val res = mediaService.downloadToCache(
                                                    context,
                                                    blobId,
                                                    message.mediaFileName ?: "file"
                                                )
                                                isDownloadingFile = false
                                                res.onSuccess { file ->
                                                    mediaService.openCachedFileWithIntent(
                                                        context,
                                                        file,
                                                        message.mediaMimeType ?: "*/*"
                                                    ).onFailure {
                                                        onFileFeedback("No app installed to open ${message.mediaFileName ?: "file"}")
                                                    }
                                                }.onFailure {
                                                    onFileFeedback(it.localizedMessage ?: "Download failed")
                                                }
                                            }
                                        }
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                            Spacer(Modifier.width(10.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = message.mediaFileName ?: message.type.uppercase(),
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = textColor,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                val kb = ((message.mediaSizeBytes ?: 0L) / 1024L).coerceAtLeast(1L)
                                                Text(
                                                    text = "${kb} KB • Tap to open",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            Icon(Icons.Rounded.Download, contentDescription = "Download")
                                        }
                                        if (isDownloadingFile) {
                                            Spacer(Modifier.height(6.dp))
                                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                                        }
                                    }
                                }
                                if (message.text.isNotBlank()) {
                                    Spacer(Modifier.height(6.dp))
                                    Text(message.text, color = textColor, style = MaterialTheme.typography.bodyLarge)
                                }
                            }
                            else -> {
                                Text(
                                    text = message.text,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = textColor
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(3.dp))
                    Row(
                        modifier = Modifier.align(Alignment.End),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (message.edited && !message.deletedForEveryone) {
                            Text(
                                text = "edited",
                                style = MaterialTheme.typography.labelSmall,
                                fontStyle = FontStyle.Italic,
                                color = textColor.copy(alpha = 0.65f)
                            )
                        }
                        Text(
                            text = formatMessageTime(message.createdAt),
                            style = MaterialTheme.typography.labelSmall,
                            color = textColor.copy(alpha = 0.68f)
                        )
                        if (isMine && !message.deletedForEveryone) {
                            val peerRead = message.readByUids.count { it != message.senderUid } > 0 &&
                                (totalParticipants <= 2 || message.readByUids.size >= totalParticipants)
                            val peerDelivered = message.deliveredToUids.any { it != message.senderUid }
                            Icon(
                                imageVector = if (peerDelivered || peerRead) Icons.Rounded.DoneAll else Icons.Rounded.Done,
                                contentDescription = when {
                                    peerRead -> "Read"
                                    peerDelivered -> "Delivered"
                                    else -> "Sent"
                                },
                                tint = if (peerRead) chatColors.readReceiptBlue else textColor.copy(alpha = 0.68f),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false }
            ) {
                if (!message.deletedForEveryone) {
                    DropdownMenuItem(
                        text = { Text("Reply") },
                        onClick = { showMenu = false; onReply() }
                    )
                    DropdownMenuItem(
                        text = { Text("Forward") },
                        onClick = { showMenu = false; onForward() }
                    )
                    if (message.text.isNotBlank()) {
                        DropdownMenuItem(
                            text = { Text("Copy") },
                            onClick = { showMenu = false; onCopy() }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Star") },
                        onClick = { showMenu = false; onStar() }
                    )
                    if (isMine && message.type == "text") {
                        DropdownMenuItem(
                            text = { Text("Edit") },
                            onClick = { showMenu = false; onEdit() }
                        )
                    }
                    if (isMine) {
                        DropdownMenuItem(
                            text = { Text("Delete for everyone") },
                            onClick = { showMenu = false; onDeleteForEveryone() }
                        )
                    }
                }
                DropdownMenuItem(
                    text = { Text("Delete for me") },
                    onClick = { showMenu = false; onDeleteForMe() }
                )
            }
        }
    }
}

@Composable
private fun VoiceMessageContent(
    message: ChatMessage,
    textColor: Color,
    playbackState: PlaybackState,
    isDownloading: Boolean,
    onTogglePlay: () -> Unit,
    onCycleSpeed: () -> Unit
) {
    val isThisActive = playbackState.activeId == message.id
    val isPlaying = isThisActive && playbackState.isPlaying
    val duration = if (isThisActive && playbackState.durationMs > 0) {
        playbackState.durationMs
    } else {
        message.mediaDurationMs ?: 1000L
    }
    val progressFraction = if (isThisActive && duration > 0) {
        (playbackState.positionMs.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    val bars = message.waveform?.takeIf { it.isNotEmpty() }
        ?: listOf(22, 48, 70, 35, 85, 60, 42, 78, 92, 55, 38, 65, 80, 45, 30, 72, 50, 28)

    Column(modifier = Modifier.widthIn(min = 210.dp, max = 260.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = onTogglePlay,
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = if (isPlaying) "Pause voice note" else "Play voice note",
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }
            Spacer(Modifier.width(8.dp))
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(28.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                bars.forEachIndexed { index, amp ->
                    val fraction = index.toFloat() / bars.size.coerceAtLeast(1)
                    val active = isThisActive && fraction <= progressFraction
                    Box(
                        modifier = Modifier
                            .width(3.dp)
                            .height(((amp / 100f) * 24f).coerceIn(5f, 24f).dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(
                                if (active) MaterialTheme.colorScheme.primary
                                else textColor.copy(alpha = 0.32f)
                            )
                    )
                }
            }
            Spacer(Modifier.width(6.dp))
            Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.65f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.clickable { onCycleSpeed() }
            ) {
                Text(
                    text = "${playbackState.speed}x",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                )
            }
        }
        if (isDownloading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
        }
        Text(
            text = if (isThisActive) {
                "${formatDurationMillis(playbackState.positionMs)} / ${formatDurationMillis(duration)}"
            } else {
                formatDurationMillis(duration)
            },
            style = MaterialTheme.typography.labelSmall,
            color = textColor.copy(alpha = 0.72f),
            modifier = Modifier.padding(start = 52.dp)
        )
    }
}
