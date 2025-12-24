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

    fun convertWavToM4a(inputWav: File, outputM4a: File): Boolean {
        if (!inputWav.exists()) return false

        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(inputWav.absolutePath)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set data source", e)
            return false
        }

        var audioTrackIndex = -1
        for (i in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(i)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
            if (mime.startsWith("audio/")) {
                audioTrackIndex = i
                extractor.selectTrack(i)
                break
            }
        }

        if (audioTrackIndex == -1) {
            Log.e(TAG, "No audio track found in WAV")
            extractor.release()
            return false
        }

        val inputFormat = extractor.getTrackFormat(audioTrackIndex)
        val sampleRate = if (inputFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)) inputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE) else 44100
        val channelCount = if (inputFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) inputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT) else 2

        // Setup Encoder (AAC)
        val outputFormat = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, channelCount)
        outputFormat.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
        outputFormat.setInteger(MediaFormat.KEY_BIT_RATE, 128000)
        outputFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384)

        var encoder: MediaCodec? = null
        var muxer: MediaMuxer? = null
        
        try {
            encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
            encoder.configure(outputFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            encoder.start()

            muxer = MediaMuxer(outputM4a.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to setup encoder/muxer", e)
            return false
        }

        val bufferInfo = MediaCodec.BufferInfo()
        var muxerAudioTrackIndex = -1
        var muxerStarted = false
        val buffer = ByteBuffer.allocate(1024 * 64) // 64kb buffer
        var isEOS = false

        try {
            while (true) {
                if (!isEOS) {
                    val inputBufferId = encoder.dequeueInputBuffer(10000)
                    if (inputBufferId >= 0) {
                        val inputBuffer = encoder.getInputBuffer(inputBufferId)
                        val sampleSize = extractor.readSampleData(buffer, 0)
                        
                        if (sampleSize < 0) {
                            encoder.queueInputBuffer(inputBufferId, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            isEOS = true
                        } else {
                            inputBuffer?.clear()
                            inputBuffer?.put(buffer.array(), 0, sampleSize)
                            encoder.queueInputBuffer(inputBufferId, 0, sampleSize, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }

                val outputBufferId = encoder.dequeueOutputBuffer(bufferInfo, 10000)
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
                    if (isEOS) break // Done if no more output and EOS sent
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Conversion failed", e)
            return false
        } finally {
            try { encoder.stop(); encoder.release() } catch (e: Exception) {}
            try { extractor.release() } catch (e: Exception) {}
            try { 
                if (muxerStarted) muxer.stop()
                muxer.release() 
            } catch (e: Exception) {}
        }
        return true
    }
}
