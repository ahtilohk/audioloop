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
                raf.seek(22)
                val channels = raf.readShort().toInt().and(0xFFFF)
                raf.seek(24)
                val sampleRate = Integer.reverseBytes(raf.readInt())
                raf.seek(34)
                val bitsPerSample = raf.readShort().toInt().and(0xFFFF)

                if (bitsPerSample != 16) return false // Only 16-bit supported

                // Find data chunk
                raf.seek(12)
                val chunkHeader = ByteArray(4)
                while (raf.read(chunkHeader) != -1) {
                    val chunkId = String(chunkHeader)
                    val chunkSize = Integer.reverseBytes(raf.readInt())

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

            // Step 3: Re-encode to AAC and mux
            return encodeAndMux(processed, sampleRate, channels, outputFile)
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

    private fun encodeAndMux(
        samples: ShortArray,
        sampleRate: Int,
        channels: Int,
        outputFile: File
    ): Boolean {
        val totalFrames = samples.size / channels
        val durationUs = totalFrames.toLong() * 1_000_000L / sampleRate

        val encoderFormat = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, channels)
        encoderFormat.setInteger(MediaFormat.KEY_AAC_PROFILE, android.media.MediaCodecInfo.CodecProfileLevel.AACObjectLC)
        encoderFormat.setInteger(MediaFormat.KEY_BIT_RATE, 128000)
        encoderFormat.setLong(MediaFormat.KEY_DURATION, durationUs)
        encoderFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384)

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
        val frameSizeBytes = 1024 * channels * 2 // AAC frame = 1024 samples per channel
        val bytesPerSample = channels * 2
        var presentationTimeUs = 0L

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
                                val samplesInChunk = aligned / bytesPerSample
                                presentationTimeUs = (totalBytesRead.toLong() / bytesPerSample) * 1_000_000L / sampleRate
                                encoder.queueInputBuffer(inputIndex, 0, aligned, presentationTimeUs, 0)
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
                            // Skip codec config
                        } else if (muxerStarted && bufferInfo.size > 0) {
                            outputBuffer.position(bufferInfo.offset)
                            outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                            muxer.writeSampleData(muxerTrackIndex, outputBuffer, bufferInfo)
                        }
                        encoder.releaseOutputBuffer(outputIndex, false)
                        if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                            return@encodeAndMux true
                        }
                    }
                }

                if (inputDone && outputIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                    // Keep draining encoder
                    continue
                }
            }
        } finally {
            try { encoder.stop() } catch (_: Exception) {}
            try { encoder.release() } catch (_: Exception) {}
            try { if (muxerStarted) muxer.stop() } catch (_: Exception) {}
            try { muxer.release() } catch (_: Exception) {}
        }
    }
}
