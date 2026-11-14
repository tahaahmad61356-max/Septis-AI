package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.credentials.CredentialManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.auth.AuthManager
import com.example.data.remote.MediaBlobService
import com.example.data.remote.SeptisFirebaseProvider
import com.example.data.repository.AdminRepository
import com.example.data.repository.MessagingRepository
import com.example.data.repository.SocialCallRepository
import com.example.notifications.NotificationHelper
import com.example.ui.screens.AccountSuspendedScreen
import com.example.ui.screens.ActiveCallScreen
import com.example.ui.screens.AdminDashboardScreen
import com.example.ui.screens.AdminGateScreen
import com.example.ui.screens.ChatDetailScreen
import com.example.ui.screens.HomeShellScreen
import com.example.ui.screens.NewChatScreen
import com.example.ui.screens.OtpVerificationScreen
import com.example.ui.screens.PhoneRegistrationScreen
import com.example.ui.screens.ProfileSetupScreen
import com.example.ui.theme.SeptisTheme
import com.example.ui.viewmodel.AdminViewModel
import com.example.ui.viewmodel.AuthRoute
import com.example.ui.viewmodel.AuthViewModel
import com.example.ui.viewmodel.CallViewModel
import com.example.ui.viewmodel.ChatViewModel
import com.example.ui.viewmodel.HomeViewModel
import com.example.ui.viewmodel.NewChatViewModel
import com.example.util.ConnectivityObserver
import com.example.util.ThemeMode
import com.example.util.ThemePrefs
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        NotificationHelper.ensureChannels(this)
        setContent {
            val themePrefs = remember(applicationContext) { ThemePrefs(applicationContext) }
            val themeMode by themePrefs.themeModeFlow.collectAsStateWithLifecycle(initialValue = ThemeMode.SYSTEM)
            val useDark = when (themeMode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
            }
            SeptisTheme(darkTheme = useDark) {
                SeptisMessagingRoot(
                    themePrefs = themePrefs,
                    themeMode = themeMode
                )
            }
        }
    }
}

private enum class AuthenticatedSubScreen {
    HOME,
    NEW_CHAT,
    CHAT_DETAIL
}

