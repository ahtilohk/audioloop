package ee.ahtilohk.audioloop.ui

import android.content.Context
import android.media.MediaPlayer
import android.os.Build
import ee.ahtilohk.audioloop.AudioService
import ee.ahtilohk.audioloop.MediaSessionManager
import ee.ahtilohk.audioloop.Playlist
import ee.ahtilohk.audioloop.RecordingItem
import ee.ahtilohk.audioloop.R
import ee.ahtilohk.audioloop.data.AudioMetadataHelper
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

import javax.inject.Inject
import javax.inject.Singleton
import dagger.hilt.android.qualifiers.ApplicationContext

/**
 * Manages audio playback, playlists, and related UI states.
 * Extracted from AudioLoopViewModel to reduce its complexity.
 */
@Singleton
class PlaybackManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val scope: CoroutineScope,
    private val mediaSessionManager: MediaSessionManager
) : MediaSessionManager.Callback {
    interface Listener {
        fun onSessionStart()
        fun onSessionEnd()
        fun onPlaylistComplete(playlist: Playlist)
        fun showSnackbar(message: String, isError: Boolean)
    }

    private var listener: Listener? = null

    fun setListener(listener: Listener) {
        this.listener = listener
    }

    override fun onPlay() { resumePlaying() }
    override fun onPause() { pausePlaying() }
    override fun onStop() { 
        stopPlaying()
        mediaSessionManager.abandonAudioFocus()
    }

    private val _playbackState = MutableStateFlow(PlaybackUiState())
    val playbackState = _playbackState.asStateFlow()

    private var audioService: AudioService? = null
    private val mediaPlayer: MediaPlayer? get() = audioService?.getMediaPlayer()

    private var shadowingJob: Job? = null
    private var progressJob: Job? = null
    private var sleepTimerJob: Job? = null

    fun setService(service: AudioService?) {
        this.audioService = service
    }

    fun playFile(item: RecordingItem) {
        _playbackState.update { it.copy(playingFileName = item.name, isPaused = false, currentPlaylistIteration = 1) }
        playPlaylist(listOf(item), 0)
    }

    fun startPlaylistPlayback(
        files: List<RecordingItem>,
        loopMode: Int,
        speed: Float,
        pitch: Float,
        isShadowing: Boolean,
        onComplete: () -> Unit
    ) {
        playPlaylist(
            allFiles = files,
            currentIndex = 0,
            loopCountProvider = { loopMode },
            speedProvider = { speed },
            pitchProvider = { pitch },
            shadowingProvider = { isShadowing },
            onNext = { name -> _playbackState.update { it.copy(playingFileName = name, isPaused = false) } },
            onComplete = onComplete
        )
    }

    fun playPlaylist(
        allFiles: List<RecordingItem>,
        currentIndex: Int,
        loopCountProvider: () -> Int = { _playbackState.value.loopMode },
        speedProvider: () -> Float = { _playbackState.value.playbackSpeed },
        pitchProvider: () -> Float = { _playbackState.value.playbackPitch },
        shadowingProvider: () -> Boolean = { _playbackState.value.isShadowingMode },
        onNext: (String) -> Unit = { name -> _playbackState.update { it.copy(playingFileName = name) } },
        onIterationChange: (Int) -> Unit = { iter -> _playbackState.update { it.copy(currentPlaylistIteration = iter) } },
        currentIteration: Int = 1,
        gapSeconds: Int = 0,
        onComplete: () -> Unit = { _playbackState.update { it.copy(playingFileName = "") } }
    ) {
        if (allFiles.isEmpty()) return

        if (currentIndex >= allFiles.size) {
            val loopMode = loopCountProvider()
            if (loopMode == -1 || currentIteration < loopMode) {
                onIterationChange(currentIteration + 1)
                playPlaylist(allFiles, 0, loopCountProvider, speedProvider, pitchProvider, shadowingProvider, onNext, onIterationChange, currentIteration + 1, gapSeconds, onComplete)
            } else {
                stopPlaying()
                onComplete()
                // Custom playlist complete notification
                // Assuming allFiles belongs to some playlist if it was a playlist playback
                // This is a bit weak, but in this refactoring we can improve it later.
                // Currently AudioLoopViewModel handles the specific Playlist call.
            }
            return
        }


        val speed = speedProvider()
        val pitch = pitchProvider()
        val isShadowing = shadowingProvider()

        val itemToPlay = allFiles[currentIndex]
        onNext(itemToPlay.name)

        if (currentIndex == 0 && currentIteration == 1) {
            listener?.onSessionStart()
        }


        audioService?.setOnCompletionListener {
            if (isShadowing) {
                val duration = audioService?.getMediaPlayer()?.duration?.toLong() ?: 0L
                val pauseDuration = if (_playbackState.value.shadowPauseSeconds > 0) {
                    _playbackState.value.shadowPauseSeconds * 1000L
                } else {
                    duration.coerceAtMost(15000L)
                }
                shadowingJob = scope.launch(Dispatchers.Main) {
                    _playbackState.update { s -> s.copy(isPaused = true) }
                    var remaining = pauseDuration
                    while (remaining > 0 && isActive) {
                        val secs = (remaining / 1000) + 1
                        _playbackState.update { s -> s.copy(shadowCountdownText = context.getString(R.string.msg_shadow_repeat_in, secs.toInt())) }
                        delay(1000L.coerceAtMost(remaining))
                        remaining -= 1000L
                    }
                    _playbackState.update { s -> s.copy(shadowCountdownText = "") }
                    if (isActive) {
                        val loopMode = loopCountProvider()
                        if (loopMode == -1 || currentIteration < loopMode) {
                            onIterationChange(currentIteration + 1)
                            playPlaylist(allFiles, currentIndex, loopCountProvider, speedProvider, pitchProvider, shadowingProvider, onNext, onIterationChange, currentIteration + 1, gapSeconds, onComplete)
                        } else {
                            stopPlaying()
                            onComplete()
                        }
                    }
                }
            } else {
                if (gapSeconds > 0 && currentIndex + 1 < allFiles.size) {
                    shadowingJob = scope.launch(Dispatchers.Main) {
                        _playbackState.update { s -> s.copy(isPaused = true) }
                        delay(gapSeconds * 1000L)
                        if (isActive) {
                            playPlaylist(allFiles, currentIndex + 1, loopCountProvider, speedProvider, pitchProvider, shadowingProvider, onNext, onIterationChange, currentIteration, gapSeconds, onComplete)
                        }
                    }
                } else {
                    playPlaylist(allFiles, currentIndex + 1, loopCountProvider, speedProvider, pitchProvider, shadowingProvider, onNext, onIterationChange, currentIteration, gapSeconds, onComplete)
                }
            }
        }

        audioService?.playFile(itemToPlay, speed, pitch)
        _playbackState.update { it.copy(isPaused = false) }
        startProgressTracking()
    }

    fun updateCurrentlyPlayingPlaylist(id: String?, iteration: Int) {
        _playbackState.update { it.copy(currentlyPlayingPlaylistId = id, currentPlaylistIteration = iteration) }
    }

    fun pausePlaying() {
        val wasShadowCountdown = _playbackState.value.shadowCountdownText.isNotEmpty()
        shadowingJob?.cancel()
        shadowingJob = null
        _playbackState.update { it.copy(shadowCountdownText = "") }
        try {
            if (mediaPlayer?.isPlaying == true) {
                mediaPlayer?.pause()
                _playbackState.update { it.copy(isPaused = true) }
                mediaSessionManager.updatePlaybackState(isPlaying = false, isPaused = true)
            } else if (wasShadowCountdown) {
                stopPlaying()
            }
        } catch (_: IllegalStateException) {
            stopPlaying()
        }
    }

    fun resumePlaying() {
        if (_playbackState.value.shadowCountdownText.isNotEmpty()) {
            shadowingJob?.cancel()
            shadowingJob = null
            _playbackState.update { it.copy(shadowCountdownText = "") }
        }
        val mp = mediaPlayer ?: return
        if (!mp.isPlaying) {
            mp.start()
            _playbackState.update { it.copy(isPaused = false) }
            mediaSessionManager.updatePlaybackState(isPlaying = true, isPaused = false)
        }
    }

    fun stopPlaying(endSession: Boolean = true) {
        audioService?.stopPlayback()
        _playbackState.update { it.copy(
            playingFileName = "",
            isPaused = false,
            currentlyPlayingPlaylistId = null,
            shadowCountdownText = "",
            abLoopStart = -1f,
            abLoopEnd = -1f,
            currentProgress = 0f,
            currentTimeString = "00:00"
        ) }
        shadowingJob?.cancel()
        shadowingJob = null
        if (endSession) listener?.onSessionEnd()
        mediaSessionManager.updatePlaybackState(isPlaying = false, isPaused = false)

        mediaSessionManager.abandonAudioFocus()
    }

    fun seekTo(pos: Float) {
        try { mediaPlayer?.let { if (it.duration > 0) it.seekTo((it.duration * pos).toInt()) } }
        catch (_: IllegalStateException) {}
    }

    fun seekAbsolute(ms: Int) {
        try { mediaPlayer?.seekTo(ms) } catch (_: IllegalStateException) {}
    }

    fun startProgressTracking() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (isActive) {
                val player = mediaPlayer
                val state = _playbackState.value
                if (state.playingFileName.isNotEmpty() && player != null && player.isPlaying) {
                    val current = player.currentPosition
                    val total = player.duration
                    val progress = if (total > 0) current.toFloat() / total.toFloat() else 0f
                    
                    // A-B Loop Logic
                    val abStart = state.abLoopStart
                    val abEnd = state.abLoopEnd
                    val abActive = abStart >= 0f && abEnd >= 0f && abEnd > abStart
                    
                    if (abActive && progress >= abEnd) {
                        val loopMode = state.loopMode
                        if (loopMode == -1 || state.currentPlaylistIteration < loopMode) {
                            if (state.isShadowingMode) {
                                pausePlaying()
                                val pauseDuration = if (state.shadowPauseSeconds > 0) {
                                    state.shadowPauseSeconds * 1000L
                                } else {
                                    ((abEnd - abStart) * total).toLong().coerceAtMost(15000L)
                                }
                                
                                shadowingJob = scope.launch(Dispatchers.Main) {
                                    var remaining = pauseDuration
                                    while (remaining > 0 && isActive) {
                                        val secs = (remaining / 1000) + 1
                                        _playbackState.update { s -> s.copy(shadowCountdownText = context.getString(R.string.msg_shadow_repeat_in, secs.toInt())) }
                                        delay(1000L.coerceAtMost(remaining))
                                        remaining -= 1000L
                                    }
                                    _playbackState.update { s -> s.copy(shadowCountdownText = "") }
                                    if (isActive) {
                                        _playbackState.update { it.copy(currentPlaylistIteration = it.currentPlaylistIteration + 1) }
                                        seekAbsolute((abStart * total).toInt())
                                        resumePlaying()
                                    }
                                }
                            } else {
                                _playbackState.update { it.copy(currentPlaylistIteration = it.currentPlaylistIteration + 1) }
                                seekAbsolute((abStart * total).toInt())
                            }
                        } else {
                             // Loop limit reached
                             stopPlaying(false) // stop but keep markers
                        }
                    }

                    _playbackState.update {
                        it.copy(
                            currentProgress = progress,
                            currentTimeString = AudioMetadataHelper.formatTime(current.toLong())
                        )
                    }
                } else if (state.playingFileName.isEmpty()) {
                    _playbackState.update { it.copy(currentProgress = 0f, currentTimeString = "00:00") }
                }
                delay(200)
            }
        }
    }

    fun setAbLoopStart(pos: Float) {
        _playbackState.update { it.copy(abLoopStart = pos) }
    }

    fun setAbLoopEnd(pos: Float) {
        _playbackState.update { it.copy(abLoopEnd = pos) }
    }

    fun updatePlaybackParams(speed: Float, pitch: Float) {
        _playbackState.update { it.copy(playbackSpeed = speed, playbackPitch = pitch) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                mediaPlayer?.let { if (it.isPlaying) it.playbackParams = it.playbackParams.setSpeed(speed).setPitch(pitch) }
            } catch (_: IllegalStateException) {}
        }
    }

    fun setLoopMode(mode: Int) {
        _playbackState.update { it.copy(loopMode = mode) }
    }

    fun setShadowingMode(enabled: Boolean) {
        _playbackState.update { it.copy(isShadowingMode = enabled) }
    }

    fun setShadowPause(seconds: Int) {
        _playbackState.update { it.copy(shadowPauseSeconds = seconds) }
    }

    fun setSleepTimer(minutes: Int) {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        // Note: Sleep timer logic depends on ViewModel's UI state usually, 
        // but it can be handled here if we expose the remaining time.
        // For now let's keep it in VM or just move it if possible.
    }

    fun nudgeAbLoopStart(deltaMs: Int) {
        val mp = mediaPlayer ?: return
        val total = mp.duration
        if (total <= 0) return
        val currentMs = (_playbackState.value.abLoopStart * total).toInt()
        val nextMs = (currentMs + deltaMs).coerceIn(0, total)
        _playbackState.update { it.copy(abLoopStart = nextMs.toFloat() / total.toFloat()) }
    }

    fun nudgeAbLoopEnd(deltaMs: Int) {
        val mp = mediaPlayer ?: return
        val total = mp.duration
        if (total <= 0) return
        val currentMs = (_playbackState.value.abLoopEnd * total).toInt()
        val nextMs = (currentMs + deltaMs).coerceIn(0, total)
        _playbackState.update { it.copy(abLoopEnd = nextMs.toFloat() / total.toFloat()) }
    }
}

data class PlaybackUiState(
    val playingFileName: String = "",
    val isPaused: Boolean = false,
    val currentProgress: Float = 0f,
    val currentTimeString: String = "00:00",
    val loopMode: Int = 1,
    val playbackSpeed: Float = 1.0f,
    val playbackPitch: Float = 1.0f,
    val isShadowingMode: Boolean = false,
    val shadowPauseSeconds: Int = 0,
    val shadowCountdownText: String = "",
    val abLoopStart: Float = -1f,
    val abLoopEnd: Float = -1f,
    val currentlyPlayingPlaylistId: String? = null,
    val currentPlaylistIteration: Int = 1
)
