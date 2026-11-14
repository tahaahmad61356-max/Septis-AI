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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.CallMade
import androidx.compose.material.icons.automirrored.rounded.CallMissed
import androidx.compose.material.icons.automirrored.rounded.CallReceived
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.remote.MediaBlobService
import com.example.ui.components.SeptisBrandAvatar
import com.example.ui.components.formatDurationSeconds
import com.example.ui.components.formatRelativeDate
import com.example.ui.viewmodel.HomeViewModel

@Composable
fun CallsTabScreen(
    homeViewModel: HomeViewModel,
    mediaService: MediaBlobService,
    onStartVoiceCall: (uid: String, name: String, phone: String?) -> Unit,
    onStartVideoCall: (uid: String, name: String, phone: String?) -> Unit
) {
    val calls by homeViewModel.calls.collectAsStateWithLifecycle()
    val savedContacts by homeViewModel.savedContacts.collectAsStateWithLifecycle()
    val searchQuery by homeViewModel.searchQuery.collectAsStateWithLifecycle()
    val currentUid = homeViewModel.currentUid

    val filteredCalls = remember(calls, searchQuery) {
        if (searchQuery.isBlank()) calls
        else calls.filter {
            it.callerName.contains(searchQuery, ignoreCase = true) ||
                it.calleeName.contains(searchQuery, ignoreCase = true)
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 90.dp)
    ) {
        item {
            Text(
                text = "Recent Calls (${filteredCalls.size})",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(8.dp))
        }

        if (filteredCalls.isEmpty()) {
            item {
                Text(
                    "No recent calls yet. Tap a contact below or use the call buttons inside any chat.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            items(filteredCalls, key = { it.id }) { call ->
                val isOutgoing = call.callerUid == currentUid
                val peerUid = if (isOutgoing) call.calleeUid else call.callerUid
                val peerName = if (isOutgoing) call.calleeName else call.callerName
                val peerPhone = if (isOutgoing) call.calleePhone else call.callerPhone
                val isMissed = !isOutgoing && (call.status == "missed" || call.status == "rejected")

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SeptisBrandAvatar(
                        name = peerName,
                        mediaService = mediaService,
                        size = 46.dp
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = peerName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isMissed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = when {
                                    isMissed -> Icons.AutoMirrored.Rounded.CallMissed
                                    isOutgoing -> Icons.AutoMirrored.Rounded.CallMade
                                    else -> Icons.AutoMirrored.Rounded.CallReceived
                                },
                                contentDescription = null,
                                tint = if (isMissed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            val dur = if (call.durationSeconds > 0) " • ${formatDurationSeconds(call.durationSeconds)}" else ""
                            Text(
                                text = "${formatRelativeDate(call.createdAt)}$dur",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    IconButton(onClick = {
                        if (call.callType == "video") onStartVideoCall(peerUid, peerName, peerPhone)
                        else onStartVoiceCall(peerUid, peerName, peerPhone)
                    }) {
                        Icon(
                            if (call.callType == "video") Icons.Rounded.Videocam else Icons.Rounded.Call,
                            contentDescription = "Call back",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.14f))
            }
        }

        if (savedContacts.isNotEmpty()) {
            item {
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "Contacts Ready for Calling",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(8.dp))
            }
            items(savedContacts, key = { "call_${it.contactUid}" }) { contact ->
                val label = contact.localDeviceName ?: contact.displayName
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SeptisBrandAvatar(
                        name = label,
                        photoBlobId = contact.photoBlobId,
                        mediaService = mediaService,
                        size = 44.dp
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Text(contact.phoneE164, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Row {
                        IconButton(
                            onClick = { onStartVoiceCall(contact.contactUid, label, contact.phoneE164) },
                            modifier = Modifier.testTag("call_voice_${contact.contactUid}")
                        ) {
                            Icon(Icons.Rounded.Call, contentDescription = "Voice call $label", tint = MaterialTheme.colorScheme.primary)
                        }
                        IconButton(
                            onClick = { onStartVideoCall(contact.contactUid, label, contact.phoneE164) },
                            modifier = Modifier.testTag("call_video_${contact.contactUid}")
                        ) {
                            Icon(Icons.Rounded.Videocam, contentDescription = "Video call $label", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }
}
