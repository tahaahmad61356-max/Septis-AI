package com.example.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.Announcement
import com.example.data.model.AppSettings
import com.example.data.model.NotificationItem
import com.example.data.model.SeptisRecord
import com.example.data.model.UserAccount
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.SeptisDashboardTopBar
import com.example.ui.components.StatusChip
import com.example.ui.components.formatTimestamp
import com.example.ui.components.statusColorFor
import com.example.ui.viewmodel.UiState
import com.example.ui.viewmodel.UserDashboardViewModel

@Composable
fun UserDashboardScreen(
    viewModel: UserDashboardViewModel,
    isOnline: Boolean,
    onLogoutClick: () -> Unit
) {
    val accountState by viewModel.accountState.collectAsStateWithLifecycle()
    val announcementsState by viewModel.announcementsState.collectAsStateWithLifecycle()
    val recordsState by viewModel.recordsState.collectAsStateWithLifecycle()
    val notificationsState by viewModel.notificationsState.collectAsStateWithLifecycle()
    val appSettings by viewModel.appSettingsState.collectAsStateWithLifecycle()
    val hasPasscode by viewModel.hasPasscode.collectAsStateWithLifecycle()
    val feedback by viewModel.feedbackMessage.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(feedback) {
        if (!feedback.isNullOrBlank()) {
            snackbarHostState.showSnackbar(feedback!!)
            viewModel.clearFeedback()
        }
    }

    val account = (accountState as? UiState.Success)?.data
    if (account != null && account.status != "active") {
        // Real-time enforcement if Admin suspended/deactivated the user during session
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize().padding(24.dp)
        ) {
            Card(shape = MaterialTheme.shapes.extraLarge) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(24.dp)
                ) {
                    Icon(
                        Icons.Rounded.WarningAmber,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "Account ${account.status.replaceFirstChar { it.uppercase() }}",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Your SEPTIS access has been restricted by an Administrator.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = onLogoutClick,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        Text("Sign Out")
                    }
                }
            }
        }
        return
    }

    var selectedTab by remember { mutableIntStateOf(0) }
    var showCreateRecordDialog by remember { mutableStateOf(false) }
    var editingRecord by remember { mutableStateOf<SeptisRecord?>(null) }
    var showPasscodeDialog by remember { mutableStateOf(false) }

    val notifications = (notificationsState as? UiState.Success)?.data.orEmpty()
    val unreadCount = notifications.count { !it.isRead }
    val canPost = (account?.canPostContent == true) && (appSettings?.allowUserPosting != false)

    val navItems = listOf(
        "Home" to Icons.Rounded.Home,
        "Hub" to Icons.Rounded.Forum,
        "Alerts" to Icons.Rounded.Notifications,
        "Profile" to Icons.Rounded.Person
    )

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 600.dp

        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                SeptisDashboardTopBar(
                    title = "SEPTIS",
                    subtitle = account?.displayName ?: "Member Dashboard",
                    isOnline = isOnline,
                    isAdminMode = false,
                    logoutTestTag = "user_logout_button",
                    onLogoutClick = onLogoutClick
                )
            },
            bottomBar = {
                if (!useRail) {
                    NavigationBar {
                        navItems.forEachIndexed { idx, (label, icon) ->
                            NavigationBarItem(
                                selected = selectedTab == idx,
                                onClick = { selectedTab = idx },
                                icon = {
                                    if (idx == 2 && unreadCount > 0) {
                                        BadgedBox(badge = { Badge { Text("$unreadCount") } }) {
                                            Icon(icon, contentDescription = label)
                                        }
                                    } else {
                                        Icon(icon, contentDescription = label)
                                    }
                                },
                                label = { Text(label) }
                            )
                        }
                    }
                }
            },
            floatingActionButton = {
                if (selectedTab == 1 && canPost) {
                    FloatingActionButton(
                        onClick = {
                            editingRecord = null
                            showCreateRecordDialog = true
                        },
                        modifier = Modifier.testTag("user_add_record_fab")
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = "Share Post")
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useRail) {
                    NavigationRail(modifier = Modifier.fillMaxHeight()) {
                        navItems.forEachIndexed { idx, (label, icon) ->
                            NavigationRailItem(
                                selected = selectedTab == idx,
                                onClick = { selectedTab = idx },
                                icon = {
                                    if (idx == 2 && unreadCount > 0) {
                                        BadgedBox(badge = { Badge { Text("$unreadCount") } }) {
                                            Icon(icon, contentDescription = label)
                                        }
                                    } else {
                                        Icon(icon, contentDescription = label)
                                    }
                                },
                                label = { Text(label) }
                            )
                        }
                    }
                }

                Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    if (appSettings?.maintenanceMode == true) {
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Notice: SEPTIS is currently in Admin maintenance mode.",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                            )
                        }
                    }

                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        when (selectedTab) {
                            0 -> UserHomeTab(
                                account = account,
                                appSettings = appSettings,
                                announcementsState = announcementsState,
                                unreadAlerts = unreadCount
                            )
                            1 -> UserHubTab(
                                currentUserId = viewModel.currentUserId,
                                canPost = canPost,
                                recordsState = recordsState,
                                onEditRecord = {
                                    editingRecord = it
                                    showCreateRecordDialog = true
                                },
                                onDeleteRecord = viewModel::deleteOwnRecord
                            )
                            2 -> UserNotificationsTab(
                                notificationsState = notificationsState,
                                onMarkRead = viewModel::markNotificationRead
                            )
                            3 -> UserProfileTab(
                                accountState = accountState,
                                globalEditsAllowed = appSettings?.allowUserProfileEdits != false,
                                hasPasscode = hasPasscode,
                                onSaveProfile = viewModel::updateMyProfile,
                                onOpenPasscodeDialog = { showPasscodeDialog = true },
                                onLogoutClick = onLogoutClick
                            )
                        }
                    }
                }
            }
        }
    }

    if (showCreateRecordDialog) {
        UserRecordDialog(
            existing = editingRecord,
            onDismiss = {
                showCreateRecordDialog = false
                editingRecord = null
            },
            onSave = { id, title, content, category ->
                if (id == null) {
                    viewModel.createUserRecord(
                        title = title,
                        content = content,
                        category = category,
                        authorName = account?.displayName ?: "Member"
                    )
                } else {
                    viewModel.updateOwnRecord(id, title, content, category)
                }
            }
        )
    }

    if (showPasscodeDialog) {
        ChangePasscodeDialog(
            onDismiss = { showPasscodeDialog = false },
            onSave = viewModel::updateMyPasscode
        )
    }
}

