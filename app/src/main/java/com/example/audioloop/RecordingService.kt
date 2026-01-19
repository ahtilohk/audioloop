package com.example.audioloop

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
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.widget.Toast
import android.annotation.SuppressLint
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import java.io.File

class RecordingService : Service() {

    companion object {
        const val ACTION_START = "ACTION_START"
        const val ACTION_START_INTERNAL = "ACTION_START_INTERNAL"
        const val ACTION_STOP = "ACTION_STOP"
        const val ACTION_RECORDING_SAVED = "com.example.audioloop.RECORDING_SAVED"
        const val ACTION_AMPLITUDE_UPDATE = "com.example.audioloop.AMPLITUDE_UPDATE"

        const val EXTRA_FILENAME = "EXTRA_FILENAME"
        const val EXTRA_AUDIO_SOURCE = "EXTRA_AUDIO_SOURCE"
        const val EXTRA_RESULT_CODE = "EXTRA_RESULT_CODE"
        const val EXTRA_DATA = "EXTRA_DATA"
        const val EXTRA_AMPLITUDE = "EXTRA_AMPLITUDE"
        const val EXTRA_DURATION_MS = "EXTRA_DURATION_MS"
        const val EXTRA_USE_PUBLIC_STORAGE = "EXTRA_USE_PUBLIC_STORAGE"
        const val EXTRA_CATEGORY = "EXTRA_CATEGORY"

        private const val CHANNEL_ID = "recording_channel"
        private const val NOTIFICATION_ID = 1
    }

    private var mediaRecorder: MediaRecorder? = null
    private var isRecording = false
    private var currentFile: File? = null
    private var currentUri: android.net.Uri? = null // For MediaStore
    private var recordingPfd: android.os.ParcelFileDescriptor? = null
    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    
    // --- REAL-TIME AAC ENCODING PROPERTIES ---
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
    
