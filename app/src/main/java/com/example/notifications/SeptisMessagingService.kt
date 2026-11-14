package com.example.notifications

import android.util.Log
import com.example.data.remote.SeptisFirebaseProvider
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class SeptisMessagingService : FirebaseMessagingService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        val uid = Firebase.auth.currentUser?.uid ?: return
        serviceScope.launch {
            try {
                val db = SeptisFirebaseProvider.getFirestore(applicationContext)
                db.collection("users").document(uid)
                    .update(
                        mapOf(
                            "fcmToken" to token,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                    .await()
            } catch (e: Exception) {
                Log.w("SeptisFCM", "Failed to persist refreshed FCM token: ${e.message}")
            }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        val data = message.data
        val type = data["type"] ?: "message"
        val title = message.notification?.title ?: data["title"] ?: "SEPTIS"
        val body = message.notification?.body ?: data["body"] ?: ""
        when (type) {
            "call" -> {
                val callId = data["callId"] ?: return
                val callerName = data["callerName"] ?: title
                val isVideo = data["callType"] == "video"
                NotificationHelper.showIncomingCallNotification(applicationContext, callId, callerName, isVideo)
            }
            "missed_call" -> {
                val callId = data["callId"] ?: System.currentTimeMillis().toString()
                val callerName = data["callerName"] ?: title
                val isVideo = data["callType"] == "video"
                NotificationHelper.showMissedCallNotification(applicationContext, callId, callerName, isVideo)
            }
            "status", "account", "info", "alert" -> {
                NotificationHelper.showGeneralUpdateNotification(
                    applicationContext,
                    data["referenceId"] ?: System.currentTimeMillis().toString(),
                    title,
                    body
                )
            }
            else -> {
                val convId = data["conversationId"] ?: "general"
                NotificationHelper.showMessageNotification(applicationContext, convId, title, body)
            }
        }
    }
}
