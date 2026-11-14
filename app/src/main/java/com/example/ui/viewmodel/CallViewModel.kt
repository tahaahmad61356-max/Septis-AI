package com.example.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.calls.WebRtcCallManager
import com.example.data.model.CallSession
import com.example.data.repository.SocialCallRepository
import com.example.notifications.NotificationHelper
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class CallViewModel(
    private val context: Context,
    private val socialCallRepo: SocialCallRepository,
    val currentUid: String
) : ViewModel() {

    val webRtc = WebRtcCallManager(context.applicationContext)

    private val _activeCall = MutableStateFlow<CallSession?>(null)
    val activeCall: StateFlow<CallSession?> = _activeCall.asStateFlow()

    private val _elapsedSeconds = MutableStateFlow(0L)
    val elapsedSeconds: StateFlow<Long> = _elapsedSeconds.asStateFlow()

    val isMuted: StateFlow<Boolean> = webRtc.isMuted
    val isSpeakerOn: StateFlow<Boolean> = webRtc.isSpeakerOn
    val connectionState: StateFlow<String> = webRtc.connectionState

    private var callWatchJob: Job? = null
    private var candidatesWatchJob: Job? = null
    private var timerJob: Job? = null
    private var lastNotifiedIncomingCallId: String? = null
    private var sessionStartedForCallId: String? = null

    init {
        observeIncomingCalls()
    }

    private fun observeIncomingCalls() {
        viewModelScope.launch {
            socialCallRepo.observeMyCalls(currentUid).collect { calls ->
                val incoming = calls.firstOrNull {
                    it.calleeUid == currentUid && it.status == "ringing"
                }
                if (incoming != null && _activeCall.value == null) {
                    _activeCall.value = incoming
                    if (lastNotifiedIncomingCallId != incoming.id) {
                        lastNotifiedIncomingCallId = incoming.id
                        NotificationHelper.showIncomingCallNotification(
                            context = context,
                            callId = incoming.id,
                            callerName = incoming.callerName,
                            isVideo = incoming.callType == "video"
                        )
                    }
                    watchActiveCall(incoming.id, isCaller = false)
                }
            }
        }
    }

    fun startOutgoingCall(
        callerName: String,
        callerPhone: String?,
        calleeUid: String,
        calleeName: String,
        calleePhone: String?,
        isVideo: Boolean
    ) {
        viewModelScope.launch {
            val res = socialCallRepo.createOutgoingCall(
                callerName = callerName,
                callerPhone = callerPhone,
                calleeUid = calleeUid,
                calleeName = calleeName,
                calleePhone = calleePhone,
                callType = if (isVideo) "video" else "voice"
            )
            res.onSuccess { call ->
                _activeCall.value = call
                _elapsedSeconds.value = 0L
                watchActiveCall(call.id, isCaller = true)
                startWebRtcSession(call, isCaller = true)
            }
        }
    }

    fun acceptIncomingCall() {
        val call = _activeCall.value ?: return
        startWebRtcSession(call, isCaller = false)
        startDurationTimer()
    }

    fun rejectIncomingCall() {
        val call = _activeCall.value ?: return
        viewModelScope.launch {
            socialCallRepo.updateCallStatus(call.id, "rejected", 0L)
            cleanupCallState()
        }
    }

    fun endCurrentCall() {
        val call = _activeCall.value ?: return
        val finalDuration = _elapsedSeconds.value
        val nextStatus = if (call.status == "ringing" && call.callerUid == currentUid) "missed" else "ended"
        viewModelScope.launch {
            socialCallRepo.updateCallStatus(call.id, nextStatus, finalDuration)
            if (nextStatus == "missed") {
                socialCallRepo.sendNotification(
                    recipientUid = call.calleeUid,
                    senderName = call.callerName,
                    title = "Missed ${call.callType} call",
                    message = "Missed call from ${call.callerName}",
                    type = "missed_call",
                    referenceId = call.id
                )
            }
            cleanupCallState()
        }
    }

    private fun startWebRtcSession(call: CallSession, isCaller: Boolean) {
        if (sessionStartedForCallId == call.id) return
        sessionStartedForCallId = call.id
        webRtc.startSession(
            isVideo = call.callType == "video",
            isCaller = isCaller,
            remoteOfferSdp = call.offerSdp,
            onLocalSdpCreated = { isOffer, sdp ->
                viewModelScope.launch {
                    socialCallRepo.publishCallSdp(
                        callId = call.id,
                        isOffer = isOffer,
                        sdp = sdp,
                        markAnswered = !isOffer
                    )
                }
            },
            onIceCandidate = { sdpMid, sdpMLineIndex, candidate ->
                viewModelScope.launch {
                    socialCallRepo.addCallIceCandidate(
                        callId = call.id,
                        participantUids = call.participantUids,
                        sdpMid = sdpMid,
                        sdpMLineIndex = sdpMLineIndex,
                        candidate = candidate
                    )
                }
            }
        )
    }

    private fun watchActiveCall(callId: String, isCaller: Boolean) {
        callWatchJob?.cancel()
        candidatesWatchJob?.cancel()
        callWatchJob = viewModelScope.launch {
            socialCallRepo.observeCall(callId).collect { updated ->
                if (updated == null) return@collect
                _activeCall.value = updated
                if (isCaller && !updated.answerSdp.isNullOrBlank()) {
                    webRtc.applyRemoteAnswer(updated.answerSdp)
                    if (timerJob == null) startDurationTimer()
                }
                if (updated.status in listOf("ended", "rejected", "missed")) {
                    if (updated.status == "missed" && updated.calleeUid == currentUid) {
                        NotificationHelper.showMissedCallNotification(
                            context = context,
                            callId = updated.id,
                            callerName = updated.callerName,
                            isVideo = updated.callType == "video"
                        )
                    }
                    delay(800L)
                    cleanupCallState()
                }
            }
        }
        candidatesWatchJob = viewModelScope.launch {
            socialCallRepo.observeCallCandidates(callId, currentUid).collect { list ->
                webRtc.addRemoteCandidates(list, currentUid)
            }
        }
    }

    private fun startDurationTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (isActive) {
                delay(1000L)
                _elapsedSeconds.value = _elapsedSeconds.value + 1L
            }
        }
    }

    private fun cleanupCallState() {
        callWatchJob?.cancel()
        candidatesWatchJob?.cancel()
        timerJob?.cancel()
        callWatchJob = null
        candidatesWatchJob = null
        timerJob = null
        sessionStartedForCallId = null
        _elapsedSeconds.value = 0L
        _activeCall.value = null
        webRtc.disposeSession()
    }

    override fun onCleared() {
        super.onCleared()
        cleanupCallState()
        webRtc.releaseAll()
    }
}
