package com.example.ui.viewmodel

import android.app.Activity
import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.auth.AuthManager
import com.example.auth.PhoneOtpCallback
import com.example.data.model.CountryCode
import com.example.data.model.CountryCodes
import com.example.data.model.PhoneUtils
import com.example.data.model.UserAccount
import com.example.data.remote.MediaBlobService
import com.example.data.repository.AdminRepository
import com.example.data.repository.AdminVaultCheckResult
import com.example.data.repository.MessagingRepository
import com.example.data.repository.PhoneClaimResult
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.PhoneAuthProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class AuthRoute {
    LOADING,
    PHONE_REGISTRATION,
    OTP_VERIFY,
    PROFILE_SETUP,
    ACCOUNT_SUSPENDED,
    HOME,
    ADMIN_LOGIN,
    ADMIN_DASHBOARD
}

sealed interface AdminAuthStage {
    data object NeedsGoogleAuth : AdminAuthStage
    data object SetupAccessCode : AdminAuthStage
    data object VerifyAccessCode : AdminAuthStage
    data class Denied(val reason: String) : AdminAuthStage
}

class AuthViewModel(
    private val messagingRepo: MessagingRepository,
    private val adminRepo: AdminRepository,
    private val mediaService: MediaBlobService
) : ViewModel() {

    private val _route = MutableStateFlow(AuthRoute.LOADING)
    val route: StateFlow<AuthRoute> = _route.asStateFlow()

    private val _selectedCountry = MutableStateFlow(CountryCodes.defaultCountry)
    val selectedCountry: StateFlow<CountryCode> = _selectedCountry.asStateFlow()

    private val _nationalNumber = MutableStateFlow("")
    val nationalNumber: StateFlow<String> = _nationalNumber.asStateFlow()

    private val _pendingPhoneE164 = MutableStateFlow("")
    val pendingPhoneE164: StateFlow<String> = _pendingPhoneE164.asStateFlow()

    private val _otpCode = MutableStateFlow("")
    val otpCode: StateFlow<String> = _otpCode.asStateFlow()

    private val _displayName = MutableStateFlow("")
    val displayName: StateFlow<String> = _displayName.asStateFlow()

    private val _about = MutableStateFlow("Hey there! I am using SEPTIS.")
    val about: StateFlow<String> = _about.asStateFlow()

    private val _selectedAvatarUri = MutableStateFlow<Uri?>(null)
    val selectedAvatarUri: StateFlow<Uri?> = _selectedAvatarUri.asStateFlow()

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    private val _errorBanner = MutableStateFlow<String?>(null)
    val errorBanner: StateFlow<String?> = _errorBanner.asStateFlow()

    private val _infoBanner = MutableStateFlow<String?>(null)
    val infoBanner: StateFlow<String?> = _infoBanner.asStateFlow()

    private val _adminStage = MutableStateFlow<AdminAuthStage>(AdminAuthStage.NeedsGoogleAuth)
    val adminStage: StateFlow<AdminAuthStage> = _adminStage.asStateFlow()

    private var verificationId: String? = null
    private var resendToken: PhoneAuthProvider.ForceResendingToken? = null
    private var linkPhoneToGoogleUser: Boolean = false

    fun selectCountry(country: CountryCode) {
        _selectedCountry.value = country
    }

    fun updateNationalNumber(value: String) {
        _nationalNumber.value = value.filter { it.isDigit() || it == ' ' || it == '-' }.take(18)
        _errorBanner.value = null
    }

    fun updateOtpCode(value: String) {
        _otpCode.value = value.filter { it.isDigit() }.take(6)
        _errorBanner.value = null
    }

    fun updateDisplayName(value: String) {
        _displayName.value = value.take(60)
        _errorBanner.value = null
    }

    fun updateAbout(value: String) {
        _about.value = value.take(150)
    }

    fun setAvatarUri(uri: Uri?) {
        _selectedAvatarUri.value = uri
    }

    fun clearBanners() {
        _errorBanner.value = null
        _infoBanner.value = null
    }

    fun setError(msg: String) {
        _errorBanner.value = msg
    }

    fun syncWithFirebaseUser(user: FirebaseUser?) {
        if (_route.value == AuthRoute.ADMIN_DASHBOARD || _route.value == AuthRoute.ADMIN_LOGIN) {
            return
        }
        if (user == null) {
            _route.value = AuthRoute.PHONE_REGISTRATION
            return
        }
        viewModelScope.launch {
            _isBusy.value = true
            val result = messagingRepo.getMyAccount(user.uid)
            _isBusy.value = false
            result.onSuccess { account ->
                routeFromAccount(user, account)
            }.onFailure { err ->
                _errorBanner.value = err.localizedMessage ?: "Failed to load account profile."
                _route.value = AuthRoute.PHONE_REGISTRATION
            }
        }
    }

    private fun routeFromAccount(user: FirebaseUser, account: UserAccount?) {
        if (account == null || account.phoneE164.isBlank()) {
            val authPhone = user.phoneNumber.orEmpty()
            if (authPhone.isNotBlank()) {
                val country = PhoneUtils.detectCountry(authPhone)
                _selectedCountry.value = country
                _pendingPhoneE164.value = authPhone
                _nationalNumber.value = PhoneUtils.extractNationalNumber(authPhone, country.dialCode)
                linkPhoneToGoogleUser = false
                if (_displayName.value.isBlank()) {
                    _displayName.value = user.displayName.orEmpty()
                }
                _route.value = AuthRoute.PROFILE_SETUP
            } else {
                // Google signed-in (e.g. account recovery) without phone yet -> require phone binding
                linkPhoneToGoogleUser = true
                if (_displayName.value.isBlank()) {
                    _displayName.value = user.displayName.orEmpty()
                }
                _infoBanner.value = "Account signed in. Enter and verify your phone number to finish SEPTIS setup."
                _route.value = AuthRoute.PHONE_REGISTRATION
            }
            return
        }

        if (account.status != "active") {
            _route.value = AuthRoute.ACCOUNT_SUSPENDED
        } else {
            _route.value = AuthRoute.HOME
        }
    }

    fun sendPhoneOtp(activity: Activity, isResend: Boolean = false) {
        val e164 = PhoneUtils.combineToE164(_selectedCountry.value.dialCode, _nationalNumber.value)
        if (e164 == null) {
            _errorBanner.value = "Enter a valid phone number (e.g. ${_selectedCountry.value.example})."
            return
        }

        viewModelScope.launch {
            _isBusy.value = true
            clearBanners()
            // Enforce unique account per phone number before sending OTP when claiming on a Google session
            val existing = messagingRepo.lookupDirectoryByPhoneExact(e164)
            val currentUid = messagingRepo.currentUid()
            if (existing != null && currentUid != null && linkPhoneToGoogleUser && existing.uid != currentUid) {
                _isBusy.value = false
                _errorBanner.value = "This phone number ($e164) is already registered to another SEPTIS account."
                return@launch
            }

            _pendingPhoneE164.value = e164
            AuthManager.sendPhoneOtp(
                activity = activity,
                phoneE164 = e164,
                resendToken = if (isResend) resendToken else null,
                linkToExistingUser = linkPhoneToGoogleUser,
                scope = viewModelScope
            ) { callback ->
                _isBusy.value = false
                when (callback) {
                    is PhoneOtpCallback.CodeSent -> {
                        verificationId = callback.verificationId
                        resendToken = callback.resendToken
                        _otpCode.value = ""
                        _infoBanner.value = "6-digit SMS verification code sent to $e164"
                        _route.value = AuthRoute.OTP_VERIFY
                    }
                    is PhoneOtpCallback.AutoVerified -> {
                        onPhoneVerified(callback.user)
                    }
                    is PhoneOtpCallback.Failed -> {
                        _errorBanner.value = callback.message
                    }
                }
            }
        }
    }

    fun verifyOtpCode() {
        val vid = verificationId
        val code = _otpCode.value.trim()
        if (vid.isNullOrBlank()) {
            _errorBanner.value = "Request an OTP code first."
            return
        }
        if (code.length != 6) {
            _errorBanner.value = "Enter the 6-digit verification code."
            return
        }
        viewModelScope.launch {
            _isBusy.value = true
            clearBanners()
            val result = AuthManager.verifyOtpCode(vid, code, linkToExistingUser = linkPhoneToGoogleUser)
            _isBusy.value = false
            result.onSuccess { user ->
                onPhoneVerified(user)
            }.onFailure { err ->
                _errorBanner.value = err.localizedMessage ?: "Invalid verification code."
            }
        }
    }

    private fun onPhoneVerified(user: FirebaseUser) {
        viewModelScope.launch {
            _isBusy.value = true
            val existing = messagingRepo.getMyAccount(user.uid).getOrNull()
            _isBusy.value = false
            if (existing != null && existing.phoneE164.isNotBlank()) {
                if (existing.status == "active") {
                    _route.value = AuthRoute.HOME
                } else {
                    _route.value = AuthRoute.ACCOUNT_SUSPENDED
                }
            } else {
                _displayName.value = existing?.displayName?.ifBlank { user.displayName.orEmpty() }
                    ?: user.displayName.orEmpty()
                _route.value = AuthRoute.PROFILE_SETUP
            }
        }
    }

    fun onGoogleRecoverySignedIn(user: FirebaseUser) {
        viewModelScope.launch {
            _isBusy.value = true
            clearBanners()
            val account = messagingRepo.getMyAccount(user.uid).getOrNull()
            _isBusy.value = false
            if (account != null && account.phoneE164.isNotBlank()) {
                routeFromAccount(user, account)
            } else {
                // Recovery / Google session established; now require phone registration
                linkPhoneToGoogleUser = true
                _displayName.value = user.displayName.orEmpty()
                val candidateE164 = PhoneUtils.combineToE164(_selectedCountry.value.dialCode, _nationalNumber.value)
                if (candidateE164 != null) {
                    // Check uniqueness immediately
                    val existing = messagingRepo.lookupDirectoryByPhoneExact(candidateE164)
                    if (existing != null && existing.uid != user.uid) {
                        _errorBanner.value = "Phone number $candidateE164 is already linked to another SEPTIS account."
                    } else {
                        _pendingPhoneE164.value = candidateE164
                        _route.value = AuthRoute.PROFILE_SETUP
                    }
                } else {
                    _infoBanner.value = "Signed in as ${user.email ?: "Google user"}. Enter your phone number below to complete setup."
                    _route.value = AuthRoute.PHONE_REGISTRATION
                }
            }
        }
    }

    fun backToPhoneRegistration() {
        clearBanners()
        _route.value = AuthRoute.PHONE_REGISTRATION
    }

    fun completeProfileSetup(context: Context) {
        val name = _displayName.value.trim()
        if (name.length < 2) {
            _errorBanner.value = "Enter your display name (at least 2 characters)."
            return
        }
        val e164 = _pendingPhoneE164.value.ifBlank {
            PhoneUtils.combineToE164(_selectedCountry.value.dialCode, _nationalNumber.value).orEmpty()
        }
        if (e164.isBlank()) {
            _errorBanner.value = "A verified phone number is required."
            _route.value = AuthRoute.PHONE_REGISTRATION
            return
        }

        viewModelScope.launch {
            _isBusy.value = true
            clearBanners()
            var photoBlobId: String? = null
            val avatarUri = _selectedAvatarUri.value
            val uid = messagingRepo.currentUid()
            if (avatarUri != null && uid != null) {
                val upload = mediaService.uploadImageUri(
                    context = context,
                    uri = avatarUri,
                    scope = "public",
                    allowedUids = listOf(uid)
                )
                upload.onSuccess { photoBlobId = it.blobId }
                    .onFailure {
                        _isBusy.value = false
                        _errorBanner.value = "Avatar upload failed: ${it.localizedMessage}"
                        return@launch
                    }
            }

            val country = PhoneUtils.detectCountry(e164)
            val national = PhoneUtils.extractNationalNumber(e164, country.dialCode)
            val claim = messagingRepo.registerOrUpdatePhoneProfile(
                phoneE164 = e164,
                countryCode = country.dialCode,
                nationalNumber = national,
                displayName = name,
                about = _about.value.ifBlank { "Hey there! I am using SEPTIS." },
                photoBlobId = photoBlobId
            )
            _isBusy.value = false
            claim.onSuccess { res ->
                when (res) {
                    is PhoneClaimResult.AlreadyClaimed -> {
                        _errorBanner.value = "Phone number ${res.phoneE164} already belongs to an active SEPTIS account."
                    }
                    is PhoneClaimResult.Success -> {
                        if (res.account.status == "active") {
                            _route.value = AuthRoute.HOME
                        } else {
                            _route.value = AuthRoute.ACCOUNT_SUSPENDED
                        }
                    }
                }
            }.onFailure { err ->
                _errorBanner.value = err.localizedMessage ?: "Failed to create SEPTIS profile."
            }
        }
    }

    // --- Admin Portal Gate ---

    fun openAdminGate(currentUid: String?) {
        clearBanners()
        _route.value = AuthRoute.ADMIN_LOGIN
        if (currentUid == null) {
            _adminStage.value = AdminAuthStage.NeedsGoogleAuth
        } else {
            verifyAdminAuthorization()
        }
    }

    fun onAdminGoogleSignedIn() {
        verifyAdminAuthorization()
    }

    private fun verifyAdminAuthorization() {
        viewModelScope.launch {
            _isBusy.value = true
            val res = adminRepo.checkAdminAuthorization()
            _isBusy.value = false
            res.onSuccess { check ->
                _adminStage.value = when (check) {
                    AdminVaultCheckResult.NeedsInitialSetup -> AdminAuthStage.SetupAccessCode
                    AdminVaultCheckResult.ReadyForCodeVerification -> AdminAuthStage.VerifyAccessCode
                    is AdminVaultCheckResult.Unauthorized -> AdminAuthStage.Denied(check.reason)
                }
            }.onFailure {
                _adminStage.value = AdminAuthStage.Denied(it.localizedMessage ?: "Admin verification failed.")
            }
        }
    }

    fun setupAdminCode(newCode: String, confirmCode: String) {
        if (newCode.length < 6) {
            _errorBanner.value = "Admin Access Code must be at least 6 characters."
            return
        }
        if (newCode != confirmCode) {
            _errorBanner.value = "Access codes do not match."
            return
        }
        viewModelScope.launch {
            _isBusy.value = true
            clearBanners()
            val res = adminRepo.setupInitialAdminAccessCode(newCode)
            _isBusy.value = false
            res.onSuccess { _route.value = AuthRoute.ADMIN_DASHBOARD }
                .onFailure { _errorBanner.value = it.localizedMessage ?: "Failed to save Admin code." }
        }
    }

    fun verifyAdminCode(enteredCode: String) {
        if (enteredCode.isBlank()) {
            _errorBanner.value = "Enter the Admin Access Code."
            return
        }
        viewModelScope.launch {
            _isBusy.value = true
            clearBanners()
            val res = adminRepo.verifyAdminAccessCode(enteredCode)
            _isBusy.value = false
            res.onSuccess { _route.value = AuthRoute.ADMIN_DASHBOARD }
                .onFailure { _errorBanner.value = it.localizedMessage ?: "Invalid Admin Access Code." }
        }
    }

    fun exitAdminToApp(currentUser: FirebaseUser?) {
        viewModelScope.launch {
            adminRepo.clearAdminSession()
            _route.value = AuthRoute.LOADING
            syncWithFirebaseUser(currentUser)
        }
    }

    fun prepareLogout(onReady: () -> Unit) {
        viewModelScope.launch {
            adminRepo.clearAdminSession()
            linkPhoneToGoogleUser = false
            verificationId = null
            _otpCode.value = ""
            onReady()
        }
    }

    fun onSignedOutComplete() {
        _route.value = AuthRoute.PHONE_REGISTRATION
        clearBanners()
    }
}
