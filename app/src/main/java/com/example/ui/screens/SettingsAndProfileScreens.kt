package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.NotificationItem
import com.example.data.remote.MediaBlobService
import com.example.ui.components.SeptisBrandAvatar
import com.example.ui.components.ShareInviteHelper
import com.example.ui.components.formatRelativeDate
import com.example.ui.viewmodel.HomeViewModel
import com.example.util.ThemeMode

@Composable
fun SettingsTabScreen(
    homeViewModel: HomeViewModel,
    mediaService: MediaBlobService,
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    onThemeModeChange: (ThemeMode) -> Unit = {},
    onOpenAdminPortal: () -> Unit,
    onLogoutClick: () -> Unit
) {
    val context = LocalContext.current
    val account by homeViewModel.account.collectAsStateWithLifecycle()
    val blockedUsers by homeViewModel.blockedUsers.collectAsStateWithLifecycle()
    val starredMessages by homeViewModel.starredMessages.collectAsStateWithLifecycle()

    var showEditProfileDialog by remember { mutableStateOf(false) }
    var showPrivacyDialog by remember { mutableStateOf(false) }
    var showBlockedDialog by remember { mutableStateOf(false) }
    var showStarredDialog by remember { mutableStateOf(false) }

    val avatarPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) homeViewModel.updateProfilePhoto(context, uri)
    }

    if (showEditProfileDialog && account != null) {
        val acc = account!!
        var name by remember { mutableStateOf(acc.displayName) }
        var about by remember { mutableStateOf(acc.about) }
        AlertDialog(
            onDismissRequest = { showEditProfileDialog = false },
            title = { Text("Edit Profile") },
            text = {
                Column {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Display Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("settings_name_input")
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = about,
                        onValueChange = { about = it },
                        label = { Text("About") },
                        modifier = Modifier.fillMaxWidth().testTag("settings_about_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        homeViewModel.updateProfile(
                            displayName = name,
                            about = about,
                            privacyLastSeen = acc.privacyLastSeen,
                            privacyPhoto = acc.privacyPhoto,
                            privacyAbout = acc.privacyAbout,
                            readReceipts = acc.readReceiptsEnabled
                        )
                        showEditProfileDialog = false
                    },
                    modifier = Modifier.testTag("save_profile_button")
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showEditProfileDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showPrivacyDialog && account != null) {
        val acc = account!!
        var lastSeen by remember { mutableStateOf(acc.privacyLastSeen) }
        var photo by remember { mutableStateOf(acc.privacyPhoto) }
        var about by remember { mutableStateOf(acc.privacyAbout) }
        var readReceipts by remember { mutableStateOf(acc.readReceiptsEnabled) }

        AlertDialog(
            onDismissRequest = { showPrivacyDialog = false },
            title = { Text("Privacy Controls") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PrivacyOptionRow("Last seen & Online", lastSeen) { lastSeen = it }
                    PrivacyOptionRow("Profile photo", photo) { photo = it }
                    PrivacyOptionRow("About info", about) { about = it }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Read receipts", style = MaterialTheme.typography.bodyMedium)
                        Switch(checked = readReceipts, onCheckedChange = { readReceipts = it })
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    homeViewModel.updateProfile(
                        displayName = acc.displayName,
                        about = acc.about,
                        privacyLastSeen = lastSeen,
                        privacyPhoto = photo,
                        privacyAbout = about,
                        readReceipts = readReceipts
                    )
                    showPrivacyDialog = false
                }) { Text("Apply") }
            },
            dismissButton = {
                TextButton(onClick = { showPrivacyDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showBlockedDialog) {
        AlertDialog(
            onDismissRequest = { showBlockedDialog = false },
            title = { Text("Blocked Contacts (${blockedUsers.size})") },
            text = {
                if (blockedUsers.isEmpty()) {
                    Text("No blocked users.")
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 260.dp)) {
                        items(blockedUsers, key = { it.blockedUid }) { b ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(b.displayName, fontWeight = FontWeight.SemiBold)
                                    if (!b.phoneE164.isNullOrBlank()) {
                                        Text(b.phoneE164, style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                                TextButton(onClick = { homeViewModel.unblockUser(b.blockedUid) }) {
                                    Text("Unblock")
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showBlockedDialog = false }) { Text("Close") } }
        )
    }

    if (showStarredDialog) {
        AlertDialog(
            onDismissRequest = { showStarredDialog = false },
            title = { Text("Starred Messages (${starredMessages.size})") },
            text = {
                if (starredMessages.isEmpty()) {
                    Text("Long-press any message inside a chat to star it.")
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 280.dp)) {
                        items(starredMessages, key = { it.id }) { s ->
                            Column(modifier = Modifier.padding(vertical = 6.dp)) {
                                Text(s.senderName, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                Text(s.previewText, style = MaterialTheme.typography.bodyMedium)
                            }
                            HorizontalDivider()
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showStarredDialog = false }) { Text("Close") } }
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SeptisBrandAvatar(
                        name = account?.displayName ?: "SEPTIS",
                        photoBlobId = account?.photoBlobId,
                        mediaService = mediaService,
                        size = 64.dp,
                        isOnline = true,
                        modifier = Modifier.clickable {
                            avatarPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        }
                    )
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = account?.displayName?.ifBlank { "SEPTIS User" } ?: "Loading...",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = account?.phoneE164.orEmpty(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = account?.about.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(
                        onClick = { showEditProfileDialog = true },
                        modifier = Modifier.testTag("settings_edit_profile_button")
                    ) {
                        Icon(Icons.Rounded.Edit, contentDescription = "Edit profile")
                    }
                }
            }
        }

        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.DarkMode, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(10.dp))
                        Text("Appearance Theme", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ThemeMode.entries.forEach { mode ->
                            FilterChip(
                                selected = themeMode == mode,
                                onClick = { onThemeModeChange(mode) },
                                label = { Text(mode.name.lowercase().replaceFirstChar { it.uppercase() }) }
                            )
                        }
                    }
                }
            }
        }

        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column {
                    ListItem(
                        headlineContent = { Text("Privacy & Read Receipts") },
                        supportingContent = { Text("Last seen: ${account?.privacyLastSeen ?: "everyone"}") },
                        leadingContent = { Icon(Icons.Rounded.Lock, contentDescription = null) },
                        modifier = Modifier
                            .clickable { showPrivacyDialog = true }
                            .testTag("settings_privacy_row")
                    )
                    HorizontalDivider()
                    ListItem(
                        headlineContent = { Text("Starred Messages") },
                        supportingContent = { Text("${starredMessages.size} saved") },
                        leadingContent = { Icon(Icons.Rounded.Star, contentDescription = null) },
                        modifier = Modifier
                            .clickable { showStarredDialog = true }
                            .testTag("settings_starred_row")
                    )
                    HorizontalDivider()
                    ListItem(
                        headlineContent = { Text("Blocked Contacts") },
                        supportingContent = { Text("${blockedUsers.size} blocked") },
                        leadingContent = { Icon(Icons.Rounded.Block, contentDescription = null) },
                        modifier = Modifier
                            .clickable { showBlockedDialog = true }
                            .testTag("settings_blocked_row")
                    )
                    HorizontalDivider()
                    ListItem(
                        headlineContent = { Text("Invite Friends to SEPTIS") },
                        leadingContent = { Icon(Icons.Rounded.Share, contentDescription = null) },
                        modifier = Modifier
                            .clickable { ShareInviteHelper.shareSeptisInvite(context) }
                            .testTag("settings_invite_row")
                    )
                    HorizontalDivider()
                    ListItem(
                        headlineContent = { Text("Administrator Portal") },
                        supportingContent = { Text("Authorized admins with confidential vault code") },
                        leadingContent = { Icon(Icons.Rounded.AdminPanelSettings, contentDescription = null) },
                        modifier = Modifier
                            .clickable { onOpenAdminPortal() }
                            .testTag("settings_admin_portal_row")
                    )
                }
            }
        }

        item {
            OutlinedButton(
                onClick = onLogoutClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("settings_logout_button")
            ) {
                Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Log Out")
            }
        }
    }
}

