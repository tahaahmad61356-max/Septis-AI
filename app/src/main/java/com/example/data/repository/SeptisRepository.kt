package com.example.data.repository

import android.content.Context
import android.os.Build
import com.example.data.model.AdminMember
import com.example.data.model.AdminVaultConfig
import com.example.data.model.Announcement
import com.example.data.model.AppSettings
import com.example.data.model.AuditLog
import com.example.data.model.NotificationItem
import com.example.data.model.PendingAccount
import com.example.data.model.SeptisRecord
import com.example.data.model.UserAccount
import com.example.data.model.UserPasscode
import com.example.data.remote.OperationType
import com.example.data.remote.SeptisFirebaseProvider
import com.example.data.remote.handleFirestoreError
import com.example.data.remote.unwrapFirestoreException
import com.example.util.CryptoUtils
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot.ServerTimestampBehavior
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

sealed interface AdminVaultCheckResult {
    data object NeedsInitialSetup : AdminVaultCheckResult
    data object ReadyForCodeVerification : AdminVaultCheckResult
    data class Unauthorized(val reason: String) : AdminVaultCheckResult
}

class SeptisRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        SeptisFirebaseProvider.getFirestore(context),
        Firebase.auth
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("Authentication required before accessing SEPTIS backend.")
    }

    private fun requireUserEmail(): String {
        return auth.currentUser?.email ?: "unknown@septis.local"
    }

    // --- Admin Authentication & Confidential Vault Verification ---

    suspend fun checkAdminAuthorization(): Result<AdminVaultCheckResult> = runCatching {
        requireUserId()
        val vaultDoc = try {
            db.collection("admin_vault").document("config").get().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, "admin_vault/config")
            return Result.success(
                AdminVaultCheckResult.Unauthorized("Backend denied Admin access for this Google account.")
            )
        }
        if (vaultDoc.exists()) {
            AdminVaultCheckResult.ReadyForCodeVerification
        } else {
            AdminVaultCheckResult.NeedsInitialSetup
        }
    }

    suspend fun setupInitialAdminAccessCode(newCode: String): Result<Unit> = runCatching {
        val uid = requireUserId()
        require(newCode.length >= 6) { "Admin Access Code must be at least 6 characters." }
        val salt = CryptoUtils.generateSaltHex()
        val codeHash = CryptoUtils.hashSecret(salt, newCode)

        val vaultPayload = mapOf(
            "salt" to salt,
            "codeHash" to codeHash,
            "updatedBy" to uid,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        val vaultRef = db.collection("admin_vault").document("config")
        try {
            vaultRef.set(vaultPayload).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, vaultRef.path)
            throw e
        }

        val sessionPayload = mapOf(
            "uid" to uid,
            "submittedHash" to codeHash,
            "deviceLabel" to "${Build.MANUFACTURER} ${Build.MODEL}".take(120).ifBlank { "Android Device" },
            "verifiedAt" to FieldValue.serverTimestamp()
        )
        val sessionRef = db.collection("admin_sessions").document(uid)
        try {
            sessionRef.set(sessionPayload).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, sessionRef.path)
            throw e
        }

        logAudit("ADMIN_CODE_INITIALIZED", uid, "Confidential Admin Access Code configured.")
    }

    suspend fun verifyAdminAccessCode(enteredCode: String): Result<Unit> = runCatching {
        val uid = requireUserId()
        require(enteredCode.isNotBlank()) { "Please enter the Admin Access Code." }
        val vaultRef = db.collection("admin_vault").document("config")
        val config = try {
            vaultRef.get().await().toObject(AdminVaultConfig::class.java, ServerTimestampBehavior.ESTIMATE)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, vaultRef.path)
            throw SecurityException("Backend denied Admin vault verification.")
        } ?: throw IllegalStateException("Admin vault is not configured yet.")

        val candidateHash = CryptoUtils.hashSecret(config.salt, enteredCode)
        val sessionRef = db.collection("admin_sessions").document(uid)
        val sessionPayload = mapOf(
            "uid" to uid,
            "submittedHash" to candidateHash,
            "deviceLabel" to "${Build.MANUFACTURER} ${Build.MODEL}".take(120).ifBlank { "Android Device" },
            "verifiedAt" to FieldValue.serverTimestamp()
        )
        try {
            // Backend Firestore Security Rule validates submittedHash == config.codeHash
            sessionRef.set(sessionPayload).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, sessionRef.path)
            throw SecurityException("Invalid Admin Access Code or insufficient privileges.")
        }

        logAudit("ADMIN_LOGIN", uid, "Admin session verified via backend rules.")
    }

    suspend fun rotateAdminAccessCode(currentCode: String, newCode: String): Result<Unit> = runCatching {
        val uid = requireUserId()
        require(newCode.length >= 6) { "New Admin Access Code must be at least 6 characters." }
        val vaultRef = db.collection("admin_vault").document("config")
        val existingConfig = try {
            vaultRef.get().await().toObject(AdminVaultConfig::class.java, ServerTimestampBehavior.ESTIMATE)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, vaultRef.path)
            throw e
        } ?: throw IllegalStateException("Admin vault missing.")

        val currentHash = CryptoUtils.hashSecret(existingConfig.salt, currentCode)
        if (currentHash != existingConfig.codeHash) {
            throw SecurityException("Current Admin Access Code does not match.")
        }

        val newSalt = CryptoUtils.generateSaltHex()
        val newHash = CryptoUtils.hashSecret(newSalt, newCode)
        try {
            vaultRef.set(
                mapOf(
                    "salt" to newSalt,
                    "codeHash" to newHash,
                    "updatedBy" to uid,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            db.collection("admin_sessions").document(uid).set(
                mapOf(
                    "uid" to uid,
                    "submittedHash" to newHash,
                    "deviceLabel" to "${Build.MANUFACTURER} ${Build.MODEL}".take(120).ifBlank { "Android Device" },
                    "verifiedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, vaultRef.path)
            throw e
        }

        logAudit("ADMIN_CODE_ROTATED", uid, "Confidential Admin Access Code rotated.")
    }

    suspend fun clearAdminSession(): Result<Unit> = runCatching {
        val uid = auth.currentUser?.uid ?: return@runCatching
        try {
            db.collection("admin_sessions").document(uid).delete().await()
        } catch (_: Exception) {
        }
    }

    // --- User Provisioning & Account Sync ---

    suspend fun ensureUserAccount(uid: String, email: String, defaultDisplayName: String): Result<UserAccount> =
        runCatching {
            val userRef = db.collection("users").document(uid)
            val existingSnap = try {
                userRef.get().await()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.GET, userRef.path)
                throw e
            }

            if (existingSnap.exists()) {
                val account = existingSnap.toObject(UserAccount::class.java, ServerTimestampBehavior.ESTIMATE)
                if (account != null) return@runCatching account
            }

            // Check if Admin pre-provisioned a pending account for this verified email
            val normalizedEmail = email.trim()
            val pendingSnap = try {
                db.collection("pending_accounts")
                    .whereEqualTo("email", normalizedEmail)
                    .get()
                    .await()
            } catch (_: Exception) {
                null
            }

            val pendingDoc = pendingSnap?.documents?.firstOrNull {
                it.getBoolean("claimed") == false
            }

            val displayName = pendingDoc?.getString("displayName")
                ?: defaultDisplayName.ifBlank { normalizedEmail.substringBefore("@").ifBlank { "SEPTIS Member" } }
            val department = pendingDoc?.getString("department")
            val bio = pendingDoc?.getString("bio")
            val phone = pendingDoc?.getString("phone")
            val status = pendingDoc?.getString("status") ?: "active"
            val canEditProfile = pendingDoc?.getBoolean("canEditProfile") ?: true
            val canPostContent = pendingDoc?.getBoolean("canPostContent") ?: true

            val payload = mutableMapOf<String, Any>(
                "userId" to uid,
                "email" to normalizedEmail,
                "displayName" to displayName.take(100),
                "role" to "user",
                "status" to status,
                "canEditProfile" to canEditProfile,
                "canPostContent" to canPostContent,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (!department.isNullOrBlank()) payload["department"] = department.take(100)
            if (!bio.isNullOrBlank()) payload["bio"] = bio.take(300)
            if (!phone.isNullOrBlank()) payload["phone"] = phone.take(40)

            try {
                userRef.set(payload).await()
                if (pendingDoc != null) {
                    db.collection("pending_accounts").document(pendingDoc.id).update(
                        mapOf(
                            "claimed" to true,
                            "claimedUid" to uid,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.CREATE, userRef.path)
                throw e
            }

            userRef.get().await().toObject(UserAccount::class.java, ServerTimestampBehavior.ESTIMATE)
                ?: UserAccount(
                    id = uid,
                    userId = uid,
                    email = normalizedEmail,
                    displayName = displayName,
                    department = department,
                    bio = bio,
                    phone = phone,
                    role = "user",
                    status = status,
                    canEditProfile = canEditProfile,
                    canPostContent = canPostContent,
                    createdAt = Timestamp.now(),
                    updatedAt = Timestamp.now()
                )
        }

    fun observeMyAccount(uid: String): Flow<UserAccount?> = flow {
        val docRef = db.collection("users").document(uid)
        emitAll(
            docRef.snapshots()
                .map { it.toObject(UserAccount::class.java, ServerTimestampBehavior.ESTIMATE) }
                .catch { error ->
                    val firestoreError = error.unwrapFirestoreException()
                    if (firestoreError == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(firestoreError ?: error, OperationType.GET, docRef.path)
                    }
                    throw error
                }
        )
    }

    suspend fun updateMyProfile(
        uid: String,
        displayName: String,
        bio: String,
        department: String,
        phone: String
    ): Result<Unit> = runCatching {
        val callerUid = requireUserId()
        require(callerUid == uid) { "Cannot update another user's profile." }
        val docRef = db.collection("users").document(uid)
        val updates = mutableMapOf<String, Any>(
            "displayName" to displayName.trim().take(100),
            "bio" to bio.trim().take(300),
            "department" to department.trim().take(100),
            "phone" to phone.trim().take(40),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            docRef.update(updates).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    // --- User Personal Passcode (App Lock PIN) ---

    suspend fun hasPasscodeSet(uid: String): Boolean {
        val docRef = db.collection("users").document(uid).collection("security").document("passcode")
        return try {
            docRef.get().await().exists()
        } catch (_: Exception) {
            false
        }
    }

    suspend fun updateMyPasscode(uid: String, newPasscode: String): Result<Unit> = runCatching {
        val callerUid = requireUserId()
        require(callerUid == uid) { "Cannot update another user's passcode." }
        require(newPasscode.length >= 4) { "Passcode must be at least 4 characters." }
        val salt = CryptoUtils.generateSaltHex()
        val pinHash = CryptoUtils.hashSecret(salt, newPasscode)
        val docRef = db.collection("users").document(uid).collection("security").document("passcode")
        try {
            docRef.set(
                mapOf(
                    "userId" to uid,
                    "salt" to salt,
                    "pinHash" to pinHash,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            throw e
        }
    }

    suspend fun verifyMyPasscode(uid: String, passcode: String): Result<Boolean> = runCatching {
        val docRef = db.collection("users").document(uid).collection("security").document("passcode")
        val doc = try {
            docRef.get().await().toObject(UserPasscode::class.java, ServerTimestampBehavior.ESTIMATE)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, docRef.path)
            throw e
        } ?: return@runCatching true
        CryptoUtils.hashSecret(doc.salt, passcode) == doc.pinHash
    }

    // --- Announcements ---

    fun observePublishedAnnouncements(): Flow<List<Announcement>> = flow {
        val uid = requireUserId()
        val query = db.collection("announcements").whereEqualTo("visibility", "all")
        emitAll(
            query.snapshots()
                .map { snap ->
                    snap.toObjects(Announcement::class.java, ServerTimestampBehavior.ESTIMATE)
                        .sortedWith(
                            compareByDescending<Announcement> { it.pinned }
                                .thenByDescending { it.createdAt ?: Timestamp(0, 0) }
                        )
                }
                .catch { error ->
                    val firestoreError = error.unwrapFirestoreException()
                    if (firestoreError == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(firestoreError ?: error, OperationType.LIST, "announcements")
                    }
                    throw error
                }
        )
    }

    fun observeAllAnnouncements(): Flow<List<Announcement>> = flow {
        val uid = requireUserId()
        val colRef = db.collection("announcements")
        emitAll(
            colRef.snapshots()
                .map { snap ->
                    snap.toObjects(Announcement::class.java, ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    val firestoreError = error.unwrapFirestoreException()
                    if (firestoreError == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(firestoreError ?: error, OperationType.LIST, "announcements")
                    }
                    throw error
                }
        )
    }

    suspend fun saveAnnouncementByAdmin(
        id: String?,
        title: String,
        body: String,
        priority: String,
        pinned: Boolean,
        visibility: String,
        authorName: String
    ): Result<Unit> = runCatching {
        val uid = requireUserId()
        val colRef = db.collection("announcements")
        val docRef = if (id.isNullOrBlank()) colRef.document() else colRef.document(id)
        try {
            if (id.isNullOrBlank()) {
                docRef.set(
                    mapOf(
                        "title" to title.trim().take(150),
                        "body" to body.trim().take(2000),
                        "priority" to priority,
                        "pinned" to pinned,
                        "visibility" to visibility,
                        "authorUid" to uid,
                        "authorName" to authorName.trim().take(100),
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
                logAudit("ANNOUNCEMENT_CREATED", docRef.id, "Published announcement: ${title.take(60)}")
            } else {
                docRef.update(
                    mapOf(
                        "title" to title.trim().take(150),
                        "body" to body.trim().take(2000),
                        "priority" to priority,
                        "pinned" to pinned,
                        "visibility" to visibility,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
                logAudit("ANNOUNCEMENT_UPDATED", docRef.id, "Updated announcement: ${title.take(60)}")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            throw e
        }
    }

    suspend fun deleteAnnouncementByAdmin(id: String): Result<Unit> = runCatching {
        requireUserId()
        val docRef = db.collection("announcements").document(id)
        try {
            docRef.delete().await()
            logAudit("ANNOUNCEMENT_DELETED", id, "Deleted announcement $id")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            throw e
        }
    }

    // --- SEPTIS Records (Chat, Connect, Share, Resources) ---

    fun observeSharedRecords(): Flow<List<SeptisRecord>> = flow {
        val uid = requireUserId()
        val query = db.collection("records").whereEqualTo("visibility", "all")
        emitAll(
            query.snapshots()
                .map { snap ->
                    snap.toObjects(SeptisRecord::class.java, ServerTimestampBehavior.ESTIMATE)
                        .filter { it.status != "archived" }
                        .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    val firestoreError = error.unwrapFirestoreException()
                    if (firestoreError == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(firestoreError ?: error, OperationType.LIST, "records")
                    }
                    throw error
                }
        )
    }

    fun observeAllRecordsByAdmin(): Flow<List<SeptisRecord>> = flow {
        val uid = requireUserId()
        emitAll(
            db.collection("records").snapshots()
                .map { snap ->
                    snap.toObjects(SeptisRecord::class.java, ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    val firestoreError = error.unwrapFirestoreException()
                    if (firestoreError == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(firestoreError ?: error, OperationType.LIST, "records")
                    }
                    throw error
                }
        )
    }

    suspend fun createRecord(
        title: String,
        content: String,
        category: String,
        authorName: String,
        isProtected: Boolean = false,
        visibility: String = "all",
        status: String = "published"
    ): Result<String> = runCatching {
        val uid = requireUserId()
        val docRef = db.collection("records").document()
        try {
            docRef.set(
                mapOf(
                    "title" to title.trim().take(150),
                    "content" to content.trim().take(3000),
                    "category" to category,
                    "status" to status,
                    "visibility" to visibility,
                    "isProtected" to isProtected,
                    "authorUid" to uid,
                    "authorName" to authorName.trim().take(100),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            docRef.id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }

    suspend fun updateRecordByUser(
        recordId: String,
        title: String,
        content: String,
        category: String
    ): Result<Unit> = runCatching {
        requireUserId()
        val docRef = db.collection("records").document(recordId)
        try {
            docRef.update(
                mapOf(
                    "title" to title.trim().take(150),
                    "content" to content.trim().take(3000),
                    "category" to category,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    suspend fun updateRecordByAdmin(
        recordId: String,
        title: String,
        content: String,
        category: String,
        status: String,
        visibility: String,
        isProtected: Boolean
    ): Result<Unit> = runCatching {
        requireUserId()
        val docRef = db.collection("records").document(recordId)
        try {
            docRef.update(
                mapOf(
                    "title" to title.trim().take(150),
                    "content" to content.trim().take(3000),
                    "category" to category,
                    "status" to status,
                    "visibility" to visibility,
                    "isProtected" to isProtected,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            logAudit("RECORD_UPDATED", recordId, "Updated record: ${title.take(60)}")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    suspend fun deleteRecord(recordId: String, isAdminAction: Boolean = false): Result<Unit> = runCatching {
        requireUserId()
        val docRef = db.collection("records").document(recordId)
        try {
            docRef.delete().await()
            if (isAdminAction) {
                logAudit("RECORD_DELETED", recordId, "Deleted record $recordId")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            throw e
        }
    }

    // --- Notifications ---

    fun observeMyNotifications(uid: String): Flow<List<NotificationItem>> = flow {
        val personalFlow = db.collection("notifications")
            .whereEqualTo("recipientUid", uid)
            .snapshots()
            .map { it.toObjects(NotificationItem::class.java, ServerTimestampBehavior.ESTIMATE) }
        val broadcastFlow = db.collection("notifications")
            .whereEqualTo("recipientUid", "all")
            .snapshots()
            .map { it.toObjects(NotificationItem::class.java, ServerTimestampBehavior.ESTIMATE) }

        emitAll(
            combine(personalFlow, broadcastFlow) { personal, broadcast ->
                (personal + broadcast)
                    .distinctBy { it.id }
                    .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
            }.catch { error ->
                val firestoreError = error.unwrapFirestoreException()
                if (firestoreError == null && error is CancellationException) throw error
                if (auth.currentUser?.uid == uid) {
                    handleFirestoreError(firestoreError ?: error, OperationType.LIST, "notifications")
                }
                throw error
            }
        )
    }

    fun observeAllNotificationsByAdmin(): Flow<List<NotificationItem>> = flow {
        val uid = requireUserId()
        emitAll(
            db.collection("notifications").snapshots()
                .map { snap ->
                    snap.toObjects(NotificationItem::class.java, ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    val firestoreError = error.unwrapFirestoreException()
                    if (firestoreError == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(firestoreError ?: error, OperationType.LIST, "notifications")
                    }
                    throw error
                }
        )
    }

    suspend fun sendNotificationByAdmin(
        recipientUid: String,
        title: String,
        message: String,
        type: String
    ): Result<Unit> = runCatching {
        val uid = requireUserId()
        val docRef = db.collection("notifications").document()
        try {
            docRef.set(
                mapOf(
                    "recipientUid" to recipientUid.trim(),
                    "title" to title.trim().take(150),
                    "message" to message.trim().take(1000),
                    "type" to type,
                    "isRead" to false,
                    "senderUid" to uid,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            logAudit("NOTIFICATION_SENT", recipientUid, "Sent notification: ${title.take(60)}")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }

    suspend fun markNotificationRead(notificationId: String): Result<Unit> = runCatching {
        requireUserId()
        val docRef = db.collection("notifications").document(notificationId)
        try {
            docRef.update(
                mapOf(
                    "isRead" to true,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    suspend fun deleteNotificationByAdmin(notificationId: String): Result<Unit> = runCatching {
        requireUserId()
        val docRef = db.collection("notifications").document(notificationId)
        try {
            docRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            throw e
        }
    }

    // --- Admin User Management & Pre-Provisioning ---

    fun observeAllUsersByAdmin(): Flow<List<UserAccount>> = flow {
        val uid = requireUserId()
        emitAll(
            db.collection("users").snapshots()
                .map { snap ->
                    snap.toObjects(UserAccount::class.java, ServerTimestampBehavior.ESTIMATE)
                        .sortedBy { it.displayName.lowercase() }
                }
                .catch { error ->
                    val firestoreError = error.unwrapFirestoreException()
                    if (firestoreError == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(firestoreError ?: error, OperationType.LIST, "users")
                    }
                    throw error
                }
        )
    }

    fun observePendingAccountsByAdmin(): Flow<List<PendingAccount>> = flow {
        val uid = requireUserId()
        emitAll(
            db.collection("pending_accounts").snapshots()
                .map { snap ->
                    snap.toObjects(PendingAccount::class.java, ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    val firestoreError = error.unwrapFirestoreException()
                    if (firestoreError == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(firestoreError ?: error, OperationType.LIST, "pending_accounts")
                    }
                    throw error
                }
        )
    }

    suspend fun createPendingAccountByAdmin(
        email: String,
        displayName: String,
        department: String,
        bio: String,
        phone: String,
        status: String,
        canEditProfile: Boolean,
        canPostContent: Boolean
    ): Result<String> = runCatching {
        val uid = requireUserId()
        val docRef = db.collection("pending_accounts").document()
        val payload = mutableMapOf<String, Any>(
            "email" to email.trim().take(200),
            "displayName" to displayName.trim().take(100),
            "status" to status,
            "canEditProfile" to canEditProfile,
            "canPostContent" to canPostContent,
            "claimed" to false,
            "createdBy" to uid,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        if (department.isNotBlank()) payload["department"] = department.trim().take(100)
        if (bio.isNotBlank()) payload["bio"] = bio.trim().take(300)
        if (phone.isNotBlank()) payload["phone"] = phone.trim().take(40)

        try {
            docRef.set(payload).await()
            logAudit("USER_PROVISIONED", docRef.id, "Created user account invite for ${email.trim()}")
            docRef.id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }

    suspend fun updateUserByAdmin(
        userId: String,
        displayName: String,
        department: String,
        bio: String,
        phone: String,
        status: String,
        canEditProfile: Boolean,
        canPostContent: Boolean
    ): Result<Unit> = runCatching {
        requireUserId()
        val docRef = db.collection("users").document(userId)
        val updates = mapOf(
            "displayName" to displayName.trim().take(100),
            "department" to department.trim().take(100),
            "bio" to bio.trim().take(300),
            "phone" to phone.trim().take(40),
            "status" to status,
            "canEditProfile" to canEditProfile,
            "canPostContent" to canPostContent,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            docRef.update(updates).await()
            logAudit("USER_UPDATED", userId, "Updated user $displayName (status=$status)")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    suspend fun setUserStatusByAdmin(userId: String, newStatus: String): Result<Unit> = runCatching {
        requireUserId()
        val docRef = db.collection("users").document(userId)
        try {
            docRef.update(
                mapOf(
                    "status" to newStatus,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            logAudit("USER_STATUS_CHANGED", userId, "Changed account status to $newStatus")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    suspend fun deleteUserByAdmin(userId: String): Result<Unit> = runCatching {
        requireUserId()
        val docRef = db.collection("users").document(userId)
        try {
            docRef.delete().await()
            logAudit("USER_DELETED", userId, "Deleted user account $userId")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            throw e
        }
    }

    suspend fun deletePendingAccountByAdmin(inviteId: String): Result<Unit> = runCatching {
        requireUserId()
        val docRef = db.collection("pending_accounts").document(inviteId)
        try {
            docRef.delete().await()
            logAudit("PENDING_USER_DELETED", inviteId, "Removed pending invite $inviteId")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            throw e
        }
    }

    // --- Global App Settings & Admin Management ---

    fun observeAppSettings(): Flow<AppSettings?> = flow {
        val uid = requireUserId()
        val docRef = db.collection("app_settings").document("global")
        emitAll(
            docRef.snapshots()
                .map { it.toObject(AppSettings::class.java, ServerTimestampBehavior.ESTIMATE) }
                .catch { error ->
                    val firestoreError = error.unwrapFirestoreException()
                    if (firestoreError == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(firestoreError ?: error, OperationType.GET, docRef.path)
                    }
                    throw error
                }
        )
    }

    suspend fun saveAppSettingsByAdmin(
        appBannerMessage: String,
        allowUserPosting: Boolean,
        allowUserProfileEdits: Boolean,
        maintenanceMode: Boolean
    ): Result<Unit> = runCatching {
        val uid = requireUserId()
        val docRef = db.collection("app_settings").document("global")
        try {
            docRef.set(
                mapOf(
                    "appBannerMessage" to appBannerMessage.trim().take(300),
                    "allowUserPosting" to allowUserPosting,
                    "allowUserProfileEdits" to allowUserProfileEdits,
                    "maintenanceMode" to maintenanceMode,
                    "updatedBy" to uid,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            logAudit("SETTINGS_UPDATED", "global", "Updated global SEPTIS settings")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            throw e
        }
    }

    fun observeAdmins(): Flow<List<AdminMember>> = flow {
        val uid = requireUserId()
        emitAll(
            db.collection("admins").snapshots()
                .map { snap -> snap.toObjects(AdminMember::class.java, ServerTimestampBehavior.ESTIMATE) }
                .catch { error ->
                    val firestoreError = error.unwrapFirestoreException()
                    if (firestoreError == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(firestoreError ?: error, OperationType.LIST, "admins")
                    }
                    throw error
                }
        )
    }

    suspend fun grantAdminRoleByOwner(
        targetUid: String,
        email: String,
        displayName: String
    ): Result<Unit> = runCatching {
        val ownerUid = requireUserId()
        val docRef = db.collection("admins").document(targetUid.trim())
        try {
            docRef.set(
                mapOf(
                    "uid" to targetUid.trim(),
                    "email" to email.trim(),
                    "displayName" to displayName.trim().take(100),
                    "grantedBy" to ownerUid,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()
            logAudit("ADMIN_GRANTED", targetUid, "Granted admin authority to $email")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            throw e
        }
    }

    suspend fun revokeAdminRoleByOwner(targetUid: String): Result<Unit> = runCatching {
        requireUserId()
        val docRef = db.collection("admins").document(targetUid)
        try {
            docRef.delete().await()
            logAudit("ADMIN_REVOKED", targetUid, "Revoked admin authority from $targetUid")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            throw e
        }
    }

    fun observeAuditLogs(): Flow<List<AuditLog>> = flow {
        val uid = requireUserId()
        emitAll(
            db.collection("audit_logs").snapshots()
                .map { snap ->
                    snap.toObjects(AuditLog::class.java, ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                        .take(100)
                }
                .catch { error ->
                    val firestoreError = error.unwrapFirestoreException()
                    if (firestoreError == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(firestoreError ?: error, OperationType.LIST, "audit_logs")
                    }
                    throw error
                }
        )
    }

    private suspend fun logAudit(action: String, targetId: String, details: String) {
        val uid = auth.currentUser?.uid ?: return
        val email = requireUserEmail()
        try {
            db.collection("audit_logs").document().set(
                mapOf(
                    "action" to action.take(80),
                    "actorUid" to uid,
                    "actorEmail" to email.take(200),
                    "targetId" to targetId.take(128),
                    "details" to details.take(500),
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (_: Exception) {
            // Best-effort audit trail
        }
    }
}
