package com.example.ui.screens

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Article
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Analytics
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PauseCircle
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.Announcement
import com.example.data.model.AuditLog
import com.example.data.model.PendingAccount
import com.example.data.model.SeptisRecord
import com.example.data.model.UserAccount
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.SeptisDashboardTopBar
import com.example.ui.components.StatusChip
import com.example.ui.components.formatTimestamp
import com.example.ui.components.statusColorFor
import com.example.ui.viewmodel.AdminDashboardViewModel
import com.example.ui.viewmodel.UiState

@Composable
fun AdminDashboardScreen(
    viewModel: AdminDashboardViewModel,
    isOnline: Boolean,
    onLogoutClick: () -> Unit
) {
    val usersState by viewModel.usersState.collectAsStateWithLifecycle()
    val pendingState by viewModel.pendingAccountsState.collectAsStateWithLifecycle()
    val announcementsState by viewModel.announcementsState.collectAsStateWithLifecycle()
    val recordsState by viewModel.recordsState.collectAsStateWithLifecycle()
    val notificationsState by viewModel.notificationsState.collectAsStateWithLifecycle()
    val appSettings by viewModel.appSettingsState.collectAsStateWithLifecycle()
    val admins by viewModel.adminsState.collectAsStateWithLifecycle()
    val auditLogs by viewModel.auditLogsState.collectAsStateWithLifecycle()
    val feedback by viewModel.feedbackMessage.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(feedback) {
        if (!feedback.isNullOrBlank()) {
            snackbarHostState.showSnackbar(feedback!!)
            viewModel.clearFeedback()
        }
    }

    var selectedTab by remember { mutableIntStateOf(0) }
    var showCreateUserDialog by remember { mutableStateOf(false) }
    var editingUser by remember { mutableStateOf<UserAccount?>(null) }
    var deletingUser by remember { mutableStateOf<UserAccount?>(null) }

    var contentSubTab by remember { mutableIntStateOf(0) } // 0 = Announcements, 1 = Records
    var showAnnouncementDialog by remember { mutableStateOf(false) }
    var editingAnnouncement by remember { mutableStateOf<Announcement?>(null) }
    var showRecordDialog by remember { mutableStateOf(false) }
    var editingRecord by remember { mutableStateOf<SeptisRecord?>(null) }
    var showRotateCodeDialog by remember { mutableStateOf(false) }

    val navItems = listOf(
        "Overview" to Icons.Rounded.Analytics,
        "Users" to Icons.Rounded.Group,
        "Content" to Icons.AutoMirrored.Rounded.Article,
        "Controls" to Icons.Rounded.Tune
    )

    val users = (usersState as? UiState.Success)?.data.orEmpty()
    val pending = (pendingState as? UiState.Success)?.data.orEmpty()
    val announcements = (announcementsState as? UiState.Success)?.data.orEmpty()
    val records = (recordsState as? UiState.Success)?.data.orEmpty()
    val notifications = (notificationsState as? UiState.Success)?.data.orEmpty()

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 600.dp

        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                SeptisDashboardTopBar(
                    title = "SEPTIS Admin",
                    subtitle = viewModel.adminEmail.ifBlank { "Administrator Console" },
                    isOnline = isOnline,
                    isAdminMode = true,
                    logoutTestTag = "admin_logout_button",
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
                                icon = { Icon(icon, contentDescription = label) },
                                label = { Text(label) }
                            )
                        }
                    }
                }
            },
            floatingActionButton = {
                when (selectedTab) {
                    1 -> FloatingActionButton(
                        onClick = { showCreateUserDialog = true },
                        modifier = Modifier.testTag("admin_add_user_fab")
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = "Create User Account")
                    }
                    2 -> FloatingActionButton(
                        onClick = {
                            if (contentSubTab == 0) {
                                editingAnnouncement = null
                                showAnnouncementDialog = true
                            } else {
                                editingRecord = null
                                showRecordDialog = true
                            }
                        },
                        modifier = Modifier.testTag("admin_add_content_fab")
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = "Add Content")
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
                                icon = { Icon(icon, contentDescription = label) },
                                label = { Text(label) }
                            )
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    when (selectedTab) {
                        0 -> AdminOverviewTab(
                            users = users,
                            pendingCount = pending.count { !it.claimed },
                            announcementsCount = announcements.size,
                            recordsCount = records.size,
                            notificationsCount = notifications.size,
                            auditLogs = auditLogs
                        )
                        1 -> AdminUsersTab(
                            usersState = usersState,
                            pendingAccounts = pending,
                            onEditUser = { editingUser = it },
                            onChangeStatus = { uid, st -> viewModel.setUserStatus(uid, st) },
                            onDeleteUser = { deletingUser = it },
                            onDeletePending = { viewModel.deletePendingInvite(it) }
                        )
                        2 -> AdminContentTab(
                            subTab = contentSubTab,
                            onSubTabChange = { contentSubTab = it },
                            announcementsState = announcementsState,
                            recordsState = recordsState,
                            onEditAnnouncement = {
                                editingAnnouncement = it
                                showAnnouncementDialog = true
                            },
                            onDeleteAnnouncement = { viewModel.deleteAnnouncement(it) },
                            onEditRecord = {
                                editingRecord = it
                                showRecordDialog = true
                            },
                            onDeleteRecord = { viewModel.deleteRecord(it) }
                        )
                        3 -> AdminControlsTab(
                            users = users,
                            notifications = notifications,
                            appSettings = appSettings,
                            admins = admins,
                            currentAdminUid = viewModel.adminUid,
                            onSendNotification = viewModel::sendNotification,
                            onDeleteNotification = viewModel::deleteNotification,
                            onSaveSettings = viewModel::saveAppSettings,
                            onRotateCodeClick = { showRotateCodeDialog = true },
                            onGrantAdmin = viewModel::grantAdminAccess,
                            onRevokeAdmin = viewModel::revokeAdminAccess
                        )
                    }
                }
            }
        }
    }

    if (showCreateUserDialog || editingUser != null) {
        CreateOrEditUserDialog(
            existingUser = editingUser,
            onDismiss = {
                showCreateUserDialog = false
                editingUser = null
            },
            onSubmitNew = viewModel::createPendingUser,
            onSubmitEdit = viewModel::updateUser
        )
    }

    if (deletingUser != null) {
        AlertDialog(
            onDismissRequest = { deletingUser = null },
            title = { Text("Delete User Account?") },
            text = { Text("Permanently remove ${deletingUser!!.displayName} (${deletingUser!!.email}) from SEPTIS?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteUser(deletingUser!!.userId)
                    deletingUser = null
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingUser = null }) { Text("Cancel") }
            }
        )
    }

    if (showAnnouncementDialog) {
        AnnouncementEditorDialog(
            existing = editingAnnouncement,
            onDismiss = {
                showAnnouncementDialog = false
                editingAnnouncement = null
            },
            onSave = viewModel::saveAnnouncement
        )
    }

    if (showRecordDialog) {
        AdminRecordEditorDialog(
            existing = editingRecord,
            onDismiss = {
                showRecordDialog = false
                editingRecord = null
            },
            onSave = { id, title, content, category, status, visibility, isProtected ->
                if (id == null) {
                    viewModel.createAdminRecord(title, content, category, status, visibility, isProtected)
                } else {
                    viewModel.updateAdminRecord(id, title, content, category, status, visibility, isProtected)
                }
            }
        )
    }

    if (showRotateCodeDialog) {
        RotateAdminCodeDialog(
            onDismiss = { showRotateCodeDialog = false },
            onRotate = viewModel::rotateAdminCode
        )
    }
}

