package ee.ahtilohk.audioloop

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.MediaRecorder
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.annotation.SuppressLint
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.PlaybackException
import androidx.media3.common.AudioAttributes as Media3AudioAttributes
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.*
import java.io.File

class AudioService : Service() {

    companion object {
        const val ACTION_START_RECORDING = "ACTION_START_RECORDING"
        const val ACTION_START_INTERNAL_RECORDING = "ACTION_START_INTERNAL_RECORDING"
        const val ACTION_STOP_RECORDING = "ACTION_STOP_RECORDING"
        
        const val ACTION_RECORDING_SAVED = "ee.ahtilohk.audioloop.RECORDING_SAVED"
        const val ACTION_START_RECORDING_EVENT = "ee.ahtilohk.audioloop.START_RECORDING"
        const val ACTION_AMPLITUDE_UPDATE = "ee.ahtilohk.audioloop.AMPLITUDE_UPDATE"

        const val EXTRA_FILENAME = "EXTRA_FILENAME"
        const val EXTRA_AUDIO_SOURCE = "EXTRA_AUDIO_SOURCE"
        const val EXTRA_RESULT_CODE = "EXTRA_RESULT_CODE"
        const val EXTRA_DATA = "EXTRA_DATA"
        const val EXTRA_AMPLITUDE = "EXTRA_AMPLITUDE"
        const val EXTRA_DURATION_MS = "EXTRA_DURATION_MS"
        const val EXTRA_USE_PUBLIC_STORAGE = "EXTRA_USE_PUBLIC_STORAGE"
        const val EXTRA_CATEGORY = "EXTRA_CATEGORY"

        private const val CHANNEL_ID = "audio_service_channel"
        private const val NOTIFICATION_ID_RECORDING = 1
        private const val NOTIFICATION_ID_PLAYBACK = 2
    }

    private val binder = AudioBinder()
    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private val stateLock = Any()

    // --- Recording State ---
    private var mediaRecorder: MediaRecorder? = null
    private var isRecording = false
    private var recordingState = RecordingState.IDLE
    private var pendingStart: PendingStart? = null
    private var currentFile: File? = null
    private var currentUri: android.net.Uri? = null
    private var recordingPfd: android.os.ParcelFileDescriptor? = null
    
    // --- Internal Recording ---
    private var mediaProjection: android.media.projection.MediaProjection? = null
    private var isInternalRecording = false
    private var audioRecord: AudioRecord? = null
    private var aacEncoder: MediaCodec? = null
    private var aacMuxer: MediaMuxer? = null
    private var encodingJob: Job? = null
    private var isEncoderStarted = false
    private var muxerTrackIndex = -1
    private var isMuxerStarted = false
    private var currentMaxAmplitude = 0
    private var tickerJob: Job? = null
    private var recordingStartTime = 0L

    // --- Playback State ---
    private var exoPlayer: ExoPlayer? = null
    private var mediaSessionManager: MediaSessionManager? = null
    private var isPlaying = false
    private var onCompletionListener: (() -> Unit)? = null
    private var playbackListener: Player.Listener? = null


    inner class AudioBinder : Binder() {
        fun getService(): AudioService = this@AudioService
    }

    private enum class RecordingState {
        IDLE, RECORDING, STOPPING
    }

    private sealed class PendingStart {
        data class Mic(val fileName: String, val source: Int, val usePublic: Boolean, val category: String) : PendingStart()
        data class Internal(val fileName: String, val resultCode: Int, val data: Intent) : PendingStart()
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        // We'll initialize MediaSessionManager when playback starts or via binder
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_RECORDING -> {
                val fileName = intent.getStringExtra(EXTRA_FILENAME) ?: "recording"
                val source = intent.getIntExtra(EXTRA_AUDIO_SOURCE, MediaRecorder.AudioSource.MIC)
                val usePublic = intent.getBooleanExtra(EXTRA_USE_PUBLIC_STORAGE, false)
                val category = intent.getStringExtra(EXTRA_CATEGORY) ?: "General"
                serviceScope.launch { startMicRecording(fileName, source, usePublic, category) }
            }
            ACTION_START_INTERNAL_RECORDING -> {
                val fileName = intent.getStringExtra(EXTRA_FILENAME) ?: "internal_rec"
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
                val data = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(EXTRA_DATA, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra<Intent>(EXTRA_DATA)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && data != null) {
                    serviceScope.launch { startInternalRecording(fileName, resultCode, data) }
                }
            }
            ACTION_STOP_RECORDING -> {
                serviceScope.launch { stopRecording() }
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        safelyStopRecorder()
        stopPlayback()
        super.onDestroy()
        serviceScope.cancel()
    }

