package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.StatusUpdate
import com.example.data.remote.MediaBlobService
import com.example.ui.components.BlobImagePreview
import com.example.ui.components.SeptisBrandAvatar
import com.example.ui.components.formatRelativeDate
import com.example.ui.viewmodel.HomeViewModel

@Composable
fun UpdatesTabScreen(
    homeViewModel: HomeViewModel,
    mediaService: MediaBlobService
) {
    val context = LocalContext.current
    val account by homeViewModel.account.collectAsStateWithLifecycle()
    val statuses by homeViewModel.statuses.collectAsStateWithLifecycle()
    val announcements by homeViewModel.announcements.collectAsStateWithLifecycle()
    val searchQuery by homeViewModel.searchQuery.collectAsStateWithLifecycle()
    val currentUid = homeViewModel.currentUid

    var showTextCreator by remember { mutableStateOf(false) }
    var viewingStatus by remember { mutableStateOf<StatusUpdate?>(null) }
    var privacyForMedia by remember { mutableStateOf("everyone") }
    var mediaCaption by remember { mutableStateOf("") }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            homeViewModel.postMediaStatus(context, uri, isVideo = false, caption = mediaCaption, privacy = privacyForMedia)
        }
    }
    val videoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            homeViewModel.postMediaStatus(context, uri, isVideo = true, caption = mediaCaption, privacy = privacyForMedia)
        }
    }

    val filteredStatuses = remember(statuses, searchQuery) {
        if (searchQuery.isBlank()) statuses
        else statuses.filter {
            it.authorName.contains(searchQuery, ignoreCase = true) ||
                it.text.contains(searchQuery, ignoreCase = true)
        }
    }

    if (showTextCreator) {
        var statusText by remember { mutableStateOf("") }
        var selectedPrivacy by remember { mutableStateOf("everyone") }
        var bgHex by remember { mutableStateOf("#0B6E3D") }
        val colors = listOf("#0B6E3D", "#1565C0", "#6A1B9A", "#AD1457", "#37474F")

        AlertDialog(
            onDismissRequest = { showTextCreator = false },
            title = { Text("New Status Update") },
            text = {
                Column {
                    OutlinedTextField(
                        value = statusText,
                        onValueChange = { statusText = it },
                        label = { Text("Share an update (24h)") },
                        minLines = 3,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("status_text_input")
                    )
                    Spacer(Modifier.height(10.dp))
                    Text("Background color", style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        colors.forEach { hex ->
                            Box(
                                modifier = Modifier
                                    .size(30.dp)
                                    .clip(CircleShape)
                                    .background(Color(android.graphics.Color.parseColor(hex)))
                                    .border(
                                        width = if (bgHex == hex) 3.dp else 1.dp,
                                        color = if (bgHex == hex) MaterialTheme.colorScheme.onSurface else Color.Gray,
                                        shape = CircleShape
                                    )
                                    .clickable { bgHex = hex }
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Text("Privacy", style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("everyone" to "Everyone", "contacts" to "Contacts", "only_me" to "Only Me").forEach { (key, label) ->
                            FilterChip(
                                selected = selectedPrivacy == key,
                                onClick = { selectedPrivacy = key },
                                label = { Text(label) }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        homeViewModel.postTextStatus(statusText, bgHex, selectedPrivacy)
                        showTextCreator = false
                    },
                    enabled = statusText.isNotBlank(),
                    modifier = Modifier.testTag("post_status_button")
                ) { Text("Share") }
            },
            dismissButton = {
                TextButton(onClick = { showTextCreator = false }) { Text("Cancel") }
            }
        )
    }

    if (viewingStatus != null) {
        val status = statuses.firstOrNull { it.id == viewingStatus!!.id } ?: viewingStatus!!
        LaunchedEffect(status.id) {
            if (status.authorUid != currentUid) {
                homeViewModel.markStatusViewed(status.id)
            }
        }
        AlertDialog(
            onDismissRequest = { viewingStatus = null },
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(status.authorName, fontWeight = FontWeight.Bold)
                        Text(
                            formatRelativeDate(status.createdAt),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (status.authorUid == currentUid) {
                        IconButton(onClick = {
                            homeViewModel.deleteStatus(status.id)
                            viewingStatus = null
                        }) {
                            Icon(Icons.Rounded.Delete, contentDescription = "Delete status")
                        }
                    }
                }
            },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (status.type == "image" && !status.mediaBlobId.isNullOrBlank()) {
                        BlobImagePreview(
                            blobId = status.mediaBlobId,
                            mediaService = mediaService,
                            contentDescription = "Status photo",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(260.dp)
                                .clip(RoundedCornerShape(12.dp))
                        )
                        if (status.text.isNotBlank()) {
                            Spacer(Modifier.height(8.dp))
                            Text(status.text, style = MaterialTheme.typography.bodyLarge)
                        }
                    } else if (status.type == "video" && !status.mediaBlobId.isNullOrBlank()) {
                        Text("Video status (${status.text.ifBlank { "Shared clip" }})")
                    } else {
                        val parsedColor = try {
                            Color(android.graphics.Color.parseColor(status.backgroundColorHex ?: "#0B6E3D"))
                        } catch (_: Exception) {
                            Color(0xFF0B6E3D)
                        }
                        Surface(
                            color = parsedColor,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = status.text,
                                    color = Color.White,
                                    style = MaterialTheme.typography.titleLarge,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    if (status.authorUid == currentUid) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Visibility, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "${status.viewerUids.size} views • ${status.reactions.size} reactions",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            listOf("\u2764\uFE0F", "\uD83D\uDD25", "\uD83D\uDE02", "\uD83D\uDE2E", "\uD83D\uDC4F").forEach { emoji ->
                                Text(
                                    text = emoji,
                                    fontSize = 24.sp,
                                    modifier = Modifier
                                        .clickable { homeViewModel.reactToStatus(status.id, emoji) }
                                        .padding(4.dp)
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { viewingStatus = null }) { Text("Close") }
            }
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SeptisBrandAvatar(
                            name = account?.displayName ?: "Me",
                            photoBlobId = account?.photoBlobId,
                            mediaService = mediaService,
                            size = 50.dp
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("My Status", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(
                                "Share updates that disappear after 24 hours",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { showTextCreator = true },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("create_text_status_button")
                        ) {
                            Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Text")
                        }
                        OutlinedButton(
                            onClick = {
                                imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("create_photo_status_button")
                        ) {
                            Icon(Icons.Rounded.Image, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Photo")
                        }
                        OutlinedButton(
                            onClick = {
                                videoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Rounded.Videocam, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Video")
                        }
                    }
                }
            }
        }

        item {
            Text(
                text = "Recent Updates (${filteredStatuses.size})",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }

        if (filteredStatuses.isEmpty()) {
            item {
                Text(
                    "No active status updates from the last 24 hours.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            items(filteredStatuses, key = { it.id }) { status ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewingStatus = status }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .border(2.5.dp, MaterialTheme.colorScheme.primary, CircleShape)
                            .padding(3.dp)
                    ) {
                        SeptisBrandAvatar(
                            name = status.authorName,
                            photoBlobId = status.authorPhotoBlobId,
                            mediaService = mediaService,
                            size = 48.dp
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(status.authorName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            text = "${formatRelativeDate(status.createdAt)} • ${status.type.replaceFirstChar { it.uppercase() }}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (status.reactions.isNotEmpty()) {
                        Text("${status.reactions.values.take(3).joinToString("")}")
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.14f))
            }
        }

        if (announcements.isNotEmpty()) {
            item {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Official SEPTIS Announcements",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            items(announcements, key = { it.id }) { ann ->
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f))
                ) {
                    Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
                        Icon(Icons.Rounded.Campaign, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(ann.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(4.dp))
                            Text(ann.body, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
    }
}
