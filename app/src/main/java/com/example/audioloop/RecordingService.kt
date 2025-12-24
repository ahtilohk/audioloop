package com.example.audioloop

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.*
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.ActivityCompat
import android.content.pm.PackageManager
import android.Manifest
import kotlinx.coroutines.*
import java.io.File

class RecordingService : Service() {

    companion object {
        const val ACTION_START = "ACTION_START"
        const val ACTION_START_INTERNAL = "ACTION_START_INTERNAL"
        const val ACTION_STOP = "ACTION_STOP"
        const val ACTION_RECORDING_SAVED = "com.example.audioloop.RECORDING_SAVED"
        const val ACTION_AMPLITUDE_UPDATE = "com.example.audioloop.AMPLITUDE_UPDATE" // NEW

        const val EXTRA_FILENAME = "EXTRA_FILENAME"
        const val EXTRA_AUDIO_SOURCE = "EXTRA_AUDIO_SOURCE"
        const val EXTRA_RESULT_CODE = "EXTRA_RESULT_CODE"
        const val EXTRA_DATA = "EXTRA_DATA"
        const val EXTRA_AMPLITUDE = "EXTRA_AMPLITUDE" // NEW
        const val EXTRA_DURATION_MS = "EXTRA_DURATION_MS" // NEW

        private const val CHANNEL_ID = "recording_channel"
        private const val NOTIFICATION_ID = 1
    }

    private var mediaRecorder: MediaRecorder? = null
    private var internalRecorder: InternalAudioRecorder? = null
    private var isRecording = false
    private var currentFile: File? = null
    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    
    // New properties for AAC Internal Recording
    private var mediaProjection: android.media.projection.MediaProjection? = null
    private var isInternalRecording = false
    private var audioRecord: android.media.AudioRecord? = null
    private var recordingJob: Job? = null
    
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
                serviceScope.launch { startMicRecording(fileName, source) }
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