    // Live Waveform Ticker
    private var tickerJob: Job? = null
    private var recordingStartTime = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onDestroy() {
        safelyStopRecorder()
        super.onDestroy()
        serviceScope.cancel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val fileName = intent.getStringExtra(EXTRA_FILENAME) ?: "recording"
                val source = intent.getIntExtra(EXTRA_AUDIO_SOURCE, MediaRecorder.AudioSource.MIC)
                val usePublic = intent.getBooleanExtra(EXTRA_USE_PUBLIC_STORAGE, false)
                val category = intent.getStringExtra(EXTRA_CATEGORY) ?: "General"
                serviceScope.launch { startMicRecording(fileName, source, usePublic, category) }
            }
            ACTION_START_INTERNAL -> {
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
                } else {
                    stopSelf()
                }
            }
            ACTION_STOP -> {
                serviceScope.launch { stopRecording() }
            }
        }
        return START_NOT_STICKY
    }

    private suspend fun startMicRecording(fileName: String, source: Int, usePublic: Boolean, category: String) {
        if (isRecording) return

        val finalFileName = if (fileName.endsWith(".m4a")) fileName else "$fileName.m4a"
        
        var fileDescriptor: java.io.FileDescriptor? = null
        var pfd: android.os.ParcelFileDescriptor? = null
        
        currentFile = null
        currentUri = null

        withContext(Dispatchers.Main) {
            startForegroundServiceNotification("Mikrofon lindistab...", false)
        }

        try {
             // 1. Prepare Output (File or MediaStore)
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
                 } else {
                     throw java.io.IOException("Failed to create MediaStore entry")
                 }
             } else {
                 val file = File(filesDir, if (category == "General") finalFileName else "$category/$finalFileName")
                 file.parentFile?.mkdirs()
                 currentFile = file
             }

            val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(this)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
            mediaRecorder = recorder

            recorder.apply {
                setAudioSource(source)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(128000)
                setAudioSamplingRate(44100)
                
                if (usePublic && fileDescriptor != null) {
                    setOutputFile(fileDescriptor)
                } else if (currentFile != null) {
                    setOutputFile(currentFile!!.absolutePath)
                } else {
                    throw java.io.IOException("No output target")
                }
                
                setOnErrorListener { _, _, _ ->
                   showToast("Viga salvestamisel!")
                   safelyStopRecorder()
                }
                prepare()
                start()
            }
            isRecording = true
            isInternalRecording = false
            recordingStartTime = System.currentTimeMillis()
            startAmplitudeTicker()
            showToast("Salvestan: $finalFileName")
            
            // Keep PFD open? MediaRecorder might need it.
            // If we close PFD now, does MediaRecorder crash?
            // Usually valid to close AFTER setOutputFile? No, it needs to write.
            // We should keep pfd reference and close in stop.
            // But we can't keep local pfd variable. Passing to class property?
            // Actually, MediaRecorder dups the FD. But for ParcelFileDescriptor we should be careful.
            // Let's close PFD after start()? Documentation says "The descriptor... must not be closed... until stop".
            // So I need to keep pfd in class field?
            // Or rely on MediaRecorder taking ownership? `setOutputFile(FileDescriptor)` usually doesn't take ownership.
            // So I'll execute `pfd.close()` in `stopRecording` or keep it open.
            // Actually, let's close it in `safelyStopRecorder` if I store it.
            // Wait, I can't easily store it as local var. I'll add `private var recordingPfd: ParcelFileDescriptor?` to class.
            
            if (pfd != null) recordingPfd = pfd
            
        } catch (e: Exception) {
            e.printStackTrace()
            showToast("Viga mikrofoniga: ${e.message}")
            cleanupMicRecorder()
            currentFile?.delete()
            currentUri?.let { contentResolver.delete(it, null, null) }
            pfd?.close()
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun startInternalRecording(fileName: String, resultCode: Int, data: Intent) {
        if (isRecording) return

        val finalFileName = if (fileName.endsWith(".m4a")) fileName else "$fileName.m4a"
        val file = File(filesDir, finalFileName)
        currentFile = file
        
        startForegroundServiceNotification("Voogsalvestus käib...", true)

        try {
            var projection = mediaProjection
            if (projection == null) {
                 val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as android.media.projection.MediaProjectionManager
                 projection = projectionManager.getMediaProjection(resultCode, data)
            }
            if (projection == null) throw IllegalStateException("MediaProjection on null")
            mediaProjection = projection

            // 1. Setup AudioRecord (Input)
            val sampleRate = 44100
            val channelConfig = AudioFormat.CHANNEL_IN_STEREO
            val audioFormat = AudioFormat.ENCODING_PCM_16BIT
            
            val config = AudioPlaybackCaptureConfiguration.Builder(projection)
                .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                .addMatchingUsage(AudioAttributes.USAGE_GAME)
                .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
                .build()

            val minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
            val bufferSize = minBufferSize * 2

            val recordFormat = AudioFormat.Builder()
                .setEncoding(audioFormat)
                .setSampleRate(sampleRate)
                .setChannelMask(channelConfig)
                .build()

            @SuppressLint("MissingPermission")
            val record = AudioRecord.Builder()
                .setAudioFormat(recordFormat)
                .setBufferSizeInBytes(bufferSize)
                .setAudioPlaybackCaptureConfig(config)
                .build()
            audioRecord = record

            if (record.state != AudioRecord.STATE_INITIALIZED) {
                throw IllegalStateException("AudioRecord init failed state=${record.state}")
            }
            
            // 2. Setup MediaCodec (Encoder)
            val encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
            val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, 2)
            format.setInteger(MediaFormat.KEY_BIT_RATE, 192000)
            format.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, bufferSize) 
            
            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            aacEncoder = encoder
            
            // 3. Setup MediaMuxer (Output)
            aacMuxer = MediaMuxer(file.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            isMuxerStarted = false
            muxerTrackIndex = -1

            // 4. Start Loop
            try {
                record.startRecording()
            } catch (e: IllegalStateException) {
                throw IllegalStateException("AudioRecord start failed: ${e.message}")
            }
            encoder.start()
            isEncoderStarted = true
            isRecording = true
            isInternalRecording = true
            recordingStartTime = System.currentTimeMillis()
            
            startEncodingLoop(record, encoder, bufferSize)
            
            startAmplitudeTicker() 
            showToast("Voogsalvestus (Real-Time) algas!")

        } catch (e: Throwable) { // Catch Throwable to handle NoClassDefFoundError etc
            e.printStackTrace()
            showToast("Viga Voogsalvestusel: ${e.message}")
            safelyStopRecorder()
        }
    }

    private fun startEncodingLoop(record: AudioRecord, encoder: MediaCodec, bufferSize: Int) {
        encodingJob = serviceScope.launch(Dispatchers.Default) {
            val buffer = ByteArray(bufferSize)
            val bufferInfo = MediaCodec.BufferInfo()
            
            try {
                while (isActive && isRecording) {
                    // 1. Read PCM
                    val read = record.read(buffer, 0, bufferSize)
                    if (read < 0) {
                        Log.e("AudioLoop", "AudioRecord error: $read")
                        break 
                    }
                    
                    // Live Amplitude Calculation
                    if (read > 0) {
                        var sum = 0.0
                        // Sample every 100th byte (approx)
                        for (i in 0 until read step 100) {
                            sum += kotlin.math.abs(buffer[i].toInt())
                        }
                        currentMaxAmplitude = (sum / (read/100 + 1)).toInt() * 10 // Adjusted scaling
                    }

                    // 2. Feed Encoder
                    val inputIndex = encoder.dequeueInputBuffer(2000)
                    if (inputIndex >= 0) {
                        val inputBuffer = encoder.getInputBuffer(inputIndex)
                        if (inputBuffer != null) {
                            inputBuffer.clear()
                            inputBuffer.put(buffer, 0, read)
                            encoder.queueInputBuffer(inputIndex, 0, read, System.nanoTime() / 1000, 0)
                        }
                    }

                    // 3. Pull Encoded Data & Mux
                    var outputIndex = encoder.dequeueOutputBuffer(bufferInfo, 0)
                    while (outputIndex >= 0) {
                        val outputBuffer = encoder.getOutputBuffer(outputIndex) ?: break
                        
                        if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                            bufferInfo.size = 0
                        }

                        if (bufferInfo.size != 0) {
                            if (!isMuxerStarted) {
                                muxerTrackIndex = aacMuxer?.addTrack(encoder.outputFormat) ?: -1
                                aacMuxer?.start()
                                isMuxerStarted = true
                            }
                            
                            outputBuffer.position(bufferInfo.offset)
                            outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                            aacMuxer?.writeSampleData(muxerTrackIndex, outputBuffer, bufferInfo)
                        }

                        encoder.releaseOutputBuffer(outputIndex, false)
                        outputIndex = encoder.dequeueOutputBuffer(bufferInfo, 0)
                    }
                }
            } catch (e: Exception) {
                Log.e("AudioLoop", "Encoding Loop Error", e)
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(applicationContext, "Internal recording stopped: ${e.message}", Toast.LENGTH_SHORT).show()
                }
                safelyStopRecorder()
            }
        }
    }

    private fun safelyStopRecorder() {
        if (!isRecording) return
        
        // 1. Mark state immediately to prevent re-entry
        isRecording = false
        
        // 2. UI Feedback immediately (Main Thread safe)
        startForegroundServiceNotification("Salvestamine peatatud...", false)
        stopForegroundService()
        tickerJob?.cancel()

        // 3. Launch Async Cleanup (Avoids blocking Main Thread & Deadlocks)
        // We use NonCancellable to ensure cleanup finishes even if Service is destroyed/scope cancelled.
        serviceScope.launch(Dispatchers.IO) {
            withContext(NonCancellable) {
                try {
                    // Cancel encoding job. 
                    // Since we are in a separate coroutine, we can safely join (wait) for it,
                    // even if safelyStopRecorder was called FROM encodingJob (because that call returns immediately).
                    encodingJob?.cancelAndJoin()
                } catch (e: Exception) { e.printStackTrace() }

                // 4. Deterministic Resource Release
                releaseResources()

                // Close PFD
                try {
                    recordingPfd?.close()
                    recordingPfd = null
                } catch (e: Exception) { e.printStackTrace() }
                
                // Update Pending Status (Public Storage)
                if (currentUri != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    try {
                        val values = android.content.ContentValues().apply {
                            put(android.provider.MediaStore.MediaColumns.IS_PENDING, 0)
                        }
                        contentResolver.update(currentUri!!, values, null, null)
                    } catch (e: Exception) { e.printStackTrace() }
                }

                // 5. Notify Completion
                val intent = Intent(ACTION_RECORDING_SAVED)
                intent.setPackage(packageName)
                sendBroadcast(intent)
                
                withContext(Dispatchers.Main) {
                    Toast.makeText(applicationContext, "Salvestatud!", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun releaseResources() {
        // AAC Components (Encoder/Muxer)
        try {
            if (isEncoderStarted) {
                aacEncoder?.stop()
                aacEncoder?.release()
            }
        } catch (e: Exception) { e.printStackTrace() }
        
        try {
            if (isMuxerStarted) {
                aacMuxer?.stop() 
                aacMuxer?.release()
            }
        } catch (e: Exception) { e.printStackTrace() }
        
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Exception) { e.printStackTrace() }

        // Mic Recorder
        try {
            mediaRecorder?.stop()
            mediaRecorder?.release()
        } catch (e: Exception) { 
             currentFile?.delete()
        }
        
        // Reset State Variables (Idempotency)
        mediaRecorder = null
        aacEncoder = null
        aacMuxer = null
        audioRecord = null
        isMuxerStarted = false
        isEncoderStarted = false
        // isRecording is already false
    }

    private fun startAmplitudeTicker() {
        tickerJob?.cancel()
        tickerJob = serviceScope.launch(Dispatchers.IO) {
            while (isActive && isRecording) {
                try {
                    val maxAmp = if (isInternalRecording) {
                        currentMaxAmplitude
                    } else {
                        mediaRecorder?.maxAmplitude ?: 0
                    }
                    val duration = System.currentTimeMillis() - recordingStartTime
                    val intent = Intent(ACTION_AMPLITUDE_UPDATE).apply {
                        putExtra(EXTRA_AMPLITUDE, maxAmp)
                        putExtra(EXTRA_DURATION_MS, duration)
                        setPackage(packageName)
                    }
                    sendBroadcast(intent)
                } catch (e: Exception) { /* Ignore */ }
                delay(50)
            }
        }
    }

    private fun stopAmplitudeTicker() {
        tickerJob?.cancel()
        tickerJob = null
    }

    private suspend fun stopRecording() {
        safelyStopRecorder()
    }

    private fun cleanupMicRecorder() {
        try { mediaRecorder?.release() } catch (e: Exception) {}
        mediaRecorder = null
    }

    private fun startForegroundServiceNotification(contentText: String, isMediaProjection: Boolean) {
        val notification = createNotification(contentText)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val type = if (isMediaProjection) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                } else {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                }
                startForeground(NOTIFICATION_ID, notification, type)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) { e.printStackTrace() }
    }

    private fun showToast(message: String) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Salvestamine", NotificationManager.IMPORTANCE_LOW)
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(content: String): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("AudioLoop")
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }
    
    private fun stopForegroundService() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
             @Suppress("DEPRECATION")
            stopForeground(true)
        }
    }
}
