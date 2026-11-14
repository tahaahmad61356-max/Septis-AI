package com.example.ui.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.Announcement
import com.example.data.model.AppSettings
import com.example.data.model.BlockedUser
import com.example.data.model.CallSession
import com.example.data.model.ChatPreference
import com.example.data.model.Community
import com.example.data.model.Conversation
import com.example.data.model.NotificationItem
import com.example.data.model.SavedContact
import com.example.data.model.StarredMessage
import com.example.data.model.StatusUpdate
import com.example.data.model.UserAccount
import com.example.data.remote.MediaBlobService
import com.example.data.repository.MessagingRepository
import com.example.data.repository.SocialCallRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class MainTab {
    CHATS,
    UPDATES,
    COMMUNITIES,
    CALLS,
    SETTINGS
}

class HomeViewModel(
    private val messagingRepo: MessagingRepository,
    private val socialCallRepo: SocialCallRepository,
    private val mediaService: MediaBlobService,
    val currentUid: String
) : ViewModel() {

    val account: StateFlow<UserAccount?> = messagingRepo.observeMyAccount(currentUid)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val conversations: StateFlow<List<Conversation>> = messagingRepo.observeConversations(currentUid)
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val chatPrefs: StateFlow<Map<String, ChatPreference>> = messagingRepo.observeChatPreferences(currentUid)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val savedContacts: StateFlow<List<SavedContact>> = messagingRepo.observeSavedContacts(currentUid)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val blockedUsers: StateFlow<List<BlockedUser>> = messagingRepo.observeBlockedUsers(currentUid)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val starredMessages: StateFlow<List<StarredMessage>> = messagingRepo.observeStarredMessages(currentUid)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val communities: StateFlow<List<Community>> = socialCallRepo.observeMyCommunities(currentUid)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val statuses: StateFlow<List<StatusUpdate>> = socialCallRepo.observeActiveStatuses(currentUid)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val calls: StateFlow<List<CallSession>> = socialCallRepo.observeMyCalls(currentUid)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val notifications: StateFlow<List<NotificationItem>> = socialCallRepo.observeNotifications(currentUid)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val announcements: StateFlow<List<Announcement>> = messagingRepo.observePublicAnnouncements()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val globalSettings: StateFlow<AppSettings> = messagingRepo.observeGlobalSettings()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettings())

    private val _selectedTab = MutableStateFlow(MainTab.CHATS)
    val selectedTab: StateFlow<MainTab> = _selectedTab.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _showArchivedChats = MutableStateFlow(false)
    val showArchivedChats: StateFlow<Boolean> = _showArchivedChats.asStateFlow()

    private val _feedback = MutableStateFlow<String?>(null)
    val feedback: StateFlow<String?> = _feedback.asStateFlow()

    private val _uploadProgress = MutableStateFlow<Float?>(null)
    val uploadProgress: StateFlow<Float?> = _uploadProgress.asStateFlow()

    fun selectTab(tab: MainTab) {
        _selectedTab.value = tab
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun toggleShowArchived() {
        _showArchivedChats.value = !_showArchivedChats.value
    }

    fun clearFeedback() {
        _feedback.value = null
    }

    fun togglePinChat(convId: String, current: Boolean) {
        viewModelScope.launch {
            messagingRepo.updateChatPreference(convId, pinned = !current)
        }
    }

    fun toggleArchiveChat(convId: String, current: Boolean) {
        viewModelScope.launch {
            messagingRepo.updateChatPreference(convId, archived = !current)
            _feedback.value = if (!current) "Chat archived" else "Chat unarchived"
        }
    }

    fun toggleMuteChat(convId: String, current: Boolean) {
        viewModelScope.launch {
            messagingRepo.updateChatPreference(convId, muted = !current)
            _feedback.value = if (!current) "Notifications muted" else "Notifications unmuted"
        }
    }

    fun createGroup(
        title: String,
        description: String,
        memberUids: List<String>,
        communityId: String? = null,
        onCreated: (Conversation) -> Unit
    ) {
        viewModelScope.launch {
            val res = messagingRepo.createGroupConversation(
                title = title,
                description = description,
                memberUids = memberUids,
                communityId = communityId
            )
            res.onSuccess { conv ->
                if (communityId != null) {
                    socialCallRepo.addGroupToCommunity(communityId, conv.id, conv.participantUids)
                }
                _feedback.value = "Group \"${conv.title}\" created"
                onCreated(conv)
            }.onFailure {
                _feedback.value = it.localizedMessage ?: "Failed to create group"
            }
        }
    }

    fun createCommunity(
        name: String,
        description: String,
        iconEmoji: String,
        memberUids: List<String>,
        groupIds: List<String>
    ) {
        viewModelScope.launch {
            val res = socialCallRepo.createCommunity(name, description, iconEmoji, memberUids, groupIds)
            res.onSuccess { _feedback.value = "Community \"${it.name}\" created" }
                .onFailure { _feedback.value = it.localizedMessage ?: "Failed to create community" }
        }
    }

    fun deleteCommunity(communityId: String) {
        viewModelScope.launch {
            socialCallRepo.deleteCommunity(communityId)
                .onSuccess { _feedback.value = "Community deleted" }
                .onFailure { _feedback.value = it.localizedMessage }
        }
    }

    fun updateGroupSettings(
        conversationId: String,
        title: String,
        description: String,
        onlyAdminsCanSend: Boolean,
        onlyAdminsCanEditInfo: Boolean
    ) {
        viewModelScope.launch {
            socialCallRepo.updateGroupDetails(
                conversationId, title, description, onlyAdminsCanSend, onlyAdminsCanEditInfo
            ).onSuccess { _feedback.value = "Group updated" }
                .onFailure { _feedback.value = it.localizedMessage }
        }
    }

    fun addGroupMember(conversationId: String, uid: String) {
        viewModelScope.launch {
            socialCallRepo.addGroupMember(conversationId, uid)
                .onSuccess { _feedback.value = "Member added" }
                .onFailure { _feedback.value = it.localizedMessage }
        }
    }

    fun removeGroupMember(conversationId: String, uid: String) {
        viewModelScope.launch {
            socialCallRepo.removeGroupMember(conversationId, uid)
                .onSuccess { _feedback.value = "Member removed" }
                .onFailure { _feedback.value = it.localizedMessage }
        }
    }

    fun toggleGroupAdmin(conversationId: String, uid: String, makeAdmin: Boolean) {
        viewModelScope.launch {
            socialCallRepo.setGroupAdmin(conversationId, uid, makeAdmin)
                .onSuccess { _feedback.value = if (makeAdmin) "Promoted to admin" else "Admin removed" }
                .onFailure { _feedback.value = it.localizedMessage }
        }
    }

    fun leaveGroup(conversationId: String, onDone: () -> Unit) {
        viewModelScope.launch {
            socialCallRepo.leaveGroup(conversationId)
                .onSuccess {
                    _feedback.value = "Left group"
                    onDone()
                }.onFailure { _feedback.value = it.localizedMessage }
        }
    }

    fun deleteGroup(conversationId: String, onDone: () -> Unit) {
        viewModelScope.launch {
            socialCallRepo.deleteGroup(conversationId)
                .onSuccess {
                    _feedback.value = "Group deleted"
                    onDone()
                }.onFailure { _feedback.value = it.localizedMessage }
        }
    }

    fun postTextStatus(text: String, bgHex: String, privacy: String) {
        val acc = account.value ?: return
        if (text.isBlank()) return
        viewModelScope.launch {
            val contactUids = savedContacts.value.map { it.contactUid }
            socialCallRepo.postStatus(
                authorName = acc.displayName,
                authorPhone = acc.phoneE164,
                authorPhotoBlobId = acc.photoBlobId,
                type = "text",
                text = text,
                backgroundColorHex = bgHex,
                mediaBlobId = null,
                mediaMimeType = null,
                privacy = privacy,
                contactUids = contactUids
            ).onSuccess { _feedback.value = "Status shared for 24h" }
                .onFailure { _feedback.value = it.localizedMessage ?: "Failed to post status" }
        }
    }

    fun postMediaStatus(
        context: Context,
        uri: Uri,
        isVideo: Boolean,
        caption: String,
        privacy: String
    ) {
        val acc = account.value ?: return
        val contactUids = savedContacts.value.map { it.contactUid }
        viewModelScope.launch {
            _uploadProgress.value = 0.05f
            val uploadRes = if (isVideo) {
                mediaService.uploadFileUri(
                    context = context,
                    uri = uri,
                    fallbackMime = "video/mp4",
                    scope = "status",
                    allowedUids = contactUids + currentUid,
                    onProgress = { _uploadProgress.value = it }
                )
            } else {
                mediaService.uploadImageUri(
                    context = context,
                    uri = uri,
                    scope = "status",
                    allowedUids = contactUids + currentUid,
                    onProgress = { _uploadProgress.value = it }
                )
            }
            _uploadProgress.value = null

            uploadRes.onSuccess { uploaded ->
                socialCallRepo.postStatus(
                    authorName = acc.displayName,
                    authorPhone = acc.phoneE164,
                    authorPhotoBlobId = acc.photoBlobId,
                    type = if (isVideo) "video" else "image",
                    text = caption,
                    backgroundColorHex = null,
                    mediaBlobId = uploaded.blobId,
                    mediaMimeType = uploaded.mimeType,
                    privacy = privacy,
                    contactUids = contactUids
                ).onSuccess { _feedback.value = "Status uploaded for 24h" }
                    .onFailure { _feedback.value = it.localizedMessage }
            }.onFailure {
                _feedback.value = it.localizedMessage ?: "Media upload failed"
            }
        }
    }

    fun markStatusViewed(statusId: String) {
        viewModelScope.launch {
            socialCallRepo.markStatusViewed(statusId)
        }
    }

    fun reactToStatus(statusId: String, emoji: String) {
        viewModelScope.launch {
            socialCallRepo.reactToStatus(statusId, emoji)
                .onSuccess { _feedback.value = "Reacted $emoji" }
                .onFailure { _feedback.value = it.localizedMessage }
        }
    }

    fun deleteStatus(statusId: String) {
        viewModelScope.launch {
            socialCallRepo.deleteStatus(statusId)
                .onSuccess { _feedback.value = "Status deleted" }
                .onFailure { _feedback.value = it.localizedMessage }
        }
    }

    fun updateProfile(
        displayName: String,
        about: String,
        privacyLastSeen: String,
        privacyPhoto: String,
        privacyAbout: String,
        readReceipts: Boolean
    ) {
        val acc = account.value ?: return
        viewModelScope.launch {
            messagingRepo.updateProfileAndPrivacy(
                account = acc,
                displayName = displayName,
                about = about,
                photoBlobId = acc.photoBlobId,
                privacyLastSeen = privacyLastSeen,
                privacyPhoto = privacyPhoto,
                privacyAbout = privacyAbout,
                readReceiptsEnabled = readReceipts
            ).onSuccess { _feedback.value = "Profile & privacy updated" }
                .onFailure { _feedback.value = it.localizedMessage }
        }
    }

    fun updateProfilePhoto(context: Context, uri: Uri) {
        val acc = account.value ?: return
        viewModelScope.launch {
            _uploadProgress.value = 0.1f
            val res = mediaService.uploadImageUri(
                context = context,
                uri = uri,
                scope = "public",
                allowedUids = listOf(currentUid),
                onProgress = { _uploadProgress.value = it }
            )
            _uploadProgress.value = null
            res.onSuccess { uploaded ->
                messagingRepo.updateProfileAndPrivacy(account = acc, photoBlobId = uploaded.blobId)
                    .onSuccess { _feedback.value = "Profile photo updated" }
            }.onFailure {
                _feedback.value = it.localizedMessage ?: "Photo upload failed"
            }
        }
    }

    fun unblockUser(blockedUid: String) {
        viewModelScope.launch {
            messagingRepo.unblockUser(blockedUid)
                .onSuccess { _feedback.value = "User unblocked" }
                .onFailure { _feedback.value = it.localizedMessage }
        }
    }

    fun markNotificationRead(id: String) {
        viewModelScope.launch {
            socialCallRepo.markNotificationRead(id)
        }
    }

    fun setOnlinePresence(online: Boolean) {
        viewModelScope.launch {
            messagingRepo.setOnlinePresence(online, account.value?.phoneE164)
        }
    }
}
