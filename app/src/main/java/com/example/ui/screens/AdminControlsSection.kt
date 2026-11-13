package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.model.AdminMember
import com.example.data.model.AppSettings
import com.example.data.model.NotificationItem
import com.example.data.model.UserAccount
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.formatTimestamp

@Composable
fun AdminControlsTab(
    users: List<UserAccount>,
    notifications: List<NotificationItem>,
    appSettings: AppSettings?,
    admins: List<AdminMember>,
    currentAdminUid: String,
    onSendNotification: (recipientUid: String, title: String, message: String, type: String) -> Unit,
    onDeleteNotification: (String) -> Unit,
    onSaveSettings: (banner: String, allowPost: Boolean, allowEdit: Boolean, maintenance: Boolean) -> Unit,
    onRotateCodeClick: () -> Unit,
    onGrantAdmin: (uid: String, email: String, name: String) -> Unit,
    onRevokeAdmin: (uid: String) -> Unit
) {
    var recipientUid by remember { mutableStateOf("all") }
    var notifTitle by remember { mutableStateOf("") }
    var notifMessage by remember { mutableStateOf("") }
    var notifType by remember { mutableStateOf("info") }

    var bannerText by remember { mutableStateOf(appSettings?.appBannerMessage.orEmpty()) }
    var allowPosting by remember { mutableStateOf(appSettings?.allowUserPosting ?: true) }
    var allowProfileEdits by remember { mutableStateOf(appSettings?.allowUserProfileEdits ?: true) }
    var maintenanceMode by remember { mutableStateOf(appSettings?.maintenanceMode ?: false) }

    LaunchedEffect(appSettings) {
        if (appSettings != null) {
            bannerText = appSettings.appBannerMessage
            allowPosting = appSettings.allowUserPosting
            allowProfileEdits = appSettings.allowUserProfileEdits
            maintenanceMode = appSettings.maintenanceMode
        }
    }

    var newAdminUid by remember { mutableStateOf("") }
    var newAdminEmail by remember { mutableStateOf("") }
    var newAdminName by remember { mutableStateOf("") }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.padding(16.dp)
    ) {
        // 1. Dispatch Notifications
        item {
            Card(
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(18.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.NotificationsActive, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Send Notification", style = MaterialTheme.typography.titleLarge)
                    }
                    Text("Target Recipient", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(
                            selected = recipientUid == "all",
                            onClick = { recipientUid = "all" },
                            label = { Text("All Users") }
                        )
                        users.take(3).forEach { user ->
                            FilterChip(
                                selected = recipientUid == user.userId,
                                onClick = { recipientUid = user.userId },
                                label = { Text(user.displayName.take(12)) }
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("info", "alert", "account").forEach { type ->
                            FilterChip(
                                selected = notifType == type,
                                onClick = { notifType = type },
                                label = { Text(type.replaceFirstChar { it.uppercase() }) }
                            )
                        }
                    }
                    OutlinedTextField(
                        value = notifTitle,
                        onValueChange = { notifTitle = it },
                        label = { Text("Notification Title") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = notifMessage,
                        onValueChange = { notifMessage = it },
                        label = { Text("Notification Message") },
                        minLines = 2,
                        maxLines = 4,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        onClick = {
                            onSendNotification(recipientUid, notifTitle, notifMessage, notifType)
                            notifTitle = ""
                            notifMessage = ""
                        },
                        enabled = notifTitle.isNotBlank() && notifMessage.isNotBlank(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("admin_send_notification_button")
                    ) {
                        Text("Dispatch Notification")
                    }
                }
            }
        }

        // 2. Application Settings
        item {
            Card(
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(18.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Settings, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Application Settings", style = MaterialTheme.typography.titleLarge)
                    }
                    OutlinedTextField(
                        value = bannerText,
                        onValueChange = { bannerText = it },
                        label = { Text("Global User Dashboard Banner") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Allow Users to Create Hub Posts")
                        Switch(checked = allowPosting, onCheckedChange = { allowPosting = it })
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Allow User Profile Edits")
                        Switch(checked = allowProfileEdits, onCheckedChange = { allowProfileEdits = it })
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Maintenance Mode Banner")
                        Switch(checked = maintenanceMode, onCheckedChange = { maintenanceMode = it })
                    }
                    Button(
                        onClick = { onSaveSettings(bannerText, allowPosting, allowProfileEdits, maintenanceMode) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("admin_save_settings_button")
                    ) {
                        Text("Save Global Settings")
                    }
                }
            }
        }

        // 3. Confidential Admin Vault & Admin Access Registry
        item {
            Card(
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(18.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Shield, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Confidential Admin Security", style = MaterialTheme.typography.titleLarge)
                    }
                    Text(
                        "Rotate the private Admin Access Code or authorize additional Admin UIDs (Root Owner only).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedButton(
                        onClick = onRotateCodeClick,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("admin_rotate_code_button")
                    ) {
                        Icon(Icons.Rounded.Key, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Change Admin Access Code")
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text("Authorize Co-Admin (Root Owner Only)", style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(
                        value = newAdminUid,
                        onValueChange = { newAdminUid = it },
                        label = { Text("Target User UID") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = newAdminEmail,
                        onValueChange = { newAdminEmail = it },
                        label = { Text("Co-Admin Email") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = newAdminName,
                        onValueChange = { newAdminName = it },
                        label = { Text("Co-Admin Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        onClick = {
                            onGrantAdmin(newAdminUid, newAdminEmail, newAdminName)
                            newAdminUid = ""
                            newAdminEmail = ""
                            newAdminName = ""
                        },
                        enabled = newAdminUid.isNotBlank() && newAdminEmail.contains("@") && newAdminName.isNotBlank(),
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        Icon(Icons.Rounded.PersonAdd, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Grant Admin Authority")
                    }

                    admins.forEach { admin ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(admin.displayName, style = MaterialTheme.typography.bodyMedium)
                                Text(admin.email, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (admin.uid != currentAdminUid) {
                                IconButton(onClick = { onRevokeAdmin(admin.uid) }) {
                                    Icon(Icons.Rounded.Delete, contentDescription = "Revoke Admin", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }
        }

        // 4. Recent Dispatched Notifications
        item {
            Text("Dispatched Notifications (${notifications.size})", style = MaterialTheme.typography.titleMedium)
        }
        if (notifications.isEmpty()) {
            item {
                EmptyStateCard(
                    title = "No notifications sent yet",
                    message = "Use the form above to notify users or broadcast alerts."
                )
            }
        } else {
            items(notifications, key = { it.id }) { item ->
                Card(
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(item.title, style = MaterialTheme.typography.titleMedium)
                            Text(item.message, style = MaterialTheme.typography.bodySmall)
                            Text(
                                text = "To: ${if (item.recipientUid == "all") "All Users" else item.recipientUid.take(10)} • ${formatTimestamp(item.createdAt)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { onDeleteNotification(item.id) }) {
                            Icon(Icons.Rounded.Delete, contentDescription = "Delete Notification", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }
}