@Composable
private fun AdminOverviewTab(
    users: List<UserAccount>,
    pendingCount: Int,
    announcementsCount: Int,
    recordsCount: Int,
    notificationsCount: Int,
    auditLogs: List<AuditLog>
) {
    val activeUsers = users.count { it.status == "active" }
    val suspendedUsers = users.count { it.status == "suspended" }
    val deactivatedUsers = users.count { it.status == "deactivated" }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.padding(16.dp)
    ) {
        item {
            Text("Reports & System Statistics", style = MaterialTheme.typography.headlineMedium)
            Text(
                "Live snapshot across SEPTIS accounts, content, and security audit trail.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                StatCard("Total Users", "${users.size}", "$activeUsers active", Modifier.weight(1f))
                StatCard("Restricted", "${suspendedUsers + deactivatedUsers}", "$suspendedUsers susp • $deactivatedUsers off", Modifier.weight(1f))
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                StatCard("Content", "$recordsCount", "$announcementsCount announcements", Modifier.weight(1f))
                StatCard("Alerts & Invites", "$notificationsCount", "$pendingCount pending invites", Modifier.weight(1f))
            }
        }
        item {
            Spacer(modifier = Modifier.height(6.dp))
            Text("Recent Audit Trail", style = MaterialTheme.typography.titleLarge)
        }
        if (auditLogs.isEmpty()) {
            item {
                EmptyStateCard(
                    title = "No audit events yet",
                    message = "Administrative actions and security checks appear here automatically."
                )
            }
        } else {
            items(auditLogs, key = { it.id }) { log ->
                Card(
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(log.action, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                            Text(formatTimestamp(log.createdAt), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(log.details, style = MaterialTheme.typography.bodyMedium)
                        Text("By: ${log.actorEmail}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, caption: String, modifier: Modifier = Modifier) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(4.dp))
            Text(value, style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(2.dp))
            Text(caption, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun AdminUsersTab(
    usersState: UiState<List<UserAccount>>,
    pendingAccounts: List<PendingAccount>,
    onEditUser: (UserAccount) -> Unit,
    onChangeStatus: (String, String) -> Unit,
    onDeleteUser: (UserAccount) -> Unit,
    onDeletePending: (String) -> Unit
) {
    var search by remember { mutableStateOf("") }
    var filterStatus by remember { mutableStateOf("all") }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        OutlinedTextField(
            value = search,
            onValueChange = { search = it },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            placeholder = { Text("Search users by name or email...") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("all", "active", "suspended", "deactivated").forEach { status ->
                FilterChip(
                    selected = filterStatus == status,
                    onClick = { filterStatus = status },
                    label = { Text(status.replaceFirstChar { it.uppercase() }) }
                )
            }
        }
        Spacer(modifier = Modifier.height(10.dp))

        when (usersState) {
            UiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            is UiState.Error -> EmptyStateCard("Could not load users", usersState.message)
            is UiState.Success -> {
                val filtered = usersState.data.filter { user ->
                    val matchesFilter = filterStatus == "all" || user.status == filterStatus
                    val matchesSearch = search.isBlank() ||
                        user.displayName.contains(search, ignoreCase = true) ||
                        user.email.contains(search, ignoreCase = true)
                    matchesFilter && matchesSearch
                }
                val unclaimed = pendingAccounts.filter { !it.claimed }

                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (filtered.isEmpty() && unclaimed.isEmpty()) {
                        item {
                            EmptyStateCard(
                                title = "No matching user accounts",
                                message = "Tap + to pre-provision a new user account or adjust your search."
                            )
                        }
                    } else {
                        items(filtered, key = { it.userId }) { user ->
                            UserAccountCard(
                                user = user,
                                onEdit = { onEditUser(user) },
                                onChangeStatus = { newStatus -> onChangeStatus(user.userId, newStatus) },
                                onDelete = { onDeleteUser(user) }
                            )
                        }
                        if (unclaimed.isNotEmpty()) {
                            item {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("Pre-Provisioned Invites (${unclaimed.size})", style = MaterialTheme.typography.titleMedium)
                            }
                            items(unclaimed, key = { it.id }) { invite ->
                                Card(
                                    shape = MaterialTheme.shapes.medium,
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth().padding(14.dp)
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(invite.displayName, style = MaterialTheme.typography.titleMedium)
                                            Text(invite.email, style = MaterialTheme.typography.bodySmall)
                                            Text("Awaiting first Google Sign-In • Status: ${invite.status}", style = MaterialTheme.typography.labelSmall)
                                        }
                                        IconButton(onClick = { onDeletePending(invite.id) }) {
                                            Icon(Icons.Rounded.Delete, contentDescription = "Remove invite", tint = MaterialTheme.colorScheme.error)
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
private fun UserAccountCard(
    user: UserAccount,
    onEdit: () -> Unit,
    onChangeStatus: (String) -> Unit,
    onDelete: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(user.displayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(user.email, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                StatusChip(label = user.status, color = statusColorFor(user.status))
                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Rounded.MoreVert, contentDescription = "Manage user")
                    }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        DropdownMenuItem(
                            text = { Text("Edit Profile & Permissions") },
                            leadingIcon = { Icon(Icons.Rounded.Edit, contentDescription = null) },
                            onClick = { menuExpanded = false; onEdit() }
                        )
                        if (user.status != "active") {
                            DropdownMenuItem(
                                text = { Text("Activate / Restore Account") },
                                leadingIcon = { Icon(Icons.Rounded.Restore, contentDescription = null) },
                                onClick = { menuExpanded = false; onChangeStatus("active") }
                            )
                        }
                        if (user.status != "suspended") {
                            DropdownMenuItem(
                                text = { Text("Suspend Account") },
                                leadingIcon = { Icon(Icons.Rounded.PauseCircle, contentDescription = null) },
                                onClick = { menuExpanded = false; onChangeStatus("suspended") }
                            )
                        }
                        if (user.status != "deactivated") {
                            DropdownMenuItem(
                                text = { Text("Deactivate Account") },
                                leadingIcon = { Icon(Icons.Rounded.Block, contentDescription = null) },
                                onClick = { menuExpanded = false; onChangeStatus("deactivated") }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Delete Account", color = MaterialTheme.colorScheme.error) },
                            leadingIcon = { Icon(Icons.Rounded.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                            onClick = { menuExpanded = false; onDelete() }
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Dept: ${user.department?.ifBlank { "General" } ?: "General"} • Edit Profile: ${if (user.canEditProfile) "Yes" else "Locked"} • Post Hub: ${if (user.canPostContent) "Yes" else "Locked"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun AdminContentTab(
    subTab: Int,
    onSubTabChange: (Int) -> Unit,
    announcementsState: UiState<List<Announcement>>,
    recordsState: UiState<List<SeptisRecord>>,
    onEditAnnouncement: (Announcement) -> Unit,
    onDeleteAnnouncement: (String) -> Unit,
    onEditRecord: (SeptisRecord) -> Unit,
    onDeleteRecord: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = subTab == 0,
                onClick = { onSubTabChange(0) },
                label = { Text("Announcements") }
            )
            FilterChip(
                selected = subTab == 1,
                onClick = { onSubTabChange(1) },
                label = { Text("SEPTIS Records Hub") }
            )
        }
        Spacer(modifier = Modifier.height(10.dp))

        if (subTab == 0) {
            when (announcementsState) {
                UiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                is UiState.Error -> EmptyStateCard("Failed to load announcements", announcementsState.message)
                is UiState.Success -> {
                    if (announcementsState.data.isEmpty()) {
                        EmptyStateCard("No announcements", "Tap + to publish an announcement to users.")
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(
                                                "${ann.visibility} • ${formatTimestamp(ann.createdAt)}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Row {
                                                IconButton(onClick = { onEditAnnouncement(ann) }) {
                                                    Icon(Icons.Rounded.Edit, contentDescription = "Edit announcement")
                                                }
                                                IconButton(onClick = { onDeleteAnnouncement(ann.id) }) {
                                                    Icon(Icons.Rounded.Delete, contentDescription = "Delete announcement", tint = MaterialTheme.colorScheme.error)
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
        } else {
            when (recordsState) {
                UiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                is UiState.Error -> EmptyStateCard("Failed to load records", recordsState.message)
                is UiState.Success -> {
                    if (recordsState.data.isEmpty()) {
                        EmptyStateCard("No records yet", "Tap + to add a managed or protected SEPTIS record.")
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(recordsState.data, key = { it.id }) { rec ->
                                Card(
                                    shape = MaterialTheme.shapes.large,
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                                ) {
                                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(rec.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                                            if (rec.isProtected) {
                                                Icon(Icons.Rounded.Lock, contentDescription = "Protected record", tint = MaterialTheme.colorScheme.secondary)
                                                Spacer(modifier = Modifier.width(6.dp))
                                            }
                                            StatusChip(rec.status, statusColorFor(rec.status))
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
                                                "${rec.category.uppercase()} • ${rec.authorName} • ${rec.visibility}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Row {
                                                IconButton(onClick = { onEditRecord(rec) }) {
                                                    Icon(Icons.Rounded.Edit, contentDescription = "Edit record")
                                                }
                                                IconButton(onClick = { onDeleteRecord(rec.id) }) {
                                                    Icon(Icons.Rounded.Delete, contentDescription = "Delete record", tint = MaterialTheme.colorScheme.error)
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
