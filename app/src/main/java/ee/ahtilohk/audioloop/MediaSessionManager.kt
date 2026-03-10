package ee.ahtilohk.audioloop

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat

/**
 * Manages MediaSession for Bluetooth/headset media button handling
 * and AudioFocus for proper audio coexistence with other apps.
 */
class MediaSessionManager(
    private val context: Context,
    private val onPlay: () -> Unit,
    private val onPause: () -> Unit,
    private val onStop: () -> Unit
) {
    private var mediaSession: MediaSessionCompat? = null
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var audioFocusRequest: AudioFocusRequest? = null
    private var hasAudioFocus = false
    private var wasPlayingBeforeFocusLoss = false

    private val audioFocusListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                // Regained focus - resume if we were playing before
                if (wasPlayingBeforeFocusLoss) {
                    onPlay()
                    wasPlayingBeforeFocusLoss = false
                }
            }
            AudioManager.AUDIOFOCUS_LOSS -> {
                // Permanent loss - stop playback
                wasPlayingBeforeFocusLoss = false
                hasAudioFocus = false
                onStop()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                // Temporary loss (e.g. phone call) - pause
                wasPlayingBeforeFocusLoss = true
                onPause()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                // Can duck - for a practice/learning app, pause is better than ducking
                wasPlayingBeforeFocusLoss = true
                onPause()
            }
        }
    }

    fun initialize() {
        mediaSession = MediaSessionCompat(context, "AudioLoopSession").apply {
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() {
                    this@MediaSessionManager.onPlay()
                }
                override fun onPause() {
                    this@MediaSessionManager.onPause()
                }
                override fun onStop() {
                    this@MediaSessionManager.onStop()
                }
                override fun onMediaButtonEvent(mediaButtonEvent: android.content.Intent): Boolean {
                    // Let the default handler process the KeyEvent from the intent
                    return super.onMediaButtonEvent(mediaButtonEvent)
                }
            })
            // Allow media buttons and transport controls
            setFlags(
                MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or
                MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS
            )
            isActive = true
        }
    }

    fun requestAudioFocus(): Boolean {
        if (hasAudioFocus) return true

        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()

        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(attrs)
            .setOnAudioFocusChangeListener(audioFocusListener)
            .setWillPauseWhenDucked(true)
            .build()
        audioFocusRequest = request

        val result = audioManager.requestAudioFocus(request)
        hasAudioFocus = (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
        return hasAudioFocus
    }

    fun abandonAudioFocus() {
        audioFocusRequest?.let {
            audioManager.abandonAudioFocusRequest(it)
        }
        hasAudioFocus = false
        wasPlayingBeforeFocusLoss = false
    }

    fun updatePlaybackState(isPlaying: Boolean, isPaused: Boolean, position: Long = 0L) {
        val state = when {
            isPlaying && !isPaused -> PlaybackStateCompat.STATE_PLAYING
            isPaused -> PlaybackStateCompat.STATE_PAUSED
            else -> PlaybackStateCompat.STATE_STOPPED
        }
        val playbackState = PlaybackStateCompat.Builder()
            .setActions(
                PlaybackStateCompat.ACTION_PLAY or
                PlaybackStateCompat.ACTION_PAUSE or
                PlaybackStateCompat.ACTION_PLAY_PAUSE or
                PlaybackStateCompat.ACTION_STOP
            )
            .setState(state, position, 1f)
            .build()
        mediaSession?.setPlaybackState(playbackState)
    }

    fun updateMetadata(title: String, durationMs: Long = 0L) {
        val metadata = MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, title)
            .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, durationMs)
            .build()
        mediaSession?.setMetadata(metadata)
    }

    fun release() {
        abandonAudioFocus()
        mediaSession?.isActive = false
        mediaSession?.release()
        mediaSession = null
    }

    fun getSessionToken(): MediaSessionCompat.Token? = mediaSession?.sessionToken
}
