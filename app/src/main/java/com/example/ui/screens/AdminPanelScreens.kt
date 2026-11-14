package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import com.example.ui.components.SeptisBadge
import com.example.ui.components.formatRelativeDate
import com.example.ui.theme.StatusActiveGreen
import com.example.ui.theme.StatusDeactivatedRed
import com.example.ui.theme.StatusSuspendedAmber
import com.example.ui.viewmodel.AdminTab
import com.example.ui.viewmodel.AdminViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminDashboardScreen(
    adminViewModel: AdminViewModel,
    onExitAdmin: () -> Unit,
    onLogoutClick: () -> Unit
) {
    val selectedTab by adminViewModel.selectedTab.collectAsStateWithLifecycle()
    val users by adminViewModel.users.collectAsStateWithLifecycle()
    val reports by adminViewModel.reports.collectAsStateWithLifecycle()
    val groups by adminViewModel.groups.collectAsStateWithLifecycle()
    val communities by adminViewModel.communities.collectAsStateWithLifecycle()
    val announcements by adminViewModel.announcements.collectAsStateWithLifecycle()
    val auditLogs by adminViewModel.auditLogs.collectAsStateWithLifecycle()
    val settings by adminViewModel.settings.collectAsStateWithLifecycle()
    val feedback by adminViewModel.feedback.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(feedback) {
        if (!feedback.isNullOrBlank()) {
            snackbarHostState.showSnackbar(feedback!!)
            adminViewModel.clearFeedback()
        }
    }

    var showAnnouncementDialog by remember { mutableStateOf(false) }
    var showRotateCodeDialog by remember { mutableStateOf(false) }

    if (showAnnouncementDialog) {
        var title by remember { mutableStateOf("") }
        var body by remember { mutableStateOf("") }
        var pinned by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { showAnnouncementDialog = false },
            title = { Text("Publish SEPTIS Announcement") },
            text = {
                Column {
                    OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Title") })
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = body, onValueChange = { body = it }, label = { Text("Body") }, minLines = 3)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(checked = pinned, onCheckedChange = { pinned = it })
                        Spacer(Modifier.width(8.dp))
                        Text("Pin announcement")
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        adminViewModel.postAnnouncement(title, body, "important", pinned)
                        showAnnouncementDialog = false
                    },
                    enabled = title.isNotBlank() && body.isNotBlank()
                ) { Text("Publish") }
            },
            dismissButton = { TextButton(onClick = { showAnnouncementDialog = false }) { Text("Cancel") } }
        )
    }

    if (showRotateCodeDialog) {
        var current by remember { mutableStateOf("") }
        var next by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showRotateCodeDialog = false },
            title = { Text("Rotate Admin Access Code") },
            text = {
                Column {
                    OutlinedTextField(
                        value = current,
                        onValueChange = { current = it },
                        label = { Text("Current Code") },
                        visualTransformation = PasswordVisualTransformation()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = next,
                        onValueChange = { next = it },
                        label = { Text("New Code (min 6 chars)") },
                        visualTransformation = PasswordVisualTransformation()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    adminViewModel.rotateAccessCode(current, next)
                    showRotateCodeDialog = false
                }) { Text("Rotate") }
            },
            dismissButton = { TextButton(onClick = { showRotateCodeDialog = false }) { Text("Cancel") } }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Column {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        titleContentColor = MaterialTheme.colorScheme.onPrimary,
                        navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                        actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    navigationIcon = {
                        IconButton(onClick = onExitAdmin) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Exit admin")
                        }
                    },
                    title = { Text("SEPTIS Admin Console", fontWeight = FontWeight.Bold) },
                    actions = {
                        IconButton(onClick = onLogoutClick) {
                            Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = "Logout")
                        }
                    }
                )
                TabRow(selectedTabIndex = selectedTab.ordinal) {
                    Tab(
                        selected = selectedTab == AdminTab.USERS,
                        onClick = { adminViewModel.selectTab(AdminTab.USERS) },
                        text = { Text("Users (${users.size})") },
                        modifier = Modifier.testTag("admin_tab_users")
                    )
                    Tab(
                        selected = selectedTab == AdminTab.REPORTS,
                        onClick = { adminViewModel.selectTab(AdminTab.REPORTS) },
                        text = { Text("Reports (${reports.size})") },
                        modifier = Modifier.testTag("admin_tab_reports")
                    )
                    Tab(
                        selected = selectedTab == AdminTab.GROUPS,
                        onClick = { adminViewModel.selectTab(AdminTab.GROUPS) },
                        text = { Text("Groups (${groups.size})") },
                        modifier = Modifier.testTag("admin_tab_groups")
                    )
                    Tab(
                        selected = selectedTab == AdminTab.STATS_AND_SETTINGS,
                        onClick = { adminViewModel.selectTab(AdminTab.STATS_AND_SETTINGS) },
                        text = { Text("System") },
                        modifier = Modifier.testTag("admin_tab_system")
                    )
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            when (selectedTab) {
                AdminTab.USERS -> {
                    items(users, key = { it.userId }) { user ->
                        val statusColor = when (user.status) {
                            "active" -> StatusActiveGreen
                            "suspended" -> StatusSuspendedAmber
                            else -> StatusDeactivatedRed
                        }
                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(user.displayName.ifBlank { "Unnamed" }, fontWeight = FontWeight.Bold)
                                        Text(user.phoneE164, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                                    }
                                    SeptisBadge(user.status.uppercase(), statusColor.copy(alpha = 0.16f), statusColor)
                                }
                                Spacer(Modifier.height(8.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    if (user.status != "active") {
                                        OutlinedButton(onClick = { adminViewModel.setUserStatus(user, "active") }) {
                                            Text("Activate")
                                        }
                                    }
                                    if (user.status != "suspended") {
                                        OutlinedButton(onClick = { adminViewModel.setUserStatus(user, "suspended") }) {
                                            Text("Suspend")
                                        }
                                    }
                                    TextButton(onClick = { adminViewModel.deleteUser(user) }) {
                                        Text("Delete", color = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                    }
                }

                AdminTab.REPORTS -> {
                    if (reports.isEmpty()) {
                        item { Text("No user abuse reports recorded.") }
                    } else {
                        items(reports, key = { it.id }) { rep ->
                            Card(
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Text("Reason: ${rep.reason}", fontWeight = FontWeight.Bold)
                                    Text("Target UID: ${rep.reportedUid} (${rep.reportedPhone ?: "no phone"})", style = MaterialTheme.typography.bodySmall)
                                    if (!rep.details.isNullOrBlank()) {
                                        Text(rep.details, style = MaterialTheme.typography.bodyMedium)
                                    }
                                    Text("Status: ${rep.status.uppercase()}", style = MaterialTheme.typography.labelMedium)
                                    Spacer(Modifier.height(8.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedButton(onClick = { adminViewModel.updateReportStatus(rep.id, "reviewed") }) {
                                            Text("Mark Reviewed")
                                        }
                                        Button(onClick = { adminViewModel.updateReportStatus(rep.id, "resolved") }) {
                                            Text("Resolve")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                AdminTab.GROUPS -> {
                    item {
                        Text(
                            "Active Groups (${groups.size}) & Communities (${communities.size})",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    items(groups, key = { it.id }) { grp ->
                        Card(shape = RoundedCornerShape(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(grp.title, fontWeight = FontWeight.Bold)
                                    Text("${grp.participantUids.size} members", style = MaterialTheme.typography.bodySmall)
                                }
                                IconButton(onClick = { adminViewModel.removeGroup(grp.id) }) {
                                    Icon(Icons.Rounded.Delete, contentDescription = "Remove group", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                    items(communities, key = { it.id }) { comm ->
                        Card(shape = RoundedCornerShape(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("${comm.iconEmoji} ${comm.name}", fontWeight = FontWeight.Bold)
                                    Text("${comm.groupIds.size} groups • ${comm.memberUids.size} members", style = MaterialTheme.typography.bodySmall)
                                }
                                IconButton(onClick = { adminViewModel.removeCommunity(comm.id) }) {
                                    Icon(Icons.Rounded.Delete, contentDescription = "Remove community", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }

                AdminTab.STATS_AND_SETTINGS -> {
                    item {
                        Card(shape = RoundedCornerShape(16.dp)) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Platform Overview", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text("Registered Users: ${users.size} (Active: ${users.count { it.status == "active" }}, Suspended: ${users.count { it.status == "suspended" }})")
                                Text("Groups: ${groups.size} • Communities: ${communities.size}")
                                Text("Open Reports: ${reports.count { it.status == "open" }}")
                            }
                        }
                    }

                    item {
                        var banner by remember(settings) { mutableStateOf(settings.appBannerMessage) }
                        var allowPost by remember(settings) { mutableStateOf(settings.allowUserPosting) }
                        var allowProfile by remember(settings) { mutableStateOf(settings.allowUserProfileEdits) }
                        var maintenance by remember(settings) { mutableStateOf(settings.maintenanceMode) }

                        Card(shape = RoundedCornerShape(16.dp)) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Application Controls", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                OutlinedTextField(
                                    value = banner,
                                    onValueChange = { banner = it },
                                    label = { Text("Global Banner Notice") },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Text("Allow user status/media posting")
                                    Switch(checked = allowPost, onCheckedChange = { allowPost = it })
                                }
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Text("Allow profile edits")
                                    Switch(checked = allowProfile, onCheckedChange = { allowProfile = it })
                                }
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Text("Maintenance mode banner")
                                    Switch(checked = maintenance, onCheckedChange = { maintenance = it })
                                }
                                Button(
                                    onClick = { adminViewModel.saveSettings(banner, allowPost, allowProfile, maintenance) },
                                    modifier = Modifier.fillMaxWidth()
                                ) { Text("Save Application Settings") }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(onClick = { showAnnouncementDialog = true }, modifier = Modifier.weight(1f)) {
                                        Text("Post Announcement")
                                    }
                                    OutlinedButton(onClick = { showRotateCodeDialog = true }, modifier = Modifier.weight(1f)) {
                                        Text("Rotate Vault Code")
                                    }
                                }
                            }
                        }
                    }

                    item {
                        Text("Recent Audit Trail (${auditLogs.size})", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    }
                    items(auditLogs.take(25), key = { it.id }) { log ->
                        Column(modifier = Modifier.padding(vertical = 4.dp)) {
                            Text("${log.action} • ${formatRelativeDate(log.createdAt)}", fontWeight = FontWeight.SemiBold)
                            Text(log.details, style = MaterialTheme.typography.bodySmall)
                        }
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}
