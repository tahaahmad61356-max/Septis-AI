package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.data.model.Announcement
import com.example.data.model.SeptisRecord
import com.example.data.model.UserAccount

@Composable
fun CreateOrEditUserDialog(
    existingUser: UserAccount?,
    onDismiss: () -> Unit,
    onSubmitNew: (email: String, name: String, dept: String, bio: String, phone: String, status: String, canEdit: Boolean, canPost: Boolean) -> Unit,
    onSubmitEdit: (userId: String, name: String, dept: String, bio: String, phone: String, status: String, canEdit: Boolean, canPost: Boolean) -> Unit
) {
    var email by remember { mutableStateOf(existingUser?.email.orEmpty()) }
    var displayName by remember { mutableStateOf(existingUser?.displayName.orEmpty()) }
    var department by remember { mutableStateOf(existingUser?.department.orEmpty()) }
    var bio by remember { mutableStateOf(existingUser?.bio.orEmpty()) }
    var phone by remember { mutableStateOf(existingUser?.phone.orEmpty()) }
    var status by remember { mutableStateOf(existingUser?.status ?: "active") }
    var canEditProfile by remember { mutableStateOf(existingUser?.canEditProfile ?: true) }
    var canPostContent by remember { mutableStateOf(existingUser?.canPostContent ?: true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (existingUser == null) "Create User Account" else "Edit User Account")
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                if (existingUser == null) {
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text("User Google Email") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    Text(
                        text = "Account: ${existingUser.email}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                OutlinedTextField(
                    value = displayName,
                    onValueChange = { displayName = it },
                    label = { Text("Full Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = department,
                    onValueChange = { department = it },
                    label = { Text("Department / Team") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Phone (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = bio,
                    onValueChange = { bio = it },
                    label = { Text("Bio / Role Notes") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Account Status", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("active", "suspended", "deactivated").forEach { option ->
                        FilterChip(
                            selected = status == option,
                            onClick = { status = option },
                            label = { Text(option.replaceFirstChar { it.uppercase() }) }
                        )
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Allow Profile Editing", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = canEditProfile, onCheckedChange = { canEditProfile = it })
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Allow Hub Posting", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = canPostContent, onCheckedChange = { canPostContent = it })
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (existingUser == null) {
                        onSubmitNew(email, displayName, department, bio, phone, status, canEditProfile, canPostContent)
                    } else {
                        onSubmitEdit(existingUser.userId, displayName, department, bio, phone, status, canEditProfile, canPostContent)
                    }
                    onDismiss()
                },
                enabled = displayName.isNotBlank() && (existingUser != null || email.contains("@"))
            ) {
                Text(if (existingUser == null) "Provision User" else "Save Changes")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun AnnouncementEditorDialog(
    existing: Announcement?,
    onDismiss: () -> Unit,
    onSave: (id: String?, title: String, body: String, priority: String, pinned: Boolean, visibility: String) -> Unit
) {
    var title by remember { mutableStateOf(existing?.title.orEmpty()) }
    var body by remember { mutableStateOf(existing?.body.orEmpty()) }
    var priority by remember { mutableStateOf(existing?.priority ?: "normal") }
    var pinned by remember { mutableStateOf(existing?.pinned ?: false) }
    var visibility by remember { mutableStateOf(existing?.visibility ?: "all") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "New Announcement" else "Edit Announcement") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Headline") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = body,
                    onValueChange = { body = it },
                    label = { Text("Announcement Details") },
                    minLines = 3,
                    maxLines = 6,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Priority", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("normal", "important", "urgent").forEach { opt ->
                        FilterChip(
                            selected = priority == opt,
                            onClick = { priority = opt },
                            label = { Text(opt.replaceFirstChar { it.uppercase() }) }
                        )
                    }
                }
                Text("Visibility", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        selected = visibility == "all",
                        onClick = { visibility = "all" },
                        label = { Text("All Users") }
                    )
                    FilterChip(
                        selected = visibility == "admin_only",
                        onClick = { visibility = "admin_only" },
                        label = { Text("Admin Draft") }
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = pinned, onCheckedChange = { pinned = it })
                    Text("Pin to top of User Dashboard")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(existing?.id, title, body, priority, pinned, visibility)
                    onDismiss()
                },
                enabled = title.isNotBlank() && body.isNotBlank()
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun AdminRecordEditorDialog(
    existing: SeptisRecord?,
    onDismiss: () -> Unit,
    onSave: (id: String?, title: String, content: String, category: String, status: String, visibility: String, isProtected: Boolean) -> Unit
) {
    var title by remember { mutableStateOf(existing?.title.orEmpty()) }
    var content by remember { mutableStateOf(existing?.content.orEmpty()) }
    var category by remember { mutableStateOf(existing?.category ?: "share") }
    var status by remember { mutableStateOf(existing?.status ?: "published") }
    var visibility by remember { mutableStateOf(existing?.visibility ?: "all") }
    var isProtected by remember { mutableStateOf(existing?.isProtected ?: true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Add SEPTIS Record" else "Edit Record") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
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
                    label = { Text("Content") },
                    minLines = 3,
                    maxLines = 6,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Category", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("chat", "connect", "share", "resource").forEach { cat ->
                        FilterChip(
                            selected = category == cat,
                            onClick = { category = cat },
                            label = { Text(cat.replaceFirstChar { it.uppercase() }) }
                        )
                    }
                }
                Text("Visibility & Status", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        selected = visibility == "all",
                        onClick = { visibility = "all" },
                        label = { Text("Public") }
                    )
                    FilterChip(
                        selected = visibility == "admin_only",
                        onClick = { visibility = "admin_only" },
                        label = { Text("Admin Only") }
                    )
                    FilterChip(
                        selected = status == "archived",
                        onClick = { status = if (status == "archived") "published" else "archived" },
                        label = { Text("Archived") }
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Protect Record (Lock from user edits)", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = isProtected, onCheckedChange = { isProtected = it })
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(existing?.id, title, content, category, status, visibility, isProtected)
                    onDismiss()
                },
                enabled = title.isNotBlank() && content.isNotBlank()
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun RotateAdminCodeDialog(
    onDismiss: () -> Unit,
    onRotate: (currentCode: String, newCode: String) -> Unit
) {
    var currentCode by remember { mutableStateOf("") }
    var newCode by remember { mutableStateOf("") }
    var confirmCode by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rotate Admin Access Code") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Enter your current confidential code and choose a new code (min 6 chars).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = currentCode,
                    onValueChange = { currentCode = it },
                    label = { Text("Current Access Code") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = newCode,
                    onValueChange = { newCode = it },
                    label = { Text("New Access Code") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = confirmCode,
                    onValueChange = { confirmCode = it },
                    label = { Text("Confirm New Code") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(2.dp))
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onRotate(currentCode, newCode)
                    onDismiss()
                },
                enabled = currentCode.isNotBlank() && newCode.length >= 6 && newCode == confirmCode
            ) {
                Text("Update Secret")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
