package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.auth.AuthManager
import com.example.data.remote.SeptisFirebaseProvider
import com.example.data.repository.SeptisRepository
import com.example.ui.screens.AdminDashboardScreen
import com.example.ui.screens.AdminLoginScreen
import com.example.ui.screens.UserDashboardScreen
import com.example.ui.screens.UserLoginScreen
import com.example.ui.screens.WelcomeScreen
import com.example.ui.theme.SeptisTheme
import com.example.ui.viewmodel.AdminDashboardViewModel
import com.example.ui.viewmodel.PortalRoute
import com.example.ui.viewmodel.SeptisAuthViewModel
import com.example.ui.viewmodel.UserDashboardViewModel
import com.example.util.ConnectivityObserver
import com.google.firebase.Firebase
import com.google.firebase.auth.auth

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SeptisTheme {
                SeptisAppRoot()
            }
        }
    }
}

@Composable
fun SeptisAppRoot() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }
    val connectivityObserver = remember(context) { ConnectivityObserver(context) }
    val isOnline by connectivityObserver.observeIsOnline().collectAsStateWithLifecycle(initialValue = true)

    val currentUser by AuthManager.authStateFlow(Firebase.auth)
        .collectAsStateWithLifecycle(initialValue = Firebase.auth.currentUser)

    val authViewModel: SeptisAuthViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY])
                val db = SeptisFirebaseProvider.getFirestore(app)
                SeptisAuthViewModel(SeptisRepository(db, Firebase.auth))
            }
        }
    )

    val route by authViewModel.route.collectAsStateWithLifecycle()
    val adminStage by authViewModel.adminStage.collectAsStateWithLifecycle()
    val userStage by authViewModel.userStage.collectAsStateWithLifecycle()
    val isBusy by authViewModel.isBusy.collectAsStateWithLifecycle()
    val errorBanner by authViewModel.errorBanner.collectAsStateWithLifecycle()

    Crossfade(targetState = route, label = "septis_portal_navigation") { activeRoute ->
        when (activeRoute) {
            PortalRoute.WELCOME -> {
                WelcomeScreen(
                    isOnline = isOnline,
                    onAdminLoginClick = {
                        authViewModel.openAdminLogin(currentUser?.uid)
                    },
                    onUserLoginClick = {
                        authViewModel.openUserLogin(
                            currentUid = currentUser?.uid,
                            email = currentUser?.email,
                            displayName = currentUser?.displayName
                        )
                    }
                )
            }

            PortalRoute.ADMIN_LOGIN -> {
                AdminLoginScreen(
                    stage = adminStage,
                    isBusy = isBusy,
                    errorBanner = errorBanner,
                    currentUserEmail = currentUser?.email,
                    onBack = authViewModel::navigateBackToWelcome,
                    onGoogleSignInClick = {
                        authViewModel.clearError()
                        AuthManager.launchInteractiveGoogleSignIn(
                            context = context,
                            credentialManager = credentialManager,
                            scope = scope,
                            onAuthSuccess = { _ -> authViewModel.onAdminGoogleSignedIn() },
                            onAuthError = { msg -> authViewModel.setError(msg) },
                            onAuthCancelled = { authViewModel.setError("Google Sign-In was cancelled.") }
                        )
                    },
                    onInitializeCode = authViewModel::initializeAdminCode,
                    onVerifyAccessCode = authViewModel::submitAdminAccessCode,
                    onSignOutClick = {
                        authViewModel.prepareAdminLogout {
                            AuthManager.signOut(credentialManager, scope) {
                                authViewModel.onSignedOutComplete()
                            }
                        }
                    }
                )
            }

            PortalRoute.USER_LOGIN -> {
                UserLoginScreen(
                    stage = userStage,
                    isBusy = isBusy,
                    errorBanner = errorBanner,
                    onBack = authViewModel::navigateBackToWelcome,
                    onGoogleSignInClick = {
                        authViewModel.clearError()
                        AuthManager.launchInteractiveGoogleSignIn(
                            context = context,
                            credentialManager = credentialManager,
                            scope = scope,
                            onAuthSuccess = { user ->
                                authViewModel.onUserGoogleSignedIn(
                                    uid = user.uid,
                                    email = user.email.orEmpty(),
                                    displayName = user.displayName.orEmpty()
                                )
                            },
                            onAuthError = { msg -> authViewModel.setError(msg) },
                            onAuthCancelled = { authViewModel.setError("Google Sign-In was cancelled.") }
                        )
                    },
                    onSignOutClick = {
                        AuthManager.signOut(credentialManager, scope) {
                            authViewModel.onSignedOutComplete()
                        }
                    }
                )
            }

            PortalRoute.ADMIN_DASHBOARD -> {
                val user = currentUser
                if (user == null) {
                    WelcomeScreen(
                        isOnline = isOnline,
                        onAdminLoginClick = { authViewModel.openAdminLogin(null) },
                        onUserLoginClick = { authViewModel.openUserLogin(null, null, null) }
                    )
                } else {
                    key("admin_${user.uid}") {
                        val adminViewModel: AdminDashboardViewModel = viewModel(
                            key = "admin_${user.uid}",
                            factory = viewModelFactory {
                                initializer {
                                    val app = checkNotNull(this[APPLICATION_KEY])
                                    val db = SeptisFirebaseProvider.getFirestore(app)
                                    AdminDashboardViewModel(
                                        repository = SeptisRepository(db, Firebase.auth),
                                        adminUid = user.uid,
                                        adminEmail = user.email.orEmpty(),
                                        adminName = user.displayName.orEmpty()
                                    )
                                }
                            }
                        )
                        AdminDashboardScreen(
                            viewModel = adminViewModel,
                            isOnline = isOnline,
                            onLogoutClick = {
                                authViewModel.prepareAdminLogout {
                                    AuthManager.signOut(credentialManager, scope) {
                                        authViewModel.onSignedOutComplete()
                                    }
                                }
                            }
                        )
                    }
                }
            }

            PortalRoute.USER_DASHBOARD -> {
                val user = currentUser
                if (user == null) {
                    WelcomeScreen(
                        isOnline = isOnline,
                        onAdminLoginClick = { authViewModel.openAdminLogin(null) },
                        onUserLoginClick = { authViewModel.openUserLogin(null, null, null) }
                    )
                } else {
                    key("user_${user.uid}") {
                        val userViewModel: UserDashboardViewModel = viewModel(
                            key = "user_${user.uid}",
                            factory = viewModelFactory {
                                initializer {
                                    val app = checkNotNull(this[APPLICATION_KEY])
                                    val db = SeptisFirebaseProvider.getFirestore(app)
                                    UserDashboardViewModel(
                                        repository = SeptisRepository(db, Firebase.auth),
                                        currentUserId = user.uid
                                    )
                                }
                            }
                        )
                        UserDashboardScreen(
                            viewModel = userViewModel,
                            isOnline = isOnline,
                            onLogoutClick = {
                                AuthManager.signOut(credentialManager, scope) {
                                    authViewModel.onSignedOutComplete()
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}
