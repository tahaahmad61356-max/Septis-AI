package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CallEnd
import androidx.compose.material.icons.rounded.Cameraswitch
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.CallSession
import com.example.ui.components.SeptisBrandAvatar
import com.example.ui.components.formatDurationSeconds
import com.example.ui.viewmodel.CallViewModel
import org.webrtc.SurfaceViewRenderer

@Composable
fun ActiveCallScreen(
    call: CallSession,
    callViewModel: CallViewModel,
    onMinimize: () -> Unit
) {
    BackHandler { onMinimize() }
    val context = LocalContext.current
    val isMuted by callViewModel.isMuted.collectAsStateWithLifecycle()
    val isSpeakerOn by callViewModel.isSpeakerOn.collectAsStateWithLifecycle()
    val elapsedSeconds by callViewModel.elapsedSeconds.collectAsStateWithLifecycle()
    val connState by callViewModel.connectionState.collectAsStateWithLifecycle()

    val isCaller = call.callerUid == callViewModel.currentUid
    val isIncomingRinging = !isCaller && call.status == "ringing"
    val peerName = if (isCaller) call.calleeName else call.callerName
    val isVideo = call.callType == "video"

    val localRenderer = remember { if (isVideo) SurfaceViewRenderer(context) else null }
    val remoteRenderer = remember { if (isVideo) SurfaceViewRenderer(context) else null }

    DisposableEffect(call.id, isVideo) {
        if (isVideo) {
            callViewModel.webRtc.attachRenderers(localRenderer, remoteRenderer)
        }
        onDispose {
            try {
                localRenderer?.release()
                remoteRenderer?.release()
            } catch (_: Exception) {
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A1E15))
    ) {
        if (isVideo && remoteRenderer != null) {
            AndroidView(
                factory = { remoteRenderer },
                modifier = Modifier.fillMaxSize()
            )
        }

        if (isVideo && localRenderer != null) {
            AndroidView(
                factory = { localRenderer },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(16.dp)
                    .size(width = 110.dp, height = 156.dp)
                    .clip(RoundedCornerShape(14.dp))
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.height(28.dp))
                Text(
                    text = if (isVideo) "SEPTIS Video Call" else "SEPTIS Voice Call",
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White.copy(alpha = 0.78f)
                )
                Spacer(Modifier.height(16.dp))
                if (!isVideo || call.status != "answered") {
                    SeptisBrandAvatar(
                        name = peerName,
                        size = 104.dp
                    )
                    Spacer(Modifier.height(16.dp))
                }
                Text(
                    text = peerName,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(Modifier.height(6.dp))
                val statusLabel = when {
                    isIncomingRinging -> "Incoming call..."
                    call.status == "ringing" -> "Ringing..."
                    call.status == "answered" && elapsedSeconds > 0 -> formatDurationSeconds(elapsedSeconds)
                    else -> connState
                }
                Text(
                    text = statusLabel,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color(0xFF81E6B2)
                )
            }

            if (isIncomingRinging) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    IconButton(
                        onClick = { callViewModel.rejectIncomingCall() },
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = Color(0xFFD32F2F),
                            contentColor = Color.White
                        ),
                        modifier = Modifier
                            .size(68.dp)
                            .clip(CircleShape)
                            .testTag("call_reject_button")
                    ) {
                        Icon(Icons.Rounded.CallEnd, contentDescription = "Decline call", modifier = Modifier.size(30.dp))
                    }
                    IconButton(
                        onClick = { callViewModel.acceptIncomingCall() },
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = Color(0xFF1B9E55),
                            contentColor = Color.White
                        ),
                        modifier = Modifier
                            .size(68.dp)
                            .clip(CircleShape)
                            .testTag("call_accept_button")
                    ) {
                        Icon(Icons.Rounded.Call, contentDescription = "Accept call", modifier = Modifier.size(30.dp))
                    }
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 20.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { callViewModel.webRtc.toggleMute() },
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = if (isMuted) Color.White else Color.White.copy(alpha = 0.2f),
                            contentColor = if (isMuted) Color.Black else Color.White
                        ),
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .testTag("call_mute_button")
                    ) {
                        Icon(
                            if (isMuted) Icons.Rounded.MicOff else Icons.Rounded.Mic,
                            contentDescription = "Mute"
                        )
                    }

                    IconButton(
                        onClick = { callViewModel.webRtc.toggleSpeakerphone() },
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = if (isSpeakerOn) Color.White else Color.White.copy(alpha = 0.2f),
                            contentColor = if (isSpeakerOn) Color.Black else Color.White
                        ),
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .testTag("call_speaker_button")
                    ) {
                        Icon(Icons.Rounded.VolumeUp, contentDescription = "Speaker")
                    }

                    if (isVideo) {
                        IconButton(
                            onClick = { callViewModel.webRtc.switchCamera() },
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = Color.White.copy(alpha = 0.2f),
                                contentColor = Color.White
                            ),
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .testTag("call_flip_camera_button")
                        ) {
                            Icon(Icons.Rounded.Cameraswitch, contentDescription = "Switch camera")
                        }
                    }

                    IconButton(
                        onClick = { callViewModel.endCurrentCall() },
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = Color(0xFFD32F2F),
                            contentColor = Color.White
                        ),
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .testTag("call_end_button")
                    ) {
                        Icon(Icons.Rounded.CallEnd, contentDescription = "End call", modifier = Modifier.size(30.dp))
                    }
                }
            }
        }
    }
}
