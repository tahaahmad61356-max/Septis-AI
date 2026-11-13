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
    val email: String = "",
    val displayName: String = "",
    val bio: String? = null,
    val department: String? = null,
    val phone: String? = null,
    val role: String = "user",
    val status: String = "active",
    val canEditProfile: Boolean = true,
    val canPostContent: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class UserPasscode(
    @DocumentId val id: String = "",
    val userId: String = "",
    val salt: String = "",
    val pinHash: String = "",
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class PendingAccount(
    @DocumentId val id: String = "",
    val email: String = "",
    val displayName: String = "",
    val department: String? = null,
    val bio: String? = null,
    val phone: String? = null,
    val status: String = "active",
    val canEditProfile: Boolean = true,
    val canPostContent: Boolean = true,
    val claimed: Boolean = false,
    val claimedUid: String? = null,
    val createdBy: String = "",
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
data class SeptisRecord(
    @DocumentId val id: String = "",
    val title: String = "",
    val content: String = "",
    val category: String = "chat",
    val status: String = "published",
    val visibility: String = "all",
    val isProtected: Boolean = false,
    val authorUid: String = "",
    val authorName: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class NotificationItem(
    @DocumentId val id: String = "",
    val recipientUid: String = "",
    val title: String = "",
    val message: String = "",
    val type: String = "info",
    val isRead: Boolean = false,
    val senderUid: String = "",
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
