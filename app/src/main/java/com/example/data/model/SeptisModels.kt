package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class AdminMember(
    @DocumentId val id: String = "",
    val uid: String = "",
    val email: String = "",
    val displayName: String = "",
    val grantedBy: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@IgnoreExtraProperties
data class AdminVaultConfig(
    @DocumentId val id: String = "",
    val salt: String = "",
    val codeHash: String = "",
    val updatedBy: String = "",
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class AdminSession(
    @DocumentId val id: String = "",
    val uid: String = "",
    val submittedHash: String = "",
    val deviceLabel: String = "",
    @ServerTimestamp val verifiedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class UserAccount(
    @DocumentId val id: String = "",
    val userId: String = "",
    val phoneE164: String = "",
    val countryCode: String = "+92",
    val nationalNumber: String = "",
    val displayName: String = "",
    val about: String = "Hey there! I am using SEPTIS.",
    val photoBlobId: String? = null,
    val email: String? = null,
    val role: String = "user",
    val status: String = "active",
    val isOnline: Boolean = false,
    val lastSeen: Timestamp? = null,
    val privacyLastSeen: String = "everyone",
    val privacyPhoto: String = "everyone",
    val privacyAbout: String = "everyone",
    val readReceiptsEnabled: Boolean = true,
    val fcmToken: String? = null,
    val canEditProfile: Boolean = true,
    val canPostContent: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class PhoneDirectoryEntry(
    @DocumentId val id: String = "",
    val phoneKey: String = "",
    val uid: String = "",
    val phoneE164: String = "",
    val displayName: String = "",
    val about: String = "Hey there! I am using SEPTIS.",
    val photoBlobId: String? = null,
    val isOnline: Boolean = false,
    val lastSeen: Timestamp? = null,
    val privacyLastSeen: String = "everyone",
    val privacyPhoto: String = "everyone",
    val privacyAbout: String = "everyone",
    val status: String = "active",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class SavedContact(
    @DocumentId val id: String = "",
    val contactUid: String = "",
    val phoneE164: String = "",
    val displayName: String = "",
    val localDeviceName: String? = null,
    val about: String? = null,
    val photoBlobId: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class BlockedUser(
    @DocumentId val id: String = "",
    val blockedUid: String = "",
    val phoneE164: String? = null,
    val displayName: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@IgnoreExtraProperties
data class StarredMessage(
    @DocumentId val id: String = "",
    val conversationId: String = "",
    val messageId: String = "",
    val senderName: String = "",
    val previewText: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@IgnoreExtraProperties
data class ChatPreference(
    @DocumentId val id: String = "",
    val conversationId: String = "",
    val pinned: Boolean = false,
    val archived: Boolean = false,
    val muted: Boolean = false,
    val unreadCount: Int = 0,
    val draftText: String = "",
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class Conversation(
    @DocumentId val id: String = "",
    val type: String = "direct",
    val participantUids: List<String> = emptyList(),
    val adminUids: List<String> = emptyList(),
    val title: String = "",
    val description: String? = null,
    val photoBlobId: String? = null,
    val communityId: String? = null,
    val onlyAdminsCanSend: Boolean = false,
    val onlyAdminsCanEditInfo: Boolean = false,
    val lastMessageText: String = "",
    val lastMessageType: String = "text",
    val lastMessageSenderUid: String? = null,
    val lastMessageAt: Timestamp? = null,
    val typingUids: List<String> = emptyList(),
    val createdBy: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class ChatMessage(
    @DocumentId val id: String = "",
    val conversationId: String = "",
    val senderUid: String = "",
    val senderName: String = "",
    val participantUids: List<String> = emptyList(),
    val type: String = "text",
    val text: String = "",
    val mediaBlobId: String? = null,
    val mediaMimeType: String? = null,
    val mediaFileName: String? = null,
    val mediaSizeBytes: Long? = null,
    val mediaDurationMs: Long? = null,
    val waveform: List<Int>? = null,
    val replyToId: String? = null,
    val replyToSender: String? = null,
    val replyToText: String? = null,
    val forwardedFrom: String? = null,
    val edited: Boolean = false,
    val deletedForEveryone: Boolean = false,
    val deletedForUids: List<String> = emptyList(),
    val deliveredToUids: List<String> = emptyList(),
    val readByUids: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class Community(
    @DocumentId val id: String = "",
    val name: String = "",
    val description: String = "",
    val iconEmoji: String = "\uD83C\uDF10",
    val ownerUid: String = "",
    val adminUids: List<String> = emptyList(),
    val memberUids: List<String> = emptyList(),
    val groupIds: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class StatusUpdate(
    @DocumentId val id: String = "",
    val authorUid: String = "",
    val authorName: String = "",
    val authorPhone: String = "",
    val authorPhotoBlobId: String? = null,
    val type: String = "text",
    val text: String = "",
    val backgroundColorHex: String? = "#0B6E3D",
    val mediaBlobId: String? = null,
    val mediaMimeType: String? = null,
    val privacy: String = "everyone",
    val visibleToUids: List<String> = emptyList(),
    val viewerUids: List<String> = emptyList(),
    val reactions: Map<String, String> = emptyMap(),
    val expiresAtMillis: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class CallSession(
    @DocumentId val id: String = "",
    val callerUid: String = "",
    val callerName: String = "",
    val callerPhone: String? = null,
    val calleeUid: String = "",
    val calleeName: String = "",
    val calleePhone: String? = null,
    val participantUids: List<String> = emptyList(),
    val callType: String = "voice",
    val status: String = "ringing",
    val offerSdp: String? = null,
    val answerSdp: String? = null,
    val durationSeconds: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class IceCandidateDoc(
    @DocumentId val id: String = "",
    val senderUid: String = "",
    val participantUids: List<String> = emptyList(),
    val sdpMid: String = "",
    val sdpMLineIndex: Int = 0,
    val candidate: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@IgnoreExtraProperties
data class NotificationItem(
    @DocumentId val id: String = "",
    val recipientUid: String = "",
    val senderUid: String = "",
    val senderName: String? = null,
    val title: String = "",
    val message: String = "",
    val type: String = "info",
    val referenceId: String? = null,
    val isRead: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class ReportItem(
    @DocumentId val id: String = "",
    val reporterUid: String = "",
    val reportedUid: String = "",
    val reportedPhone: String? = null,
    val reason: String = "",
    val details: String? = null,
    val status: String = "open",
    val reviewedBy: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class Announcement(
    @DocumentId val id: String = "",
    val title: String = "",
    val body: String = "",
    val priority: String = "normal",
    val pinned: Boolean = false,
    val visibility: String = "all",
    val authorUid: String = "",
    val authorName: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class AppSettings(
    @DocumentId val id: String = "",
    val appBannerMessage: String = "",
    val allowUserPosting: Boolean = true,
    val allowUserProfileEdits: Boolean = true,
    val maintenanceMode: Boolean = false,
    val updatedBy: String = "",
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class AuditLog(
    @DocumentId val id: String = "",
    val action: String = "",
    val actorUid: String = "",
    val actorEmail: String = "",
    val targetId: String = "",
    val details: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)