@Composable
private fun UserHomeTab(
    account: UserAccount?,
    appSettings: AppSettings?,
    announcementsState: UiState<List<Announcement>>,
    unreadAlerts: Int
) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.padding(16.dp)
    ) {
        item {
            Card(
                shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
                    Text(
                        text = "Welcome back, ${account?.displayName ?: "Member"}",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Chat • Connect • Share  |  Unread Alerts: $unreadAlerts",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatusChip(
                            label = if (account?.canEditProfile == true && appSettings?.allowUserProfileEdits != false) {
                                "Profile Editable"
                            } else {
                                "Profile Locked"
                            },
                            color = statusColorFor(if (account?.canEditProfile == true) "active" else "suspended")
                        )
                        StatusChip(
                            label = if (account?.canPostContent == true && appSettings?.allowUserPosting != false) {
                                "Hub Posting On"
                            } else {
                                "View Only"
                            },
                            color = statusColorFor(if (account?.canPostContent == true) "active" else "suspended")
                        )
                    }
                }
            }
        }

        if (!appSettings?.appBannerMessage.isNullOrBlank()) {
            item {
                Card(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(16.dp)
                    ) {
                        Icon(Icons.Rounded.Campaign, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = appSettings!!.appBannerMessage,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }
        }

        item {
            Text("Announcements", style = MaterialTheme.typography.titleLarge)
        }

        when (announcementsState) {
            UiState.Loading -> item { CircularProgressIndicator() }
            is UiState.Error -> item { EmptyStateCard("Could not load announcements", announcementsState.message) }
            is UiState.Success -> {
                if (announcementsState.data.isEmpty()) {
                    item {
                        EmptyStateCard(
                            title = "No announcements yet",
                            message = "Official announcements from the SEPTIS Admin will appear here.",
                            icon = Icons.Rounded.Campaign
                        )
                    }
                } else {
                    items(announcementsState.data, key = { it.id }) { ann ->
                        Card(
                            shape = MaterialTheme.shapes.large,
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (ann.pinned) {
                                        Icon(Icons.Rounded.PushPin, contentDescription = "Pinned", tint = MaterialTheme.colorScheme.secondary)
                                        Spacer(modifier = Modifier.width(6.dp))
                                    }
                                    Text(ann.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                                    StatusChip(ann.priority, statusColorFor(ann.priority))
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(ann.body, style = MaterialTheme.typography.bodyMedium)
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "By ${ann.authorName} • ${formatTimestamp(ann.createdAt)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UserHubTab(
    currentUserId: String,
    canPost: Boolean,
    recordsState: UiState<List<SeptisRecord>>,
    onEditRecord: (SeptisRecord) -> Unit,
    onDeleteRecord: (String) -> Unit
) {
    var selectedCategory by remember { mutableStateOf("all") }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        if (!canPost) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "Posting is currently restricted by Administrator. You can browse permitted content.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(12.dp)
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
        }

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("all", "chat", "connect", "share", "resource").forEach { cat ->
                FilterChip(
                    selected = selectedCategory == cat,
                    onClick = { selectedCategory = cat },
                    label = { Text(cat.replaceFirstChar { it.uppercase() }) }
                )
            }
        }
        Spacer(modifier = Modifier.height(10.dp))

        when (recordsState) {
            UiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            is UiState.Error -> EmptyStateCard("Could not load hub", recordsState.message)
            is UiState.Success -> {
                val filtered = recordsState.data.filter {
                    selectedCategory == "all" || it.category == selectedCategory
                }
                if (filtered.isEmpty()) {
                    EmptyStateCard(
                        title = "SEPTIS Hub is quiet",
                        message = if (canPost) "Tap + to share an update or resource with your team." else "No shared records available yet.",
                        icon = Icons.Rounded.Forum
                    )
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(filtered, key = { it.id }) { rec ->
                            val canModify = rec.authorUid == currentUserId && !rec.isProtected
                            Card(
                                shape = MaterialTheme.shapes.large,
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(rec.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                                        if (rec.isProtected) {
                                            Icon(Icons.Rounded.Lock, contentDescription = "Protected record", tint = MaterialTheme.colorScheme.secondary)
                                            Spacer(modifier = Modifier.width(4.dp))
                                        }
                                        StatusChip(rec.category, MaterialTheme.colorScheme.primary)
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(rec.content, style = MaterialTheme.typography.bodyMedium)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            "${rec.authorName} • ${formatTimestamp(rec.createdAt)}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        if (canModify) {
                                            Row {
                                                IconButton(onClick = { onEditRecord(rec) }) {
                                                    Icon(Icons.Rounded.Edit, contentDescription = "Edit post")
                                                }
                                                IconButton(onClick = { onDeleteRecord(rec.id) }) {
                                                    Icon(Icons.Rounded.Delete, contentDescription = "Delete post", tint = MaterialTheme.colorScheme.error)
                                                }
                                            }
                                        }
                                    }
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
private fun UserNotificationsTab(
    notificationsState: UiState<List<NotificationItem>>,
    onMarkRead: (String) -> Unit
) {
    when (notificationsState) {
        UiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        is UiState.Error -> Box(Modifier.padding(16.dp)) { EmptyStateCard("Could not load notifications", notificationsState.message) }
        is UiState.Success -> {
            if (notificationsState.data.isEmpty()) {
                Box(Modifier.padding(16.dp)) {
                    EmptyStateCard(
                        title = "All caught up",
                        message = "Personal and broadcast notifications from Admin appear here.",
                        icon = Icons.Rounded.Notifications
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(16.dp)
                ) {
                    items(notificationsState.data, key = { it.id }) { notif ->
                        Card(
                            shape = MaterialTheme.shapes.large,
                            colors = CardDefaults.cardColors(
                                containerColor = if (notif.isRead) {
                                    MaterialTheme.colorScheme.surface
                                } else {
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                                }
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (!notif.isRead && notif.recipientUid != "all") {
                                        onMarkRead(notif.id)
                                    }
                                }
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        notif.title,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = if (notif.isRead) FontWeight.Medium else FontWeight.Bold
                                    )
                                    StatusChip(notif.type, statusColorFor(notif.type))
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(notif.message, style = MaterialTheme.typography.bodyMedium)
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "${if (notif.recipientUid == "all") "Broadcast" else "Direct"} • ${formatTimestamp(notif.createdAt)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UserProfileTab(
    accountState: UiState<UserAccount?>,
    globalEditsAllowed: Boolean,
    hasPasscode: Boolean,
    onSaveProfile: (name: String, bio: String, dept: String, phone: String) -> Unit,
    onOpenPasscodeDialog: () -> Unit,
    onLogoutClick: () -> Unit
) {
    val account = (accountState as? UiState.Success)?.data
    val editable = (account?.canEditProfile == true) && globalEditsAllowed

    var displayName by remember(account?.displayName) { mutableStateOf(account?.displayName.orEmpty()) }
    var department by remember(account?.department) { mutableStateOf(account?.department.orEmpty()) }
    var phone by remember(account?.phone) { mutableStateOf(account?.phone.orEmpty()) }
    var bio by remember(account?.bio) { mutableStateOf(account?.bio.orEmpty()) }

    Column(
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Card(
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(18.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Badge, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("My SEPTIS Profile", style = MaterialTheme.typography.titleLarge)
                }
                Text(
                    text = "Email: ${account?.email.orEmpty()} • Role: USER",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (!editable) {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            "Profile editing is restricted by Admin.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
                OutlinedTextField(
                    value = displayName,
                    onValueChange = { displayName = it },
                    enabled = editable,
                    label = { Text("Display Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = department,
                    onValueChange = { department = it },
                    enabled = editable,
                    label = { Text("Department") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    enabled = editable,
                    label = { Text("Phone") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = bio,
                    onValueChange = { bio = it },
                    enabled = editable,
                    label = { Text("Bio") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    onClick = { onSaveProfile(displayName, bio, department, phone) },
                    enabled = editable && displayName.isNotBlank(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("user_save_profile_button")
                ) {
                    Text("Save Permitted Profile")
                }
            }
        }

        Card(
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(18.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Key, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Personal Security & Passcode", style = MaterialTheme.typography.titleLarge)
                }
                Text(
                    text = if (hasPasscode) "Personal passcode is active and hashed securely." else "Set a personal security passcode for your SEPTIS profile.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedButton(
                    onClick = onOpenPasscodeDialog,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("user_change_passcode_button")
                ) {
                    Text(if (hasPasscode) "Change Personal Passcode" else "Set Personal Passcode")
                }
                OutlinedButton(
                    onClick = onLogoutClick,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Text("Log Out Securely")
                }
            }
        }
    }
}

@Composable
private fun UserRecordDialog(
    existing: SeptisRecord?,
    onDismiss: () -> Unit,
    onSave: (id: String?, title: String, content: String, category: String) -> Unit
) {
    var title by remember { mutableStateOf(existing?.title.orEmpty()) }
    var content by remember { mutableStateOf(existing?.content.orEmpty()) }
    var category by remember { mutableStateOf(existing?.category ?: "chat") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Share to SEPTIS Hub" else "Edit Post") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("Message / Content") },
                    minLines = 3,
                    maxLines = 5,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("chat", "connect", "share", "resource").forEach { cat ->
                        FilterChip(
                            selected = category == cat,
                            onClick = { category = cat },
                            label = { Text(cat.replaceFirstChar { it.uppercase() }) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(existing?.id, title, content, category)
                    onDismiss()
                },
                enabled = title.isNotBlank() && content.isNotBlank()
            ) {
                Text("Publish")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun ChangePasscodeDialog(
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var passcode by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Update Personal Passcode") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Choose a personal security passcode (minimum 4 characters).",
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedTextField(
                    value = passcode,
                    onValueChange = { passcode = it },
                    label = { Text("New Passcode") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = confirm,
                    onValueChange = { confirm = it },
                    label = { Text("Confirm Passcode") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(passcode)
                    onDismiss()
                },
                enabled = passcode.length >= 4 && passcode == confirm
            ) {
                Text("Save Passcode")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
