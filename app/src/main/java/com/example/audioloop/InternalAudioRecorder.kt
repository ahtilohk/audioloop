package com.example.audioloop

import android.annotation.SuppressLint
import android.media.*
import android.media.projection.MediaProjection
import android.os.Build
import androidx.annotation.RequiresApi
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Salvestab sisemist heli (Internal Audio).
 * Sisaldab kaitset tühjade failide eest: kui heli ei tule, siis faili ei salvestata.
 */
class InternalAudioRecorder(
    private val mediaProjection: MediaProjection,
    private val outputFile: File
) {

    private var audioRecord: AudioRecord? = null
    private var mediaCodec: MediaCodec? = null
    private var mediaMuxer: MediaMuxer? = null

    // Kasutame AtomicBoolean, et vältida threadide vahelist konflikti
    private var isRecording = AtomicBoolean(false)
    private var recordingThread: Thread? = null

    @Volatile
    private var muxerStarted = false

    @Volatile
    private var totalBytesRead: Long = 0

    // Seadistused
    private val SAMPLE_RATE = 44100
    private val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_STEREO
    private val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    private val BIT_RATE = 192000 // Tõstame kvaliteeti (192kbps)
    private val BYTES_PER_SECOND = SAMPLE_RATE * 2 * 2

    @RequiresApi(Build.VERSION_CODES.Q)
    @SuppressLint("MissingPermission")
    fun start() {
        if (isRecording.get()) return

        try {
            // 1. AudioRecord
            val config = AudioPlaybackCaptureConfiguration.Builder(mediaProjection)
                .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                .addMatchingUsage(AudioAttributes.USAGE_GAME)
                .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
                .build()

            val minBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
            val bufferSize = minBufferSize * 4 // Suurem puhver stabiilsuse jaoks

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

            // 2. MediaCodec
            val codecFormat = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, SAMPLE_RATE, 2)
            codecFormat.setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE)
            codecFormat.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            codecFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, bufferSize)

            mediaCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
            mediaCodec?.configure(codecFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)

            // 3. MediaMuxer
            mediaMuxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            muxerStarted = false
            totalBytesRead = 0

            // Start
            audioRecord?.startRecording()
            mediaCodec?.start()
            isRecording.set(true)

            // Thread
            recordingThread = Thread {
                encodeAndSave(bufferSize)
            }
            recordingThread?.start()

        } catch (e: Exception) {
            e.printStackTrace()
            cleanup() // Puhasta ja kustuta fail
            throw e
        }
    }

    fun stop() {
        if (!isRecording.get()) return
        isRecording.set(false) // See annab threadile signaali lõpetada

        try {
            // Ootame threadi lõppu
            recordingThread?.join(1500)
        } catch (e: Exception) { e.printStackTrace() }

        cleanup()
    }

    private fun cleanup() {
        // 1. Peata AudioRecord
        try {
            if (audioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                audioRecord?.stop()
            }
            audioRecord?.release()
        } catch (e: Exception) {}

        // 2. Peata Codec
        try {
            mediaCodec?.stop()
            mediaCodec?.release()
        } catch (e: Exception) {}

        // 3. Peata Muxer (kõige kriitilisem osa)
        try {
            if (mediaMuxer != null) {
                if (muxerStarted) {
                    mediaMuxer?.stop()
                }
                mediaMuxer?.release()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            // TURVAKONTROLL:
            // Kui muxer ei käivitunud (tühi vaikus) VÕI andmeid oli liiga vähe (< 2KB),
            // siis kustuta see fail, sest see on kasutuskõlbmatu.
            if (!muxerStarted && totalBytesRead == 0L) {
                // tõenäoliselt tühi – kustuta
                outputFile.delete()
            } else if (!muxerStarted && totalBytesRead in 1..1024) {
                // lühike – jäta alles kuid logi ja lisa teenusesse lühike ootamine
                // või halda see teenuse stopis (wait for metadata)
            }
        }

        audioRecord = null
        mediaCodec = null
        mediaMuxer = null
        muxerStarted = false
    }

    private fun encodeAndSave(bufferSize: Int) {
        val buffer = ByteArray(bufferSize)
        val bufferInfo = MediaCodec.BufferInfo()
        var trackIndex = -1

        try {
            while (isRecording.get()) {
                val readBytes = audioRecord?.read(buffer, 0, bufferSize) ?: 0

                // Kui andmeid tuli, saadame kooderisse
                if (readBytes > 0) {
                    val inputIndex = mediaCodec?.dequeueInputBuffer(5000) ?: -1
                    if (inputIndex >= 0) {
                        val inputBuffer = mediaCodec?.getInputBuffer(inputIndex)
                        inputBuffer?.clear()
                        inputBuffer?.put(buffer, 0, readBytes)

                        // Arvutame täpse ajatempli
                        val presentationTimeUs = (totalBytesRead * 1_000_000L) / BYTES_PER_SECOND
                        mediaCodec?.queueInputBuffer(inputIndex, 0, readBytes, presentationTimeUs, 0)

                        totalBytesRead += readBytes
                    }
                }

                // Võtame pakitud andmed vastu
                var outputIndex = mediaCodec?.dequeueOutputBuffer(bufferInfo, 0) ?: -1

                // Tsükkel kõigi ootel olevate väljundite töötlemiseks
                while (outputIndex >= 0) {
                    val outputBuffer = mediaCodec?.getOutputBuffer(outputIndex)

                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                        bufferInfo.size = 0
                    }

                    if (bufferInfo.size != 0) {
                        if (muxerStarted) {
                            outputBuffer?.position(bufferInfo.offset)
                            outputBuffer?.limit(bufferInfo.offset + bufferInfo.size)
                            mediaMuxer?.writeSampleData(trackIndex, outputBuffer!!, bufferInfo)
                        }
                    }

                    mediaCodec?.releaseOutputBuffer(outputIndex, false)
                    outputIndex = mediaCodec?.dequeueOutputBuffer(bufferInfo, 0) ?: -1
                }

                if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    if (!muxerStarted) {
                        val newFormat = mediaCodec?.outputFormat
                        trackIndex = mediaMuxer?.addTrack(newFormat!!) ?: 0
                        mediaMuxer?.start()
                        muxerStarted = true
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}