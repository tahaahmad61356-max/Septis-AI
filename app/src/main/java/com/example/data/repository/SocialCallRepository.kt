package com.example.data.repository

import android.content.Context
import com.example.data.model.CallSession
import com.example.data.model.Community
import com.example.data.model.IceCandidateDoc
import com.example.data.model.NotificationItem
import com.example.data.model.StatusUpdate
import com.example.data.remote.OperationType
import com.example.data.remote.SeptisFirebaseProvider
import com.example.data.remote.handleFirestoreError
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot.ServerTimestampBehavior
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.UUID

class SocialCallRepository(
    val db: FirebaseFirestore,
    val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        SeptisFirebaseProvider.getFirestore(context),
        Firebase.auth
    )

    private fun requireUid(): String =
        auth.currentUser?.uid ?: throw IllegalStateException("Please sign in to SEPTIS.")

    // --- Group Management ---

    suspend fun updateGroupDetails(
        conversationId: String,
        title: String,
        description: String,
        onlyAdminsCanSend: Boolean,
        onlyAdminsCanEditInfo: Boolean
    ): Result<Unit> = runCatching {
        requireUid()
        db.collection("conversations").document(conversationId)
            .update(
                mapOf(
                    "title" to title.trim().take(120),
                    "description" to description.trim().take(400),
                    "onlyAdminsCanSend" to onlyAdminsCanSend,
                    "onlyAdminsCanEditInfo" to onlyAdminsCanEditInfo,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
    }

    suspend fun addGroupMember(conversationId: String, memberUid: String): Result<Unit> = runCatching {
        requireUid()
        db.collection("conversations").document(conversationId)
            .update(
                mapOf(
                    "participantUids" to FieldValue.arrayUnion(memberUid),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
    }

    suspend fun removeGroupMember(conversationId: String, memberUid: String): Result<Unit> = runCatching {
        requireUid()
        db.collection("conversations").document(conversationId)
            .update(
                mapOf(
                    "participantUids" to FieldValue.arrayRemove(memberUid),
                    "adminUids" to FieldValue.arrayRemove(memberUid),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
    }

    suspend fun setGroupAdmin(conversationId: String, memberUid: String, makeAdmin: Boolean): Result<Unit> = runCatching {
        requireUid()
        val op = if (makeAdmin) FieldValue.arrayUnion(memberUid) else FieldValue.arrayRemove(memberUid)
        db.collection("conversations").document(conversationId)
            .update(
                mapOf(
                    "adminUids" to op,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
    }

    suspend fun leaveGroup(conversationId: String): Result<Unit> = runCatching {
        val uid = requireUid()
        db.collection("conversations").document(conversationId)
            .update(
                mapOf(
                    "participantUids" to FieldValue.arrayRemove(uid),
                    "adminUids" to FieldValue.arrayRemove(uid),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
    }

    suspend fun deleteGroup(conversationId: String): Result<Unit> = runCatching {
        requireUid()
        db.collection("conversations").document(conversationId).delete().await()
    }

    // --- Communities ---

    fun observeMyCommunities(uid: String): Flow<List<Community>> = flow {
        val query = db.collection("communities").whereArrayContains("memberUids", uid)
        emitAll(
            query.snapshots().map { qs ->
                qs.documents.mapNotNull {
                    it.toObject(Community::class.java, ServerTimestampBehavior.ESTIMATE)
                }.sortedByDescending { it.updatedAt?.toDate()?.time ?: 0L }
            }.catch { e ->
                handleFirestoreError(e, OperationType.LIST, "communities")
                emit(emptyList())
            }
        )
    }

    suspend fun createCommunity(
        name: String,
        description: String,
        iconEmoji: String,
        memberUids: List<String>,
        groupIds: List<String> = emptyList()
    ): Result<Community> = runCatching {
        val uid = requireUid()
        val cleanName = name.trim().take(100)
        require(cleanName.isNotEmpty()) { "Community name is required." }
        val communityId = "comm_${UUID.randomUUID().toString().replace("-", "").take(18)}"
        val safeMembers = (memberUids + uid).distinct().take(100)
        val payload = hashMapOf<String, Any?>(
            "name" to cleanName,
            "description" to description.trim().take(500),
            "iconEmoji" to iconEmoji.ifBlank { "\uD83C\uDF10" }.take(16),
            "ownerUid" to uid,
            "adminUids" to listOf(uid),
            "memberUids" to safeMembers,
            "groupIds" to groupIds.take(50),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        db.collection("communities").document(communityId).set(payload).await()
        Community(
            id = communityId,
            name = cleanName,
            description = description.trim(),
            iconEmoji = iconEmoji.ifBlank { "\uD83C\uDF10" },
            ownerUid = uid,
            adminUids = listOf(uid),
            memberUids = safeMembers,
            groupIds = groupIds
        )
    }

    suspend fun addGroupToCommunity(communityId: String, groupId: String, memberUids: List<String>): Result<Unit> = runCatching {
        requireUid()
        db.collection("communities").document(communityId)
            .update(
                mapOf(
                    "groupIds" to FieldValue.arrayUnion(groupId),
                    "memberUids" to FieldValue.arrayUnion(*memberUids.toTypedArray()),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
    }

    suspend fun deleteCommunity(communityId: String): Result<Unit> = runCatching {
        requireUid()
        db.collection("communities").document(communityId).delete().await()
    }

    // --- 24-Hour Status Updates ---

    fun observeActiveStatuses(uid: String): Flow<List<StatusUpdate>> {
        val mineFlow = flow {
            val q = db.collection("statuses").whereEqualTo("authorUid", uid)
            emitAll(
                q.snapshots().map { qs ->
                    qs.documents.mapNotNull { it.toObject(StatusUpdate::class.java, ServerTimestampBehavior.ESTIMATE) }
                }.catch { emit(emptyList()) }
            )
        }
        val everyoneFlow = flow {
            val q = db.collection("statuses").whereEqualTo("privacy", "everyone")
            emitAll(
                q.snapshots().map { qs ->
                    qs.documents.mapNotNull { it.toObject(StatusUpdate::class.java, ServerTimestampBehavior.ESTIMATE) }
                }.catch { emit(emptyList()) }
            )
        }
        val contactVisibleFlow = flow {
            val q = db.collection("statuses").whereArrayContains("visibleToUids", uid)
            emitAll(
                q.snapshots().map { qs ->
                    qs.documents.mapNotNull { it.toObject(StatusUpdate::class.java, ServerTimestampBehavior.ESTIMATE) }
                }.catch { emit(emptyList()) }
            )
        }

        return combine(mineFlow, everyoneFlow, contactVisibleFlow) { mine, everyone, visible ->
            val now = System.currentTimeMillis()
            (mine + everyone + visible)
                .distinctBy { it.id }
                .filter { it.expiresAtMillis > now }
                .sortedByDescending { it.createdAt?.toDate()?.time ?: 0L }
        }
    }

    suspend fun postStatus(
        authorName: String,
        authorPhone: String,
        authorPhotoBlobId: String?,
        type: String,
        text: String,
        backgroundColorHex: String?,
        mediaBlobId: String?,
        mediaMimeType: String?,
        privacy: String,
        contactUids: List<String>
    ): Result<Unit> = runCatching {
        val uid = requireUid()
        val id = "st_${UUID.randomUUID().toString().replace("-", "").take(18)}"
        val expiresAt = System.currentTimeMillis() + 24L * 60L * 60L * 1000L
        val visibleList = when (privacy) {
            "contacts" -> (contactUids + uid).distinct().take(100)
            "only_me" -> listOf(uid)
            else -> emptyList()
        }
        val payload = hashMapOf<String, Any?>(
            "authorUid" to uid,
            "authorName" to authorName.ifBlank { "SEPTIS User" }.take(100),
            "authorPhone" to authorPhone.take(24),
            "authorPhotoBlobId" to authorPhotoBlobId,
            "type" to type,
            "text" to text.trim().take(1000),
            "backgroundColorHex" to backgroundColorHex,
            "mediaBlobId" to mediaBlobId,
            "mediaMimeType" to mediaMimeType,
            "privacy" to privacy,
            "visibleToUids" to visibleList,
            "viewerUids" to emptyList<String>(),
            "reactions" to emptyMap<String, String>(),
            "expiresAtMillis" to expiresAt,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        db.collection("statuses").document(id).set(payload).await()
    }

    suspend fun markStatusViewed(statusId: String) {
        val uid = auth.currentUser?.uid ?: return
        try {
            db.collection("statuses").document(statusId)
                .update(
                    mapOf(
                        "viewerUids" to FieldValue.arrayUnion(uid),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
        } catch (_: Exception) {
        }
    }

    suspend fun reactToStatus(statusId: String, emoji: String): Result<Unit> = runCatching {
        val uid = requireUid()
        db.collection("statuses").document(statusId)
            .update(
                mapOf(
                    "reactions.$uid" to emoji.take(12),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
    }

    suspend fun deleteStatus(statusId: String): Result<Unit> = runCatching {
        requireUid()
        db.collection("statuses").document(statusId).delete().await()
    }

    // --- WebRTC Calls & Signaling ---

    fun observeMyCalls(uid: String): Flow<List<CallSession>> = flow {
        val query = db.collection("calls").whereArrayContains("participantUids", uid)
        emitAll(
            query.snapshots().map { qs ->
                qs.documents.mapNotNull {
                    it.toObject(CallSession::class.java, ServerTimestampBehavior.ESTIMATE)
                }.sortedByDescending { it.createdAt?.toDate()?.time ?: 0L }
            }.catch { e ->
                handleFirestoreError(e, OperationType.LIST, "calls")
                emit(emptyList())
            }
        )
    }

    fun observeCall(callId: String): Flow<CallSession?> = flow {
        val ref = db.collection("calls").document(callId)
        emitAll(
            ref.snapshots().map {
                it.toObject(CallSession::class.java, ServerTimestampBehavior.ESTIMATE)
            }.catch { emit(null) }
        )
    }

    fun observeCallCandidates(callId: String, uid: String): Flow<List<IceCandidateDoc>> = flow {
        val query = db.collection("calls").document(callId)
            .collection("candidates")
            .whereArrayContains("participantUids", uid)
        emitAll(
            query.snapshots().map { qs ->
                qs.documents.mapNotNull {
                    it.toObject(IceCandidateDoc::class.java, ServerTimestampBehavior.ESTIMATE)
                }
            }.catch { emit(emptyList()) }
        )
    }

    suspend fun createOutgoingCall(
        callerName: String,
        callerPhone: String?,
        calleeUid: String,
        calleeName: String,
        calleePhone: String?,
        callType: String
    ): Result<CallSession> = runCatching {
        val uid = requireUid()
        val callId = "call_${UUID.randomUUID().toString().replace("-", "").take(18)}"
        val participants = listOf(uid, calleeUid).distinct()
        val payload = hashMapOf<String, Any?>(
            "callerUid" to uid,
            "callerName" to callerName.ifBlank { "SEPTIS User" }.take(100),
            "callerPhone" to callerPhone,
            "calleeUid" to calleeUid,
            "calleeName" to calleeName.ifBlank { "SEPTIS User" }.take(100),
            "calleePhone" to calleePhone,
            "participantUids" to participants,
            "callType" to callType,
            "status" to "ringing",
            "offerSdp" to null,
            "answerSdp" to null,
            "durationSeconds" to 0L,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        db.collection("calls").document(callId).set(payload).await()
        CallSession(
            id = callId,
            callerUid = uid,
            callerName = callerName,
            callerPhone = callerPhone,
            calleeUid = calleeUid,
            calleeName = calleeName,
            calleePhone = calleePhone,
            participantUids = participants,
            callType = callType,
            status = "ringing"
        )
    }

    suspend fun publishCallSdp(callId: String, isOffer: Boolean, sdp: String, markAnswered: Boolean = false) {
        val key = if (isOffer) "offerSdp" else "answerSdp"
        val map = mutableMapOf<String, Any>(
            key to sdp,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        if (markAnswered) {
            map["status"] = "answered"
        }
        try {
            db.collection("calls").document(callId).update(map).await()
        } catch (_: Exception) {
        }
    }

    suspend fun addCallIceCandidate(
        callId: String,
        participantUids: List<String>,
        sdpMid: String,
        sdpMLineIndex: Int,
        candidate: String
    ) {
        val uid = auth.currentUser?.uid ?: return
        val candId = "ice_${UUID.randomUUID().toString().replace("-", "").take(18)}"
        try {
            db.collection("calls").document(callId).collection("candidates").document(candId)
                .set(
                    mapOf(
                        "senderUid" to uid,
                        "participantUids" to participantUids,
                        "sdpMid" to sdpMid.take(64),
                        "sdpMLineIndex" to sdpMLineIndex,
                        "candidate" to candidate.take(2000),
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                ).await()
        } catch (_: Exception) {
        }
    }

    suspend fun updateCallStatus(callId: String, status: String, durationSeconds: Long = 0L) {
        try {
            db.collection("calls").document(callId)
                .update(
                    mapOf(
                        "status" to status,
                        "durationSeconds" to durationSeconds.coerceAtLeast(0L),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
        } catch (_: Exception) {
        }
    }

    // --- Notifications & Reports ---

    fun observeNotifications(uid: String): Flow<List<NotificationItem>> = flow {
        val query = db.collection("notifications").whereIn("recipientUid", listOf(uid, "all"))
        emitAll(
            query.snapshots().map { qs ->
                qs.documents.mapNotNull {
                    it.toObject(NotificationItem::class.java, ServerTimestampBehavior.ESTIMATE)
                }.sortedByDescending { it.createdAt?.toDate()?.time ?: 0L }
            }.catch { e ->
                handleFirestoreError(e, OperationType.LIST, "notifications")
                emit(emptyList())
            }
        )
    }

    suspend fun sendNotification(
        recipientUid: String,
        senderName: String,
        title: String,
        message: String,
        type: String,
        referenceId: String? = null
    ) {
        val uid = auth.currentUser?.uid ?: return
        val id = "ntf_${UUID.randomUUID().toString().replace("-", "").take(18)}"
        try {
            db.collection("notifications").document(id).set(
                hashMapOf<String, Any?>(
                    "recipientUid" to recipientUid,
                    "senderUid" to uid,
                    "senderName" to senderName.take(100),
                    "title" to title.take(150),
                    "message" to message.take(1000),
                    "type" to type,
                    "referenceId" to referenceId,
                    "isRead" to false,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (_: Exception) {
        }
    }

    suspend fun markNotificationRead(notificationId: String) {
        try {
            db.collection("notifications").document(notificationId)
                .update(mapOf("isRead" to true, "updatedAt" to FieldValue.serverTimestamp()))
                .await()
        } catch (_: Exception) {
        }
    }

    suspend fun submitReport(
        reportedUid: String,
        reportedPhone: String?,
        reason: String,
        details: String
    ): Result<Unit> = runCatching {
        val uid = requireUid()
        val id = "rep_${UUID.randomUUID().toString().replace("-", "").take(18)}"
        db.collection("reports").document(id).set(
            hashMapOf<String, Any?>(
                "reporterUid" to uid,
                "reportedUid" to reportedUid,
                "reportedPhone" to reportedPhone,
                "reason" to reason.trim().ifBlank { "Abuse / Spam" }.take(150),
                "details" to details.trim().take(1000).ifBlank { null },
                "status" to "open",
                "reviewedBy" to null,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()
    }
}
