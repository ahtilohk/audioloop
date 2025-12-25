package com.example.audioloop

import android.annotation.SuppressLint
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.projection.MediaProjection
import android.os.Build
import androidx.annotation.RequiresApi
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Salvestab sisemist heli (Internal Audio) RAW PCM formaadis.
 * See on kõige stabiilsem viis andmete kättesaamiseks.
 * Hiljem konverteeritakse see M4A-ks.
 */
class InternalAudioRecorder(
    private val mediaProjection: MediaProjection,
    private val outputFile: File
) {

    private var audioRecord: AudioRecord? = null
    private var isRecording = AtomicBoolean(false)
    private var recordingThread: Thread? = null

    // Standard: 44.1kHz, Stereo, 16-bit
    private val SAMPLE_RATE = 44100
    private val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_STEREO
    private val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    
    @RequiresApi(Build.VERSION_CODES.Q)
    @SuppressLint("MissingPermission")
    fun start() {
        if (isRecording.get()) return

        val config = AudioPlaybackCaptureConfiguration.Builder(mediaProjection)
            .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
            .addMatchingUsage(AudioAttributes.USAGE_GAME)
            .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
            .build()

        val minBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        val bufferSize = minBufferSize * 2

        val audioFormat = AudioFormat.Builder()
            .setEncoding(AUDIO_FORMAT)
            .setSampleRate(SAMPLE_RATE)
            .setChannelMask(CHANNEL_CONFIG)
            .build()

        audioRecord = AudioRecord.Builder()
            .setAudioFormat(audioFormat)
            .setBufferSizeInBytes(bufferSize)
            .setAudioPlaybackCaptureConfig(config)
            .build()

        if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
            throw IllegalStateException("AudioRecord ei initsialiseerunud.")
        }

        audioRecord?.startRecording()
        isRecording.set(true)

        recordingThread = Thread {
            writePcmToFile(bufferSize)
        }
        recordingThread?.start()
    }

    private fun writePcmToFile(bufferSize: Int) {
        val buffer = ByteArray(bufferSize)
        FileOutputStream(outputFile).use { fos ->
            while (isRecording.get()) {
                val read = audioRecord?.read(buffer, 0, bufferSize) ?: 0
                if (read > 0) {
                    fos.write(buffer, 0, read)
                }
            }
        }
    }

    fun stop() {
        if (!isRecording.get()) return
        isRecording.set(false)
        try {
            recordingThread?.join(1000)
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Exception) { e.printStackTrace() }
        audioRecord = null
    }
}
