package com.example.audioloop

import android.media.MediaCodec
import android.media.MediaCodecList
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.min

object AudioMerger {

    /**
     * Merges multiple audio files into a single output file.
     * All files are decoded to PCM, concatenated, then re-encoded to AAC/m4a.
     * WAV files are handled natively if ALL inputs are WAV, otherwise all go through decode path.
     */
    suspend fun mergeFiles(
        inputFiles: List<File>,
        outputFile: File,
        targetSampleRate: Int = 44100,
        targetChannels: Int = 2
    ): Boolean = withContext(Dispatchers.IO) {
        if (inputFiles.size < 2) return@withContext false

        try {
            val allWav = inputFiles.all { it.extension.equals("wav", ignoreCase = true) }

            // Decode all files to PCM samples
            val allSamples = mutableListOf<Short>()
            var detectedSampleRate = targetSampleRate
            var detectedChannels = targetChannels

            for (file in inputFiles) {
                val result = decodeFileToPcm(file)
                if (result != null) {
                    detectedSampleRate = result.sampleRate
                    detectedChannels = result.channels
                    allSamples.addAll(result.samples.toList())
                }
            }

            if (allSamples.isEmpty()) return@withContext false

            val combined = ShortArray(allSamples.size) { allSamples[it] }

            // Output format based on input
            if (allWav) {
                writeWav(combined, detectedSampleRate, detectedChannels, outputFile)
            } else {
                encodeToAac(combined, detectedSampleRate, detectedChannels, outputFile)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            outputFile.delete()
            false
        }
    }

    private data class PcmResult(val samples: ShortArray, val sampleRate: Int, val channels: Int)

    private fun decodeFileToPcm(file: File): PcmResult? {
        if (file.extension.equals("wav", ignoreCase = true)) {
            return decodeWavToPcm(file)
        }
        return decodeCompressedToPcm(file)
    }

    private fun decodeWavToPcm(file: File): PcmResult? {
        try {
            val raf = RandomAccessFile(file, "r")
            raf.use {
                raf.seek(22)
                val channels = raf.readShort().toInt().and(0xFFFF)
                raf.seek(24)
                val sampleRate = Integer.reverseBytes(raf.readInt())
                raf.seek(34)
                val bitsPerSample = raf.readShort().toInt().and(0xFFFF)
                if (bitsPerSample != 16) return null

                raf.seek(12)
                val chunkHeader = ByteArray(4)
                while (raf.read(chunkHeader) != -1) {
                    val chunkId = String(chunkHeader)
                    val chunkSize = Integer.reverseBytes(raf.readInt())
                    if (chunkId == "data") {
                        val rawBytes = ByteArray(chunkSize)
                        raf.read(rawBytes)
                        val bb = ByteBuffer.wrap(rawBytes).order(ByteOrder.LITTLE_ENDIAN)
                        val numSamples = chunkSize / 2
                        val samples = ShortArray(numSamples) { bb.getShort() }
                        return PcmResult(samples, sampleRate, channels)
                    } else {
                        raf.skipBytes(chunkSize)
                    }
                }
            }
            return null
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    private fun decodeCompressedToPcm(file: File): PcmResult? {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(file.absolutePath)

            var audioFormat: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    extractor.selectTrack(i)
                    audioFormat = format
                    break
                }
            }
            if (audioFormat == null) return null

            val mime = audioFormat.getString(MediaFormat.KEY_MIME) ?: return null
            val sampleRate = audioFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val channels = audioFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)

            val decoder = MediaCodec.createDecoderByType(mime)
            decoder.configure(audioFormat, null, null, 0)
            decoder.start()

            val bufferInfo = MediaCodec.BufferInfo()
            var isEos = false
            val allSamples = mutableListOf<Short>()

            while (!isEos) {
                val inputIndex = decoder.dequeueInputBuffer(10000)
                if (inputIndex >= 0) {
                    val inputBuffer = decoder.getInputBuffer(inputIndex) ?: continue
                    val sampleSize = extractor.readSampleData(inputBuffer, 0)
                    if (sampleSize < 0) {
                        decoder.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        isEos = true
                    } else {
                        decoder.queueInputBuffer(inputIndex, 0, sampleSize, extractor.sampleTime, 0)
                        extractor.advance()
                    }
                }

                var outputIndex = decoder.dequeueOutputBuffer(bufferInfo, 10000)
                while (outputIndex >= 0) {
                    val outputBuffer = decoder.getOutputBuffer(outputIndex) ?: break
                    val shortCount = bufferInfo.size / 2
                    for (i in 0 until shortCount) {
                        allSamples.add(outputBuffer.getShort(bufferInfo.offset + i * 2))
                    }
                    decoder.releaseOutputBuffer(outputIndex, false)
                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                    outputIndex = decoder.dequeueOutputBuffer(bufferInfo, 0)
                }
            }

            decoder.stop()
            decoder.release()

            return PcmResult(ShortArray(allSamples.size) { allSamples[it] }, sampleRate, channels)
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        } finally {
            try { extractor.release() } catch (_: Exception) {}
        }
    }

    private fun writeWav(samples: ShortArray, sampleRate: Int, channels: Int, outputFile: File): Boolean {
        try {
            outputFile.outputStream().use { fos ->
                val dataLen = samples.size * 2
                val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
                header.put("RIFF".toByteArray())
                header.putInt(dataLen + 36)
                header.put("WAVE".toByteArray())
                header.put("fmt ".toByteArray())
                header.putInt(16)
                header.putShort(1)
                header.putShort(channels.toShort())
                header.putInt(sampleRate)
                header.putInt(sampleRate * channels * 2)
                header.putShort((channels * 2).toShort())
                header.putShort(16)
                header.put("data".toByteArray())
                header.putInt(dataLen)
                fos.write(header.array())

                val outBuf = ByteBuffer.allocate(samples.size * 2).order(ByteOrder.LITTLE_ENDIAN)
                for (s in samples) outBuf.putShort(s)
                fos.write(outBuf.array())
            }
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            outputFile.delete()
            return false
        }
    }

    private fun encodeToAac(samples: ShortArray, sampleRate: Int, channels: Int, outputFile: File): Boolean {
        val encoderFormat = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, channels)
        encoderFormat.setInteger(MediaFormat.KEY_AAC_PROFILE, android.media.MediaCodecInfo.CodecProfileLevel.AACObjectLC)
        encoderFormat.setInteger(MediaFormat.KEY_BIT_RATE, 128000)

        val encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        encoder.configure(encoderFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        encoder.start()

        val muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var muxerTrackIndex = -1
        var muxerStarted = false

        val bufferInfo = MediaCodec.BufferInfo()
        val pcmBytes = ByteBuffer.allocate(samples.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (s in samples) pcmBytes.putShort(s)
        pcmBytes.flip()

        var inputDone = false
        var totalBytesRead = 0
        val bytesPerSample = channels * 2

        try {
            while (true) {
                if (!inputDone) {
                    val inputIndex = encoder.dequeueInputBuffer(10000)
                    if (inputIndex >= 0) {
                        val inputBuffer = encoder.getInputBuffer(inputIndex) ?: continue
                        val remaining = pcmBytes.remaining()
                        if (remaining <= 0) {
                            encoder.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            val toRead = min(inputBuffer.capacity(), remaining)
                            val aligned = toRead - (toRead % bytesPerSample)
                            if (aligned <= 0) {
                                encoder.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                inputDone = true
                            } else {
                                val slice = ByteArray(aligned)
                                pcmBytes.get(slice)
                                inputBuffer.clear()
                                inputBuffer.put(slice)
                                val pts = (totalBytesRead.toLong() / bytesPerSample) * 1_000_000L / sampleRate
                                encoder.queueInputBuffer(inputIndex, 0, aligned, pts, 0)
                                totalBytesRead += aligned
                            }
                        }
                    }
                }

                val outputIndex = encoder.dequeueOutputBuffer(bufferInfo, 10000)
                when {
                    outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        if (!muxerStarted) {
                            muxerTrackIndex = muxer.addTrack(encoder.outputFormat)
                            muxer.start()
                            muxerStarted = true
                        }
                    }
                    outputIndex >= 0 -> {
                        val outputBuffer = encoder.getOutputBuffer(outputIndex) ?: continue
                        if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                            // skip
                        } else if (muxerStarted && bufferInfo.size > 0) {
                            outputBuffer.position(bufferInfo.offset)
                            outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                            muxer.writeSampleData(muxerTrackIndex, outputBuffer, bufferInfo)
                        }
                        encoder.releaseOutputBuffer(outputIndex, false)
                        if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                            return true
                        }
                    }
                }

                if (inputDone && outputIndex == MediaCodec.INFO_TRY_AGAIN_LATER) continue
            }
        } finally {
            try { encoder.stop() } catch (_: Exception) {}
            try { encoder.release() } catch (_: Exception) {}
            try { if (muxerStarted) muxer.stop() } catch (_: Exception) {}
            try { muxer.release() } catch (_: Exception) {}
        }
    }
}
