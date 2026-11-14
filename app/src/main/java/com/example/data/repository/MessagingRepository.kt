package com.example.data.repository

import android.content.Context
import com.example.data.model.Announcement
import com.example.data.model.AppSettings
import com.example.data.model.BlockedUser
import com.example.data.model.ChatMessage
import com.example.data.model.ChatPreference
import com.example.data.model.Conversation
import com.example.data.model.PhoneDirectoryEntry
import com.example.data.model.PhoneUtils
import com.example.data.model.SavedContact
import com.example.data.model.StarredMessage
import com.example.data.model.UserAccount
import com.example.data.remote.OperationType
import com.example.data.remote.SeptisFirebaseProvider
import com.example.data.remote.handleFirestoreError
import com.example.util.DeviceContact
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot.ServerTimestampBehavior
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.UUID

sealed interface PhoneClaimResult {
    data class Success(val account: UserAccount) : PhoneClaimResult
    data class AlreadyClaimed(val phoneE164: String) : PhoneClaimResult
}

data class LookupContactResult(
    val phoneE164: String,
    val registeredEntry: PhoneDirectoryEntry?
)

class MessagingRepository(
    val db: FirebaseFirestore,
    val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        SeptisFirebaseProvider.getFirestore(context),
        Firebase.auth
    )

    fun currentUid(): String? = auth.currentUser?.uid

    private fun requireUid(): String =
        auth.currentUser?.uid ?: throw IllegalStateException("Please sign in to SEPTIS.")

    suspend fun getMyAccount(uid: String = requireUid()): Result<UserAccount?> = runCatching {
        val ref = db.collection("users").document(uid)
        val snap = try {
            ref.get().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, ref.path)
            throw e
        }
        snap.toObject(UserAccount::class.java, ServerTimestampBehavior.ESTIMATE)
    }

    fun observeMyAccount(uid: String): Flow<UserAccount?> = flow {
        val ref = db.collection("users").document(uid)
        emitAll(
            ref.snapshots().map { snap ->
                snap.toObject(UserAccount::class.java, ServerTimestampBehavior.ESTIMATE)
            }.catch { e ->
                handleFirestoreError(e, OperationType.GET, ref.path)
                emit(null)
            }
        )
    }

    suspend fun registerOrUpdatePhoneProfile(
        phoneE164: String,
        countryCode: String,
        nationalNumber: String,
        displayName: String,
        about: String = "Hey there! I am using SEPTIS.",
        photoBlobId: String? = null
    ): Result<PhoneClaimResult> = runCatching {
        val uid = requireUid()
        val email = auth.currentUser?.email
        val cleanName = displayName.trim().ifBlank { phoneE164 }.take(100)
        val cleanAbout = about.trim().ifBlank { "Hey there! I am using SEPTIS." }.take(200)
        val phoneKey = PhoneUtils.toDirectoryKey(phoneE164)

        val dirRef = db.collection("phone_directory").document(phoneKey)
        val existingDir = try {
            dirRef.get().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, dirRef.path)
            throw e
        }

        if (existingDir.exists()) {
            val ownerUid = existingDir.getString("uid").orEmpty()
            if (ownerUid.isNotBlank() && ownerUid != uid) {
                return@runCatching PhoneClaimResult.AlreadyClaimed(phoneE164)
            }
        }

        val userRef = db.collection("users").document(uid)
        val existingUserSnap = try {
            userRef.get().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, userRef.path)
            throw e
        }

        val previousPhone = existingUserSnap.getString("phoneE164")
        val resolvedPhoto = photoBlobId ?: existingUserSnap.getString("photoBlobId")

        if (existingUserSnap.exists()) {
            val updates = hashMapOf<String, Any?>(
                "phoneE164" to phoneE164,
                "countryCode" to countryCode,
                "nationalNumber" to nationalNumber,
                "displayName" to cleanName,
                "about" to cleanAbout,
                "photoBlobId" to resolvedPhoto,
                "email" to email,
                "isOnline" to true,
                "lastSeen" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            try {
                userRef.update(updates).await()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.UPDATE, userRef.path)
                throw e
            }
        } else {
            val newUser = hashMapOf<String, Any?>(
                "userId" to uid,
                "phoneE164" to phoneE164,
                "countryCode" to countryCode,
                "nationalNumber" to nationalNumber,
                "displayName" to cleanName,
                "about" to cleanAbout,
                "photoBlobId" to resolvedPhoto,
                "email" to email,
                "role" to "user",
                "status" to "active",
                "isOnline" to true,
                "lastSeen" to FieldValue.serverTimestamp(),
                "privacyLastSeen" to "everyone",
                "privacyPhoto" to "everyone",
                "privacyAbout" to "everyone",
                "readReceiptsEnabled" to true,
                "fcmToken" to null,
                "canEditProfile" to true,
                "canPostContent" to true,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            try {
                userRef.set(newUser).await()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.CREATE, userRef.path)
                throw e
            }
        }

        if (!previousPhone.isNullOrBlank() && previousPhone != phoneE164) {
            val oldKey = PhoneUtils.toDirectoryKey(previousPhone)
            try {
                db.collection("phone_directory").document(oldKey).delete().await()
            } catch (_: Exception) {
            }
        }

        val userAfter = userRef.get().await()
            .toObject(UserAccount::class.java, ServerTimestampBehavior.ESTIMATE)
            ?: UserAccount(
                id = uid,
                userId = uid,
                phoneE164 = phoneE164,
                countryCode = countryCode,
                nationalNumber = nationalNumber,
                displayName = cleanName,
                about = cleanAbout,
                photoBlobId = resolvedPhoto
            )

        val dirPayload = hashMapOf<String, Any?>(
            "phoneKey" to phoneKey,
            "uid" to uid,
            "phoneE164" to phoneE164,
            "displayName" to cleanName,
            "about" to cleanAbout,
            "photoBlobId" to resolvedPhoto,
            "isOnline" to true,
            "lastSeen" to FieldValue.serverTimestamp(),
            "privacyLastSeen" to userAfter.privacyLastSeen,
            "privacyPhoto" to userAfter.privacyPhoto,
            "privacyAbout" to userAfter.privacyAbout,
            "status" to userAfter.status,
            "createdAt" to (existingDir.getTimestamp("createdAt") ?: FieldValue.serverTimestamp()),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            dirRef.set(dirPayload).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, dirRef.path)
            throw e
        }

        PhoneClaimResult.Success(userAfter)
    }

    suspend fun updateProfileAndPrivacy(
        account: UserAccount,
        displayName: String = account.displayName,
        about: String = account.about,
        photoBlobId: String? = account.photoBlobId,
        privacyLastSeen: String = account.privacyLastSeen,
        privacyPhoto: String = account.privacyPhoto,
        privacyAbout: String = account.privacyAbout,
        readReceiptsEnabled: Boolean = account.readReceiptsEnabled
    ): Result<Unit> = runCatching {
        val uid = requireUid()
        val cleanName = displayName.trim().ifBlank { account.displayName }.take(100)
        val cleanAbout = about.trim().take(200)
        val userRef = db.collection("users").document(uid)
        val updates = hashMapOf<String, Any?>(
            "displayName" to cleanName,
            "about" to cleanAbout,
            "photoBlobId" to photoBlobId,
            "privacyLastSeen" to privacyLastSeen,
            "privacyPhoto" to privacyPhoto,
            "privacyAbout" to privacyAbout,
            "readReceiptsEnabled" to readReceiptsEnabled,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        userRef.update(updates).await()

        if (account.phoneE164.isNotBlank()) {
            val dirRef = db.collection("phone_directory").document(PhoneUtils.toDirectoryKey(account.phoneE164))
            try {
                dirRef.update(
                    mapOf(
                        "displayName" to cleanName,
                        "about" to cleanAbout,
                        "photoBlobId" to photoBlobId,
                        "privacyLastSeen" to privacyLastSeen,
                        "privacyPhoto" to privacyPhoto,
                        "privacyAbout" to privacyAbout,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } catch (_: Exception) {
            }
        }
    }

    suspend fun setOnlinePresence(isOnline: Boolean, phoneE164: String? = null) {
        val uid = auth.currentUser?.uid ?: return
        try {
            db.collection("users").document(uid)
                .update(
                    mapOf(
                        "isOnline" to isOnline,
                        "lastSeen" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                .await()
            if (!phoneE164.isNullOrBlank()) {
                db.collection("phone_directory").document(PhoneUtils.toDirectoryKey(phoneE164))
                    .update(
                        mapOf(
                            "isOnline" to isOnline,
                            "lastSeen" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                    .await()
            }
        } catch (_: Exception) {
        }
    }

    suspend fun lookupRegisteredUserByPhone(
        rawPhone: String,
        defaultCountryCode: String
    ): Result<LookupContactResult> = runCatching {
        requireUid()
        val e164 = PhoneUtils.normalizeToE164(rawPhone, defaultCountryCode)
            ?: throw IllegalArgumentException("Enter a complete valid phone number including country code.")
        val key = PhoneUtils.toDirectoryKey(e164)
        val snap = try {
            db.collection("phone_directory").document(key).get().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, "phone_directory/$key")
            throw e
        }
        val entry = snap.toObject(PhoneDirectoryEntry::class.java, ServerTimestampBehavior.ESTIMATE)
        LookupContactResult(phoneE164 = e164, registeredEntry = entry)
    }

    suspend fun lookupDirectoryByPhoneExact(e164: String): PhoneDirectoryEntry? {
        return try {
            val key = PhoneUtils.toDirectoryKey(e164)
            val snap = db.collection("phone_directory").document(key).get().await()
            snap.toObject(PhoneDirectoryEntry::class.java, ServerTimestampBehavior.ESTIMATE)
        } catch (_: Exception) {
            null
        }
    }

    suspend fun syncDeviceContacts(deviceContacts: List<DeviceContact>): Result<List<SavedContact>> = runCatching {
        val uid = requireUid()
        val matched = coroutineScope {
            deviceContacts.take(80).map { dc ->
                async {
                    val entry = lookupDirectoryByPhoneExact(dc.normalizedE164)
                    if (entry != null && entry.uid != uid && entry.status == "active") {
                        dc to entry
                    } else null
                }
            }.awaitAll().filterNotNull()
        }

        val saved = mutableListOf<SavedContact>()
        for ((dc, entry) in matched) {
            val contactRef = db.collection("users").document(uid)
                .collection("contacts").document(entry.uid)
            val payload = hashMapOf<String, Any?>(
                "contactUid" to entry.uid,
                "phoneE164" to entry.phoneE164,
                "displayName" to entry.displayName,
                "localDeviceName" to dc.displayName.take(100),
                "about" to entry.about.take(200),
                "photoBlobId" to entry.photoBlobId,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            contactRef.set(payload).await()
            saved.add(
                SavedContact(
                    id = entry.uid,
                    contactUid = entry.uid,
                    phoneE164 = entry.phoneE164,
                    displayName = entry.displayName,
                    localDeviceName = dc.displayName,
                    about = entry.about,
                    photoBlobId = entry.photoBlobId
                )
            )
        }
        saved
    }

    suspend fun saveContactFromDirectory(entry: PhoneDirectoryEntry): Result<Unit> = runCatching {
        val uid = requireUid()
        val ref = db.collection("users").document(uid)
            .collection("contacts").document(entry.uid)
        ref.set(
            mapOf(
                "contactUid" to entry.uid,
                "phoneE164" to entry.phoneE164,
                "displayName" to entry.displayName,
                "localDeviceName" to null,
                "about" to entry.about.take(200),
                "photoBlobId" to entry.photoBlobId,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()
    }

    fun observeSavedContacts(uid: String): Flow<List<SavedContact>> = flow {
        val ref = db.collection("users").document(uid).collection("contacts")
        emitAll(
            ref.snapshots().map { qs ->
                qs.documents.mapNotNull {
                    it.toObject(SavedContact::class.java, ServerTimestampBehavior.ESTIMATE)
                }.sortedBy { (it.localDeviceName ?: it.displayName).lowercase() }
            }.catch { e ->
                handleFirestoreError(e, OperationType.LIST, ref.path)
                emit(emptyList())
            }
        )
    }

    fun observeBlockedUsers(uid: String): Flow<List<BlockedUser>> = flow {
        val ref = db.collection("users").document(uid).collection("blocked")
        emitAll(
            ref.snapshots().map { qs ->
                qs.documents.mapNotNull {
                    it.toObject(BlockedUser::class.java, ServerTimestampBehavior.ESTIMATE)
                }
            }.catch { e ->
                handleFirestoreError(e, OperationType.LIST, ref.path)
                emit(emptyList())
            }
        )
    }

    suspend fun blockUser(blockedUid: String, phoneE164: String?, displayName: String): Result<Unit> = runCatching {
        val uid = requireUid()
        db.collection("users").document(uid).collection("blocked").document(blockedUid)
            .set(
                mapOf(
                    "blockedUid" to blockedUid,
                    "phoneE164" to phoneE164,
                    "displayName" to displayName.ifBlank { "SEPTIS User" }.take(100),
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()
    }

    suspend fun unblockUser(blockedUid: String): Result<Unit> = runCatching {
        val uid = requireUid()
        db.collection("users").document(uid).collection("blocked").document(blockedUid).delete().await()
    }

    fun observeConversations(uid: String): Flow<List<Conversation>> = flow {
        val query = db.collection("conversations").whereArrayContains("participantUids", uid)
        emitAll(
            query.snapshots().map { qs ->
                qs.documents.mapNotNull {
                    it.toObject(Conversation::class.java, ServerTimestampBehavior.ESTIMATE)
                }.sortedByDescending { it.lastMessageAt?.toDate()?.time ?: it.updatedAt?.toDate()?.time ?: 0L }
            }.catch { e ->
                handleFirestoreError(e, OperationType.LIST, "conversations")
                throw e
            }
        )
    }

    fun observeConversation(conversationId: String): Flow<Conversation?> = flow {
        val ref = db.collection("conversations").document(conversationId)
        emitAll(
            ref.snapshots().map {
                it.toObject(Conversation::class.java, ServerTimestampBehavior.ESTIMATE)
            }.catch { e ->
                handleFirestoreError(e, OperationType.GET, ref.path)
                emit(null)
            }
        )
    }

    fun observeChatPreferences(uid: String): Flow<Map<String, ChatPreference>> = flow {
        val ref = db.collection("users").document(uid).collection("chat_prefs")
        emitAll(
            ref.snapshots().map { qs ->
                qs.documents.mapNotNull {
                    it.toObject(ChatPreference::class.java, ServerTimestampBehavior.ESTIMATE)
                }.associateBy { it.conversationId }
            }.catch { e ->
                handleFirestoreError(e, OperationType.LIST, ref.path)
                emit(emptyMap())
            }
        )
    }

    suspend fun updateChatPreference(
        conversationId: String,
        pinned: Boolean? = null,
        archived: Boolean? = null,
        muted: Boolean? = null,
        unreadCount: Int? = null,
        draftText: String? = null
    ): Result<Unit> = runCatching {
        val uid = requireUid()
        val ref = db.collection("users").document(uid).collection("chat_prefs").document(conversationId)
        val existing = ref.get().await().toObject(ChatPreference::class.java)
            ?: ChatPreference(id = conversationId, conversationId = conversationId)
        val payload = mapOf(
            "conversationId" to conversationId,
            "pinned" to (pinned ?: existing.pinned),
            "archived" to (archived ?: existing.archived),
            "muted" to (muted ?: existing.muted),
            "unreadCount" to (unreadCount ?: existing.unreadCount).coerceAtLeast(0),
            "draftText" to (draftText ?: existing.draftText).take(2000),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        ref.set(payload).await()
    }

    suspend fun getOrCreateDirectConversation(peer: PhoneDirectoryEntry): Result<Conversation> = runCatching {
        val uid = requireUid()
        saveContactFromDirectory(peer)
        val convId = PhoneUtils.generateDirectConversationId(uid, peer.uid)
        val ref = db.collection("conversations").document(convId)
        val snap = try {
            ref.get().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, ref.path)
            throw e
        }
        val existing = snap.toObject(Conversation::class.java, ServerTimestampBehavior.ESTIMATE)
        if (existing != null) return@runCatching existing

        val participants = listOf(uid, peer.uid).distinct().sorted()
        val payload = hashMapOf<String, Any?>(
            "type" to "direct",
            "participantUids" to participants,
            "adminUids" to emptyList<String>(),
            "title" to peer.displayName.take(120),
            "description" to null,
            "photoBlobId" to null,
            "communityId" to null,
            "onlyAdminsCanSend" to false,
            "onlyAdminsCanEditInfo" to false,
            "lastMessageText" to "",
            "lastMessageType" to "text",
            "lastMessageSenderUid" to null,
            "lastMessageAt" to null,
            "typingUids" to emptyList<String>(),
            "createdBy" to uid,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        ref.set(payload).await()
        ref.get().await().toObject(Conversation::class.java, ServerTimestampBehavior.ESTIMATE)
            ?: Conversation(id = convId, type = "direct", participantUids = participants, title = peer.displayName, createdBy = uid)
    }

    suspend fun createGroupConversation(
        title: String,
        description: String,
        memberUids: List<String>,
        photoBlobId: String? = null,
        communityId: String? = null
    ): Result<Conversation> = runCatching {
        val uid = requireUid()
        val cleanTitle = title.trim().take(120)
        require(cleanTitle.isNotEmpty()) { "Group name is required." }
        val participants = (memberUids + uid).distinct().take(100)
        val convId = "grp_${UUID.randomUUID().toString().replace("-", "").take(18)}"
        val ref = db.collection("conversations").document(convId)
        val payload = hashMapOf<String, Any?>(
            "type" to "group",
            "participantUids" to participants,
            "adminUids" to listOf(uid),
            "title" to cleanTitle,
            "description" to description.trim().take(400),
            "photoBlobId" to photoBlobId,
            "communityId" to communityId,
            "onlyAdminsCanSend" to false,
            "onlyAdminsCanEditInfo" to false,
            "lastMessageText" to "Group created",
            "lastMessageType" to "text",
            "lastMessageSenderUid" to uid,
            "lastMessageAt" to FieldValue.serverTimestamp(),
            "typingUids" to emptyList<String>(),
            "createdBy" to uid,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        ref.set(payload).await()
        ref.get().await().toObject(Conversation::class.java, ServerTimestampBehavior.ESTIMATE)
            ?: Conversation(id = convId, type = "group", participantUids = participants, adminUids = listOf(uid), title = cleanTitle, createdBy = uid)
    }

    fun observeMessages(conversationId: String, uid: String): Flow<List<ChatMessage>> = flow {
        val query = db.collection("conversations").document(conversationId)
            .collection("messages")
            .whereArrayContains("participantUids", uid)
        emitAll(
            query.snapshots().map { qs ->
                qs.documents.mapNotNull {
                    it.toObject(ChatMessage::class.java, ServerTimestampBehavior.ESTIMATE)
                }.filter { uid !in it.deletedForUids }
                    .sortedBy { it.createdAt?.toDate()?.time ?: Long.MAX_VALUE }
            }.catch { e ->
                handleFirestoreError(e, OperationType.LIST, "conversations/$conversationId/messages")
                throw e
            }
        )
    }

    suspend fun sendMessage(
        conversation: Conversation,
        senderName: String,
        type: String = "text",
        text: String,
        mediaBlobId: String? = null,
        mediaMimeType: String? = null,
        mediaFileName: String? = null,
        mediaSizeBytes: Long? = null,
        mediaDurationMs: Long? = null,
        waveform: List<Int>? = null,
        replyTo: ChatMessage? = null,
        forwardedFrom: String? = null
    ): Result<ChatMessage> = runCatching {
        val uid = requireUid()
        if (conversation.type == "group" && conversation.onlyAdminsCanSend && uid !in conversation.adminUids) {
            throw IllegalStateException("Only group admins can send messages in this group.")
        }
        val msgId = "msg_${UUID.randomUUID().toString().replace("-", "").take(20)}"
        val msgRef = db.collection("conversations").document(conversation.id)
            .collection("messages").document(msgId)
        val cleanText = text.trim().take(4000)
        val preview = when (type) {
            "image" -> "\uD83D\uDCF7 Photo"
            "video" -> "\uD83C\uDFA5 Video"
            "voice" -> "\uD83C\uDF99\uFE0F Voice message"
            "audio" -> "\uD83C\uDFB5 Audio"
            "document" -> "\uD83D\uDCC4 ${mediaFileName ?: "Document"}"
            "sticker" -> cleanText
            else -> cleanText
        }.take(500)

        val payload = hashMapOf<String, Any?>(
            "conversationId" to conversation.id,
            "senderUid" to uid,
            "senderName" to senderName.ifBlank { "SEPTIS User" }.take(100),
            "participantUids" to conversation.participantUids,
            "type" to type,
            "text" to cleanText,
            "mediaBlobId" to mediaBlobId,
            "mediaMimeType" to mediaMimeType,
            "mediaFileName" to mediaFileName,
            "mediaSizeBytes" to mediaSizeBytes,
            "mediaDurationMs" to mediaDurationMs,
            "waveform" to waveform,
            "replyToId" to replyTo?.id,
            "replyToSender" to replyTo?.senderName,
            "replyToText" to replyTo?.let {
                if (it.deletedForEveryone) "Deleted message" else it.text.ifBlank { "[${it.type}]" }.take(120)
            },
            "forwardedFrom" to forwardedFrom?.take(100),
            "edited" to false,
            "deletedForEveryone" to false,
            "deletedForUids" to emptyList<String>(),
            "deliveredToUids" to listOf(uid),
            "readByUids" to listOf(uid),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        msgRef.set(payload).await()

        val convUpdate = hashMapOf<String, Any?>(
            "lastMessageText" to preview,
            "lastMessageType" to type,
            "lastMessageSenderUid" to uid,
            "lastMessageAt" to FieldValue.serverTimestamp(),
            "typingUids" to FieldValue.arrayRemove(uid),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        db.collection("conversations").document(conversation.id).update(convUpdate).await()

        ChatMessage(
            id = msgId,
            conversationId = conversation.id,
            senderUid = uid,
            senderName = senderName,
            participantUids = conversation.participantUids,
            type = type,
            text = cleanText,
            mediaBlobId = mediaBlobId,
            mediaMimeType = mediaMimeType,
            mediaFileName = mediaFileName,
            mediaSizeBytes = mediaSizeBytes,
            mediaDurationMs = mediaDurationMs,
            waveform = waveform
        )
    }

    suspend fun setTypingStatus(conversationId: String, isTyping: Boolean) {
        val uid = auth.currentUser?.uid ?: return
        try {
            val op = if (isTyping) FieldValue.arrayUnion(uid) else FieldValue.arrayRemove(uid)
            db.collection("conversations").document(conversationId)
                .update(
                    mapOf(
                        "typingUids" to op,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
        } catch (_: Exception) {
        }
    }

    suspend fun markMessagesDeliveredAndRead(
        conversationId: String,
        messages: List<ChatMessage>,
        sendReadReceipts: Boolean
    ) {
        val uid = auth.currentUser?.uid ?: return
        val targets = messages.filter {
            it.senderUid != uid && (uid !in it.deliveredToUids || (sendReadReceipts && uid !in it.readByUids))
        }
        for (msg in targets.take(25)) {
            try {
                val updates = mutableMapOf<String, Any>(
                    "deliveredToUids" to FieldValue.arrayUnion(uid),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (sendReadReceipts) {
                    updates["readByUids"] = FieldValue.arrayUnion(uid)
                }
                db.collection("conversations").document(conversationId)
                    .collection("messages").document(msg.id)
                    .update(updates)
                    .await()
            } catch (_: Exception) {
            }
        }
    }

    suspend fun editMessage(conversationId: String, messageId: String, newText: String): Result<Unit> = runCatching {
        requireUid()
        val clean = newText.trim().take(4000)
        require(clean.isNotEmpty()) { "Message cannot be empty." }
        db.collection("conversations").document(conversationId)
            .collection("messages").document(messageId)
            .update(
                mapOf(
                    "text" to clean,
                    "edited" to true,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
    }

    suspend fun deleteMessageForEveryone(conversationId: String, messageId: String): Result<Unit> = runCatching {
        requireUid()
        db.collection("conversations").document(conversationId)
            .collection("messages").document(messageId)
            .update(
                mapOf(
                    "text" to "This message was deleted",
                    "deletedForEveryone" to true,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
    }

    suspend fun deleteMessageForMe(conversationId: String, messageId: String): Result<Unit> = runCatching {
        val uid = requireUid()
        db.collection("conversations").document(conversationId)
            .collection("messages").document(messageId)
            .update(
                mapOf(
                    "deletedForUids" to FieldValue.arrayUnion(uid),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
    }

    suspend fun starMessage(message: ChatMessage): Result<Unit> = runCatching {
        val uid = requireUid()
        val preview = message.text.ifBlank { "[${message.type}]" }.take(500)
        db.collection("users").document(uid)
            .collection("starred").document(message.id)
            .set(
                mapOf(
                    "conversationId" to message.conversationId,
                    "messageId" to message.id,
                    "senderName" to message.senderName.ifBlank { "SEPTIS" }.take(100),
                    "previewText" to preview,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()
    }

    suspend fun unstarMessage(messageId: String): Result<Unit> = runCatching {
        val uid = requireUid()
        db.collection("users").document(uid).collection("starred").document(messageId).delete().await()
    }

    fun observeStarredMessages(uid: String): Flow<List<StarredMessage>> = flow {
        val ref = db.collection("users").document(uid).collection("starred")
        emitAll(
            ref.snapshots().map { qs ->
                qs.documents.mapNotNull {
                    it.toObject(StarredMessage::class.java, ServerTimestampBehavior.ESTIMATE)
                }.sortedByDescending { it.createdAt?.toDate()?.time ?: 0L }
            }.catch { e ->
                handleFirestoreError(e, OperationType.LIST, ref.path)
                emit(emptyList())
            }
        )
    }

    fun observePublicAnnouncements(): Flow<List<Announcement>> = flow {
        val query = db.collection("announcements").whereEqualTo("visibility", "all")
        emitAll(
            query.snapshots().map { qs ->
                qs.documents.mapNotNull {
                    it.toObject(Announcement::class.java, ServerTimestampBehavior.ESTIMATE)
                }.sortedWith(compareByDescending<Announcement> { it.pinned }.thenByDescending { it.createdAt?.toDate()?.time ?: 0L })
            }.catch { e ->
                handleFirestoreError(e, OperationType.LIST, "announcements")
                emit(emptyList())
            }
        )
    }

    fun observeGlobalSettings(): Flow<AppSettings> = flow {
        val ref = db.collection("app_settings").document("global")
        emitAll(
            ref.snapshots().map { snap ->
                snap.toObject(AppSettings::class.java, ServerTimestampBehavior.ESTIMATE) ?: AppSettings()
            }.catch { _ ->
                emit(AppSettings())
            }
        )
    }
}
