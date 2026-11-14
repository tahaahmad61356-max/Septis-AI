package com.example.ui.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.ChatMessage
import com.example.data.model.ChatPreference
import com.example.data.model.Conversation
import com.example.data.model.PhoneDirectoryEntry
import com.example.data.model.UserAccount
import com.example.data.remote.MediaBlobService
import com.example.data.repository.MessagingRepository
import com.example.data.repository.SocialCallRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ChatViewModel(
    private val messagingRepo: MessagingRepository,
    private val socialCallRepo: SocialCallRepository,
    private val mediaService: MediaBlobService,
    val conversationId: String,
    val currentUid: String
) : ViewModel() {

    val conversation: StateFlow<Conversation?> = messagingRepo.observeConversation(conversationId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val messages: StateFlow<List<ChatMessage>> = messagingRepo.observeMessages(conversationId, currentUid)
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val account: StateFlow<UserAccount?> = messagingRepo.observeMyAccount(currentUid)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val allPrefs: StateFlow<Map<String, ChatPreference>> = messagingRepo.observeChatPreferences(currentUid)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    private val _composerText = MutableStateFlow("")
    val composerText: StateFlow<String> = _composerText.asStateFlow()

    private val _replyTo = MutableStateFlow<ChatMessage?>(null)
    val replyTo: StateFlow<ChatMessage?> = _replyTo.asStateFlow()

    private val _editingMessage = MutableStateFlow<ChatMessage?>(null)
    val editingMessage: StateFlow<ChatMessage?> = _editingMessage.asStateFlow()

    private val _uploadProgress = MutableStateFlow<Float?>(null)
    val uploadProgress: StateFlow<Float?> = _uploadProgress.asStateFlow()

    private val _inChatSearch = MutableStateFlow("")
    val inChatSearch: StateFlow<String> = _inChatSearch.asStateFlow()

    private val _feedback = MutableStateFlow<String?>(null)
    val feedback: StateFlow<String?> = _feedback.asStateFlow()

    private val _peerDirectory = MutableStateFlow<PhoneDirectoryEntry?>(null)
    val peerDirectory: StateFlow<PhoneDirectoryEntry?> = _peerDirectory.asStateFlow()

    private var typingDebounceJob: Job? = null
    private var draftLoaded = false

    fun loadPeerDirectory(phoneE164: String?) {
        if (phoneE164.isNullOrBlank()) return
        viewModelScope.launch {
            _peerDirectory.value = messagingRepo.lookupDirectoryByPhoneExact(phoneE164)
        }
    }

    fun showFeedback(msg: String) {
        _feedback.value = msg
    }

    fun loadInitialDraftIfAny(prefs: Map<String, ChatPreference>) {
        if (!draftLoaded) {
            val savedDraft = prefs[conversationId]?.draftText.orEmpty()
            if (savedDraft.isNotBlank()) {
                _composerText.value = savedDraft
            }
            draftLoaded = true
        }
    }

    fun onComposerTextChanged(text: String) {
        _composerText.value = text
        typingDebounceJob?.cancel()
        typingDebounceJob = viewModelScope.launch {
            messagingRepo.setTypingStatus(conversationId, text.isNotBlank())
            delay(500L)
            messagingRepo.updateChatPreference(conversationId, draftText = text)
            if (text.isNotBlank()) {
                delay(2200L)
                messagingRepo.setTypingStatus(conversationId, false)
            }
        }
    }

    fun setReplyTarget(msg: ChatMessage?) {
        _editingMessage.value = null
        _replyTo.value = msg
    }

    fun startEditMessage(msg: ChatMessage) {
        _replyTo.value = null
        _editingMessage.value = msg
        _composerText.value = msg.text
    }

    fun cancelEditOrReply() {
        _replyTo.value = null
        if (_editingMessage.value != null) {
            _editingMessage.value = null
            _composerText.value = ""
        }
    }

    fun updateInChatSearch(query: String) {
        _inChatSearch.value = query
    }

    fun clearFeedback() {
        _feedback.value = null
    }

    fun markVisibleMessagesRead(msgs: List<ChatMessage>) {
        val sendRead = account.value?.readReceiptsEnabled ?: true
        viewModelScope.launch {
            messagingRepo.markMessagesDeliveredAndRead(conversationId, msgs, sendRead)
            messagingRepo.updateChatPreference(conversationId, unreadCount = 0)
        }
    }

    fun sendCurrentText(senderName: String) {
        val conv = conversation.value ?: return
        val text = _composerText.value.trim()
        if (text.isEmpty()) return

        val editing = _editingMessage.value
        if (editing != null) {
            viewModelScope.launch {
                messagingRepo.editMessage(conversationId, editing.id, text)
                    .onSuccess {
                        _editingMessage.value = null
                        _composerText.value = ""
                        messagingRepo.updateChatPreference(conversationId, draftText = "")
                    }.onFailure { _feedback.value = it.localizedMessage }
            }
            return
        }

        val reply = _replyTo.value
        _composerText.value = ""
        _replyTo.value = null
        viewModelScope.launch {
            messagingRepo.updateChatPreference(conversationId, draftText = "")
            val res = messagingRepo.sendMessage(
                conversation = conv,
                senderName = senderName,
                type = "text",
                text = text,
                replyTo = reply
            )
            res.onSuccess {
                notifyPeer(conv, senderName, text)
            }.onFailure {
                _feedback.value = it.localizedMessage ?: "Failed to send message"
            }
        }
    }

    fun sendSticker(senderName: String, sticker: String) {
        val conv = conversation.value ?: return
        viewModelScope.launch {
            messagingRepo.sendMessage(
                conversation = conv,
                senderName = senderName,
                type = "sticker",
                text = sticker,
                replyTo = _replyTo.value
            ).onSuccess {
                _replyTo.value = null
                notifyPeer(conv, senderName, sticker)
            }.onFailure { _feedback.value = it.localizedMessage }
        }
    }

    fun sendVoiceNote(
        senderName: String,
        bytes: ByteArray,
        durationMs: Long,
        waveform: List<Int>
    ) {
        val conv = conversation.value ?: return
        viewModelScope.launch {
            _uploadProgress.value = 0.1f
            val upload = mediaService.uploadBytes(
                bytes = bytes,
                fileName = "voice_${System.currentTimeMillis()}.m4a",
                mimeType = "audio/mp4",
                scope = "participants",
                allowedUids = conv.participantUids,
                onProgress = { _uploadProgress.value = it }
            )
            _uploadProgress.value = null
            upload.onSuccess { uploaded ->
                messagingRepo.sendMessage(
                    conversation = conv,
                    senderName = senderName,
                    type = "voice",
                    text = "",
                    mediaBlobId = uploaded.blobId,
                    mediaMimeType = uploaded.mimeType,
                    mediaFileName = uploaded.fileName,
                    mediaSizeBytes = uploaded.sizeBytes,
                    mediaDurationMs = durationMs,
                    waveform = waveform,
                    replyTo = _replyTo.value
                ).onSuccess {
                    _replyTo.value = null
                    notifyPeer(conv, senderName, "\uD83C\uDF99\uFE0F Voice message")
                }
            }.onFailure {
                _feedback.value = it.localizedMessage ?: "Voice upload failed"
            }
        }
    }

    fun sendMediaAttachment(
        context: Context,
        senderName: String,
        uri: Uri,
        messageType: String,
        caption: String = ""
    ) {
        val conv = conversation.value ?: return
        viewModelScope.launch {
            _uploadProgress.value = 0.08f
            val upload = if (messageType == "image") {
                mediaService.uploadImageUri(
                    context = context,
                    uri = uri,
                    scope = "participants",
                    allowedUids = conv.participantUids,
                    onProgress = { _uploadProgress.value = it }
                )
            } else {
                val fallbackMime = when (messageType) {
                    "video" -> "video/mp4"
                    "audio" -> "audio/mpeg"
                    else -> "application/pdf"
                }
                mediaService.uploadFileUri(
                    context = context,
                    uri = uri,
                    fallbackMime = fallbackMime,
                    scope = "participants",
                    allowedUids = conv.participantUids,
                    onProgress = { _uploadProgress.value = it }
                )
            }
            _uploadProgress.value = null

            upload.onSuccess { uploaded ->
                messagingRepo.sendMessage(
                    conversation = conv,
                    senderName = senderName,
                    type = messageType,
                    text = caption,
                    mediaBlobId = uploaded.blobId,
                    mediaMimeType = uploaded.mimeType,
                    mediaFileName = uploaded.fileName,
                    mediaSizeBytes = uploaded.sizeBytes,
                    replyTo = _replyTo.value
                ).onSuccess {
                    _replyTo.value = null
                    notifyPeer(conv, senderName, caption.ifBlank { "[${messageType.uppercase()}]" })
                }.onFailure { _feedback.value = it.localizedMessage }
            }.onFailure {
                _feedback.value = it.localizedMessage ?: "Attachment upload failed"
            }
        }
    }

    fun forwardMessageTo(targetConv: Conversation, original: ChatMessage, senderName: String) {
        viewModelScope.launch {
            messagingRepo.sendMessage(
                conversation = targetConv,
                senderName = senderName,
                type = original.type,
                text = original.text,
                mediaBlobId = original.mediaBlobId,
                mediaMimeType = original.mediaMimeType,
                mediaFileName = original.mediaFileName,
                mediaSizeBytes = original.mediaSizeBytes,
                mediaDurationMs = original.mediaDurationMs,
                waveform = original.waveform,
                forwardedFrom = original.senderName
            ).onSuccess {
                _feedback.value = "Forwarded to ${targetConv.title.ifBlank { "chat" }}"
            }.onFailure { _feedback.value = it.localizedMessage }
        }
    }

    fun deleteForMe(messageId: String) {
        viewModelScope.launch {
            messagingRepo.deleteMessageForMe(conversationId, messageId)
                .onFailure { _feedback.value = it.localizedMessage }
        }
    }

    fun deleteForEveryone(messageId: String) {
        viewModelScope.launch {
            messagingRepo.deleteMessageForEveryone(conversationId, messageId)
                .onFailure { _feedback.value = it.localizedMessage }
        }
    }

    fun starMessage(msg: ChatMessage) {
        viewModelScope.launch {
            messagingRepo.starMessage(msg)
                .onSuccess { _feedback.value = "Message starred" }
                .onFailure { _feedback.value = it.localizedMessage }
        }
    }

    fun blockUser(peerUid: String, peerPhone: String?, peerName: String) {
        viewModelScope.launch {
            messagingRepo.blockUser(peerUid, peerPhone, peerName)
                .onSuccess { _feedback.value = "Blocked $peerName" }
                .onFailure { _feedback.value = it.localizedMessage }
        }
    }

    fun reportUser(peerUid: String, peerPhone: String?, reason: String, details: String) {
        viewModelScope.launch {
            socialCallRepo.submitReport(peerUid, peerPhone, reason, details)
                .onSuccess { _feedback.value = "Report submitted for admin review" }
                .onFailure { _feedback.value = it.localizedMessage }
        }
    }

    private fun notifyPeer(conv: Conversation, senderName: String, preview: String) {
        viewModelScope.launch {
            conv.participantUids.filter { it != currentUid }.forEach { peerUid ->
                socialCallRepo.sendNotification(
                    recipientUid = peerUid,
                    senderName = senderName,
                    title = if (conv.type == "group") "${conv.title} • $senderName" else senderName,
                    message = preview.take(180),
                    type = if (conv.type == "group") "group" else "message",
                    referenceId = conv.id
                )
            }
        }
    }
}
