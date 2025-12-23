package com.example.audioloop

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.widget.Toast
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import java.io.File

class RecordingService : Service() {

    companion object {
        const val ACTION_START = "ACTION_START"
        const val ACTION_START_INTERNAL = "ACTION_START_INTERNAL"
        const val ACTION_STOP = "ACTION_STOP"
        const val ACTION_RECORDING_SAVED = "com.example.audioloop.RECORDING_SAVED"

        const val EXTRA_FILENAME = "EXTRA_FILENAME"
        const val EXTRA_AUDIO_SOURCE = "EXTRA_AUDIO_SOURCE"
        const val EXTRA_RESULT_CODE = "EXTRA_RESULT_CODE"
        const val EXTRA_DATA = "EXTRA_DATA"

        private const val CHANNEL_ID = "recording_channel"
        private const val NOTIFICATION_ID = 1
    }

    private var mediaRecorder: MediaRecorder? = null
    private var internalRecorder: InternalAudioRecorder? = null
    private var isRecording = false
    private var currentFile: File? = null
    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onDestroy() {
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
                // OLULINE MUUDATUS: Eemaldasime sampleRate ja bitRate sundimise.
                // Laseme telefonil valida vaikesätted, mis on riistvaraga ühilduvad.
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }
            isRecording = true
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
        val finalFileName = if (fileName.endsWith(".m4a")) fileName else "$fileName.m4a"
        val file = File(filesDir, finalFileName)
        file.parentFile?.mkdirs()
        currentFile = file

        withContext(Dispatchers.Main) {
            startForegroundServiceNotification("Voogsalvestus käib...", true)
        }

        try {
            val mpManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as android.media.projection.MediaProjectionManager
            val mediaProjection = mpManager.getMediaProjection(resultCode, data)
            if (mediaProjection == null) { stopSelf(); return }

            internalRecorder = InternalAudioRecorder(mediaProjection, file)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                internalRecorder?.start()
                isRecording = true
                showToast("Voogsalvestus algas!")
            }
        } catch (e: Exception) {
            showToast("Viga: ${e.message}")
            stopRecording()
        }
    }

    private suspend fun stopRecording() {
        if (mediaRecorder != null) {
            try {
                mediaRecorder?.stop()
            } catch (e: RuntimeException) {
                currentFile?.delete()
            } finally {
                cleanupMicRecorder()
            }
        }
        if (internalRecorder != null) {
            internalRecorder?.stop()
            internalRecorder = null
        }

        isRecording = false
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