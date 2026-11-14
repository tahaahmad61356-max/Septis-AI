package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Contacts
import androidx.compose.material.icons.rounded.GroupAdd
import androidx.compose.material.icons.rounded.PersonSearch
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.remote.MediaBlobService
import com.example.ui.components.CountryCodePickerDialog
import com.example.ui.components.SeptisBrandAvatar
import com.example.ui.components.ShareInviteHelper
import com.example.ui.viewmodel.HomeViewModel
import com.example.ui.viewmodel.NewChatViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewChatScreen(
    newChatViewModel: NewChatViewModel,
    homeViewModel: HomeViewModel,
    mediaService: MediaBlobService,
    onBack: () -> Unit,
    onConversationOpened: (String) -> Unit
) {
    BackHandler { onBack() }
    val context = LocalContext.current

    val selectedCountry by newChatViewModel.selectedCountry.collectAsStateWithLifecycle()
    val phoneInput by newChatViewModel.phoneInput.collectAsStateWithLifecycle()
    val lookupResult by newChatViewModel.lookupResult.collectAsStateWithLifecycle()
    val isSearching by newChatViewModel.isSearching.collectAsStateWithLifecycle()
    val isSyncing by newChatViewModel.isSyncingContacts.collectAsStateWithLifecycle()
    val statusMessage by newChatViewModel.statusMessage.collectAsStateWithLifecycle()
    val savedContacts by homeViewModel.savedContacts.collectAsStateWithLifecycle()

    var showCountryDialog by remember { mutableStateOf(false) }
    var showCreateGroupDialog by remember { mutableStateOf(false) }

    val contactsPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            newChatViewModel.syncContacts(context)
        }
    }

    if (showCountryDialog) {
        CountryCodePickerDialog(
            onSelect = {
                newChatViewModel.selectCountry(it)
                showCountryDialog = false
            },
            onDismiss = { showCountryDialog = false }
        )
    }

    if (showCreateGroupDialog) {
        CreateGroupDialog(
            savedContacts = savedContacts,
            onCreate = { title, desc, members ->
                homeViewModel.createGroup(title, desc, members) { conv ->
                    showCreateGroupDialog = false
                    onConversationOpened(conv.id)
                }
            },
            onDismiss = { showCreateGroupDialog = false }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                ),
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("new_chat_back_button")) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                title = { Text("New Chat & Contacts", fontWeight = FontWeight.Bold) }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Find user by phone number",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Enter a complete phone number with country code to message any registered SEPTIS user.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(12.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedButton(
                                onClick = { showCountryDialog = true },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.height(54.dp)
                            ) {
                                Text("${selectedCountry.flag} ${selectedCountry.dialCode}")
                                Icon(Icons.Rounded.ArrowDropDown, contentDescription = "Country")
                            }
                            Spacer(Modifier.width(8.dp))
                            OutlinedTextField(
                                value = phoneInput,
                                onValueChange = newChatViewModel::updatePhoneInput,
                                label = { Text("Full phone number") },
                                placeholder = { Text(selectedCountry.example) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("find_phone_input")
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = { newChatViewModel.searchPhoneNumber() },
                            enabled = !isSearching,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("search_phone_button")
                        ) {
                            if (isSearching) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(Icons.Rounded.PersonSearch, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Find on SEPTIS")
                            }
                        }

                        if (!statusMessage.isNullOrBlank()) {
                            Spacer(Modifier.height(10.dp))
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = statusMessage!!,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(10.dp)
                                )
                            }
                        }

                        val result = lookupResult
                        if (result != null) {
                            Spacer(Modifier.height(12.dp))
                            val entry = result.registeredEntry
                            if (entry != null) {
                                Surface(
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        SeptisBrandAvatar(
                                            name = entry.displayName,
                                            photoBlobId = entry.photoBlobId,
                                            mediaService = mediaService,
                                            size = 50.dp,
                                            isOnline = entry.isOnline
                                        )
                                        Spacer(Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                entry.displayName,
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                entry.phoneE164,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            Text(
                                                entry.about,
                                                style = MaterialTheme.typography.bodySmall,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        Button(
                                            onClick = {
                                                newChatViewModel.startChatWithDirectoryEntry(entry) { conv ->
                                                    onConversationOpened(conv.id)
                                                }
                                            },
                                            modifier = Modifier.testTag("start_found_chat_button")
                                        ) { Text("Chat") }
                                    }
                                }
                            } else {
                                OutlinedButton(
                                    onClick = { ShareInviteHelper.shareSeptisInvite(context, result.phoneE164) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .testTag("invite_unregistered_button")
                                ) {
                                    Icon(Icons.Rounded.Share, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Invite ${result.phoneE164} to SEPTIS")
                                }
                            }
                        }
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            val granted = ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.READ_CONTACTS
                            ) == PackageManager.PERMISSION_GRANTED
                            if (granted) newChatViewModel.syncContacts(context)
                            else contactsPermLauncher.launch(Manifest.permission.READ_CONTACTS)
                        },
                        enabled = !isSyncing,
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .testTag("sync_contacts_button")
                    ) {
                        Icon(Icons.Rounded.Contacts, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text(if (isSyncing) "Syncing..." else "Sync Contacts")
                    }
                    OutlinedButton(
                        onClick = { showCreateGroupDialog = true },
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .testTag("new_group_button")
                    ) {
                        Icon(Icons.Rounded.GroupAdd, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("New Group")
                    }
                }
            }

            item {
                Text(
                    text = "Contacts on SEPTIS (${savedContacts.size})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            if (savedContacts.isEmpty()) {
                item {
                    Text(
                        text = "No synced contacts yet. Search any phone number above or tap Sync Contacts.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                items(savedContacts, key = { it.contactUid }) { contact ->
                    val label = contact.localDeviceName?.takeIf { it.isNotBlank() } ?: contact.displayName
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                newChatViewModel.startChatWithSavedContact(contact) { conv ->
                                    onConversationOpened(conv.id)
                                }
                            }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SeptisBrandAvatar(
                            name = label,
                            photoBlobId = contact.photoBlobId,
                            mediaService = mediaService,
                            size = 46.dp
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            Text(
                                text = contact.about?.ifBlank { contact.phoneE164 } ?: contact.phoneE164,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                }
            }
        }
    }
}
