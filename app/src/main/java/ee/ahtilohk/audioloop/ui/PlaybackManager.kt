package ee.ahtilohk.audioloop.ui

import android.content.Context
import androidx.media3.exoplayer.ExoPlayer

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
    private val exoPlayer: ExoPlayer? get() = audioService?.getExoPlayer()


    private var shadowingJob: Job? = null
    private var progressJob: Job? = null
    private var sleepTimerJob: Job? = null

    private var currentPlaylist: List<RecordingItem> = emptyList()
    private var currentFileIndex: Int = 0

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
        onComplete: () -> Unit = { }
    ) {
        if (allFiles.isEmpty()) return
        this.currentPlaylist = allFiles
        this.currentFileIndex = currentIndex

        if (currentIndex >= allFiles.size) {
            val loopMode = loopCountProvider()
            if (loopMode == -1 || currentIteration < loopMode) {
                onIterationChange(currentIteration + 1)
                playPlaylist(allFiles, 0, loopCountProvider, speedProvider, pitchProvider, shadowingProvider, onNext, onIterationChange, currentIteration + 1, gapSeconds, onComplete)
            } else {
                stopPlaying(false)
                onComplete()
            }
            return
        }


        val speed = speedProvider()
        val pitch = pitchProvider()
        val isShadowing = shadowingProvider()

        val itemToPlay = allFiles[currentIndex]
        
        // Clear markers if we moved to a new track in a playlist
        if (itemToPlay.name != _playbackState.value.playingFileName && _playbackState.value.playingFileName.isNotEmpty()) {
            _playbackState.update { it.copy(abLoopStart = -1f, abLoopEnd = -1f) }
        }

        onNext(itemToPlay.name)

        if (currentIndex == 0 && currentIteration == 1) {
            listener?.onSessionStart()
        }

        audioService?.setOnCompletionListener {
            if (isShadowing) {

                val duration = audioService?.getExoPlayer()?.duration ?: 0L

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

        val startMs = if (_playbackState.value.abLoopStart >= 0f) {
            (_playbackState.value.abLoopStart * itemToPlay.durationMillis).toInt()
        } else 0

        audioService?.playFile(itemToPlay, speed, pitch, startMs)
        _playbackState.update { it.copy(
            isPaused = false,
            currentProgress = if (itemToPlay.durationMillis > 0) startMs.toFloat() / itemToPlay.durationMillis else 0f,
            currentTimeString = AudioMetadataHelper.formatTime(startMs.toLong())
        ) }
        startProgressTracking()
    }

    fun updateCurrentlyPlayingPlaylist(id: String?, iteration: Int) {
        _playbackState.update { it.copy(currentlyPlayingPlaylistId = id, currentPlaylistIteration = iteration) }
    }

    fun pausePlaying() {
        val wasShadowCountdown = _playbackState.value.shadowCountdownText.isNotEmpty()
        shadowingJob?.cancel()
        shadowingJob = null
        _playbackState.update { it.copy(shadowCountdownText = "", isPaused = true) }
        try {
            exoPlayer?.pause()
            mediaSessionManager.updatePlaybackState(isPlaying = false, isPaused = true, position = exoPlayer?.currentPosition ?: 0L)
            if (!wasShadowCountdown && exoPlayer == null) {
                stopPlaying()
            }
        } catch (_: Exception) {
            stopPlaying()
        }
    }


    fun resumePlaying() {
        if (_playbackState.value.shadowCountdownText.isNotEmpty()) {
            shadowingJob?.cancel()
            shadowingJob = null
            _playbackState.update { it.copy(shadowCountdownText = "") }
        }
        val ep = exoPlayer
        if (ep == null && _playbackState.value.playingFileName.isNotEmpty()) {
            // Re-start session if it was stopped at loop limit
            if (currentPlaylist.isNotEmpty() && currentFileIndex in currentPlaylist.indices) {
                playPlaylist(currentPlaylist, currentFileIndex, currentIteration = 1)
            }
            return
        }
        if (ep != null && !ep.isPlaying) {
            ep.play()
            _playbackState.update { it.copy(isPaused = false) }
            mediaSessionManager.updatePlaybackState(isPlaying = true, isPaused = false, position = ep.currentPosition)
        }

    }

    fun stopPlaying(endSession: Boolean = true) {
        audioService?.stopPlayback()
        _playbackState.update { it.copy(
            playingFileName = if (endSession) "" else it.playingFileName,
            isPaused = !endSession, // If we reached limit but kept panel, show it as paused (so Play button shows up)
            currentlyPlayingPlaylistId = if (endSession) null else it.currentlyPlayingPlaylistId,
            shadowCountdownText = "",
            abLoopStart = if (endSession) -1f else it.abLoopStart,
            abLoopEnd = if (endSession) -1f else it.abLoopEnd,
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
        try { exoPlayer?.let { if (it.duration > 0) it.seekTo((it.duration * pos).toLong()) } }
        catch (_: Exception) {}
    }

    fun seekAbsolute(ms: Long) {
        try { exoPlayer?.seekTo(ms) } catch (_: Exception) {}
    }


    fun startProgressTracking() {
        progressJob?.cancel()
        progressJob = scope.launch(Dispatchers.Main.immediate) {
            while (isActive) {
                val player = exoPlayer
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
                        // A-B Loop is ALWAYS infinite as per user request
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
                                    // A-B loop doesn't increment main iteration counter if it's "whole file only"
                                    seekAbsolute((abStart * total).toLong())
                                    resumePlaying()
                                }
                            }
                        } else {
                            seekAbsolute((abStart * total).toLong())
                        }

                    } else if (!abActive && progress >= 0.999f) {
                        // This technically shouldn't happen here as onCompletion covers it, 
                        // but it's good for safety if we had some custom logic.
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
        _playbackState.update { it.copy(abLoopStart = pos, currentPlaylistIteration = 1) }
    }

    fun setAbLoopEnd(pos: Float) {
        _playbackState.update { it.copy(abLoopEnd = pos, currentPlaylistIteration = 1) }
        val state = _playbackState.value // Read updated state
        
        // If we just set B and have A, start looping immediately
        if (pos >= 0f && state.abLoopStart >= 0f) {
            if (currentPlaylist.isNotEmpty() && currentFileIndex in currentPlaylist.indices) {
                playPlaylist(currentPlaylist, currentFileIndex)
            }
        }
    }

    fun updatePlaybackParams(speed: Float, pitch: Float) {
        _playbackState.update { it.copy(playbackSpeed = speed, playbackPitch = pitch) }
        audioService?.updatePlaybackParams(speed, pitch)
    }


    fun setLoopMode(mode: Int) {
        _playbackState.update { it.copy(loopMode = mode, currentPlaylistIteration = 1) }
        
        // Restart playback if a file is selected to show it's active
        if (_playbackState.value.playingFileName.isNotEmpty()) {
            if (currentPlaylist.isNotEmpty() && currentFileIndex in currentPlaylist.indices) {
                playPlaylist(currentPlaylist, currentFileIndex)
            }
        }
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
        val ep = exoPlayer ?: return
        val total = ep.duration
        if (total <= 0) return
        val currentMs = (_playbackState.value.abLoopStart * total).toLong()
        val nextMs = (currentMs + deltaMs).coerceIn(0, total)
        _playbackState.update { it.copy(abLoopStart = nextMs.toFloat() / total.toFloat()) }
    }

    fun nudgeAbLoopEnd(deltaMs: Int) {
        val ep = exoPlayer ?: return
        val total = ep.duration
        if (total <= 0) return
        val currentMs = (_playbackState.value.abLoopEnd * total).toLong()
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