@Composable
private fun PrivacyOptionRow(
    title: String,
    currentValue: String,
    onSelect: (String) -> Unit
) {
    Column {
        Text(title, style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("everyone" to "Everyone", "contacts" to "Contacts", "nobody" to "Nobody").forEach { (key, label) ->
                FilterChip(
                    selected = currentValue == key,
                    onClick = { onSelect(key) },
                    label = { Text(label) }
                )
            }
        }
    }
}

@Composable
fun NotificationsSheetDialog(
    notifications: List<NotificationItem>,
    onMarkRead: (String) -> Unit,
    onOpenConversation: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Notifications, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Notifications")
            }
        },
        text = {
            if (notifications.isEmpty()) {
                Text("No notifications yet.")
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                    items(notifications, key = { it.id }) { item ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onMarkRead(item.id)
                                    val refId = item.referenceId
                                    if (!refId.isNullOrBlank() && (item.type == "message" || item.type == "group")) {
                                        onOpenConversation(refId)
                                    }
                                }
                                .padding(vertical = 8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    item.title,
                                    fontWeight = if (item.isRead) FontWeight.Medium else FontWeight.Bold,
                                    color = if (item.isRead) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.primary
                                )
                                Text(formatRelativeDate(item.createdAt), style = MaterialTheme.typography.labelSmall)
                            }
                            Text(item.message, style = MaterialTheme.typography.bodySmall)
                        }
                        HorizontalDivider()
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}