    // ── Recording Logic (Migrated from RecordingService) ──

    private suspend fun startMicRecording(fileName: String, source: Int, usePublic: Boolean, category: String) {
        val shouldStart = synchronized(stateLock) {
            when (recordingState) {
                RecordingState.RECORDING -> false
                RecordingState.STOPPING -> {
                    pendingStart = PendingStart.Mic(fileName, source, usePublic, category)
                    false
                }
                RecordingState.IDLE -> {
                    recordingState = RecordingState.RECORDING
                    true
                }
            }
        }
        if (!shouldStart) return

        val finalFileName = if (fileName.endsWith(".m4a")) fileName else "$fileName.m4a"
        var fileDescriptor: java.io.FileDescriptor? = null
        var pfd: android.os.ParcelFileDescriptor? = null
        
        currentFile = null
        currentUri = null

        withContext(Dispatchers.Main) {
            startForegroundNotification(NOTIFICATION_ID_RECORDING, getString(R.string.notification_recording_mic), false, true)
        }

        try {
             if (usePublic) {
                 val values = android.content.ContentValues().apply {
                     put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, finalFileName)
                     put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "audio/mp4")
                     if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                         val relativePath = if (category == "General") "Music/AudioLoop" else "Music/AudioLoop/$category"
                         put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
                         put(android.provider.MediaStore.MediaColumns.IS_PENDING, 1)
                     }
                 }
                 val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                     android.provider.MediaStore.Audio.Media.getContentUri(android.provider.MediaStore.VOLUME_EXTERNAL_PRIMARY)
                 } else {
                     android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                 }
                 val uri = contentResolver.insert(collection, values)
                 if (uri != null) {
                     currentUri = uri
                     pfd = contentResolver.openFileDescriptor(uri, "w")
                     fileDescriptor = pfd?.fileDescriptor
                 } else throw java.io.IOException("Failed to create MediaStore entry")
             } else {
                 val file = File(filesDir, if (category == "General") finalFileName else "$category/$finalFileName")
                 file.parentFile?.mkdirs()
                 currentFile = file
             }

            val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(this) else @Suppress("DEPRECATION") MediaRecorder()
            mediaRecorder = recorder
            recorder.apply {
                setAudioSource(source)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(128000)
                setAudioSamplingRate(44100)
                if (usePublic && fileDescriptor != null) setOutputFile(fileDescriptor)
                else if (currentFile != null) setOutputFile(currentFile!!.absolutePath)
                setOnErrorListener { _, _, _ -> safelyStopRecorder() }
                prepare()
                start()
            }
            isRecording = true
            sendBroadcast(Intent(ACTION_START_RECORDING_EVENT).apply { setPackage(packageName) })
            isInternalRecording = false
            recordingStartTime = System.currentTimeMillis()
            startAmplitudeTicker()
            if (pfd != null) recordingPfd = pfd
        } catch (e: Exception) {
            AppLog.e("Error starting mic recording", e)
            cleanupMicRecorder()
            currentFile?.delete()
            currentUri?.let { contentResolver.delete(it, null, null) }
            pfd?.close()
            synchronized(stateLock) { if (recordingState == RecordingState.RECORDING) recordingState = RecordingState.IDLE }
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private suspend fun startInternalRecording(fileName: String, resultCode: Int, data: Intent) {
        val shouldStart = synchronized(stateLock) {
            when (recordingState) {
                RecordingState.RECORDING -> false
                RecordingState.STOPPING -> { pendingStart = PendingStart.Internal(fileName, resultCode, data); false }
                RecordingState.IDLE -> { recordingState = RecordingState.RECORDING; true }
            }
        }
        if (!shouldStart) return

        val finalFileName = if (fileName.endsWith(".m4a")) fileName else "$fileName.m4a"
        val file = File(filesDir, finalFileName)
        currentFile = file
        
        withContext(Dispatchers.Main) {
            startForegroundNotification(NOTIFICATION_ID_RECORDING, getString(R.string.notification_recording_stream), true, true)
        }

        try {
            var projection = mediaProjection
            if (projection == null) {
                 val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as android.media.projection.MediaProjectionManager
                 projection = projectionManager.getMediaProjection(resultCode, data)
            }
            if (projection == null) throw IllegalStateException("MediaProjection on null")
            mediaProjection = projection

            val sampleRate = 48000
            val channelConfig = AudioFormat.CHANNEL_IN_STEREO
            val audioFormat = AudioFormat.ENCODING_PCM_16BIT
            val config = AudioPlaybackCaptureConfiguration.Builder(projection)
                .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                .addMatchingUsage(AudioAttributes.USAGE_GAME)
                .build()

            val minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
            val bufferSize = (minBufferSize * 4).coerceAtLeast(8192)

            @SuppressLint("MissingPermission")
            val record = AudioRecord.Builder()
                .setAudioFormat(AudioFormat.Builder().setEncoding(audioFormat).setSampleRate(sampleRate).setChannelMask(channelConfig).build())
                .setBufferSizeInBytes(bufferSize)
                .setAudioPlaybackCaptureConfig(config)
                .build()
            audioRecord = record

            val encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
            val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, 2)
            format.setInteger(MediaFormat.KEY_BIT_RATE, 192000)
            format.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, bufferSize)
            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            aacEncoder = encoder
            
            aacMuxer = MediaMuxer(file.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            record.startRecording()
            encoder.start()
            isEncoderStarted = true
            isRecording = true
            sendBroadcast(Intent(ACTION_START_RECORDING_EVENT).apply { setPackage(packageName) })
            isInternalRecording = true
            recordingStartTime = System.currentTimeMillis()
            startEncodingLoop(record, encoder, bufferSize)
            startAmplitudeTicker()
        } catch (e: Throwable) {
            AppLog.e("Error starting internal recording", e)
            safelyStopRecorder()
            synchronized(stateLock) { if (recordingState == RecordingState.RECORDING) recordingState = RecordingState.IDLE }
        }
    }

    private fun startEncodingLoop(record: AudioRecord, encoder: MediaCodec, bufferSize: Int) {
        encodingJob = serviceScope.launch(Dispatchers.Default) {
            val buffer = ByteArray(bufferSize)
            val bufferInfo = MediaCodec.BufferInfo()
            try {
                while (isActive && isRecording) {
                    val read = record.read(buffer, 0, bufferSize)
                    if (read <= 0) continue
                    
                    var sum = 0L
                    for (i in 0 until read step 4) {
                        val sample = (buffer[i+1].toInt() shl 8) or (buffer[i].toInt() and 0xFF)
                        sum += kotlin.math.abs(sample.toInt())
                    }
                    currentMaxAmplitude = (sum / (read / 4 + 1)).toInt() * 2

                    val inputIndex = encoder.dequeueInputBuffer(2000)
                    if (inputIndex >= 0) {
                        encoder.getInputBuffer(inputIndex)?.apply { clear(); put(buffer, 0, read) }
                        encoder.queueInputBuffer(inputIndex, 0, read, System.nanoTime() / 1000, 0)
                    }

                    var outputIndex = encoder.dequeueOutputBuffer(bufferInfo, 0)
                    while (outputIndex >= 0) {
                        val outputBuffer = encoder.getOutputBuffer(outputIndex) ?: break
                        if (bufferInfo.size != 0) {
                            if (!isMuxerStarted) {
                                muxerTrackIndex = aacMuxer?.addTrack(encoder.outputFormat) ?: -1
                                aacMuxer?.start(); isMuxerStarted = true
                            }
                            outputBuffer.position(bufferInfo.offset).limit(bufferInfo.offset + bufferInfo.size)
                            aacMuxer?.writeSampleData(muxerTrackIndex, outputBuffer, bufferInfo)
                        }
                        encoder.releaseOutputBuffer(outputIndex, false)
                        outputIndex = encoder.dequeueOutputBuffer(bufferInfo, 0)
                    }
                }
            } catch (e: Exception) { safelyStopRecorder() }
        }
    }

    private fun safelyStopRecorder() {
        if (!isRecording) return
        isRecording = false
        synchronized(stateLock) { recordingState = RecordingState.STOPPING }
        tickerJob?.cancel()

        serviceScope.launch(Dispatchers.IO) {
            withContext(NonCancellable) {
                try { encodingJob?.cancelAndJoin() } catch (_: Exception) {}
                releaseRecordingResources()
                recordingPfd?.close(); recordingPfd = null
                if (currentUri != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    try { contentResolver.update(currentUri!!, android.content.ContentValues().apply { put(android.provider.MediaStore.MediaColumns.IS_PENDING, 0) }, null, null) } catch (_: Exception) {}
                }
                sendBroadcast(Intent(ACTION_RECORDING_SAVED).apply { setPackage(packageName) })
            }
            val nextStart = synchronized(stateLock) { recordingState = RecordingState.IDLE; val next = pendingStart; pendingStart = null; next }
            when (nextStart) {
                is PendingStart.Mic -> startMicRecording(nextStart.fileName, nextStart.source, nextStart.usePublic, nextStart.category)
                is PendingStart.Internal -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) startInternalRecording(nextStart.fileName, nextStart.resultCode, nextStart.data)
                null -> stopForeground(true)
            }
        }
    }

    private fun releaseRecordingResources() {
        try { if (isEncoderStarted) { aacEncoder?.stop(); aacEncoder?.release() } } catch (_: Exception) {}
        try { if (isMuxerStarted) { aacMuxer?.stop(); aacMuxer?.release() } } catch (_: Exception) {}
        try { audioRecord?.stop(); audioRecord?.release() } catch (_: Exception) {}
        try { mediaProjection?.stop() } catch (_: Exception) {}
        try { mediaRecorder?.stop(); mediaRecorder?.release() } catch (_: Exception) { currentFile?.delete() }
        mediaRecorder = null; aacEncoder = null; aacMuxer = null; audioRecord = null; mediaProjection = null
        isMuxerStarted = false; isEncoderStarted = false
    }

    private fun startAmplitudeTicker() {
        tickerJob?.cancel()
        tickerJob = serviceScope.launch(Dispatchers.IO) {
            while (isActive && isRecording) {
                try {
                    val maxAmp = if (isInternalRecording) currentMaxAmplitude else mediaRecorder?.maxAmplitude ?: 0
                    val duration = System.currentTimeMillis() - recordingStartTime
                    sendBroadcast(Intent(ACTION_AMPLITUDE_UPDATE).apply {
                        putExtra(EXTRA_AMPLITUDE, maxAmp); putExtra(EXTRA_DURATION_MS, duration); setPackage(packageName)
                    })
                } catch (_: Exception) {}
                delay(50)
            }
        }
    }

    private suspend fun stopRecording() { safelyStopRecorder() }
    private fun cleanupMicRecorder() { try { mediaRecorder?.release() } catch (_: Exception) {}; mediaRecorder = null }

    // ── Playback Logic (NEW: Managed Foreground Playback with Media3/ExoPlayer) ──

    fun playFile(item: RecordingItem, speed: Float, pitch: Float, startMs: Int = 0) {
        val mainHandler = Handler(Looper.getMainLooper())
        mainHandler.post {
            try {
                // Ensure we have a player
                val player = exoPlayer ?: ExoPlayer.Builder(this)
                    .setLooper(Looper.getMainLooper())
                    .build().also { exoPlayer = it }
                
                player.stop()
                player.clearMediaItems()
                
                // Add listener once if not already there
                if (playbackListener == null) {
                    playbackListener = object : Player.Listener {
                        override fun onPlaybackStateChanged(state: Int) {
                            if (state == Player.STATE_ENDED) {
                                onPlaybackComplete()
                            }
                        }
                        override fun onPlayerError(error: PlaybackException) {
                            Log.e("AudioService", "ExoPlayer error: ${error.message}", error)
                            onPlaybackComplete()
                        }
                        override fun onIsPlayingChanged(isPlaying: Boolean) {
                            mediaSessionManager?.updatePlaybackState(
                                isPlaying = isPlaying,
                                isPaused = !isPlaying && player.playbackState != Player.STATE_ENDED,
                                position = player.currentPosition
                            )
                        }
                    }
                    player.addListener(playbackListener!!)
                }

                isPlaying = true
                startForegroundNotification(NOTIFICATION_ID_PLAYBACK, getString(R.string.notification_playing, item.name), false, false)

                if (mediaSessionManager?.requestAudioFocus() == false) {
                    stopPlayback()
                    return@post
                }

                val mediaItem = if (item.uri != android.net.Uri.EMPTY) {
                    MediaItem.fromUri(item.uri)
                } else {
                    MediaItem.fromUri(android.net.Uri.fromFile(item.file))
                }
                
                player.setMediaItem(mediaItem)
                player.playbackParameters = PlaybackParameters(speed, pitch)
                if (startMs > 0) player.seekTo(startMs.toLong())
                player.prepare()
                player.play()
                
                mediaSessionManager?.updateMetadata(item.name, item.durationMillis)
            } catch (e: Exception) {
                Log.e("AudioService", "Critical playback error", e)
                stopPlayback()
            }
        }
    }

    fun updatePlaybackParams(speed: Float, pitch: Float) {
        Handler(Looper.getMainLooper()).post {
            exoPlayer?.playbackParameters = PlaybackParameters(speed, pitch)
        }
    }


    fun setMediaSessionManager(manager: MediaSessionManager) {
        this.mediaSessionManager = manager
    }


    fun pausePlayback() {
        exoPlayer?.let { 
            if (it.isPlaying) { 
                it.pause() 
                mediaSessionManager?.updatePlaybackState(isPlaying = false, isPaused = true, position = it.currentPosition) 
            } 
        }
    }

    fun resumePlayback() {
        exoPlayer?.let { 
            if (!it.isPlaying) { 
                it.play() 
                mediaSessionManager?.updatePlaybackState(isPlaying = true, isPaused = false, position = it.currentPosition) 
            } 
        }
    }

    fun stopPlayback() {
        Handler(Looper.getMainLooper()).post {
            isPlaying = false
            onCompletionListener = null
            exoPlayer?.let {
                it.stop()
                it.release()
            }
            exoPlayer = null
            playbackListener = null
            mediaSessionManager?.updatePlaybackState(isPlaying = false, isPaused = false)
            stopForeground(true)
        }
    }


    fun onPlaybackComplete() {
        onCompletionListener?.invoke()
        sendBroadcast(Intent("ee.ahtilohk.audioloop.PLAYBACK_COMPLETE").setPackage(packageName))
    }

    fun setOnCompletionListener(listener: () -> Unit) {
        this.onCompletionListener = listener
    }

    fun getExoPlayer(): ExoPlayer? = exoPlayer

    // ── Common Helpers ──

    private fun startForegroundNotification(id: Int, text: String, isMediaProjection: Boolean, isRecording: Boolean) {
        val notification = createNotification(text)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            var type = if (isRecording) ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            if (isMediaProjection) type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            startForeground(id, notification, type)
        } else startForeground(id, notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, "Audio Service", NotificationManager.IMPORTANCE_LOW))
        }
    }

    private fun createNotification(content: String): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(content)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }
}
