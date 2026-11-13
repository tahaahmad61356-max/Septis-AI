package com.example.ui.viewmodel

import com.example.data.model.UserAccount
import com.example.data.repository.AdminVaultCheckResult
import com.example.data.repository.SeptisRepository
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class PortalRoute {
    WELCOME,
    ADMIN_LOGIN,
    USER_LOGIN,
    ADMIN_DASHBOARD,
    USER_DASHBOARD
}

sealed interface AdminAuthStage {
    data object NeedsGoogleSignIn : AdminAuthStage
    data object CheckingBackendAuth : AdminAuthStage
    data object NeedsInitialVaultSetup : AdminAuthStage
    data object NeedsAccessCode : AdminAuthStage
    data class Unauthorized(val reason: String) : AdminAuthStage
    data object Verified : AdminAuthStage
}

sealed interface UserAuthStage {
    data object NeedsGoogleSignIn : UserAuthStage
    data object LoadingProfile : UserAuthStage
    data class Restricted(val status: String, val email: String) : UserAuthStage
    data class Error(val message: String) : UserAuthStage
    data class Verified(val account: UserAccount) : UserAuthStage
}

class SeptisAuthViewModel(
    private val repository: SeptisRepository
) : ViewModel() {

    private val _route = MutableStateFlow(PortalRoute.WELCOME)
    val route: StateFlow<PortalRoute> = _route.asStateFlow()

    private val _adminStage = MutableStateFlow<AdminAuthStage>(AdminAuthStage.NeedsGoogleSignIn)
    val adminStage: StateFlow<AdminAuthStage> = _adminStage.asStateFlow()

    private val _userStage = MutableStateFlow<UserAuthStage>(UserAuthStage.NeedsGoogleSignIn)
    val userStage: StateFlow<UserAuthStage> = _userStage.asStateFlow()

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    private val _errorBanner = MutableStateFlow<String?>(null)
    val errorBanner: StateFlow<String?> = _errorBanner.asStateFlow()

    fun openAdminLogin(currentUid: String?) {
        _errorBanner.value = null
        _route.value = PortalRoute.ADMIN_LOGIN
        if (currentUid != null) {
            verifyAdminBackendAuthorization()
        } else {
            _adminStage.value = AdminAuthStage.NeedsGoogleSignIn
        }
    }

    fun openUserLogin(currentUid: String?, email: String?, displayName: String?) {
        _errorBanner.value = null
        _route.value = PortalRoute.USER_LOGIN
        if (currentUid != null) {
            loadOrCreateUserAccount(currentUid, email.orEmpty(), displayName.orEmpty())
        } else {
            _userStage.value = UserAuthStage.NeedsGoogleSignIn
        }
    }

    fun navigateBackToWelcome() {
        _errorBanner.value = null
        _route.value = PortalRoute.WELCOME
    }

    fun clearError() {
        _errorBanner.value = null
    }

    fun setError(message: String) {
        _errorBanner.value = message
    }

    fun onAdminGoogleSignedIn() {
        _errorBanner.value = null
        verifyAdminBackendAuthorization()
    }

    private fun verifyAdminBackendAuthorization() {
        viewModelScope.launch {
            _adminStage.value = AdminAuthStage.CheckingBackendAuth
            _isBusy.value = true
            val result = repository.checkAdminAuthorization()
            _isBusy.value = false
            result.onSuccess { check ->
                _adminStage.value = when (check) {
                    AdminVaultCheckResult.NeedsInitialSetup -> AdminAuthStage.NeedsInitialVaultSetup
                    AdminVaultCheckResult.ReadyForCodeVerification -> AdminAuthStage.NeedsAccessCode
                    is AdminVaultCheckResult.Unauthorized -> AdminAuthStage.Unauthorized(check.reason)
                }
            }.onFailure { err ->
                _adminStage.value = AdminAuthStage.Unauthorized(
                    err.localizedMessage ?: "Failed to verify Admin authorization."
                )
            }
        }
    }

    fun initializeAdminCode(code: String, confirmCode: String) {
        if (code.length < 6) {
            _errorBanner.value = "Admin Access Code must be at least 6 characters."
            return
        }
        if (code != confirmCode) {
            _errorBanner.value = "Access codes do not match."
            return
        }
        viewModelScope.launch {
            _isBusy.value = true
            _errorBanner.value = null
            val res = repository.setupInitialAdminAccessCode(code)
            _isBusy.value = false
            res.onSuccess {
                _adminStage.value = AdminAuthStage.Verified
                _route.value = PortalRoute.ADMIN_DASHBOARD
            }.onFailure { err ->
                _errorBanner.value = err.localizedMessage ?: "Failed to configure Admin Access Code."
            }
        }
    }

    fun submitAdminAccessCode(enteredCode: String) {
        if (enteredCode.isBlank()) {
            _errorBanner.value = "Enter your confidential Admin Access Code."
            return
        }
        viewModelScope.launch {
            _isBusy.value = true
            _errorBanner.value = null
            val res = repository.verifyAdminAccessCode(enteredCode)
            _isBusy.value = false
            res.onSuccess {
                _adminStage.value = AdminAuthStage.Verified
                _route.value = PortalRoute.ADMIN_DASHBOARD
            }.onFailure { err ->
                _errorBanner.value = err.localizedMessage ?: "Access denied. Invalid Admin Access Code."
            }
        }
    }

    fun onUserGoogleSignedIn(uid: String, email: String, displayName: String) {
        _errorBanner.value = null
        loadOrCreateUserAccount(uid, email, displayName)
    }

    private fun loadOrCreateUserAccount(uid: String, email: String, displayName: String) {
        viewModelScope.launch {
            _userStage.value = UserAuthStage.LoadingProfile
            _isBusy.value = true
            val res = repository.ensureUserAccount(uid, email, displayName)
            _isBusy.value = false
            res.onSuccess { account ->
                if (account.status == "active") {
                    _userStage.value = UserAuthStage.Verified(account)
                    _route.value = PortalRoute.USER_DASHBOARD
                } else {
                    _userStage.value = UserAuthStage.Restricted(account.status, account.email)
                }
            }.onFailure { err ->
                _userStage.value = UserAuthStage.Error(
                    err.localizedMessage ?: "Could not load your SEPTIS account."
                )
            }
        }
    }

    fun onSignedOutComplete() {
        _errorBanner.value = null
        _adminStage.value = AdminAuthStage.NeedsGoogleSignIn
        _userStage.value = UserAuthStage.NeedsGoogleSignIn
        _route.value = PortalRoute.WELCOME
    }

    fun prepareAdminLogout(onReadyToSignOut: () -> Unit) {
        viewModelScope.launch {
            repository.clearAdminSession()
            onReadyToSignOut()
        }
    }
}