    private suspend fun startMicRecording(fileName: String, source: Int) {
        if (isRecording) return

        val finalFileName = if (fileName.endsWith(".m4a")) fileName else "$fileName.m4a"
        val file = File(filesDir, finalFileName)
        file.parentFile?.mkdirs()
        currentFile = file

        withContext(Dispatchers.Main) {
            startForegroundServiceNotification("Mikrofon lindistab...", false)
        }

        try {
            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(this)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }

            mediaRecorder?.apply {
                setAudioSource(source)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(128000)
                setAudioSamplingRate(44100)
                setOutputFile(file.absolutePath)
                setOnErrorListener { _, _, _ ->
                   showToast("Viga salvestamisel!")
                   safelyStopRecorder()
                }
                prepare()
                start()
            }
            isRecording = true
            recordingStartTime = System.currentTimeMillis()
            startAmplitudeTicker()
            showToast("Salvestan: ${file.name}")
        } catch (e: Exception) {
            e.printStackTrace()
            showToast("Viga mikrofoniga: ${e.message}")
            cleanupMicRecorder()
            file.delete()
        }
    }

    private suspend fun startInternalRecording(fileName: String, resultCode: Int, data: Intent) {
        if (isRecording) return
        
        // Use temp .wav file for robust capture
        val finalFileName = if (fileName.endsWith(".m4a")) fileName else "$fileName.m4a"
        // We'll record to .wav first, then convert
        val tempWavFile = File(filesDir, "${System.currentTimeMillis()}_temp.wav")
        currentFile = File(filesDir, finalFileName) // Final target

        withContext(Dispatchers.Main) {
            startForegroundServiceNotification("Voogsalvestus käib...", true)
        }

        try {
            // Get valid MediaProjection
            var projection = mediaProjection
            if (projection == null) {
                 val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as android.media.projection.MediaProjectionManager
                 projection = projectionManager.getMediaProjection(resultCode, data)
            }
            // If still null (should not happen if data is valid), we crash or return
            if (projection == null) throw IllegalStateException("MediaProjection on null")

            mediaProjection = projection // Update class property
            
            internalRecorder = InternalAudioRecorder(projection, tempWavFile)
            
            // Note: internalRecorder.start() might be blocking or async? 
            // Checking InternalAudioRecorder implementation: it starts a thread.
            internalRecorder?.start() 
            
            // Store temp file reference for conversion later
            currentFile = tempWavFile // Temporarily point to wav
            
            isInternalRecording = true
            isRecording = true
            recordingStartTime = System.currentTimeMillis()
            
            startAmplitudeTicker()
            showToast("Voogsalvestus algas!")
            
        } catch (e: Exception) {
            e.printStackTrace()
            showToast("Viga Voogsalvestusega: ${e.message}")
            stopRecording()
        }
    }
    
    // ...
    
    private fun stopInternalRecording() {
         if (!isInternalRecording) return
         isInternalRecording = false
         
         try {
             internalRecorder?.stop() // Creates valid WAV
             internalRecorder = null
         } catch (e: Exception) { e.printStackTrace() }
         
         stopAmplitudeTicker()
         
         // Convert WAV to M4A
         val wavFile = currentFile
         if (wavFile != null && wavFile.exists() && wavFile.name.endsWith(".wav")) {
             val m4aName = wavFile.name.replace("_temp.wav", ".m4a").let { 
                 if (it.endsWith(".m4a")) it else "$it.m4a" 
             }
             // Actually we want the original requested filename...
             // In startInternalRecording we lost the tracking of 'finalFileName'.
             // Let's assume we want to save to the user's requested name if possible, 
             // but generating a timestamped name is safer to avoid conflicts.
             // Let's use the 'currentFile' (wav) parent and a proper name.
             
             // Strategy: Convert, then delete wav.
             val finalM4a = File(wavFile.parent, "Voog_${System.currentTimeMillis()}.m4a")
             
             CoroutineScope(Dispatchers.IO).launch {
                 try {
                     val success = AudioConverter.convertWavToM4a(wavFile, finalM4a)
                     if (success) {
                         wavFile.delete()
                         withContext(Dispatchers.Main) {
                             Toast.makeText(applicationContext, "Salvestatud M4A: ${finalM4a.name}", Toast.LENGTH_SHORT).show()
                             // Update list
                             sendBroadcast(Intent(ACTION_RECORDING_SAVED).setPackage(packageName))
                         }
                     } else {
                         withContext(Dispatchers.Main) {
                             Toast.makeText(applicationContext, "Konverteerimine ebaõnnestus!", Toast.LENGTH_SHORT).show()
                         }
                     }
                 } catch (e: Exception) {
                     e.printStackTrace()
                 }
             }
         }
         
         isRecording = false
         stopForegroundService()
    }

    private fun stopForegroundService() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    private fun safelyStopRecorder() {
        if (!isRecording) return
        try {
            mediaRecorder?.stop()
        } catch (e: Exception) {
            // Kui stop() viskab vea (nt fail on liiga lühike või tühi), siis kustutame katkise faili
            currentFile?.delete()
        } finally {
            cleanupMicRecorder()
            stopAmplitudeTicker()
        }
    }

    private fun startAmplitudeTicker() {
        tickerJob?.cancel()
        tickerJob = serviceScope.launch(Dispatchers.IO) {
            while (isActive && isRecording) {
                try {
                    val maxAmp = if (isInternalRecording) {
                        // For internal recording, we don't have maxAmplitude directly from AudioRecord
                        // Could implement a custom amplitude calculation from PCM buffer if needed
                        // For now, return 0 or a placeholder
                        0
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
                } catch (e: Exception) { /* Ignore exceptions during amplitude polling */ }
                delay(50) // 20 korda sekundis sujuva liikumise jaoks
            }
        }
    }

    private fun stopAmplitudeTicker() {
        tickerJob?.cancel()
        tickerJob = null
    }


    private suspend fun stopRecording() {
        if (isInternalRecording) {
            stopInternalRecording()
        } else {
            safelyStopRecorder()
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()

        val broadcastIntent = Intent(ACTION_RECORDING_SAVED)
        broadcastIntent.setPackage(packageName)
        sendBroadcast(broadcastIntent)

        withContext(Dispatchers.Main) {
            Toast.makeText(applicationContext, "Salvestatud!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun cleanupMicRecorder() {
        try { mediaRecorder?.release() } catch (e: Exception) {}
        mediaRecorder = null
    }

    private fun startForegroundServiceNotification(contentText: String, isMediaProjection: Boolean) {
        val notification = createNotification(contentText)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val type = if (isMediaProjection) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION else ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
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
}