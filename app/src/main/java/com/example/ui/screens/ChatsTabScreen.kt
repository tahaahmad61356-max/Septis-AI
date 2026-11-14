package com.example.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.ExitToApp
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Unarchive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.ChatPreference
import com.example.data.model.Conversation
import com.example.data.model.SavedContact
import com.example.data.remote.MediaBlobService
import com.example.ui.components.SeptisBrandAvatar
import com.example.ui.components.formatRelativeDate
import com.example.ui.viewmodel.HomeViewModel

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatsTabScreen(
    homeViewModel: HomeViewModel,
    mediaService: MediaBlobService,
    onOpenConversation: (String) -> Unit,
    onOpenNewChat: () -> Unit
) {
    val conversations by homeViewModel.conversations.collectAsStateWithLifecycle()
    val chatPrefs by homeViewModel.chatPrefs.collectAsStateWithLifecycle()
    val savedContacts by homeViewModel.savedContacts.collectAsStateWithLifecycle()
    val searchQuery by homeViewModel.searchQuery.collectAsStateWithLifecycle()
    val showArchived by homeViewModel.showArchivedChats.collectAsStateWithLifecycle()
    val currentUid = homeViewModel.currentUid

    var menuConv by remember { mutableStateOf<Conversation?>(null) }

    val contactMap = remember(savedContacts) { savedContacts.associateBy { it.contactUid } }

    fun displayInfoFor(conv: Conversation): Pair<String, String?> {
        if (conv.type == "group") {
            return (conv.title.ifBlank { "SEPTIS Group" }) to conv.photoBlobId
        }
        val peerUid = conv.participantUids.firstOrNull { it != currentUid }
        val peerContact: SavedContact? = peerUid?.let { contactMap[it] }
        val name = peerContact?.localDeviceName?.takeIf { it.isNotBlank() }
            ?: peerContact?.displayName?.takeIf { it.isNotBlank() }
            ?: conv.title.ifBlank { "SEPTIS Chat" }
        val photo = peerContact?.photoBlobId ?: conv.photoBlobId
        return name to photo
    }

    val archivedCount = remember(conversations, chatPrefs) {
        conversations.count { chatPrefs[it.id]?.archived == true }
    }

    val visibleList = remember(conversations, chatPrefs, searchQuery, showArchived, contactMap) {
        conversations
            .filter { conv ->
                val isArchived = chatPrefs[conv.id]?.archived == true
                if (showArchived) isArchived else !isArchived
            }
            .filter { conv ->
                if (searchQuery.isBlank()) true
                else {
                    val (name, _) = displayInfoFor(conv)
                    name.contains(searchQuery, ignoreCase = true) ||
                        conv.lastMessageText.contains(searchQuery, ignoreCase = true)
                }
            }
            .sortedWith(
                compareByDescending<Conversation> { chatPrefs[it.id]?.pinned == true }
                    .thenByDescending { it.lastMessageAt?.toDate()?.time ?: it.updatedAt?.toDate()?.time ?: 0L }
            )
    }

    if (menuConv != null) {
        val conv = menuConv!!
        val pref = chatPrefs[conv.id] ?: ChatPreference(conversationId = conv.id)
        val (title, _) = displayInfoFor(conv)
        AlertDialog(
            onDismissRequest = { menuConv = null },
            title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            text = {
                Column {
                    ListItem(
                        headlineContent = { Text(if (pref.pinned) "Unpin chat" else "Pin chat") },
                        leadingContent = { Icon(Icons.Rounded.PushPin, contentDescription = null) },
                        modifier = Modifier.clickable {
                            homeViewModel.togglePinChat(conv.id, pref.pinned)
                            menuConv = null
                        }
                    )
                    ListItem(
                        headlineContent = { Text(if (pref.archived) "Unarchive chat" else "Archive chat") },
                        leadingContent = {
                            Icon(
                                if (pref.archived) Icons.Rounded.Unarchive else Icons.Rounded.Archive,
                                contentDescription = null
                            )
                        },
                        modifier = Modifier.clickable {
                            homeViewModel.toggleArchiveChat(conv.id, pref.archived)
                            menuConv = null
                        }
                    )
                    ListItem(
                        headlineContent = { Text(if (pref.muted) "Unmute notifications" else "Mute notifications") },
                        leadingContent = { Icon(Icons.Rounded.NotificationsOff, contentDescription = null) },
                        modifier = Modifier.clickable {
                            homeViewModel.toggleMuteChat(conv.id, pref.muted)
                            menuConv = null
                        }
                    )
                    if (conv.type == "group") {
                        ListItem(
                            headlineContent = { Text("Leave group") },
                            leadingContent = { Icon(Icons.Rounded.ExitToApp, contentDescription = null) },
                            modifier = Modifier.clickable {
                                homeViewModel.leaveGroup(conv.id) {}
                                menuConv = null
                            }
                        )
                        if (currentUid in conv.adminUids) {
                            ListItem(
                                headlineContent = { Text("Delete group", color = MaterialTheme.colorScheme.error) },
                                leadingContent = {
                                    Icon(
                                        Icons.Rounded.Delete,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                },
                                modifier = Modifier.clickable {
                                    homeViewModel.deleteGroup(conv.id) {}
                                    menuConv = null
                                }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { menuConv = null }) { Text("Close") }
            }
        )
    }

    if (conversations.isEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Rounded.ChatBubbleOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(64.dp)
            )
            Spacer(Modifier.height(14.dp))
            Text(
                "No conversations yet",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Find any SEPTIS user by their phone number or sync your contacts to begin messaging.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(18.dp))
            Button(
                onClick = onOpenNewChat,
                modifier = Modifier.testTag("empty_start_chat_button")
            ) {
                Text("Find Contact by Phone")
            }
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 90.dp)
    ) {
        if (archivedCount > 0 || showArchived) {
            item(key = "archived_banner") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { homeViewModel.toggleShowArchived() }
                        .padding(horizontal = 18.dp, vertical = 14.dp)
                        .testTag("toggle_archived_chats"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (showArchived) Icons.Rounded.Unarchive else Icons.Rounded.Archive,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(16.dp))
                        Text(
                            text = if (showArchived) "Showing Archived Chats (Tap to return)" else "Archived",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    if (!showArchived) {
                        Text(
                            text = archivedCount.toString(),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
            }
        }

        items(visibleList, key = { it.id }) { conv ->
            val pref = chatPrefs[conv.id]
            val (name, photoBlobId) = displayInfoFor(conv)
            val isPeerTyping = conv.typingUids.any { it != currentUid }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        onClick = { onOpenConversation(conv.id) },
                        onLongClick = { menuConv = conv }
                    )
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .testTag("chat_row_${conv.id}"),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SeptisBrandAvatar(
                    name = name,
                    photoBlobId = photoBlobId,
                    mediaService = mediaService,
                    size = 52.dp,
                    isGroup = conv.type == "group"
                )
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = formatRelativeDate(conv.lastMessageAt ?: conv.updatedAt),
                            style = MaterialTheme.typography.bodySmall,
                            color = if ((pref?.unreadCount ?: 0) > 0) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        val preview = when {
                            isPeerTyping -> "typing..."
                            !pref?.draftText.isNullOrBlank() -> "Draft: ${pref?.draftText}"
                            conv.lastMessageText.isNotBlank() -> conv.lastMessageText
                            else -> "Tap to start chatting"
                        }
                        Text(
                            text = preview,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (isPeerTyping) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(6.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (pref?.muted == true) {
                                Icon(
                                    Icons.Rounded.NotificationsOff,
                                    contentDescription = "Muted",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            if (pref?.pinned == true) {
                                Icon(
                                    Icons.Rounded.PushPin,
                                    contentDescription = "Pinned",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            if ((pref?.unreadCount ?: 0) > 0) {
                                Surface(
                                    color = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary,
                                    shape = CircleShape
                                ) {
                                    Text(
                                        text = pref?.unreadCount.toString(),
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
            HorizontalDivider(
                modifier = Modifier.padding(start = 82.dp),
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.16f)
            )
        }
    }
}
