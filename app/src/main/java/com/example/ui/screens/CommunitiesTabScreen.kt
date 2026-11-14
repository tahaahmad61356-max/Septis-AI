package com.example.ui.screens

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
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.GroupAdd
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.Conversation
import com.example.data.model.SavedContact
import com.example.data.remote.MediaBlobService
import com.example.ui.components.SeptisBrandAvatar
import com.example.ui.viewmodel.HomeViewModel

@Composable
fun CommunitiesTabScreen(
    homeViewModel: HomeViewModel,
    mediaService: MediaBlobService,
    onOpenConversation: (String) -> Unit
) {
    val communities by homeViewModel.communities.collectAsStateWithLifecycle()
    val conversations by homeViewModel.conversations.collectAsStateWithLifecycle()
    val savedContacts by homeViewModel.savedContacts.collectAsStateWithLifecycle()
    val searchQuery by homeViewModel.searchQuery.collectAsStateWithLifecycle()
    val currentUid = homeViewModel.currentUid

    val groupChats = remember(conversations, searchQuery) {
        conversations.filter {
            it.type == "group" &&
                (searchQuery.isBlank() || it.title.contains(searchQuery, ignoreCase = true))
        }
    }

    var showCreateCommunity by remember { mutableStateOf(false) }
    var showCreateGroup by remember { mutableStateOf(false) }
    var manageGroup by remember { mutableStateOf<Conversation?>(null) }

    if (showCreateCommunity) {
        CreateCommunityDialog(
            groups = groupChats,
            onCreate = { name, desc, emoji, selectedGroupIds ->
                val memberUids = groupChats.filter { it.id in selectedGroupIds }.flatMap { it.participantUids }
                homeViewModel.createCommunity(name, desc, emoji, memberUids, selectedGroupIds)
                showCreateCommunity = false
            },
            onDismiss = { showCreateCommunity = false }
        )
    }

    if (showCreateGroup) {
        CreateGroupDialog(
            savedContacts = savedContacts,
            onCreate = { title, desc, members ->
                homeViewModel.createGroup(title, desc, members) { conv ->
                    showCreateGroup = false
                    onOpenConversation(conv.id)
                }
            },
            onDismiss = { showCreateGroup = false }
        )
    }

    if (manageGroup != null) {
        GroupInfoDialog(
            group = manageGroup!!,
            savedContacts = savedContacts,
            currentUid = currentUid,
            homeViewModel = homeViewModel,
            onDismiss = { manageGroup = null },
            onGroupRemoved = { manageGroup = null }
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = { showCreateCommunity = true },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("create_community_button")
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("New Community")
                }
                OutlinedButton(
                    onClick = { showCreateGroup = true },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("create_group_button")
                ) {
                    Icon(Icons.Rounded.GroupAdd, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("New Group")
                }
            }
        }

        item {
            Text(
                text = "Communities (${communities.size})",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }

        if (communities.isEmpty()) {
            item {
                Text(
                    "Bring related SEPTIS groups together under a Community.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            items(communities, key = { it.id }) { comm ->
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(comm.iconEmoji, fontSize = 30.sp)
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(comm.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            if (comm.description.isNotBlank()) {
                                Text(comm.description, style = MaterialTheme.typography.bodySmall, maxLines = 2)
                            }
                            Text(
                                "${comm.groupIds.size} groups • ${comm.memberUids.size} members",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        if (comm.ownerUid == currentUid) {
                            IconButton(onClick = { homeViewModel.deleteCommunity(comm.id) }) {
                                Icon(Icons.Rounded.Delete, contentDescription = "Delete community")
                            }
                        }
                    }
                }
            }
        }

        item {
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Your Groups (${groupChats.size})",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }

        items(groupChats, key = { it.id }) { grp ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenConversation(grp.id) }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SeptisBrandAvatar(
                    name = grp.title,
                    photoBlobId = grp.photoBlobId,
                    mediaService = mediaService,
                    size = 48.dp,
                    isGroup = true
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(grp.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        "${grp.participantUids.size} members • ${grp.lastMessageText}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                TextButton(onClick = { manageGroup = grp }) { Text("Manage") }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.14f))
        }
    }
}

@Composable
fun CreateGroupDialog(
    savedContacts: List<SavedContact>,
    onCreate: (title: String, desc: String, memberUids: List<String>) -> Unit,
    onDismiss: () -> Unit
) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    val selectedUids = remember { mutableStateListOf<String>() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create SEPTIS Group") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Group name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("group_title_input")
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description (optional)") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                Text("Select participants (${selectedUids.size})", style = MaterialTheme.typography.labelMedium)
                LazyColumn(modifier = Modifier.heightIn(max = 200.dp)) {
                    items(savedContacts, key = { it.contactUid }) { contact ->
                        val checked = contact.contactUid in selectedUids
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (checked) selectedUids.remove(contact.contactUid)
                                    else selectedUids.add(contact.contactUid)
                                }
                                .padding(vertical = 4.dp)
                        ) {
                            Checkbox(
                                checked = checked,
                                onCheckedChange = {
                                    if (it) selectedUids.add(contact.contactUid)
                                    else selectedUids.remove(contact.contactUid)
                                }
                            )
                            Text(contact.localDeviceName ?: contact.displayName)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onCreate(title, description, selectedUids.toList()) },
                enabled = title.isNotBlank(),
                modifier = Modifier.testTag("confirm_create_group_button")
            ) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun CreateCommunityDialog(
    groups: List<Conversation>,
    onCreate: (name: String, desc: String, emoji: String, groupIds: List<String>) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    var emoji by remember { mutableStateOf("\uD83C\uDF10") }
    val selectedGroups = remember { mutableStateListOf<String>() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Community") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Community name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = desc,
                    onValueChange = { desc = it },
                    label = { Text("Description") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("\uD83C\uDF10", "\uD83D\uDEE1\uFE0F", "\uD83D\uDE80", "\uD83C\uDFEB", "\uD83E\uDD1D").forEach { e ->
                        TextButton(onClick = { emoji = e }) { Text(e, fontSize = 22.sp) }
                    }
                }
                if (groups.isNotEmpty()) {
                    Text("Link groups", style = MaterialTheme.typography.labelMedium)
                    LazyColumn(modifier = Modifier.heightIn(max = 150.dp)) {
                        items(groups, key = { it.id }) { grp ->
                            val checked = grp.id in selectedGroups
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth().clickable {
                                    if (checked) selectedGroups.remove(grp.id) else selectedGroups.add(grp.id)
                                }
                            ) {
                                Checkbox(
                                    checked = checked,
                                    onCheckedChange = {
                                        if (it) selectedGroups.add(grp.id) else selectedGroups.remove(grp.id)
                                    }
                                )
                                Text(grp.title)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onCreate(name, desc, emoji, selectedGroups.toList()) },
                enabled = name.isNotBlank()
            ) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun GroupInfoDialog(
    group: Conversation,
    savedContacts: List<SavedContact>,
    currentUid: String,
    homeViewModel: HomeViewModel,
    onDismiss: () -> Unit,
    onGroupRemoved: () -> Unit
) {
    val isAdmin = currentUid in group.adminUids
    var title by remember(group.id) { mutableStateOf(group.title) }
    var desc by remember(group.id) { mutableStateOf(group.description.orEmpty()) }
    var adminsOnlySend by remember(group.id) { mutableStateOf(group.onlyAdminsCanSend) }
    var adminsOnlyEdit by remember(group.id) { mutableStateOf(group.onlyAdminsCanEditInfo) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Group Info & Permissions") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Group title") },
                    enabled = isAdmin || !group.onlyAdminsCanEditInfo,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = desc,
                    onValueChange = { desc = it },
                    label = { Text("Description") },
                    enabled = isAdmin || !group.onlyAdminsCanEditInfo,
                    modifier = Modifier.fillMaxWidth()
                )
                if (isAdmin) {
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text("Only admins send", style = MaterialTheme.typography.bodyMedium)
                        Switch(checked = adminsOnlySend, onCheckedChange = { adminsOnlySend = it })
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text("Only admins edit info", style = MaterialTheme.typography.bodyMedium)
                        Switch(checked = adminsOnlyEdit, onCheckedChange = { adminsOnlyEdit = it })
                    }
                    Button(
                        onClick = {
                            homeViewModel.updateGroupSettings(group.id, title, desc, adminsOnlySend, adminsOnlyEdit)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Save Group Settings") }
                }

                Spacer(Modifier.height(10.dp))
                Text("Members (${group.participantUids.size})", style = MaterialTheme.typography.labelLarge)
                LazyColumn(modifier = Modifier.heightIn(max = 170.dp)) {
                    items(group.participantUids, key = { it }) { uid ->
                        val label = if (uid == currentUid) "You"
                        else savedContacts.firstOrNull { it.contactUid == uid }?.displayName ?: uid.take(10)
                        val memberIsAdmin = uid in group.adminUids
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("$label${if (memberIsAdmin) " (Admin)" else ""}", style = MaterialTheme.typography.bodySmall)
                            if (isAdmin && uid != currentUid) {
                                Row {
                                    TextButton(onClick = { homeViewModel.toggleGroupAdmin(group.id, uid, !memberIsAdmin) }) {
                                        Text(if (memberIsAdmin) "Demote" else "Make Admin")
                                    }
                                    TextButton(onClick = { homeViewModel.removeGroupMember(group.id, uid) }) {
                                        Text("Remove", color = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                    }
                }

                if (isAdmin) {
                    val addable = savedContacts.filter { it.contactUid !in group.participantUids }
                    if (addable.isNotEmpty()) {
                        Text("Add Contact:", style = MaterialTheme.typography.labelMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            addable.take(3).forEach { c ->
                                OutlinedButton(onClick = { homeViewModel.addGroupMember(group.id, c.contactUid) }) {
                                    Text("+ ${c.displayName.take(10)}")
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { homeViewModel.leaveGroup(group.id, onGroupRemoved) },
                        modifier = Modifier.weight(1f)
                    ) { Text("Leave") }
                    if (isAdmin) {
                        OutlinedButton(
                            onClick = { homeViewModel.deleteGroup(group.id, onGroupRemoved) },
                            modifier = Modifier.weight(1f)
                        ) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}
