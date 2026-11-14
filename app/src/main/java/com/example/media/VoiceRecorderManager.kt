package com.example.media

import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.roundToInt

data class RecordingResult(
    val file: File,
    val bytes: ByteArray,
    val durationMs: Long,
    val waveform: List<Int>
)

data class PlaybackState(
    val activeId: String? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val speed: Float = 1.0f
)

class VoiceRecorderManager(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var currentFile: File? = null
    private var startTimeMs: Long = 0L
    private val sampledAmplitudes = mutableListOf<Int>()
    private var amplitudeJob: Job? = null

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _recordingDurationMs = MutableStateFlow(0L)
    val recordingDurationMs: StateFlow<Long> = _recordingDurationMs.asStateFlow()

    private val _liveAmplitudes = MutableStateFlow<List<Int>>(emptyList())
    val liveAmplitudes: StateFlow<List<Int>> = _liveAmplitudes.asStateFlow()

    private var player: MediaPlayer? = null
    private var playerJob: Job? = null

    private val _playbackState = MutableStateFlow(PlaybackState())
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    fun startRecording(scope: CoroutineScope): Result<Unit> = runCatching {
        stopRecordingInternal(deleteFile = true)
        val outFile = File(context.cacheDir, "voice_${System.currentTimeMillis()}.m4a")
        val mr = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }
        mr.setAudioSource(MediaRecorder.AudioSource.MIC)
        mr.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        mr.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        mr.setAudioEncodingBitRate(24_000)
        mr.setAudioSamplingRate(16_000)
        mr.setOutputFile(outFile.absolutePath)
        mr.prepare()
        mr.start()

        recorder = mr
        currentFile = outFile
        startTimeMs = System.currentTimeMillis()
        sampledAmplitudes.clear()
        _isRecording.value = true
        _recordingDurationMs.value = 0L
        _liveAmplitudes.value = emptyList()

        amplitudeJob = scope.launch {
            while (isActive && _isRecording.value) {
                val amp = try {
                    mr.maxAmplitude
                } catch (_: Exception) {
                    0
                }
                val normalized = ((amp / 32767f) * 100f).roundToInt().coerceIn(8, 100)
                sampledAmplitudes.add(normalized)
                _liveAmplitudes.value = sampledAmplitudes.takeLast(32)
                _recordingDurationMs.value = System.currentTimeMillis() - startTimeMs
                delay(120L)
            }
        }
    }

    fun cancelRecording() {
        stopRecordingInternal(deleteFile = true)
    }

    fun finishRecording(): Result<RecordingResult> = runCatching {
        val file = currentFile ?: throw IllegalStateException("No active recording.")
        val duration = (System.currentTimeMillis() - startTimeMs).coerceAtLeast(400L)
        val rawSamples = sampledAmplitudes.toList()
        stopRecordingInternal(deleteFile = false)

        if (!file.exists() || file.length() == 0L) {
            throw IllegalStateException("Recording produced no audio data.")
        }
        val bytes = file.readBytes()
        val compactWaveform = normalizeWaveform(rawSamples, 28)
        RecordingResult(
            file = file,
            bytes = bytes,
            durationMs = duration,
            waveform = compactWaveform
        )
    }

    private fun stopRecordingInternal(deleteFile: Boolean) {
        amplitudeJob?.cancel()
        amplitudeJob = null
        _isRecording.value = false
        try {
            recorder?.stop()
        } catch (_: Exception) {
        }
        try {
            recorder?.release()
        } catch (_: Exception) {
        }
        recorder = null
        if (deleteFile) {
            currentFile?.delete()
        }
        if (deleteFile) currentFile = null
    }

    fun togglePlay(
        id: String,
        audioFile: File,
        scope: CoroutineScope
    ): Result<Unit> = runCatching {
        val current = _playbackState.value
        if (current.activeId == id && player != null) {
            val p = player!!
            if (p.isPlaying) {
                p.pause()
                _playbackState.value = _playbackState.value.copy(isPlaying = false)
            } else {
                p.start()
                _playbackState.value = _playbackState.value.copy(isPlaying = true)
                startPlaybackTicker(scope)
            }
            return@runCatching
        }

        stopPlayback()
        val mp = MediaPlayer().apply {
            setDataSource(audioFile.absolutePath)
            prepare()
            setOnCompletionListener {
                _playbackState.value = PlaybackState(
                    activeId = id,
                    isPlaying = false,
                    positionMs = 0L,
                    durationMs = duration.toLong(),
                    speed = _playbackState.value.speed
                )
            }
        }
        applySpeed(mp, _playbackState.value.speed)
        mp.start()
        player = mp
        _playbackState.value = PlaybackState(
            activeId = id,
            isPlaying = true,
            positionMs = 0L,
            durationMs = mp.duration.toLong().coerceAtLeast(1L),
            speed = _playbackState.value.speed
        )
        startPlaybackTicker(scope)
    }

    fun cycleSpeed(): Float {
        val next = when (_playbackState.value.speed) {
            1.0f -> 1.5f
            1.5f -> 2.0f
            else -> 1.0f
        }
        _playbackState.value = _playbackState.value.copy(speed = next)
        player?.let { applySpeed(it, next) }
        return next
    }

    fun seekTo(positionMs: Long) {
        val mp = player ?: return
        mp.seekTo(positionMs.toInt().coerceIn(0, mp.duration))
        _playbackState.value = _playbackState.value.copy(positionMs = positionMs)
    }

    fun stopPlayback() {
        playerJob?.cancel()
        playerJob = null
        try {
            player?.stop()
        } catch (_: Exception) {
        }
        try {
            player?.release()
        } catch (_: Exception) {
        }
        player = null
        _playbackState.value = PlaybackState(speed = _playbackState.value.speed)
    }

    private fun startPlaybackTicker(scope: CoroutineScope) {
        playerJob?.cancel()
        playerJob = scope.launch {
            while (isActive && player != null && _playbackState.value.isPlaying) {
                val p = player ?: break
                _playbackState.value = _playbackState.value.copy(
                    positionMs = p.currentPosition.toLong(),
                    durationMs = p.duration.toLong().coerceAtLeast(1L),
                    isPlaying = p.isPlaying
                )
                delay(80L)
            }
        }
    }

    private fun applySpeed(mp: MediaPlayer, speed: Float) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val params = mp.playbackParams
                params.speed = speed
                mp.playbackParams = params
            } catch (_: Exception) {
            }
        }
    }

    private fun normalizeWaveform(samples: List<Int>, targetBars: Int): List<Int> {
        if (samples.isEmpty()) return List(targetBars) { 25 }
        if (samples.size <= targetBars) {
            val out = samples.toMutableList()
            while (out.size < targetBars) out.add(15)
            return out
        }
        val bucketSize = samples.size.toFloat() / targetBars
        return List(targetBars) { idx ->
            val start = (idx * bucketSize).toInt().coerceIn(0, samples.lastIndex)
            val end = (((idx + 1) * bucketSize).toInt()).coerceIn(start + 1, samples.size)
            samples.subList(start, end).average().roundToInt().coerceIn(10, 100)
        }
    }

    fun releaseAll() {
        stopRecordingInternal(deleteFile = true)
        stopPlayback()
    }
}