@Composable
fun SeptisMessagingRoot(
    themePrefs: ThemePrefs,
    themeMode: ThemeMode
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }
    val connectivityObserver = remember(context) { ConnectivityObserver(context) }
    val isOnline by connectivityObserver.observeIsOnline().collectAsStateWithLifecycle(initialValue = true)

    val db = remember(context) { SeptisFirebaseProvider.getFirestore(context) }
    val mediaService = remember(db) { MediaBlobService(db, Firebase.auth) }
    val currentUser by AuthManager.authStateFlow(Firebase.auth)
        .collectAsStateWithLifecycle(initialValue = Firebase.auth.currentUser)

    val authViewModel: AuthViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY])
                val firestore = SeptisFirebaseProvider.getFirestore(app)
                AuthViewModel(
                    messagingRepo = MessagingRepository(firestore, Firebase.auth),
                    adminRepo = AdminRepository(firestore, Firebase.auth),
                    mediaService = MediaBlobService(firestore, Firebase.auth)
                )
            }
        }
    )

    LaunchedEffect(currentUser?.uid) {
        authViewModel.syncWithFirebaseUser(currentUser)
    }

    val route by authViewModel.route.collectAsStateWithLifecycle()
    val selectedCountry by authViewModel.selectedCountry.collectAsStateWithLifecycle()
    val nationalNumber by authViewModel.nationalNumber.collectAsStateWithLifecycle()
    val pendingPhone by authViewModel.pendingPhoneE164.collectAsStateWithLifecycle()
    val otpCode by authViewModel.otpCode.collectAsStateWithLifecycle()
    val displayName by authViewModel.displayName.collectAsStateWithLifecycle()
    val about by authViewModel.about.collectAsStateWithLifecycle()
    val avatarUri by authViewModel.selectedAvatarUri.collectAsStateWithLifecycle()
    val isBusy by authViewModel.isBusy.collectAsStateWithLifecycle()
    val errorBanner by authViewModel.errorBanner.collectAsStateWithLifecycle()
    val infoBanner by authViewModel.infoBanner.collectAsStateWithLifecycle()
    val adminStage by authViewModel.adminStage.collectAsStateWithLifecycle()

    when (route) {
        AuthRoute.LOADING -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }

        AuthRoute.PHONE_REGISTRATION -> {
            PhoneRegistrationScreen(
                selectedCountry = selectedCountry,
                nationalNumber = nationalNumber,
                isBusy = isBusy,
                errorBanner = errorBanner,
                infoBanner = infoBanner,
                onSelectCountry = authViewModel::selectCountry,
                onPhoneChanged = authViewModel::updateNationalNumber,
                onSendOtpClick = { activity -> authViewModel.sendPhoneOtp(activity) },
                onGoogleRecoveryClick = {
                    authViewModel.clearBanners()
                    AuthManager.launchInteractiveGoogleSignIn(
                        context = context,
                        credentialManager = credentialManager,
                        scope = scope,
                        onAuthSuccess = { user -> authViewModel.onGoogleRecoverySignedIn(user) },
                        onAuthError = { msg -> authViewModel.setError(msg) },
                        onAuthCancelled = { authViewModel.setError("Google Sign-In cancelled.") }
                    )
                },
                onAdminPortalClick = { authViewModel.openAdminGate(currentUser?.uid) }
            )
        }

        AuthRoute.OTP_VERIFY -> {
            OtpVerificationScreen(
                phoneE164 = pendingPhone,
                otpCode = otpCode,
                isBusy = isBusy,
                errorBanner = errorBanner,
                infoBanner = infoBanner,
                onOtpChanged = authViewModel::updateOtpCode,
                onVerifyClick = authViewModel::verifyOtpCode,
                onResendClick = { activity -> authViewModel.sendPhoneOtp(activity, isResend = true) },
                onBackClick = authViewModel::backToPhoneRegistration
            )
        }

        AuthRoute.PROFILE_SETUP -> {
            ProfileSetupScreen(
                phoneE164 = pendingPhone,
                displayName = displayName,
                about = about,
                avatarUri = avatarUri,
                isBusy = isBusy,
                errorBanner = errorBanner,
                onDisplayNameChanged = authViewModel::updateDisplayName,
                onAboutChanged = authViewModel::updateAbout,
                onAvatarSelected = authViewModel::setAvatarUri,
                onSaveProfileClick = { authViewModel.completeProfileSetup(context) },
                onLogoutClick = {
                    authViewModel.prepareLogout {
                        AuthManager.signOut(credentialManager, scope) {
                            authViewModel.onSignedOutComplete()
                        }
                    }
                }
            )
        }

        AuthRoute.ACCOUNT_SUSPENDED -> {
            AccountSuspendedScreen(
                onLogoutClick = {
                    authViewModel.prepareLogout {
                        AuthManager.signOut(credentialManager, scope) {
                            authViewModel.onSignedOutComplete()
                        }
                    }
                }
            )
        }

        AuthRoute.ADMIN_LOGIN -> {
            AdminGateScreen(
                stage = adminStage,
                isBusy = isBusy,
                errorBanner = errorBanner,
                currentUserEmail = currentUser?.email,
                onBack = { authViewModel.exitAdminToApp(currentUser) },
                onGoogleSignInClick = {
                    authViewModel.clearBanners()
                    AuthManager.launchInteractiveGoogleSignIn(
                        context = context,
                        credentialManager = credentialManager,
                        scope = scope,
                        onAuthSuccess = { _ -> authViewModel.onAdminGoogleSignedIn() },
                        onAuthError = { msg -> authViewModel.setError(msg) },
                        onAuthCancelled = { authViewModel.setError("Google Sign-In cancelled.") }
                    )
                },
                onSetupCode = authViewModel::setupAdminCode,
                onVerifyCode = authViewModel::verifyAdminCode,
                onSignOutClick = {
                    authViewModel.prepareLogout {
                        AuthManager.signOut(credentialManager, scope) {
                            authViewModel.onSignedOutComplete()
                        }
                    }
                }
            )
        }

        AuthRoute.ADMIN_DASHBOARD -> {
            val user = currentUser
            if (user == null) {
                authViewModel.exitAdminToApp(null)
            } else {
                key("admin_${user.uid}") {
                    val adminVm: AdminViewModel = viewModel(
                        key = "admin_${user.uid}",
                        factory = viewModelFactory {
                            initializer {
                                val app = checkNotNull(this[APPLICATION_KEY])
                                val firestore = SeptisFirebaseProvider.getFirestore(app)
                                AdminViewModel(
                                    adminRepo = AdminRepository(firestore, Firebase.auth),
                                    adminUid = user.uid,
                                    adminName = user.displayName.orEmpty().ifBlank { "SEPTIS Admin" }
                                )
                            }
                        }
                    )
                    AdminDashboardScreen(
                        adminViewModel = adminVm,
                        onExitAdmin = { authViewModel.exitAdminToApp(user) },
                        onLogoutClick = {
                            authViewModel.prepareLogout {
                                AuthManager.signOut(credentialManager, scope) {
                                    authViewModel.onSignedOutComplete()
                                }
                            }
                        }
                    )
                }
            }
        }

        AuthRoute.HOME -> {
            val user = currentUser
            if (user == null) {
                authViewModel.onSignedOutComplete()
            } else {
                AuthenticatedMessagingApp(
                    uid = user.uid,
                    mediaService = mediaService,
                    isOnline = isOnline,
                    themeMode = themeMode,
                    onThemeModeChange = { next ->
                        scope.launch { themePrefs.setThemeMode(next) }
                    },
                    onOpenAdminPortal = { authViewModel.openAdminGate(user.uid) },
                    onLogoutClick = {
                        authViewModel.prepareLogout {
                            AuthManager.signOut(credentialManager, scope) {
                                authViewModel.onSignedOutComplete()
                            }
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun AuthenticatedMessagingApp(
    uid: String,
    mediaService: MediaBlobService,
    isOnline: Boolean,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    onOpenAdminPortal: () -> Unit,
    onLogoutClick: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    key("session_$uid") {
        val homeViewModel: HomeViewModel = viewModel(
            key = "home_$uid",
            factory = viewModelFactory {
                initializer {
                    val app = checkNotNull(this[APPLICATION_KEY])
                    val firestore = SeptisFirebaseProvider.getFirestore(app)
                    HomeViewModel(
                        messagingRepo = MessagingRepository(firestore, Firebase.auth),
                        socialCallRepo = SocialCallRepository(firestore, Firebase.auth),
                        mediaService = MediaBlobService(firestore, Firebase.auth),
                        currentUid = uid
                    )
                }
            }
        )
        val callViewModel: CallViewModel = viewModel(
            key = "calls_$uid",
            factory = viewModelFactory {
                initializer {
                    val app = checkNotNull(this[APPLICATION_KEY])
                    val firestore = SeptisFirebaseProvider.getFirestore(app)
                    CallViewModel(
                        context = app,
                        socialCallRepo = SocialCallRepository(firestore, Firebase.auth),
                        currentUid = uid
                    )
                }
            }
        )

        val account by homeViewModel.account.collectAsStateWithLifecycle()
        val activeCall by callViewModel.activeCall.collectAsStateWithLifecycle()
        var subScreen by remember { mutableStateOf(AuthenticatedSubScreen.HOME) }
        var activeConversationId by remember { mutableStateOf<String?>(null) }
        var showCallOverlay by remember { mutableStateOf(false) }

        val notifPermLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { }

        val callPermsLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { }

        LaunchedEffect(uid) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val granted = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
                if (!granted) notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            try {
                val token = FirebaseMessaging.getInstance().token.await()
                val firestore = SeptisFirebaseProvider.getFirestore(context)
                firestore.collection("users").document(uid)
                    .update(mapOf("fcmToken" to token, "updatedAt" to FieldValue.serverTimestamp()))
                    .await()
            } catch (_: Exception) {
            }
        }

        LaunchedEffect(activeCall?.id) {
            if (activeCall != null) showCallOverlay = true
        }

        DisposableEffect(lifecycleOwner, uid) {
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_RESUME -> homeViewModel.setOnlinePresence(true)
                    Lifecycle.Event.ON_PAUSE -> homeViewModel.setOnlinePresence(false)
                    else -> {}
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
                homeViewModel.setOnlinePresence(false)
            }
        }

        fun initiateCall(peerUid: String, peerName: String, peerPhone: String?, isVideo: Boolean) {
            val needed = if (isVideo) {
                arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA)
            } else {
                arrayOf(Manifest.permission.RECORD_AUDIO)
            }
            val missing = needed.any {
                ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
            }
            if (missing) {
                callPermsLauncher.launch(needed)
                return
            }
            val me = account
            callViewModel.startOutgoingCall(
                callerName = me?.displayName ?: "SEPTIS User",
                callerPhone = me?.phoneE164,
                calleeUid = peerUid,
                calleeName = peerName,
                calleePhone = peerPhone,
                isVideo = isVideo
            )
            showCallOverlay = true
        }

        val currentCall = activeCall
        if (showCallOverlay && currentCall != null) {
            ActiveCallScreen(
                call = currentCall,
                callViewModel = callViewModel,
                onMinimize = { showCallOverlay = false }
            )
        } else {
            when (subScreen) {
                AuthenticatedSubScreen.HOME -> {
                    HomeShellScreen(
                        homeViewModel = homeViewModel,
                        mediaService = mediaService,
                        isOnline = isOnline,
                        themeMode = themeMode,
                        onThemeModeChange = onThemeModeChange,
                        activeCall = activeCall,
                        onOpenNewChat = { subScreen = AuthenticatedSubScreen.NEW_CHAT },
                        onOpenConversation = { convId ->
                            activeConversationId = convId
                            subScreen = AuthenticatedSubScreen.CHAT_DETAIL
                        },
                        onStartVoiceCall = { pUid, pName, pPhone -> initiateCall(pUid, pName, pPhone, false) },
                        onStartVideoCall = { pUid, pName, pPhone -> initiateCall(pUid, pName, pPhone, true) },
                        onReturnToActiveCall = { showCallOverlay = true },
                        onOpenAdminPortal = onOpenAdminPortal,
                        onLogoutClick = onLogoutClick
                    )
                }

                AuthenticatedSubScreen.NEW_CHAT -> {
                    val newChatVm: NewChatViewModel = viewModel(
                        key = "new_chat_$uid",
                        factory = viewModelFactory {
                            initializer {
                                val app = checkNotNull(this[APPLICATION_KEY])
                                val firestore = SeptisFirebaseProvider.getFirestore(app)
                                NewChatViewModel(
                                    messagingRepo = MessagingRepository(firestore, Firebase.auth),
                                    currentUid = uid
                                )
                            }
                        }
                    )
                    NewChatScreen(
                        newChatViewModel = newChatVm,
                        homeViewModel = homeViewModel,
                        mediaService = mediaService,
                        onBack = { subScreen = AuthenticatedSubScreen.HOME },
                        onConversationOpened = { convId ->
                            activeConversationId = convId
                            subScreen = AuthenticatedSubScreen.CHAT_DETAIL
                        }
                    )
                }

                AuthenticatedSubScreen.CHAT_DETAIL -> {
                    val convId = activeConversationId
                    if (convId == null) {
                        subScreen = AuthenticatedSubScreen.HOME
                    } else {
                        key("chat_${uid}_$convId") {
                            val chatVm: ChatViewModel = viewModel(
                                key = "chat_${uid}_$convId",
                                factory = viewModelFactory {
                                    initializer {
                                        val app = checkNotNull(this[APPLICATION_KEY])
                                        val firestore = SeptisFirebaseProvider.getFirestore(app)
                                        ChatViewModel(
                                            messagingRepo = MessagingRepository(firestore, Firebase.auth),
                                            socialCallRepo = SocialCallRepository(firestore, Firebase.auth),
                                            mediaService = MediaBlobService(firestore, Firebase.auth),
                                            conversationId = convId,
                                            currentUid = uid
                                        )
                                    }
                                }
                            )
                            ChatDetailScreen(
                                chatViewModel = chatVm,
                                homeViewModel = homeViewModel,
                                mediaService = mediaService,
                                onBack = { subScreen = AuthenticatedSubScreen.HOME },
                                onStartVoiceCall = { pUid, pName, pPhone -> initiateCall(pUid, pName, pPhone, false) },
                                onStartVideoCall = { pUid, pName, pPhone -> initiateCall(pUid, pName, pPhone, true) }
                            )
                        }
                    }
                }
            }
        }
    }
}
