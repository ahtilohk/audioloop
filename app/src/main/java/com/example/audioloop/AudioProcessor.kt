package com.example.audioloop

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.min

object AudioProcessor {

    /**
     * Normalizes audio so the peak amplitude reaches the target level.
     * For WAV: direct PCM manipulation. For compressed: decode → process → re-encode.
     */
    suspend fun normalize(
        inputFile: File,
        outputFile: File,
        targetPeak: Float = 0.95f // 0..1, how loud the peak should be
    ): Boolean = withContext(Dispatchers.IO) {
        if (inputFile.extension.equals("wav", ignoreCase = true)) {
            normalizeWav(inputFile, outputFile, targetPeak)
        } else {
            processCompressed(inputFile, outputFile) { samples, sampleRate, channels ->
                // Find peak
                var peak = 0
                for (s in samples) {
                    val a = abs(s.toInt())
                    if (a > peak) peak = a
                }
                if (peak == 0) return@processCompressed samples
                val gain = (targetPeak * 32767f) / peak
                ShortArray(samples.size) { i ->
                    (samples[i] * gain).toInt().coerceIn(-32767, 32767).toShort()
                }
            }
        }
    }

    /**
     * Applies gain (volume boost/cut) to audio.
     * @param gainDb gain in decibels (e.g. 6.0 = double volume, -6.0 = half)
     */
    suspend fun applyGain(
        inputFile: File,
        outputFile: File,
        gainDb: Float
    ): Boolean = withContext(Dispatchers.IO) {
        val multiplier = Math.pow(10.0, gainDb / 20.0).toFloat()
        if (inputFile.extension.equals("wav", ignoreCase = true)) {
            processWavSamples(inputFile, outputFile) { samples, _, _ ->
                ShortArray(samples.size) { i ->
                    (samples[i] * multiplier).toInt().coerceIn(-32767, 32767).toShort()
                }
            }
        } else {
            processCompressed(inputFile, outputFile) { samples, _, _ ->
                ShortArray(samples.size) { i ->
                    (samples[i] * multiplier).toInt().coerceIn(-32767, 32767).toShort()
                }
            }
        }
    }

    /**
     * Applies fade-in and/or fade-out to audio.
     * @param fadeInMs duration of fade-in in milliseconds (0 = no fade-in)
     * @param fadeOutMs duration of fade-out in milliseconds (0 = no fade-out)
     */
    suspend fun applyFade(
        inputFile: File,
        outputFile: File,
        fadeInMs: Long,
        fadeOutMs: Long
    ): Boolean = withContext(Dispatchers.IO) {
        if (inputFile.extension.equals("wav", ignoreCase = true)) {
            processWavSamples(inputFile, outputFile) { samples, sampleRate, channels ->
                applyFadeToSamples(samples, sampleRate, channels, fadeInMs, fadeOutMs)
            }
        } else {
            processCompressed(inputFile, outputFile) { samples, sampleRate, channels ->
                applyFadeToSamples(samples, sampleRate, channels, fadeInMs, fadeOutMs)
            }
        }
    }

    private fun applyFadeToSamples(
        samples: ShortArray,
        sampleRate: Int,
        channels: Int,
        fadeInMs: Long,
        fadeOutMs: Long
    ): ShortArray {
        val result = samples.copyOf()
        val totalFrames = samples.size / channels
        val fadeInFrames = (fadeInMs * sampleRate / 1000).toInt().coerceAtMost(totalFrames)
        val fadeOutFrames = (fadeOutMs * sampleRate / 1000).toInt().coerceAtMost(totalFrames)

        // Fade in
        for (frame in 0 until fadeInFrames) {
            val gain = frame.toFloat() / fadeInFrames
            for (ch in 0 until channels) {
                val idx = frame * channels + ch
                if (idx < result.size) {
                    result[idx] = (result[idx] * gain).toInt().toShort()
                }
            }
        }

        // Fade out
        val fadeOutStart = totalFrames - fadeOutFrames
        for (frame in fadeOutStart until totalFrames) {
            val gain = (totalFrames - frame).toFloat() / fadeOutFrames
            for (ch in 0 until channels) {
                val idx = frame * channels + ch
                if (idx < result.size) {
                    result[idx] = (result[idx] * gain).toInt().toShort()
                }
            }
        }

        return result
    }

