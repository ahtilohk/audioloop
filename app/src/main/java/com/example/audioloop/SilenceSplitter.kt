package com.example.audioloop

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer

object SilenceSplitter {

    data class Segment(val startMs: Long, val endMs: Long)

    /**
     * Detects silence boundaries in an audio file and returns non-silent segments.
     * @param silenceThreshold amplitude threshold (0-32767) below which is silence
     * @param minSilenceDurationMs minimum silence gap to split on
     * @param minSegmentDurationMs minimum segment length to keep
     */
    suspend fun detectSegments(
        inputFile: File,
        silenceThreshold: Int = 800,
        minSilenceDurationMs: Long = 400,
        minSegmentDurationMs: Long = 500
    ): List<Segment> = withContext(Dispatchers.IO) {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null

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

            if (audioTrackIndex == -1 || audioFormat == null) return@withContext emptyList()

            extractor.selectTrack(audioTrackIndex)
            val mime = audioFormat.getString(MediaFormat.KEY_MIME) ?: return@withContext emptyList()
            val sampleRate = audioFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val channels = audioFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            val durationUs = audioFormat.getLong(MediaFormat.KEY_DURATION)
            val durationMs = durationUs / 1000

            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(audioFormat, null, null, 0)
            codec.start()

            val bufferInfo = MediaCodec.BufferInfo()
            var isEos = false
            val samplesPerMs = (sampleRate * channels) / 1000.0

            // Track silence regions
            data class TimeAmplitude(val timeMs: Long, val amplitude: Int)
            val amplitudeMap = mutableListOf<TimeAmplitude>()
            val chunkMs = 20L // analyze in 20ms chunks

            var totalSamplesDecoded = 0L

            while (!isEos) {
                // Feed input
                val inputIndex = codec.dequeueInputBuffer(10000)
                if (inputIndex >= 0) {
                    val inputBuffer = codec.getInputBuffer(inputIndex) ?: continue
                    val sampleSize = extractor.readSampleData(inputBuffer, 0)
                    if (sampleSize < 0) {
                        codec.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        isEos = true
                    } else {
                        val pts = extractor.sampleTime
                        codec.queueInputBuffer(inputIndex, 0, sampleSize, pts, 0)
                        extractor.advance()
                    }
                }

                // Drain output
                var outputIndex = codec.dequeueOutputBuffer(bufferInfo, 10000)
                while (outputIndex >= 0) {
                    val outputBuffer = codec.getOutputBuffer(outputIndex) ?: break
                    val shortCount = bufferInfo.size / 2
                    if (shortCount > 0) {
                        outputBuffer.position(bufferInfo.offset)
                        val samplesPerChunk = (chunkMs * samplesPerMs).toInt().coerceAtLeast(1)

                        var i = 0
                        while (i < shortCount) {
                            var maxAmp = 0
                            val end = minOf(i + samplesPerChunk, shortCount)
                            for (j in i until end) {
                                val sample = outputBuffer.getShort(bufferInfo.offset + j * 2).toInt()
                                val abs = kotlin.math.abs(sample)
                                if (abs > maxAmp) maxAmp = abs
                            }
                            val timeMs = ((totalSamplesDecoded + i) / samplesPerMs).toLong()
                            amplitudeMap.add(TimeAmplitude(timeMs, maxAmp))
                            i = end
                        }
                        totalSamplesDecoded += shortCount
                    }
                    codec.releaseOutputBuffer(outputIndex, false)
                    outputIndex = codec.dequeueOutputBuffer(bufferInfo, 0)
                }
            }

            codec.stop()
            codec.release()
            codec = null
            extractor.release()

            // Find silence regions
            val segments = mutableListOf<Segment>()
            var segmentStart = 0L
            var silenceStart = -1L

            for (ta in amplitudeMap) {
                if (ta.amplitude < silenceThreshold) {
                    if (silenceStart < 0) silenceStart = ta.timeMs
                } else {
                    if (silenceStart >= 0) {
                        val silenceDuration = ta.timeMs - silenceStart
                        if (silenceDuration >= minSilenceDurationMs) {
                            val splitPoint = silenceStart + silenceDuration / 2
                            if (splitPoint - segmentStart >= minSegmentDurationMs) {
                                segments.add(Segment(segmentStart, splitPoint))
                            }
                            segmentStart = splitPoint
                        }
                    }
                    silenceStart = -1L
                }
            }

            // Add final segment
            if (durationMs - segmentStart >= minSegmentDurationMs) {
                segments.add(Segment(segmentStart, durationMs))
            }

            segments
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        } finally {
            try { codec?.stop() } catch (_: Exception) {}
            try { codec?.release() } catch (_: Exception) {}
            try { extractor.release() } catch (_: Exception) {}
        }
    }

    /**
     * Splits a file into segments using AudioTrimmer for each segment.
     * Returns the list of created output files.
     */
    suspend fun splitFile(
        inputFile: File,
        outputDir: File,
        segments: List<Segment>,
        baseName: String
    ): List<File> = withContext(Dispatchers.IO) {
        val results = mutableListOf<File>()
        val ext = inputFile.extension

        segments.forEachIndexed { index, segment ->
            val outputFile = File(outputDir, "${baseName}_${index + 1}.$ext")
            val success = if (ext.equals("wav", ignoreCase = true)) {
                WavAudioTrimmer.trimAudio(inputFile, outputFile, segment.startMs, segment.endMs)
            } else {
                AudioTrimmer.trimAudio(inputFile, outputFile, segment.startMs, segment.endMs)
            }
            if (success) results.add(outputFile)
        }

        results
    }
}
