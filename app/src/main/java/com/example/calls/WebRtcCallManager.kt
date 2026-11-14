package com.example.calls

import android.content.Context
import android.media.AudioManager
import android.util.Log
import com.example.data.model.IceCandidateDoc
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.Camera2Enumerator
import org.webrtc.CameraVideoCapturer
import org.webrtc.DataChannel
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.SurfaceTextureHelper
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoSource
import org.webrtc.VideoTrack

class WebRtcCallManager(private val context: Context) {
    companion object {
        private const val TAG = "SeptisWebRTC"
        @Volatile
        private var factoryInitialized = false
    }

    val eglBase: EglBase = EglBase.create()

    private var peerConnectionFactory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null
    private var audioSource: AudioSource? = null
    private var localAudioTrack: AudioTrack? = null
    private var videoSource: VideoSource? = null
    private var localVideoTrack: VideoTrack? = null
    private var remoteVideoTrack: VideoTrack? = null
    private var videoCapturer: CameraVideoCapturer? = null
    private var surfaceTextureHelper: SurfaceTextureHelper? = null

    private var localRenderer: SurfaceViewRenderer? = null
    private var remoteRenderer: SurfaceViewRenderer? = null

    private val appliedCandidateIds = mutableSetOf<String>()
    private var remoteDescriptionSet = false
    private val pendingRemoteCandidates = mutableListOf<IceCandidate>()

    private val _isMuted = MutableStateFlow(false)
    val isMuted: StateFlow<Boolean> = _isMuted.asStateFlow()

    private val _isSpeakerOn = MutableStateFlow(false)
    val isSpeakerOn: StateFlow<Boolean> = _isSpeakerOn.asStateFlow()

    private val _isVideoCall = MutableStateFlow(false)
    val isVideoCall: StateFlow<Boolean> = _isVideoCall.asStateFlow()

    private val _connectionState = MutableStateFlow("Connecting...")
    val connectionState: StateFlow<String> = _connectionState.asStateFlow()

    private fun ensureFactory() {
        if (!factoryInitialized) {
            synchronized(Companion) {
                if (!factoryInitialized) {
                    PeerConnectionFactory.initialize(
                        PeerConnectionFactory.InitializationOptions.builder(context.applicationContext)
                            .createInitializationOptions()
                    )
                    factoryInitialized = true
                }
            }
        }
        if (peerConnectionFactory == null) {
            val encoderFactory = DefaultVideoEncoderFactory(eglBase.eglBaseContext, true, true)
            val decoderFactory = DefaultVideoDecoderFactory(eglBase.eglBaseContext)
            peerConnectionFactory = PeerConnectionFactory.builder()
                .setVideoEncoderFactory(encoderFactory)
                .setVideoDecoderFactory(decoderFactory)
                .createPeerConnectionFactory()
        }
    }

    fun attachRenderers(local: SurfaceViewRenderer?, remote: SurfaceViewRenderer?) {
        localRenderer = local
        remoteRenderer = remote
        local?.let { renderer ->
            try {
                renderer.init(eglBase.eglBaseContext, null)
                renderer.setMirror(true)
                renderer.setEnableHardwareScaler(true)
                localVideoTrack?.addSink(renderer)
            } catch (_: Exception) {
            }
        }
        remote?.let { renderer ->
            try {
                renderer.init(eglBase.eglBaseContext, null)
                renderer.setMirror(false)
                renderer.setEnableHardwareScaler(true)
                remoteVideoTrack?.addSink(renderer)
            } catch (_: Exception) {
            }
        }
    }

    fun startSession(
        isVideo: Boolean,
        isCaller: Boolean,
        remoteOfferSdp: String?,
        onLocalSdpCreated: (isOffer: Boolean, sdp: String) -> Unit,
        onIceCandidate: (sdpMid: String, sdpMLineIndex: Int, candidate: String) -> Unit
    ) {
        disposeSession()
        ensureFactory()
        _isVideoCall.value = isVideo
        _isMuted.value = false
        setSpeakerphone(isVideo)
        remoteDescriptionSet = false
        pendingRemoteCandidates.clear()
        appliedCandidateIds.clear()

        val factory = peerConnectionFactory ?: return
        val iceServers = listOf(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer()
        )
        val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
        }

