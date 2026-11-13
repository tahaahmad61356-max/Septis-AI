package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.Announcement
import com.example.data.model.AppSettings
import com.example.data.model.NotificationItem
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

class UserDashboardViewModel(
    private val repository: SeptisRepository,
    val currentUserId: String
) : ViewModel() {

    val accountState: StateFlow<UiState<UserAccount?>> = repository.observeMyAccount(currentUserId)
        .map<UserAccount?, UiState<UserAccount?>> { UiState.Success(it) }
        .catch { err ->
            val fs = err.unwrapFirestoreException()
            if (fs == null && err is CancellationException) throw err
            emit(UiState.Error((fs ?: err).localizedMessage ?: "Failed to load profile"))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val announcementsState: StateFlow<UiState<List<Announcement>>> = repository.observePublishedAnnouncements()
        .map<List<Announcement>, UiState<List<Announcement>>> { UiState.Success(it) }
        .catch { err ->
            val fs = err.unwrapFirestoreException()
            if (fs == null && err is CancellationException) throw err
            emit(UiState.Error((fs ?: err).localizedMessage ?: "Failed to load announcements"))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val recordsState: StateFlow<UiState<List<SeptisRecord>>> = repository.observeSharedRecords()
        .map<List<SeptisRecord>, UiState<List<SeptisRecord>>> { UiState.Success(it) }
        .catch { err ->
            val fs = err.unwrapFirestoreException()
            if (fs == null && err is CancellationException) throw err
            emit(UiState.Error((fs ?: err).localizedMessage ?: "Failed to load shared feed"))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val notificationsState: StateFlow<UiState<List<NotificationItem>>> = repository.observeMyNotifications(currentUserId)
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

    private val _hasPasscode = MutableStateFlow(false)
    val hasPasscode: StateFlow<Boolean> = _hasPasscode.asStateFlow()

    private val _feedbackMessage = MutableStateFlow<String?>(null)
    val feedbackMessage: StateFlow<String?> = _feedbackMessage.asStateFlow()

    init {
        viewModelScope.launch {
            _hasPasscode.value = repository.hasPasscodeSet(currentUserId)
        }
    }

    fun clearFeedback() {
        _feedbackMessage.value = null
    }

    fun updateMyProfile(displayName: String, bio: String, department: String, phone: String) {
        viewModelScope.launch {
            repository.updateMyProfile(currentUserId, displayName, bio, department, phone)
                .onSuccess { _feedbackMessage.value = "Profile saved" }
                .onFailure { _feedbackMessage.value = it.localizedMessage ?: "Profile update not permitted" }
        }
    }

    fun updateMyPasscode(newPasscode: String) {
        viewModelScope.launch {
            repository.updateMyPasscode(currentUserId, newPasscode)
                .onSuccess {
                    _hasPasscode.value = true
                    _feedbackMessage.value = "Personal passcode updated securely"
                }
                .onFailure { _feedbackMessage.value = it.localizedMessage ?: "Failed to update passcode" }
        }
    }

    fun createUserRecord(title: String, content: String, category: String, authorName: String) {
        viewModelScope.launch {
            repository.createRecord(
                title = title,
                content = content,
                category = category,
                authorName = authorName,
                isProtected = false,
                visibility = "all",
                status = "published"
            ).onSuccess {
                _feedbackMessage.value = "Published to SEPTIS Hub"
            }.onFailure {
                _feedbackMessage.value = it.localizedMessage ?: "Posting is restricted by Admin"
            }
        }
    }

    fun updateOwnRecord(recordId: String, title: String, content: String, category: String) {
        viewModelScope.launch {
            repository.updateRecordByUser(recordId, title, content, category)
                .onSuccess { _feedbackMessage.value = "Post updated" }
                .onFailure { _feedbackMessage.value = it.localizedMessage ?: "Cannot modify this record" }
        }
    }

    fun deleteOwnRecord(recordId: String) {
        viewModelScope.launch {
            repository.deleteRecord(recordId, isAdminAction = false)
                .onSuccess { _feedbackMessage.value = "Post deleted" }
                .onFailure { _feedbackMessage.value = it.localizedMessage ?: "Cannot delete protected record" }
        }
    }

    fun markNotificationRead(notificationId: String) {
        viewModelScope.launch {
            repository.markNotificationRead(notificationId)
        }
    }
}
