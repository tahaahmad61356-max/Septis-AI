package com.example.ui.screens

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.data.model.ChatMessage
import com.example.data.model.Conversation
import com.example.data.model.PhoneDirectoryEntry
import com.example.data.model.SavedContact
import com.example.data.remote.MediaBlobService
import com.example.media.CameraCaptureOverlay
import com.example.media.VoiceRecorderManager
import com.example.ui.components.BlobImagePreview
import com.example.ui.components.ChatMessageBubble
import com.example.ui.components.SeptisBrandAvatar
import com.example.ui.components.WhatsAppComposer
import com.example.ui.components.formatLastSeen
import com.example.ui.theme.LocalSeptisChatColors
import com.example.ui.viewmodel.ChatViewModel
import com.example.ui.viewmodel.HomeViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatDetailScreen(
    chatViewModel: ChatViewModel,
    homeViewModel: HomeViewModel,
    mediaService: MediaBlobService,
    onBack: () -> Unit,
    onStartVoiceCall: (uid: String, name: String, phone: String?) -> Unit,
    onStartVideoCall: (uid: String, name: String, phone: String?) -> Unit
) {
    BackHandler { onBack() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val chatColors = LocalSeptisChatColors.current

    val conversation by chatViewModel.conversation.collectAsStateWithLifecycle()
    val messages by chatViewModel.messages.collectAsStateWithLifecycle()
    val account by chatViewModel.account.collectAsStateWithLifecycle()
    val allPrefs by chatViewModel.allPrefs.collectAsStateWithLifecycle()
    val composerText by chatViewModel.composerText.collectAsStateWithLifecycle()
    val replyTo by chatViewModel.replyTo.collectAsStateWithLifecycle()
    val editingMessage by chatViewModel.editingMessage.collectAsStateWithLifecycle()
    val uploadProgress by chatViewModel.uploadProgress.collectAsStateWithLifecycle()
    val inChatSearch by chatViewModel.inChatSearch.collectAsStateWithLifecycle()
    val feedback by chatViewModel.feedback.collectAsStateWithLifecycle()
    val savedContacts by homeViewModel.savedContacts.collectAsStateWithLifecycle()
    val conversations by homeViewModel.conversations.collectAsStateWithLifecycle()

    val voiceManager = remember(context) { VoiceRecorderManager(context.applicationContext) }
    val isRecording by voiceManager.isRecording.collectAsStateWithLifecycle()
    val recordingDurationMs by voiceManager.recordingDurationMs.collectAsStateWithLifecycle()
    val liveWaveform by voiceManager.liveAmplitudes.collectAsStateWithLifecycle()
    val playbackState by voiceManager.playbackState.collectAsStateWithLifecycle()

    DisposableEffect(Unit) {
        onDispose { voiceManager.releaseAll() }
    }

    LaunchedEffect(allPrefs) {
        chatViewModel.loadInitialDraftIfAny(allPrefs)
    }

    val listState = rememberLazyListState()
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            chatViewModel.markVisibleMessagesRead(messages)
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(feedback) {
        val msg = feedback
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            chatViewModel.clearFeedback()
        }
    }

    var showOverflowMenu by remember { mutableStateOf(false) }
    var showSearchBar by remember { mutableStateOf(false) }
    var showCameraOverlay by remember { mutableStateOf(false) }
    var showGroupInfoDialog by remember { mutableStateOf(false) }
    var showReportDialog by remember { mutableStateOf(false) }
    var forwardMessage by remember { mutableStateOf<ChatMessage?>(null) }
    var fullScreenImageBlobId by remember { mutableStateOf<String?>(null) }
    var pendingMediaPreview by remember { mutableStateOf<Pair<Uri, String>?>(null) }

    val conv = conversation
    val currentUid = chatViewModel.currentUid
    val peerUid = conv?.participantUids?.firstOrNull { it != currentUid }
    val peerContact: SavedContact? = peerUid?.let { uid -> savedContacts.firstOrNull { it.contactUid == uid } }
    val peerDirectory by chatViewModel.peerDirectory.collectAsStateWithLifecycle()

    LaunchedEffect(peerContact?.phoneE164) {
        chatViewModel.loadPeerDirectory(peerContact?.phoneE164)
    }

    val peerName = when {
        conv?.type == "group" -> conv.title.ifBlank { "Group" }
        !peerContact?.localDeviceName.isNullOrBlank() -> peerContact?.localDeviceName!!
        !peerContact?.displayName.isNullOrBlank() -> peerContact?.displayName!!
        else -> conv?.title?.ifBlank { "SEPTIS Chat" } ?: "SEPTIS Chat"
    }
    val myName = account?.displayName?.ifBlank { account?.phoneE164 ?: "SEPTIS User" } ?: "SEPTIS User"

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) pendingMediaPreview = uri to "image"
    }
    val videoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) pendingMediaPreview = uri to "video"
    }
    val documentPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) pendingMediaPreview = uri to "document"
    }
    val audioPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) pendingMediaPreview = uri to "audio"
    }

    val cameraPermLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) showCameraOverlay = true
    }
    val micPermLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) voiceManager.startRecording(scope)
    }

    if (showCameraOverlay) {
        CameraCaptureOverlay(
            onCaptured = { uri ->
                showCameraOverlay = false
                pendingMediaPreview = uri to "image"
            },
            onDismiss = { showCameraOverlay = false },
            onError = { err ->
                showCameraOverlay = false
                chatViewModel.showFeedback(err)
            }
        )
        return
    }

    if (pendingMediaPreview != null) {
        val (uri, kind) = pendingMediaPreview!!
        var caption by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { pendingMediaPreview = null },
            title = { Text("Preview ${kind.replaceFirstChar { it.uppercase() }}") },
            text = {
                Column {
                    if (kind == "image") {
                        AsyncImage(
                            model = uri,
                            contentDescription = "Selected photo preview",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp)
                                .clip(RoundedCornerShape(12.dp))
                        )
                    } else {
                        Text("Ready to upload selected $kind file.")
                    }
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = caption,
                        onValueChange = { caption = it },
                        label = { Text("Add a caption (optional)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        chatViewModel.sendMediaAttachment(context, myName, uri, kind, caption)
                        pendingMediaPreview = null
                    },
                    modifier = Modifier.testTag("confirm_send_media_button")
                ) { Text("Send") }
            },
            dismissButton = {
                TextButton(onClick = { pendingMediaPreview = null }) { Text("Cancel") }
            }
        )
    }

    if (fullScreenImageBlobId != null) {
        AlertDialog(
            onDismissRequest = { fullScreenImageBlobId = null },
            title = { Text("Photo") },
            text = {
                BlobImagePreview(
                    blobId = fullScreenImageBlobId!!,
                    mediaService = mediaService,
                    contentDescription = "Full photo",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(340.dp)
                        .clip(RoundedCornerShape(12.dp))
                )
            },
            confirmButton = {
                TextButton(onClick = { fullScreenImageBlobId = null }) { Text("Close") }
            }
        )
    }

    if (forwardMessage != null) {
        AlertDialog(
            onDismissRequest = { forwardMessage = null },
            title = { Text("Forward message to...") },
            text = {
                LazyColumn(modifier = Modifier.height(260.dp)) {
                    items(conversations.filter { it.id != chatViewModel.conversationId }, key = { it.id }) { target ->
                        val targetLabel = if (target.type == "group") {
                            target.title
                        } else {
                            val pid = target.participantUids.firstOrNull { it != currentUid }
                            savedContacts.firstOrNull { it.contactUid == pid }?.displayName ?: target.title
                        }
                        Text(
                            text = targetLabel.ifBlank { "SEPTIS Chat" },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    chatViewModel.forwardMessageTo(target, forwardMessage!!, myName)
                                    forwardMessage = null
                                }
                                .padding(vertical = 12.dp, horizontal = 8.dp)
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { forwardMessage = null }) { Text("Cancel") }
            }
        )
    }

    if (showReportDialog && peerUid != null) {
        var reason by remember { mutableStateOf("Spam or Harassment") }
        var details by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showReportDialog = false },
            title = { Text("Report $peerName") },
            text = {
                Column {
                    OutlinedTextField(
                        value = reason,
                        onValueChange = { reason = it },
                        label = { Text("Reason") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = details,
                        onValueChange = { details = it },
                        label = { Text("Details") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    chatViewModel.reportUser(peerUid, peerContact?.phoneE164, reason, details)
                    showReportDialog = false
                }) { Text("Submit Report") }
            },
            dismissButton = {
                TextButton(onClick = { showReportDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showGroupInfoDialog && conv != null && conv.type == "group") {
        GroupInfoDialog(
            group = conv,
            savedContacts = savedContacts,
            currentUid = currentUid,
            homeViewModel = homeViewModel,
            onDismiss = { showGroupInfoDialog = false },
            onGroupRemoved = {
                showGroupInfoDialog = false
                onBack()
            }
        )
    }

    val filteredMessages = remember(messages, inChatSearch) {
        if (inChatSearch.isBlank()) messages
        else messages.filter { it.text.contains(inChatSearch, ignoreCase = true) }
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
                        IconButton(onClick = onBack, modifier = Modifier.testTag("chat_back_button")) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                        }
                    },
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable {
                                if (conv?.type == "group") showGroupInfoDialog = true
                            }
                        ) {
                            SeptisBrandAvatar(
                                name = peerName,
                                photoBlobId = peerContact?.photoBlobId ?: conv?.photoBlobId,
                                mediaService = mediaService,
                                size = 38.dp,
                                isGroup = conv?.type == "group"
                            )
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = peerName,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                val peerTyping = conv?.typingUids?.any { it != currentUid } == true
                                val subtitle = when {
                                    peerTyping -> "typing..."
                                    conv?.type == "group" -> "${conv.participantUids.size} participants"
                                    peerDirectory != null -> formatLastSeen(
                                        peerDirectory!!.isOnline,
                                        peerDirectory!!.lastSeen,
                                        peerDirectory!!.privacyLastSeen
                                    )
                                    else -> peerContact?.phoneE164.orEmpty()
                                }
                                if (subtitle.isNotBlank()) {
                                    Text(
                                        text = subtitle,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    },
                    actions = {
                        if (conv?.type == "direct" && peerUid != null) {
                            IconButton(
                                onClick = { onStartVideoCall(peerUid, peerName, peerContact?.phoneE164) },
                                modifier = Modifier.testTag("chat_video_call_button")
                            ) {
                                Icon(Icons.Rounded.Videocam, contentDescription = "Video call")
                            }
                            IconButton(
                                onClick = { onStartVoiceCall(peerUid, peerName, peerContact?.phoneE164) },
                                modifier = Modifier.testTag("chat_voice_call_button")
                            ) {
                                Icon(Icons.Rounded.Call, contentDescription = "Voice call")
                            }
                        }
                        Box {
                            IconButton(onClick = { showOverflowMenu = true }) {
                                Icon(Icons.Rounded.MoreVert, contentDescription = "Chat options")
                            }
                            DropdownMenu(
                                expanded = showOverflowMenu,
                                onDismissRequest = { showOverflowMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Search messages") },
                                    onClick = { showOverflowMenu = false; showSearchBar = !showSearchBar }
                                )
                                if (conv?.type == "group") {
                                    DropdownMenuItem(
                                        text = { Text("Group info") },
                                        onClick = { showOverflowMenu = false; showGroupInfoDialog = true }
                                    )
                                }
                                if (peerUid != null && conv?.type == "direct") {
                                    DropdownMenuItem(
                                        text = { Text("Block user") },
                                        onClick = {
                                            showOverflowMenu = false
                                            chatViewModel.blockUser(peerUid, peerContact?.phoneE164, peerName)
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Report user") },
                                        onClick = { showOverflowMenu = false; showReportDialog = true }
                                    )
                                }
                            }
                        }
                    }
                )
                if (showSearchBar) {
                    OutlinedTextField(
                        value = inChatSearch,
                        onValueChange = chatViewModel::updateInChatSearch,
                        placeholder = { Text("Search in conversation") },
                        singleLine = true,
                        trailingIcon = {
                            IconButton(onClick = {
                                chatViewModel.updateInChatSearch("")
                                showSearchBar = false
                            }) {
                                Icon(Icons.Rounded.Close, contentDescription = "Close search")
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
                .navigationBarsPadding()
                .background(chatColors.chatBackground)
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(filteredMessages, key = { it.id }) { msg ->
                    ChatMessageBubble(
                        message = msg,
                        isMine = msg.senderUid == currentUid,
                        isGroupChat = conv?.type == "group",
                        totalParticipants = conv?.participantUids?.size ?: 2,
                        mediaService = mediaService,
                        voiceManager = voiceManager,
                        playbackState = playbackState,
                        onReply = { chatViewModel.setReplyTarget(msg) },
                        onForward = { forwardMessage = msg },
                        onCopy = {
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(ClipData.newPlainText("SEPTIS", msg.text))
                        },
                        onStar = { chatViewModel.starMessage(msg) },
                        onEdit = { chatViewModel.startEditMessage(msg) },
                        onDeleteForMe = { chatViewModel.deleteForMe(msg.id) },
                        onDeleteForEveryone = { chatViewModel.deleteForEveryone(msg.id) },
                        onPreviewImageBlob = { blobId -> fullScreenImageBlobId = blobId },
                        onFileFeedback = { chatViewModel.showFeedback(it) }
                    )
                }
            }

            val canSend = conv?.type != "group" || !conv.onlyAdminsCanSend || currentUid in conv.adminUids
            if (!canSend) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Only group administrators can send messages.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(14.dp)
                    )
                }
            } else {
                WhatsAppComposer(
                    text = composerText,
                    replyTo = replyTo,
                    editingMessage = editingMessage,
                    uploadProgress = uploadProgress,
                    isRecording = isRecording,
                    recordingDurationMs = recordingDurationMs,
                    liveWaveform = liveWaveform,
                    onTextChanged = chatViewModel::onComposerTextChanged,
                    onSendText = { chatViewModel.sendCurrentText(myName) },
                    onSendSticker = { chatViewModel.sendSticker(myName, it) },
                    onCancelReplyOrEdit = chatViewModel::cancelEditOrReply,
                    onPickPhoto = {
                        photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                    onPickVideo = {
                        videoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
                    },
                    onPickDocument = {
                        documentPicker.launch(arrayOf("application/pdf", "application/*", "text/*"))
                    },
                    onPickAudio = {
                        audioPicker.launch(arrayOf("audio/*"))
                    },
                    onOpenCamera = {
                        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                            PackageManager.PERMISSION_GRANTED
                        if (granted) showCameraOverlay = true
                        else cameraPermLauncher.launch(Manifest.permission.CAMERA)
                    },
                    onStartRecording = {
                        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                            PackageManager.PERMISSION_GRANTED
                        if (granted) voiceManager.startRecording(scope)
                        else micPermLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    },
                    onCancelRecording = { voiceManager.cancelRecording() },
                    onFinishAndSendRecording = {
                        voiceManager.finishRecording().onSuccess { rec ->
                            chatViewModel.sendVoiceNote(myName, rec.bytes, rec.durationMs, rec.waveform)
                        }
                    }
                )
            }
        }
    }
}
