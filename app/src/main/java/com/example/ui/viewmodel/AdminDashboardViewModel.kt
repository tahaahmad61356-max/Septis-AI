package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.AdminMember
import com.example.data.model.Announcement
import com.example.data.model.AppSettings
import com.example.data.model.AuditLog
import com.example.data.model.NotificationItem
import com.example.data.model.PendingAccount
import com.example.data.model.SeptisRecord
import com.example.data.model.UserAccount
import com.example.data.remote.unwrapFirestoreException
import com.example.data.repository.SeptisRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

class AdminDashboardViewModel(
    private val repository: SeptisRepository,
    val adminUid: String,
    val adminEmail: String,
    val adminName: String
) : ViewModel() {

    val usersState: StateFlow<UiState<List<UserAccount>>> = repository.observeAllUsersByAdmin()
        .map<List<UserAccount>, UiState<List<UserAccount>>> { UiState.Success(it) }
        .catch { err ->
            val fs = err.unwrapFirestoreException()
            if (fs == null && err is CancellationException) throw err
            emit(UiState.Error((fs ?: err).localizedMessage ?: "Failed to load users"))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val pendingAccountsState: StateFlow<UiState<List<PendingAccount>>> = repository.observePendingAccountsByAdmin()
        .map<List<PendingAccount>, UiState<List<PendingAccount>>> { UiState.Success(it) }
        .catch { err ->
            val fs = err.unwrapFirestoreException()
            if (fs == null && err is CancellationException) throw err
            emit(UiState.Error((fs ?: err).localizedMessage ?: "Failed to load pending invites"))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val announcementsState: StateFlow<UiState<List<Announcement>>> = repository.observeAllAnnouncements()
        .map<List<Announcement>, UiState<List<Announcement>>> { UiState.Success(it) }
        .catch { err ->
            val fs = err.unwrapFirestoreException()
            if (fs == null && err is CancellationException) throw err
            emit(UiState.Error((fs ?: err).localizedMessage ?: "Failed to load announcements"))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val recordsState: StateFlow<UiState<List<SeptisRecord>>> = repository.observeAllRecordsByAdmin()
        .map<List<SeptisRecord>, UiState<List<SeptisRecord>>> { UiState.Success(it) }
        .catch { err ->
            val fs = err.unwrapFirestoreException()
            if (fs == null && err is CancellationException) throw err
            emit(UiState.Error((fs ?: err).localizedMessage ?: "Failed to load records"))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val notificationsState: StateFlow<UiState<List<NotificationItem>>> = repository.observeAllNotificationsByAdmin()
        .map<List<NotificationItem>, UiState<List<NotificationItem>>> { UiState.Success(it) }
        .catch { err ->
            val fs = err.unwrapFirestoreException()
            if (fs == null && err is CancellationException) throw err
            emit(UiState.Error((fs ?: err).localizedMessage ?: "Failed to load notifications"))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val appSettingsState: StateFlow<AppSettings?> = repository.observeAppSettings()
        .catch { err ->
            val fs = err.unwrapFirestoreException()
            if (fs == null && err is CancellationException) throw err
            emit(null)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

    val adminsState: StateFlow<List<AdminMember>> = repository.observeAdmins()
        .catch { err ->
            val fs = err.unwrapFirestoreException()
            if (fs == null && err is CancellationException) throw err
            emit(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val auditLogsState: StateFlow<List<AuditLog>> = repository.observeAuditLogs()
        .catch { err ->
            val fs = err.unwrapFirestoreException()
            if (fs == null && err is CancellationException) throw err
            emit(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    private val _feedbackMessage = MutableStateFlow<String?>(null)
    val feedbackMessage: StateFlow<String?> = _feedbackMessage.asStateFlow()

    fun clearFeedback() {
        _feedbackMessage.value = null
    }

    fun createPendingUser(
        email: String,
        displayName: String,
        department: String,
        bio: String,
        phone: String,
        status: String,
        canEditProfile: Boolean,
        canPostContent: Boolean
    ) {
        viewModelScope.launch {
            repository.createPendingAccountByAdmin(
                email, displayName, department, bio, phone, status, canEditProfile, canPostContent
            ).onSuccess {
                _feedbackMessage.value = "User account provisioned for $email"
            }.onFailure {
                _feedbackMessage.value = it.localizedMessage ?: "Failed to create user account"
            }
        }
    }

    fun updateUser(
        userId: String,
        displayName: String,
        department: String,
        bio: String,
        phone: String,
        status: String,
        canEditProfile: Boolean,
        canPostContent: Boolean
    ) {
        viewModelScope.launch {
            repository.updateUserByAdmin(
                userId, displayName, department, bio, phone, status, canEditProfile, canPostContent
            ).onSuccess {
                _feedbackMessage.value = "Updated user $displayName"
            }.onFailure {
                _feedbackMessage.value = it.localizedMessage ?: "Failed to update user"
            }
        }
    }

    fun setUserStatus(userId: String, status: String) {
        viewModelScope.launch {
            repository.setUserStatusByAdmin(userId, status).onSuccess {
                _feedbackMessage.value = "Account status set to $status"
            }.onFailure {
                _feedbackMessage.value = it.localizedMessage ?: "Failed to change account status"
            }
        }
    }

    fun deleteUser(userId: String) {
        viewModelScope.launch {
            repository.deleteUserByAdmin(userId).onSuccess {
                _feedbackMessage.value = "User account deleted"
            }.onFailure {
                _feedbackMessage.value = it.localizedMessage ?: "Failed to delete user"
            }
        }
    }

    fun deletePendingInvite(inviteId: String) {
        viewModelScope.launch {
            repository.deletePendingAccountByAdmin(inviteId).onSuccess {
                _feedbackMessage.value = "Pending invite removed"
            }.onFailure {
                _feedbackMessage.value = it.localizedMessage ?: "Failed to remove invite"
            }
        }
    }

    fun saveAnnouncement(
        id: String?,
        title: String,
        body: String,
        priority: String,
        pinned: Boolean,
        visibility: String
    ) {
        viewModelScope.launch {
            repository.saveAnnouncementByAdmin(
                id, title, body, priority, pinned, visibility, adminName.ifBlank { "SEPTIS Admin" }
            ).onSuccess {
                _feedbackMessage.value = if (id == null) "Announcement published" else "Announcement updated"
            }.onFailure {
                _feedbackMessage.value = it.localizedMessage ?: "Failed to save announcement"
            }
        }
    }

    fun deleteAnnouncement(id: String) {
        viewModelScope.launch {
            repository.deleteAnnouncementByAdmin(id).onSuccess {
                _feedbackMessage.value = "Announcement deleted"
            }.onFailure {
                _feedbackMessage.value = it.localizedMessage ?: "Failed to delete announcement"
            }
        }
    }

    fun createAdminRecord(
        title: String,
        content: String,
        category: String,
        status: String,
        visibility: String,
        isProtected: Boolean
    ) {
        viewModelScope.launch {
            repository.createRecord(
                title = title,
                content = content,
                category = category,
                authorName = adminName.ifBlank { "SEPTIS Admin" },
                isProtected = isProtected,
                visibility = visibility,
                status = status
            ).onSuccess {
                _feedbackMessage.value = "Record saved"
            }.onFailure {
                _feedbackMessage.value = it.localizedMessage ?: "Failed to create record"
            }
        }
    }

    fun updateAdminRecord(
        recordId: String,
        title: String,
        content: String,
        category: String,
        status: String,
        visibility: String,
        isProtected: Boolean
    ) {
        viewModelScope.launch {
            repository.updateRecordByAdmin(
                recordId, title, content, category, status, visibility, isProtected
            ).onSuccess {
                _feedbackMessage.value = "Record updated"
            }.onFailure {
                _feedbackMessage.value = it.localizedMessage ?: "Failed to update record"
            }
        }
    }

    fun deleteRecord(recordId: String) {
        viewModelScope.launch {
            repository.deleteRecord(recordId, isAdminAction = true).onSuccess {
                _feedbackMessage.value = "Record deleted"
            }.onFailure {
                _feedbackMessage.value = it.localizedMessage ?: "Failed to delete record"
            }
        }
    }

    fun sendNotification(recipientUid: String, title: String, message: String, type: String) {
        viewModelScope.launch {
            repository.sendNotificationByAdmin(recipientUid, title, message, type).onSuccess {
                _feedbackMessage.value = "Notification dispatched"
            }.onFailure {
                _feedbackMessage.value = it.localizedMessage ?: "Failed to send notification"
            }
        }
    }

    fun deleteNotification(notificationId: String) {
        viewModelScope.launch {
            repository.deleteNotificationByAdmin(notificationId).onSuccess {
                _feedbackMessage.value = "Notification removed"
            }.onFailure {
                _feedbackMessage.value = it.localizedMessage ?: "Failed to delete notification"
            }
        }
    }

    fun saveAppSettings(
        bannerMessage: String,
        allowUserPosting: Boolean,
        allowUserProfileEdits: Boolean,
        maintenanceMode: Boolean
    ) {
        viewModelScope.launch {
            repository.saveAppSettingsByAdmin(
                bannerMessage, allowUserPosting, allowUserProfileEdits, maintenanceMode
            ).onSuccess {
                _feedbackMessage.value = "Application settings updated"
            }.onFailure {
                _feedbackMessage.value = it.localizedMessage ?: "Failed to update settings"
            }
        }
    }

    fun rotateAdminCode(currentCode: String, newCode: String) {
        viewModelScope.launch {
            repository.rotateAdminAccessCode(currentCode, newCode).onSuccess {
                _feedbackMessage.value = "Admin Access Code rotated securely"
            }.onFailure {
                _feedbackMessage.value = it.localizedMessage ?: "Failed to rotate Admin Access Code"
            }
        }
    }

    fun grantAdminAccess(targetUid: String, email: String, displayName: String) {
        viewModelScope.launch {
            repository.grantAdminRoleByOwner(targetUid, email, displayName).onSuccess {
                _feedbackMessage.value = "Authorized $email as Admin"
            }.onFailure {
                _feedbackMessage.value = it.localizedMessage ?: "Only root Owner can grant Admin access"
            }
        }
    }

    fun revokeAdminAccess(targetUid: String) {
        viewModelScope.launch {
            repository.revokeAdminRoleByOwner(targetUid).onSuccess {
                _feedbackMessage.value = "Admin access revoked"
            }.onFailure {
                _feedbackMessage.value = it.localizedMessage ?: "Failed to revoke Admin access"
            }
        }
    }
}
