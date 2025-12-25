package com.example.audioloop

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.util.Log
import java.io.File
import java.nio.ByteBuffer

object AudioConverter {
    private const val TAG = "AudioConverter"

    /**
     * Converts RAW PCM file (16-bit, 44100Hz, Stereo) to M4A (AAC).
     * Also computes waveform data in the same pass.
     */
    fun convertPcmToM4a(
        pcmFile: File, 
        outputM4a: File, 
        onWaveformReady: (List<Int>) -> Unit
    ): Boolean {
        if (!pcmFile.exists()) return false

        val sampleRate = 44100
        val channelCount = 2
        val bitrate = 128000
        
        // --- Waveform calculation setup ---
        val waveform = ArrayList<Int>()
        var sumSamples = 0.0
        var sampleCount = 0
        // We want ~100 bars for the file. 
        // File size / (2 bytes * 2 channels) = total samples
        val totalSamples = pcmFile.length() / 4
        val samplesPerBar = (totalSamples / 100).toInt().coerceAtLeast(100)

        // --- Encoder Setup ---
        val outputFormat = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, channelCount)
        outputFormat.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
        outputFormat.setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
        outputFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384)

        var encoder: MediaCodec? = null
        var muxer: MediaMuxer? = null
        val inputStream = java.io.FileInputStream(pcmFile)
        
        try {
            encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
            encoder.configure(outputFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            encoder.start()

            muxer = MediaMuxer(outputM4a.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to setup encoder/muxer", e)
            inputStream.close()
            return false
        }

        val bufferInfo = MediaCodec.BufferInfo()
        var muxerAudioTrackIndex = -1
        var muxerStarted = false
        val buffer = ByteArray(4096) // Read chunk
        var isEOS = false
        
        try {
            while (true) {
                if (!isEOS) {
                    val inputBufferId = encoder.dequeueInputBuffer(5000)
                    if (inputBufferId >= 0) {
                        val inputBuffer = encoder.getInputBuffer(inputBufferId)
                        val read = inputStream.read(buffer)
                        
                        if (read == -1) {
                            encoder.queueInputBuffer(inputBufferId, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            isEOS = true
                        } else {
                            // --- Waveform Process ---
                            for (i in 0 until read step 4) { // 16-bit stereo = 4 bytes per frame
                                if (i + 1 < read) {
                                    // Just take left channel for visualization (Little Endian)
                                    val low = buffer[i].toInt() and 0xFF
                                    val high = buffer[i+1].toInt() shl 8
                                    val sample = (high or low).toShort()
                                    sumSamples += kotlin.math.abs(sample.toInt())
                                    sampleCount++
                                }
                                if (sampleCount >= samplesPerBar) {
                                    waveform.add((sumSamples / sampleCount).toInt())
                                    sumSamples = 0.0
                                    sampleCount = 0
                                }
                            }
                            
                            // --- Encoding Process ---
                            inputBuffer?.clear()
                            inputBuffer?.put(buffer, 0, read)
                            encoder.queueInputBuffer(inputBufferId, 0, read, System.nanoTime() / 1000, 0)
                        }
                    }
                }

                val outputBufferId = encoder.dequeueOutputBuffer(bufferInfo, 5000)
                if (outputBufferId == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    if (muxerStarted) throw RuntimeException("Format changed twice")
                    muxerAudioTrackIndex = muxer.addTrack(encoder.outputFormat)
                    muxer.start()
                    muxerStarted = true
                } else if (outputBufferId >= 0) {
                    val outputBuffer = encoder.getOutputBuffer(outputBufferId)
                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                        bufferInfo.size = 0
                    }
                    if (bufferInfo.size != 0 && muxerStarted) {
                        outputBuffer?.position(bufferInfo.offset)
                        outputBuffer?.limit(bufferInfo.offset + bufferInfo.size)
                        muxer.writeSampleData(muxerAudioTrackIndex, outputBuffer!!, bufferInfo)
                    }
                    encoder.releaseOutputBuffer(outputBufferId, false)
                    
                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        break
                    }
                } else if (outputBufferId == MediaCodec.INFO_TRY_AGAIN_LATER) {
                    if (isEOS) break 
                }
            }
            
            // Finalize waveform
            onWaveformReady(waveform)
            
        } catch (e: Exception) {
            Log.e(TAG, "Conversion failed", e)
            return false
        } finally {
            inputStream.close()
            try { encoder.stop(); encoder.release() } catch (e: Exception) {}
            try { if (muxerStarted) muxer.stop(); muxer.release() } catch (e: Exception) {}
        }
        return true
    }
}
