package com.example.ui.components

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BrokenImage
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.model.CountryCode
import com.example.data.model.CountryCodes
import com.example.data.remote.MediaBlobService
import com.example.ui.theme.LocalSeptisChatColors
import com.google.firebase.Timestamp
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SeptisBrandAvatar(
    name: String,
    photoBlobId: String? = null,
    mediaService: MediaBlobService? = null,
    size: Dp = 48.dp,
    isOnline: Boolean = false,
    isGroup: Boolean = false,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val chatColors = LocalSeptisChatColors.current
    var cachedFile by remember(photoBlobId) { mutableStateOf<File?>(null) }

    LaunchedEffect(photoBlobId) {
        if (!photoBlobId.isNullOrBlank() && mediaService != null) {
            cachedFile = mediaService.getCachedFile(context, photoBlobId, "avatar.jpg")
                ?: mediaService.downloadToCache(context, photoBlobId, "avatar.jpg").getOrNull()
        } else {
            cachedFile = null
        }
    }

    val initials = remember(name) {
        name.trim().split(" ").filter { it.isNotBlank() }.take(2)
            .joinToString("") { it.first().uppercaseChar().toString() }
            .ifBlank { "S" }
    }

    Box(modifier = modifier.size(size)) {
        if (cachedFile != null) {
            AsyncImage(
                model = cachedFile,
                contentDescription = "$name avatar",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), CircleShape)
            )
        } else {
            val bg = if (isGroup) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.primaryContainer
            }
            val fg = if (isGroup) {
                MaterialTheme.colorScheme.onSecondaryContainer
            } else {
                MaterialTheme.colorScheme.onPrimaryContainer
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .background(bg),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = initials,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = fg
                )
            }
        }

        if (isOnline) {
            Box(
                modifier = Modifier
                    .size(13.dp)
                    .align(Alignment.BottomEnd)
                    .clip(CircleShape)
                    .background(chatColors.onlineGreen)
                    .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape)
            )
        }
    }
}

@Composable
fun BlobImagePreview(
    blobId: String,
    mediaService: MediaBlobService,
    contentDescription: String,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop
) {
    val context = LocalContext.current
    var file by remember(blobId) { mutableStateOf<File?>(null) }
    var failed by remember(blobId) { mutableStateOf(false) }

    LaunchedEffect(blobId) {
        failed = false
        file = mediaService.getCachedFile(context, blobId, "img_$blobId.jpg")
        if (file == null) {
            mediaService.downloadToCache(context, blobId, "img_$blobId.jpg")
                .onSuccess { file = it }
                .onFailure { failed = true }
        }
    }

    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        when {
            file != null -> {
                AsyncImage(
                    model = file,
                    contentDescription = contentDescription,
                    contentScale = contentScale,
                    modifier = Modifier.fillMaxSize()
                )
            }
            failed -> {
                Icon(
                    Icons.Rounded.BrokenImage,
                    contentDescription = "Media unavailable",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            else -> {
                CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.5.dp)
            }
        }
    }
}

@Composable
fun SeptisBadge(
    text: String,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        color = containerColor,
        contentColor = contentColor,
        shape = RoundedCornerShape(999.dp),
        modifier = modifier
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

@Composable
fun CountryCodePickerDialog(
    onSelect: (CountryCode) -> Unit,
    onDismiss: () -> Unit
) {
    var search by remember { mutableStateOf("") }
    val filtered = remember(search) {
        if (search.isBlank()) CountryCodes.all
        else CountryCodes.all.filter {
            it.name.contains(search, ignoreCase = true) ||
                it.dialCode.contains(search, ignoreCase = true) ||
                it.iso.contains(search, ignoreCase = true)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Country") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    placeholder = { Text("Search country or code") },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("country_search_input")
                )
                Spacer(Modifier.height(10.dp))
                LazyColumn(modifier = Modifier.heightIn(max = 340.dp)) {
                    items(filtered, key = { "${it.iso}_${it.dialCode}" }) { country ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(country) }
                                .padding(vertical = 10.dp, horizontal = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(country.flag, style = MaterialTheme.typography.titleMedium)
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    country.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Text(
                                country.dialCode,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

object ShareInviteHelper {
    fun shareSeptisInvite(context: Context, phoneE164: String? = null) {
        val target = if (!phoneE164.isNullOrBlank()) " ($phoneE164)" else ""
        val message = "Hey$target! Join me on SEPTIS for fast messaging, voice notes, statuses, and voice/video calls."
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Join me on SEPTIS")
            putExtra(Intent.EXTRA_TEXT, message)
        }
        context.startActivity(Intent.createChooser(intent, "Invite to SEPTIS"))
    }
}

fun formatMessageTime(ts: Timestamp?): String {
    val date = ts?.toDate() ?: return ""
    return SimpleDateFormat("h:mm a", Locale.getDefault()).format(date)
}

fun formatRelativeDate(ts: Timestamp?): String {
    val date = ts?.toDate() ?: return ""
    val now = Date()
    val diffMs = now.time - date.time
    val dayMs = 24 * 60 * 60 * 1000L
    return when {
        diffMs < 60_000L -> "Just now"
        diffMs < dayMs -> SimpleDateFormat("h:mm a", Locale.getDefault()).format(date)
        diffMs < 7 * dayMs -> SimpleDateFormat("EEE", Locale.getDefault()).format(date)
        else -> SimpleDateFormat("dd/MM/yy", Locale.getDefault()).format(date)
    }
}

fun formatLastSeen(isOnline: Boolean, lastSeen: Timestamp?, privacy: String): String {
    if (privacy == "nobody") return ""
    if (isOnline) return "Online"
    val date = lastSeen?.toDate() ?: return "Last seen recently"
    return "Last seen ${SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(date)}"
}

fun formatDurationSeconds(seconds: Long): String {
    val mins = seconds / 60
    val secs = seconds % 60
    return String.format(Locale.getDefault(), "%02d:%02d", mins, secs)
}

fun formatDurationMillis(ms: Long): String {
    val totalSec = (ms / 1000L).coerceAtLeast(0L)
    return formatDurationSeconds(totalSec)
}