    // --- WAV processing ---

    private fun normalizeWav(inputFile: File, outputFile: File, targetPeak: Float): Boolean {
        return processWavSamples(inputFile, outputFile) { samples, _, _ ->
            var peak = 0
            for (s in samples) {
                val a = abs(s.toInt())
                if (a > peak) peak = a
            }
            if (peak == 0) return@processWavSamples samples
            val gain = (targetPeak * 32767f) / peak
            ShortArray(samples.size) { i ->
                (samples[i] * gain).toInt().coerceIn(-32767, 32767).toShort()
            }
        }
    }

    private fun processWavSamples(
        inputFile: File,
        outputFile: File,
        process: (ShortArray, Int, Int) -> ShortArray
    ): Boolean {
        try {
            val raf = RandomAccessFile(inputFile, "r")
            raf.use {
                // Read WAV header fields in little-endian
                val headerBytes = ByteArray(36)
                raf.read(headerBytes)
                val hdr = ByteBuffer.wrap(headerBytes).order(ByteOrder.LITTLE_ENDIAN)
                hdr.position(22)
                val channels = hdr.getShort().toInt().and(0xFFFF)
                val sampleRate = hdr.getInt() // offset 24
                hdr.position(34)
                val bitsPerSample = hdr.getShort().toInt().and(0xFFFF)

                if (bitsPerSample != 16) return false // Only 16-bit supported

                // Find data chunk
                raf.seek(12)
                val chunkHeader = ByteArray(4)
                val sizeBuf = ByteArray(4)
                while (raf.read(chunkHeader) != -1) {
                    val chunkId = String(chunkHeader)
                    raf.read(sizeBuf)
                    val chunkSize = ByteBuffer.wrap(sizeBuf).order(ByteOrder.LITTLE_ENDIAN).getInt()

                    if (chunkId == "data") {
                        val dataPos = raf.filePointer
                        val numSamples = chunkSize / 2

                        // Read all samples
                        val rawBytes = ByteArray(chunkSize)
                        raf.read(rawBytes)
                        val bb = ByteBuffer.wrap(rawBytes).order(ByteOrder.LITTLE_ENDIAN)
                        val samples = ShortArray(numSamples) { bb.getShort() }

                        // Process
                        val processed = process(samples, sampleRate, channels)

                        // Write output
                        val outBytes = ByteBuffer.allocate(processed.size * 2).order(ByteOrder.LITTLE_ENDIAN)
                        for (s in processed) outBytes.putShort(s)

                        outputFile.outputStream().use { fos ->
                            val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
                            val dataLen = processed.size * 2
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
                            fos.write(outBytes.array())
                        }
                        return true
                    } else {
                        raf.skipBytes(chunkSize)
                    }
                }
            }
            return false
        } catch (e: Exception) {
            e.printStackTrace()
            outputFile.delete()
            return false
        }
    }

    // --- Compressed format (m4a/mp3 etc) processing via decode → process → encode ---

