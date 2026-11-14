package com.example.auth

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.example.R
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.Firebase
import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

sealed interface PhoneOtpCallback {
    data class CodeSent(
        val verificationId: String,
        val resendToken: PhoneAuthProvider.ForceResendingToken
    ) : PhoneOtpCallback

    data class AutoVerified(val user: FirebaseUser) : PhoneOtpCallback
    data class Failed(val message: String) : PhoneOtpCallback
}

object AuthManager {
    private const val TAG = "Auth"

    fun authStateFlow(auth: FirebaseAuth = Firebase.auth): Flow<FirebaseUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            trySend(firebaseAuth.currentUser)
        }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    fun sendPhoneOtp(
        activity: Activity,
        phoneE164: String,
        resendToken: PhoneAuthProvider.ForceResendingToken? = null,
        linkToExistingUser: Boolean = false,
        scope: CoroutineScope,
        onResult: (PhoneOtpCallback) -> Unit
    ) {
        val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
            override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                scope.launch {
                    try {
                        val user = completeWithPhoneCredential(credential, linkToExistingUser)
                        onResult(PhoneOtpCallback.AutoVerified(user))
                    } catch (e: Exception) {
                        onResult(PhoneOtpCallback.Failed(e.localizedMessage ?: "Automatic OTP verification failed."))
                    }
                }
            }

            override fun onVerificationFailed(e: FirebaseException) {
                Log.e(TAG, "Phone verification failed for $phoneE164", e)
                val friendly = when {
                    e.message?.contains("operation-not-allowed", ignoreCase = true) == true ->
                        "Firebase Phone Authentication is not enabled yet in Firebase Console. Enable Phone under Authentication -> Sign-in method, or use Google Sign-In below."
                    e.message?.contains("TOO_MANY_REQUESTS", ignoreCase = true) == true ->
                        "Too many OTP attempts. Please wait a few minutes and try again."
                    else -> e.localizedMessage ?: "Failed to send SMS OTP."
                }
                onResult(PhoneOtpCallback.Failed(friendly))
            }

            override fun onCodeSent(
                verificationId: String,
                token: PhoneAuthProvider.ForceResendingToken
            ) {
                onResult(PhoneOtpCallback.CodeSent(verificationId, token))
            }
        }

        val builder = PhoneAuthOptions.newBuilder(Firebase.auth)
            .setPhoneNumber(phoneE164)
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(activity)
            .setCallbacks(callbacks)
        if (resendToken != null) {
            builder.setForceResendingToken(resendToken)
        }
        PhoneAuthProvider.verifyPhoneNumber(builder.build())
    }

    suspend fun verifyOtpCode(
        verificationId: String,
        smsCode: String,
        linkToExistingUser: Boolean = false
    ): Result<FirebaseUser> = runCatching {
        val credential = PhoneAuthProvider.getCredential(verificationId, smsCode.trim())
        completeWithPhoneCredential(credential, linkToExistingUser)
    }

    private suspend fun completeWithPhoneCredential(
        credential: PhoneAuthCredential,
        linkToExistingUser: Boolean
    ): FirebaseUser {
        val currentUser = Firebase.auth.currentUser
        val result = if (linkToExistingUser && currentUser != null) {
            try {
                currentUser.linkWithCredential(credential).await()
            } catch (_: FirebaseAuthUserCollisionException) {
                // Phone already owns an account; sign into that verified phone account cleanly
                Firebase.auth.signInWithCredential(credential).await()
            }
        } else {
            Firebase.auth.signInWithCredential(credential).await()
        }
        return checkNotNull(result.user) { "Phone authentication succeeded without user identity." }
    }

    fun attemptAutoSignIn(
        context: Context,
        credentialManager: CredentialManager,
        scope: CoroutineScope,
        onAuthSuccess: (FirebaseUser) -> Unit,
        onUnauthenticated: () -> Unit
    ) {
        val existing = Firebase.auth.currentUser
        if (existing != null) {
            onAuthSuccess(existing)
            return
        }
        val clientId = try {
            context.getString(R.string.default_web_client_id)
        } catch (_: Exception) {
            onUnauthenticated()
            return
        }

        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(true)
            .setServerClientId(clientId)
            .setAutoSelectEnabled(true)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        scope.launch {
            try {
                val result = credentialManager.getCredential(context, request)
                val credential = result.credential
                if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    val token = GoogleIdTokenCredential.createFrom(credential.data).idToken
                    val firebaseCred = GoogleAuthProvider.getCredential(token, null)
                    val authResult = Firebase.auth.signInWithCredential(firebaseCred).await()
                    val user = authResult.user
                    if (user != null) {
                        onAuthSuccess(user)
                    } else {
                        onUnauthenticated()
                    }
                } else {
                    onUnauthenticated()
                }
            } catch (_: NoCredentialException) {
                onUnauthenticated()
            } catch (_: Exception) {
                onUnauthenticated()
            }
        }
    }

    fun launchInteractiveGoogleSignIn(
        context: Context,
        credentialManager: CredentialManager,
        scope: CoroutineScope,
        onAuthSuccess: (FirebaseUser) -> Unit,
        onAuthError: (String) -> Unit,
        onAuthCancelled: () -> Unit
    ) {
        val clientId = try {
            context.getString(R.string.default_web_client_id)
        } catch (_: Exception) {
            onAuthError("Google Sign-In configuration missing.")
            return
        }

        val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId).build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(signInOption)
            .build()

        scope.launch {
            try {
                val activityContext = context as? Activity ?: context
                val result = credentialManager.getCredential(activityContext, request)
                val credential = result.credential
                if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    val token = GoogleIdTokenCredential.createFrom(credential.data).idToken
                    val firebaseCred = GoogleAuthProvider.getCredential(token, null)
                    val authResult = Firebase.auth.signInWithCredential(firebaseCred).await()
                    val user = authResult.user
                    if (user != null) {
                        onAuthSuccess(user)
                    } else {
                        onAuthError("Authentication succeeded without user identity.")
                    }
                } else {
                    onAuthError("Unsupported credential type returned.")
                }
            } catch (e: GetCredentialCancellationException) {
                Log.w(TAG, "Google Sign-In cancelled or dismissed: ${e.message}", e)
                onAuthCancelled()
            } catch (e: NoCredentialException) {
                Log.w(TAG, "No Google credential available on device: ${e.message}", e)
                onAuthError("No Google account found on this device. Add a Google account in device Settings and try again.")
            } catch (e: GetCredentialException) {
                Log.e(TAG, "Credential Manager error during Google Sign-In", e)
                onAuthError(e.localizedMessage ?: "Google Sign-In failed.")
            } catch (e: Exception) {
                Log.e(TAG, "Google Sign-In failed", e)
                onAuthError(e.localizedMessage ?: "Google Sign-In failed.")
            }
        }
    }

    fun signOut(
        credentialManager: CredentialManager,
        scope: CoroutineScope,
        onComplete: () -> Unit = {}
    ) {
        scope.launch {
            withContext(NonCancellable) {
                try {
                    credentialManager.clearCredentialState(ClearCredentialStateRequest())
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to clear credential state: ${e.message}")
                } finally {
                    Firebase.auth.signOut()
                    onComplete()
                }
            }
        }
    }
}