        val pc = factory.createPeerConnection(rtcConfig, object : PeerConnection.Observer {
            override fun onSignalingChange(p0: PeerConnection.SignalingState?) {}

            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
                when (state) {
                    PeerConnection.IceConnectionState.CONNECTED,
                    PeerConnection.IceConnectionState.COMPLETED -> _connectionState.value = "Connected"
                    PeerConnection.IceConnectionState.DISCONNECTED -> _connectionState.value = "Reconnecting..."
                    PeerConnection.IceConnectionState.FAILED -> _connectionState.value = "Connection failed"
                    PeerConnection.IceConnectionState.CLOSED -> _connectionState.value = "Ended"
                    else -> {}
                }
            }

            override fun onIceConnectionReceivingChange(p0: Boolean) {}
            override fun onIceGatheringChange(p0: PeerConnection.IceGatheringState?) {}

            override fun onIceCandidate(candidate: IceCandidate?) {
                if (candidate != null) {
                    onIceCandidate(candidate.sdpMid ?: "0", candidate.sdpMLineIndex, candidate.sdp)
                }
            }

            override fun onIceCandidatesRemoved(p0: Array<out IceCandidate>?) {}
            override fun onAddStream(p0: MediaStream?) {}
            override fun onRemoveStream(p0: MediaStream?) {}
            override fun onDataChannel(p0: DataChannel?) {}
            override fun onRenegotiationNeeded() {}

            override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out MediaStream>?) {
                val track = receiver?.track()
                if (track is VideoTrack) {
                    remoteVideoTrack = track
                    remoteRenderer?.let { track.addSink(it) }
                }
            }
        })

        peerConnection = pc ?: return

        val audioConstraints = MediaConstraints()
        audioSource = factory.createAudioSource(audioConstraints)
        localAudioTrack = factory.createAudioTrack("SEPTIS_AUDIO", audioSource)?.apply {
            setEnabled(true)
            pc.addTrack(this, listOf("SEPTIS_STREAM"))
        }

        if (isVideo) {
            setupLocalVideo(factory, pc)
        }

        val mediaConstraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", if (isVideo) "true" else "false"))
        }

        if (isCaller) {
            pc.createOffer(object : SimpleSdpObserver() {
                override fun onCreateSuccess(sdp: SessionDescription?) {
                    val offer = sdp ?: return
                    pc.setLocalDescription(SimpleSdpObserver(), offer)
                    onLocalSdpCreated(true, offer.description)
                }
            }, mediaConstraints)
        } else if (!remoteOfferSdp.isNullOrBlank()) {
            val remoteOffer = SessionDescription(SessionDescription.Type.OFFER, remoteOfferSdp)
            pc.setRemoteDescription(object : SimpleSdpObserver() {
                override fun onSetSuccess() {
                    remoteDescriptionSet = true
                    flushPendingCandidates()
                    pc.createAnswer(object : SimpleSdpObserver() {
                        override fun onCreateSuccess(sdp: SessionDescription?) {
                            val answer = sdp ?: return
                            pc.setLocalDescription(SimpleSdpObserver(), answer)
                            onLocalSdpCreated(false, answer.description)
                        }
                    }, mediaConstraints)
                }
            }, remoteOffer)
        }
    }

    fun applyRemoteAnswer(answerSdp: String) {
        val pc = peerConnection ?: return
        if (remoteDescriptionSet || answerSdp.isBlank()) return
        val answer = SessionDescription(SessionDescription.Type.ANSWER, answerSdp)
        pc.setRemoteDescription(object : SimpleSdpObserver() {
            override fun onSetSuccess() {
                remoteDescriptionSet = true
                flushPendingCandidates()
            }
        }, answer)
    }

    fun addRemoteCandidates(candidates: List<IceCandidateDoc>, currentUid: String) {
        val pc = peerConnection ?: return
        candidates.forEach { doc ->
            if (doc.senderUid == currentUid) return@forEach
            if (!appliedCandidateIds.add(doc.id)) return@forEach
            val candidate = IceCandidate(doc.sdpMid, doc.sdpMLineIndex, doc.candidate)
            if (remoteDescriptionSet) {
                pc.addIceCandidate(candidate)
            } else {
                pendingRemoteCandidates.add(candidate)
            }
        }
    }

    private fun flushPendingCandidates() {
        val pc = peerConnection ?: return
        pendingRemoteCandidates.forEach { pc.addIceCandidate(it) }
        pendingRemoteCandidates.clear()
    }

    private fun setupLocalVideo(factory: PeerConnectionFactory, pc: PeerConnection) {
        try {
            val enumerator = Camera2Enumerator(context)
            val deviceName = enumerator.deviceNames.firstOrNull { enumerator.isFrontFacing(it) }
                ?: enumerator.deviceNames.firstOrNull()
                ?: return
            val capturer = enumerator.createCapturer(deviceName, null) ?: return
            videoCapturer = capturer
            surfaceTextureHelper = SurfaceTextureHelper.create("SeptisVideoThread", eglBase.eglBaseContext)
            videoSource = factory.createVideoSource(capturer.isScreencast)
            capturer.initialize(surfaceTextureHelper, context, videoSource?.capturerObserver)
            capturer.startCapture(640, 480, 24)
            localVideoTrack = factory.createVideoTrack("SEPTIS_VIDEO", videoSource)?.apply {
                setEnabled(true)
                localRenderer?.let { addSink(it) }
                pc.addTrack(this, listOf("SEPTIS_STREAM"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Unable to initialize local video capturer", e)
        }
    }

    fun toggleMute() {
        val next = !_isMuted.value
        _isMuted.value = next
        localAudioTrack?.setEnabled(!next)
    }

    fun toggleSpeakerphone() {
        setSpeakerphone(!_isSpeakerOn.value)
    }

    fun setSpeakerphone(enabled: Boolean) {
        _isSpeakerOn.value = enabled
        try {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            am.mode = AudioManager.MODE_IN_COMMUNICATION
            @Suppress("DEPRECATION")
            am.isSpeakerphoneOn = enabled
        } catch (_: Exception) {
        }
    }

    fun switchCamera() {
        try {
            videoCapturer?.switchCamera(null)
        } catch (_: Exception) {
        }
    }

    fun disposeSession() {
        try {
            localRenderer?.let { localVideoTrack?.removeSink(it) }
            remoteRenderer?.let { remoteVideoTrack?.removeSink(it) }
        } catch (_: Exception) {
        }
        try {
            videoCapturer?.stopCapture()
        } catch (_: Exception) {
        }
        try {
            videoCapturer?.dispose()
        } catch (_: Exception) {
        }
        videoCapturer = null
        try {
            surfaceTextureHelper?.dispose()
        } catch (_: Exception) {
        }
        surfaceTextureHelper = null
        try {
            localAudioTrack?.dispose()
            audioSource?.dispose()
            localVideoTrack?.dispose()
            videoSource?.dispose()
            peerConnection?.close()
            peerConnection?.dispose()
        } catch (_: Exception) {
        }
        localAudioTrack = null
        audioSource = null
        localVideoTrack = null
        remoteVideoTrack = null
        videoSource = null
        peerConnection = null
        remoteDescriptionSet = false
        pendingRemoteCandidates.clear()
        appliedCandidateIds.clear()
        try {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            am.mode = AudioManager.MODE_NORMAL
            @Suppress("DEPRECATION")
            am.isSpeakerphoneOn = false
        } catch (_: Exception) {
        }
    }

    fun releaseAll() {
        disposeSession()
        try {
            localRenderer?.release()
        } catch (_: Exception) {
        }
        try {
            remoteRenderer?.release()
        } catch (_: Exception) {
        }
        localRenderer = null
        remoteRenderer = null
        try {
            eglBase.release()
        } catch (_: Exception) {
        }
    }
}

private open class SimpleSdpObserver : SdpObserver {
    override fun onCreateSuccess(p0: SessionDescription?) {}
    override fun onSetSuccess() {}
    override fun onCreateFailure(p0: String?) {
        Log.e("SeptisWebRTC", "SDP create failure: $p0")
    }
    override fun onSetFailure(p0: String?) {
        Log.e("SeptisWebRTC", "SDP set failure: $p0")
    }
}
