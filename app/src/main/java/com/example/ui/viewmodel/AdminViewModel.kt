package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.AdminMember
import com.example.data.model.Announcement
import com.example.data.model.AppSettings
import com.example.data.model.AuditLog
import com.example.data.model.Community
import com.example.data.model.Conversation
import com.example.data.model.ReportItem
import com.example.data.model.UserAccount
import com.example.data.repository.AdminRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class AdminTab {
    USERS,
    REPORTS,
    GROUPS,
    STATS_AND_SETTINGS
}

class AdminViewModel(
    private val adminRepo: AdminRepository,
    val adminUid: String,
    val adminName: String
) : ViewModel() {

    val users: StateFlow<List<UserAccount>> = adminRepo.observeAllUsers()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val reports: StateFlow<List<ReportItem>> = adminRepo.observeAllReports()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val groups: StateFlow<List<Conversation>> = adminRepo.observeAllGroups()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val communities: StateFlow<List<Community>> = adminRepo.observeAllCommunities()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val announcements: StateFlow<List<Announcement>> = adminRepo.observeAllAnnouncements()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val auditLogs: StateFlow<List<AuditLog>> = adminRepo.observeAuditLogs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val admins: StateFlow<List<AdminMember>> = adminRepo.observeAdmins()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val settings: StateFlow<AppSettings> = adminRepo.observeAppSettings()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettings())

    private val _selectedTab = MutableStateFlow(AdminTab.USERS)
    val selectedTab: StateFlow<AdminTab> = _selectedTab.asStateFlow()

    private val _feedback = MutableStateFlow<String?>(null)
    val feedback: StateFlow<String?> = _feedback.asStateFlow()

    fun selectTab(tab: AdminTab) {
        _selectedTab.value = tab
    }

    fun clearFeedback() {
        _feedback.value = null
    }

    fun setUserStatus(user: UserAccount, status: String) {
        viewModelScope.launch {
            adminRepo.setUserStatus(user, status)
                .onSuccess { _feedback.value = "${user.displayName} marked $status" }
                .onFailure { _feedback.value = it.localizedMessage }
        }
    }

    fun deleteUser(user: UserAccount) {
        viewModelScope.launch {
            adminRepo.deleteUserAccount(user)
                .onSuccess { _feedback.value = "Account deleted" }
                .onFailure { _feedback.value = it.localizedMessage }
        }
    }

    fun updateReportStatus(reportId: String, status: String) {
        viewModelScope.launch {
            adminRepo.updateReportStatus(reportId, status)
                .onSuccess { _feedback.value = "Report marked $status" }
                .onFailure { _feedback.value = it.localizedMessage }
        }
    }

    fun removeGroup(groupId: String) {
        viewModelScope.launch {
            adminRepo.removeGroupByAdmin(groupId)
                .onSuccess { _feedback.value = "Group removed" }
                .onFailure { _feedback.value = it.localizedMessage }
        }
    }

    fun removeCommunity(communityId: String) {
        viewModelScope.launch {
            adminRepo.removeCommunityByAdmin(communityId)
                .onSuccess { _feedback.value = "Community removed" }
                .onFailure { _feedback.value = it.localizedMessage }
        }
    }

    fun postAnnouncement(title: String, body: String, priority: String, pinned: Boolean) {
        viewModelScope.launch {
            adminRepo.postAnnouncement(title, body, priority, pinned, adminName)
                .onSuccess { _feedback.value = "Announcement published" }
                .onFailure { _feedback.value = it.localizedMessage }
        }
    }

    fun deleteAnnouncement(id: String) {
        viewModelScope.launch {
            adminRepo.deleteAnnouncement(id)
                .onSuccess { _feedback.value = "Announcement deleted" }
        }
    }

    fun saveSettings(banner: String, allowPosting: Boolean, allowProfile: Boolean, maintenance: Boolean) {
        viewModelScope.launch {
            adminRepo.updateAppSettings(banner, allowPosting, allowProfile, maintenance)
                .onSuccess { _feedback.value = "Global SEPTIS settings saved" }
                .onFailure { _feedback.value = it.localizedMessage }
        }
    }

    fun rotateAccessCode(current: String, next: String) {
        viewModelScope.launch {
            adminRepo.rotateAdminAccessCode(current, next)
                .onSuccess { _feedback.value = "Confidential Admin Access Code updated" }
                .onFailure { _feedback.value = it.localizedMessage }
        }
    }

    fun grantAdmin(uid: String, email: String, name: String) {
        viewModelScope.launch {
            adminRepo.grantAdminRole(uid, email, name)
                .onSuccess { _feedback.value = "Admin role granted to $email" }
                .onFailure { _feedback.value = it.localizedMessage }
        }
    }

    fun revokeAdmin(uid: String) {
        viewModelScope.launch {
            adminRepo.revokeAdminRole(uid)
                .onSuccess { _feedback.value = "Admin role revoked" }
                .onFailure { _feedback.value = it.localizedMessage }
        }
    }
}