    private fun processCompressed(
        inputFile: File,
        outputFile: File,
        process: (ShortArray, Int, Int) -> ShortArray
    ): Boolean {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(inputFile.absolutePath)

            var audioTrackIndex = -1
            var audioFormat: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    audioFormat = format
                    break
                }
            }
            if (audioTrackIndex == -1 || audioFormat == null) return false

            extractor.selectTrack(audioTrackIndex)
            val mime = audioFormat.getString(MediaFormat.KEY_MIME) ?: return false
            val sampleRate = audioFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val channels = audioFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)

            // Step 1: Decode entire file to PCM
            val pcmSamples = decodeToShortArray(extractor, audioFormat, mime)
            if (pcmSamples.isEmpty()) return false

            // Step 2: Process PCM
            val processed = process(pcmSamples, sampleRate, channels)

            // Step 3: Re-encode to M4A (AAC)
            return encodeToM4a(processed, sampleRate, channels, outputFile)
        } catch (e: Exception) {
            e.printStackTrace()
            outputFile.delete()
            return false
        } finally {
            try { extractor.release() } catch (_: Exception) {}
        }
    }

    private fun decodeToShortArray(extractor: MediaExtractor, format: MediaFormat, mime: String): ShortArray {
        val decoder = MediaCodec.createDecoderByType(mime)
        decoder.configure(format, null, null, 0)
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
                outputBuffer.position(bufferInfo.offset)
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
        return ShortArray(allSamples.size) { allSamples[it] }
    }

    private fun encodeToM4a(samples: ShortArray, sampleRate: Int, channels: Int, outputFile: File): Boolean {
        var encoder: MediaCodec? = null
        var muxer: MediaMuxer? = null
        try {
            val mime = MediaFormat.MIMETYPE_AUDIO_AAC
            val format = MediaFormat.createAudioFormat(mime, sampleRate, channels)
            format.setInteger(MediaFormat.KEY_BIT_RATE, 192000)
            format.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384)

            encoder = MediaCodec.createEncoderByType(mime)
            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            encoder.start()

            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            var muxerTrackIndex = -1
            var muxerStarted = false

            val bufferInfo = MediaCodec.BufferInfo()
            val pcmBytes = ByteBuffer.allocate(samples.size * 2).order(ByteOrder.LITTLE_ENDIAN)
            for (s in samples) pcmBytes.putShort(s)
            pcmBytes.flip()

            var inputDone = false
            var outputDone = false
            val bytesPerFrame = channels * 2
            var totalFramesSent = 0L

            while (!outputDone) {
                if (!inputDone) {
                    val inputIndex = encoder.dequeueInputBuffer(10000)
                    if (inputIndex >= 0) {
                        val inputBuffer = encoder.getInputBuffer(inputIndex)!!
                        val remaining = pcmBytes.remaining()
                        if (remaining > 0) {
                            val toWrite = minOf(remaining, inputBuffer.capacity())
                            // Align to frame boundary
                            val aligned = toWrite - (toWrite % bytesPerFrame)
                            if (aligned > 0) {
                                val oldLimit = pcmBytes.limit()
                                pcmBytes.limit(pcmBytes.position() + aligned)
                                inputBuffer.put(pcmBytes)
                                pcmBytes.limit(oldLimit)
                                val framesWritten = aligned / bytesPerFrame
                                val pts = totalFramesSent * 1_000_000L / sampleRate
                                encoder.queueInputBuffer(inputIndex, 0, aligned, pts, 0)
                                totalFramesSent += framesWritten
                            } else {
                                encoder.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                inputDone = true
                            }
                        } else {
                            encoder.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        }
                    }
                }

                val outputIndex = encoder.dequeueOutputBuffer(bufferInfo, 10000)
                if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    if (!muxerStarted) {
                        muxerTrackIndex = muxer.addTrack(encoder.outputFormat)
                        muxer.start()
                        muxerStarted = true
                    }
                } else if (outputIndex >= 0) {
                    val outputBuffer = encoder.getOutputBuffer(outputIndex)!!
                    if (muxerStarted && bufferInfo.size > 0) {
                        outputBuffer.position(bufferInfo.offset)
                        outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                        muxer.writeSampleData(muxerTrackIndex, outputBuffer, bufferInfo)
                    }
                    encoder.releaseOutputBuffer(outputIndex, false)
                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                        outputDone = true
                    }
                }
            }

            encoder.stop()
            encoder.release()
            encoder = null
            muxer.stop()
            muxer.release()
            muxer = null
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            try { encoder?.stop() } catch (_: Exception) {}
            try { encoder?.release() } catch (_: Exception) {}
            try { muxer?.stop() } catch (_: Exception) {}
            try { muxer?.release() } catch (_: Exception) {}
            outputFile.delete()
            return false
        }
    }

    private fun writeWavOutput(samples: ShortArray, sampleRate: Int, channels: Int, outputFile: File): Boolean {
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
}
