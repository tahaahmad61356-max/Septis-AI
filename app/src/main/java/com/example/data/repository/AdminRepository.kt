package com.example.data.repository

import android.content.Context
import android.os.Build
import com.example.data.model.AdminMember
import com.example.data.model.AdminVaultConfig
import com.example.data.model.Announcement
import com.example.data.model.AppSettings
import com.example.data.model.AuditLog
import com.example.data.model.Community
import com.example.data.model.Conversation
import com.example.data.model.PhoneUtils
import com.example.data.model.ReportItem
import com.example.data.model.UserAccount
import com.example.data.remote.OperationType
import com.example.data.remote.SeptisFirebaseProvider
import com.example.data.remote.handleFirestoreError
import com.example.util.CryptoUtils
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot.ServerTimestampBehavior
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.UUID

sealed interface AdminVaultCheckResult {
    data object NeedsInitialSetup : AdminVaultCheckResult
    data object ReadyForCodeVerification : AdminVaultCheckResult
    data class Unauthorized(val reason: String) : AdminVaultCheckResult
}

class AdminRepository(
    val db: FirebaseFirestore,
    val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        SeptisFirebaseProvider.getFirestore(context),
        Firebase.auth
    )

    private fun requireUid(): String =
        auth.currentUser?.uid ?: throw IllegalStateException("Admin authentication required.")

    private fun requireEmail(): String =
        auth.currentUser?.email ?: auth.currentUser?.phoneNumber ?: "admin@septis.local"

    suspend fun checkAdminAuthorization(): Result<AdminVaultCheckResult> = runCatching {
        requireUid()
        val vaultDoc = try {
            db.collection("admin_vault").document("config").get().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, "admin_vault/config")
            return Result.success(
                AdminVaultCheckResult.Unauthorized("Backend denied Admin privileges for this signed-in account.")
            )
        }
        if (vaultDoc.exists()) {
            AdminVaultCheckResult.ReadyForCodeVerification
        } else {
            AdminVaultCheckResult.NeedsInitialSetup
        }
    }

    suspend fun setupInitialAdminAccessCode(newCode: String): Result<Unit> = runCatching {
        val uid = requireUid()
        require(newCode.length >= 6) { "Admin Access Code must be at least 6 characters." }
        val salt = CryptoUtils.generateSaltHex()
        val codeHash = CryptoUtils.hashSecret(salt, newCode)

        db.collection("admin_vault").document("config").set(
            mapOf(
                "salt" to salt,
                "codeHash" to codeHash,
                "updatedBy" to uid,
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()

        db.collection("admin_sessions").document(uid).set(
            mapOf(
                "uid" to uid,
                "submittedHash" to codeHash,
                "deviceLabel" to "${Build.MANUFACTURER} ${Build.MODEL}".take(120).ifBlank { "Android Device" },
                "verifiedAt" to FieldValue.serverTimestamp()
            )
        ).await()

        logAudit("ADMIN_CODE_INITIALIZED", uid, "Confidential Admin Access Code configured.")
    }

    suspend fun verifyAdminAccessCode(enteredCode: String): Result<Unit> = runCatching {
        val uid = requireUid()
        require(enteredCode.isNotBlank()) { "Please enter the Admin Access Code." }
        val vaultSnap = try {
            db.collection("admin_vault").document("config").get().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, "admin_vault/config")
            throw SecurityException("Backend denied Admin vault access.")
        }
        val config = vaultSnap.toObject(AdminVaultConfig::class.java, ServerTimestampBehavior.ESTIMATE)
            ?: throw IllegalStateException("Admin vault is not configured yet.")

        val candidateHash = CryptoUtils.hashSecret(config.salt, enteredCode)
        try {
            db.collection("admin_sessions").document(uid).set(
                mapOf(
                    "uid" to uid,
                    "submittedHash" to candidateHash,
                    "deviceLabel" to "${Build.MANUFACTURER} ${Build.MODEL}".take(120).ifBlank { "Android Device" },
                    "verifiedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "admin_sessions/$uid")
            throw SecurityException("Invalid Admin Access Code or insufficient permissions.")
        }
        logAudit("ADMIN_LOGIN", uid, "Admin session verified via Firestore backend rules.")
    }

    suspend fun rotateAdminAccessCode(currentCode: String, newCode: String): Result<Unit> = runCatching {
        val uid = requireUid()
        require(newCode.length >= 6) { "New Admin Access Code must be at least 6 characters." }
        val vaultRef = db.collection("admin_vault").document("config")
        val config = vaultRef.get().await().toObject(AdminVaultConfig::class.java)
            ?: throw IllegalStateException("Admin vault missing.")
        if (CryptoUtils.hashSecret(config.salt, currentCode) != config.codeHash) {
            throw SecurityException("Current Admin Access Code does not match.")
        }
        val newSalt = CryptoUtils.generateSaltHex()
        val newHash = CryptoUtils.hashSecret(newSalt, newCode)
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
        logAudit("ADMIN_CODE_ROTATED", uid, "Confidential Admin Access Code rotated.")
    }

    suspend fun clearAdminSession() {
        val uid = auth.currentUser?.uid ?: return
        try {
            db.collection("admin_sessions").document(uid).delete().await()
        } catch (_: Exception) {
        }
    }

    fun observeAllUsers(): Flow<List<UserAccount>> = flow {
        val ref = db.collection("users")
        emitAll(
            ref.snapshots().map { qs ->
                qs.documents.mapNotNull { it.toObject(UserAccount::class.java, ServerTimestampBehavior.ESTIMATE) }
                    .sortedByDescending { it.createdAt?.toDate()?.time ?: 0L }
            }.catch { e ->
                handleFirestoreError(e, OperationType.LIST, "users")
                emit(emptyList())
            }
        )
    }

    fun observeAllReports(): Flow<List<ReportItem>> = flow {
        val ref = db.collection("reports")
        emitAll(
            ref.snapshots().map { qs ->
                qs.documents.mapNotNull { it.toObject(ReportItem::class.java, ServerTimestampBehavior.ESTIMATE) }
                    .sortedByDescending { it.createdAt?.toDate()?.time ?: 0L }
            }.catch { e ->
                handleFirestoreError(e, OperationType.LIST, "reports")
                emit(emptyList())
            }
        )
    }

    fun observeAllGroups(): Flow<List<Conversation>> = flow {
        val q = db.collection("conversations").whereEqualTo("type", "group")
        emitAll(
            q.snapshots().map { qs ->
                qs.documents.mapNotNull { it.toObject(Conversation::class.java, ServerTimestampBehavior.ESTIMATE) }
                    .sortedByDescending { it.updatedAt?.toDate()?.time ?: 0L }
            }.catch { e ->
                handleFirestoreError(e, OperationType.LIST, "conversations")
                emit(emptyList())
            }
        )
    }

    fun observeAllCommunities(): Flow<List<Community>> = flow {
        val ref = db.collection("communities")
        emitAll(
            ref.snapshots().map { qs ->
                qs.documents.mapNotNull { it.toObject(Community::class.java, ServerTimestampBehavior.ESTIMATE) }
                    .sortedByDescending { it.createdAt?.toDate()?.time ?: 0L }
            }.catch { e ->
                handleFirestoreError(e, OperationType.LIST, "communities")
                emit(emptyList())
            }
        )
    }

    fun observeAllAnnouncements(): Flow<List<Announcement>> = flow {
        val ref = db.collection("announcements")
        emitAll(
            ref.snapshots().map { qs ->
                qs.documents.mapNotNull { it.toObject(Announcement::class.java, ServerTimestampBehavior.ESTIMATE) }
                    .sortedByDescending { it.createdAt?.toDate()?.time ?: 0L }
            }.catch { e ->
                handleFirestoreError(e, OperationType.LIST, "announcements")
                emit(emptyList())
            }
        )
    }

    fun observeAuditLogs(): Flow<List<AuditLog>> = flow {
        val ref = db.collection("audit_logs")
        emitAll(
            ref.snapshots().map { qs ->
                qs.documents.mapNotNull { it.toObject(AuditLog::class.java, ServerTimestampBehavior.ESTIMATE) }
                    .sortedByDescending { it.createdAt?.toDate()?.time ?: 0L }
            }.catch { e ->
                handleFirestoreError(e, OperationType.LIST, "audit_logs")
                emit(emptyList())
            }
        )
    }

    fun observeAdmins(): Flow<List<AdminMember>> = flow {
        val ref = db.collection("admins")
        emitAll(
            ref.snapshots().map { qs ->
                qs.documents.mapNotNull { it.toObject(AdminMember::class.java, ServerTimestampBehavior.ESTIMATE) }
            }.catch { emit(emptyList()) }
        )
    }

    fun observeAppSettings(): Flow<AppSettings> = flow {
        val ref = db.collection("app_settings").document("global")
        emitAll(
            ref.snapshots().map {
                it.toObject(AppSettings::class.java, ServerTimestampBehavior.ESTIMATE) ?: AppSettings()
            }.catch { emit(AppSettings()) }
        )
    }

    suspend fun setUserStatus(user: UserAccount, newStatus: String): Result<Unit> = runCatching {
        val actor = requireUid()
        db.collection("users").document(user.userId)
            .update(mapOf("status" to newStatus, "updatedAt" to FieldValue.serverTimestamp()))
            .await()
        if (user.phoneE164.isNotBlank()) {
            try {
                db.collection("phone_directory").document(PhoneUtils.toDirectoryKey(user.phoneE164))
                    .update(mapOf("status" to newStatus, "updatedAt" to FieldValue.serverTimestamp()))
                    .await()
            } catch (_: Exception) {
            }
        }
        logAudit("USER_STATUS_$newStatus", user.userId, "Admin $actor changed ${user.phoneE164} to $newStatus")
    }

    suspend fun deleteUserAccount(user: UserAccount): Result<Unit> = runCatching {
        val actor = requireUid()
        db.collection("users").document(user.userId).delete().await()
        if (user.phoneE164.isNotBlank()) {
            try {
                db.collection("phone_directory").document(PhoneUtils.toDirectoryKey(user.phoneE164)).delete().await()
            } catch (_: Exception) {
            }
        }
        logAudit("USER_DELETED", user.userId, "Admin $actor deleted account ${user.phoneE164}")
    }

    suspend fun updateReportStatus(reportId: String, status: String): Result<Unit> = runCatching {
        val actor = requireUid()
        db.collection("reports").document(reportId)
            .update(
                mapOf(
                    "status" to status,
                    "reviewedBy" to actor,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        logAudit("REPORT_$status", reportId, "Report marked $status by admin")
    }

    suspend fun removeGroupByAdmin(groupId: String): Result<Unit> = runCatching {
        requireUid()
        db.collection("conversations").document(groupId).delete().await()
        logAudit("ADMIN_GROUP_REMOVED", groupId, "Group removed by administrator")
    }

    suspend fun removeCommunityByAdmin(communityId: String): Result<Unit> = runCatching {
        requireUid()
        db.collection("communities").document(communityId).delete().await()
        logAudit("ADMIN_COMMUNITY_REMOVED", communityId, "Community removed by administrator")
    }

    suspend fun postAnnouncement(
        title: String,
        body: String,
        priority: String,
        pinned: Boolean,
        authorName: String
    ): Result<Unit> = runCatching {
        val uid = requireUid()
        val id = "ann_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        db.collection("announcements").document(id).set(
            mapOf(
                "title" to title.trim().take(150),
                "body" to body.trim().take(2000),
                "priority" to priority,
                "pinned" to pinned,
                "visibility" to "all",
                "authorUid" to uid,
                "authorName" to authorName.ifBlank { "SEPTIS Admin" }.take(100),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()
        logAudit("ANNOUNCEMENT_POSTED", id, title.take(120))
    }

    suspend fun deleteAnnouncement(id: String): Result<Unit> = runCatching {
        requireUid()
        db.collection("announcements").document(id).delete().await()
        logAudit("ANNOUNCEMENT_DELETED", id, "Removed announcement $id")
    }

    suspend fun updateAppSettings(
        bannerMessage: String,
        allowPosting: Boolean,
        allowProfileEdits: Boolean,
        maintenanceMode: Boolean
    ): Result<Unit> = runCatching {
        val uid = requireUid()
        db.collection("app_settings").document("global").set(
            mapOf(
                "appBannerMessage" to bannerMessage.trim().take(300),
                "allowUserPosting" to allowPosting,
                "allowUserProfileEdits" to allowProfileEdits,
                "maintenanceMode" to maintenanceMode,
                "updatedBy" to uid,
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()
        logAudit("APP_SETTINGS_UPDATED", "global", "Updated global SEPTIS controls (maintenance=$maintenanceMode)")
    }

    suspend fun grantAdminRole(uid: String, email: String, displayName: String): Result<Unit> = runCatching {
        val actor = requireUid()
        db.collection("admins").document(uid.trim()).set(
            mapOf(
                "uid" to uid.trim(),
                "email" to email.trim(),
                "displayName" to displayName.trim().ifBlank { "SEPTIS Admin" },
                "grantedBy" to actor,
                "createdAt" to FieldValue.serverTimestamp()
            )
        ).await()
        logAudit("ADMIN_GRANTED", uid.trim(), "Granted admin role to $email")
    }

    suspend fun revokeAdminRole(uid: String): Result<Unit> = runCatching {
        requireUid()
        db.collection("admins").document(uid).delete().await()
        logAudit("ADMIN_REVOKED", uid, "Revoked admin role from $uid")
    }

    private suspend fun logAudit(action: String, targetId: String, details: String) {
        val uid = auth.currentUser?.uid ?: return
        val email = requireEmail()
        val id = "log_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        try {
            db.collection("audit_logs").document(id).set(
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
        }
    }
}
