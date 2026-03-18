package ee.ahtilohk.audioloop

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import kotlin.math.abs
import kotlin.math.sqrt

object WaveformGenerator {

    private const val TAG = "WaveformGenerator"

    /**
     * Load a previously cached waveform from the disk cache directory.
     * Returns null if no cache exists.
     */
    fun loadCachedWaveform(filesDir: File, audioFile: File): List<Int>? {
        return try {
            val waveFile = File(File(filesDir, ".waveforms"), "${audioFile.name}.wave")
            if (!waveFile.exists()) return null
            val content = waveFile.readText()
            if (content.isBlank()) return null
            content.split(",").map { it.toInt() }
        } catch (_: Exception) { null }
    }

    /**
     * Progressive waveform extraction that emits partial results as the file is decoded.
     * Bars are placed at their correct time positions — decoded regions show real data,
     * not-yet-decoded regions show a baseline placeholder, so the waveform fills in
     * uniformly across its full width rather than growing from one side.
     */
    fun extractWaveformProgressive(file: File, numBars: Int = 60): Flow<List<Int>> = flow {
        if (!file.exists() || file.length() < 100) {
            emit(emptyList())
            return@flow
        }

        val extractor = MediaExtractor()
        var codec: MediaCodec? = null

        try {
            extractor.setDataSource(file.absolutePath)
            var audioTrackIndex = -1
            var durationUs = 0L

            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    if (format.containsKey(MediaFormat.KEY_DURATION)) {
                        durationUs = format.getLong(MediaFormat.KEY_DURATION)
                    }
                    extractor.selectTrack(audioTrackIndex)
                    codec = MediaCodec.createDecoderByType(mime)
                    codec.configure(format, null, null, 0)
                    codec.start()
                    break
                }
            }

            if (audioTrackIndex == -1 || codec == null) {
                emit(emptyList())
                return@flow
            }

            val bufferInfo = MediaCodec.BufferInfo()
            var isEOS = false
            val rawAmplitudes = ArrayList<Int>()
            val timeoutUs = 5000L

            // Estimate total raw amplitude points for the entire file
            val estimatedTotal = if (durationUs > 0) {
                (durationUs / 1_000_000.0 * 22).toLong().coerceAtLeast(100)
            } else 10000L

            val isLongFile = durationUs > 30_000_000L // > 30 seconds
            val emitInterval = if (isLongFile) {
                (estimatedTotal / 20).coerceIn(200, 5000).toInt()
            } else {
                Int.MAX_VALUE
            }
            var lastEmitSize = 0

            while (true) {
                if (!isEOS) {
                    val inputIndex = codec.dequeueInputBuffer(timeoutUs)
                    if (inputIndex >= 0) {
                        val inputBuffer = codec.getInputBuffer(inputIndex)
                        if (inputBuffer != null) {
                            val sampleSize = extractor.readSampleData(inputBuffer, 0)
                            if (sampleSize < 0) {
                                codec.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                isEOS = true
                            } else {
                                val time = extractor.sampleTime
                                codec.queueInputBuffer(inputIndex, 0, sampleSize, time, 0)
                                extractor.advance()
                            }
                        }
                    }
                }

                val outputIndex = codec.dequeueOutputBuffer(bufferInfo, timeoutUs)
                if (outputIndex >= 0) {
                    val outputBuffer = codec.getOutputBuffer(outputIndex)
                    if (outputBuffer != null && bufferInfo.size > 0) {
                        processOutputBuffer(outputBuffer, bufferInfo, rawAmplitudes)
                    }
                    codec.releaseOutputBuffer(outputIndex, false)

                    // Emit partial result with correct bar positioning
                    if (rawAmplitudes.size - lastEmitSize >= emitInterval) {
                        emit(downsamplePositioned(rawAmplitudes, numBars, estimatedTotal.toInt()))
                        lastEmitSize = rawAmplitudes.size
                    }

                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        break
                    }
                } else if (outputIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                    if (isEOS) break
                }
            }

            // Emit final complete result (use full downsample, all data is available)
            if (rawAmplitudes.isNotEmpty()) {
                emit(downsample(rawAmplitudes, numBars))
            } else {
                emit(List(numBars) { 10 })
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error generating waveform", e)
            emit(List(numBars) { 10 })
        } finally {
            try {
                codec?.stop()
                codec?.release()
                extractor.release()
            } catch (e: Exception) { AppLog.e("Error releasing media resources in WaveformGenerator", e) }
        }
    }.flowOn(Dispatchers.IO)

    suspend fun extractWaveform(file: File, numBars: Int = 60): List<Int> = withContext(Dispatchers.IO) {
        if (!file.exists() || file.length() < 100) return@withContext emptyList()

        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        val amplitudes = ArrayList<Int>()

        try {
            extractor.setDataSource(file.absolutePath)
            var audioTrackIndex = -1
            var sampleRate = 44100
            var durationUs = 0L

            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                        sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    }
                    if (format.containsKey(MediaFormat.KEY_DURATION)) {
                        durationUs = format.getLong(MediaFormat.KEY_DURATION)
                    }
                    extractor.selectTrack(audioTrackIndex)
                    codec = MediaCodec.createDecoderByType(mime)
                    codec.configure(format, null, null, 0)
                    codec.start()
                    break
                }
            }

            if (audioTrackIndex == -1 || codec == null) {
                Log.e(TAG, "No audio track found in $file")
                return@withContext emptyList()
            }



            val bufferInfo = MediaCodec.BufferInfo()
            var isEOS = false
            
            // Kogume kõik amplituudid siia, et pärast keskmistada
            val rawAmplitudes = ArrayList<Int>()
            
            // Timeoutid
            val timeoutUs = 5000L

            while (true) {
                if (!isEOS) {
                    val inputIndex = codec.dequeueInputBuffer(timeoutUs)
                    if (inputIndex >= 0) {
                        val inputBuffer = codec.getInputBuffer(inputIndex)
                        if (inputBuffer != null) {
                            val sampleSize = extractor.readSampleData(inputBuffer, 0)
                            if (sampleSize < 0) {
                                codec.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                isEOS = true
                            } else {
                                val time = extractor.sampleTime
                                codec.queueInputBuffer(inputIndex, 0, sampleSize, time, 0)
                                // Always advance to next sample
                                extractor.advance()
                            }
                        }
                    }
                }

                val outputIndex = codec.dequeueOutputBuffer(bufferInfo, timeoutUs)
                if (outputIndex >= 0) {
                    val outputBuffer = codec.getOutputBuffer(outputIndex)
                    // PCM 16-bit andmed on siin
                    if (outputBuffer != null && bufferInfo.size > 0) {
                        processOutputBuffer(outputBuffer, bufferInfo, rawAmplitudes)
                    }
                    codec.releaseOutputBuffer(outputIndex, false)

                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        break
                    }
                } else if (outputIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                    if (isEOS) break // Kui sisend on läbi ja väljundit ei tule, siis aitab
                }
            }

            // Downsample rawAmplitudes to numBars
            if (rawAmplitudes.isNotEmpty()) {
                downsample(rawAmplitudes, numBars)
            } else {
                List(numBars) { 10 } // Vaikus
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error generating waveform", e)
            List(numBars) { 10 } // Fallback vaikus
        } finally {
            try {
                codec?.stop()
                codec?.release()
                extractor.release()
            } catch (e: Exception) { AppLog.e("Error releasing media resources in WaveformGenerator", e) }
        }
    }

    private fun processOutputBuffer(buffer: ByteBuffer, info: MediaCodec.BufferInfo, list: ArrayList<Int>) {
        // PCM 16-bit: 2 bytes per sample (Little Endian)
        val limit = info.size / 2
        var i = 0
        val windowSize = 2000
        val step = 4 // Sample every 4th sample within the window for speed
        
        while (i < limit) {
            var peak = 0
            val windowEnd = minOf(i + windowSize, limit)
            for (j in i until windowEnd step step) {
                // Respect buffer offset
                val sampleIndex = info.offset + j * 2
                if (sampleIndex + 1 < buffer.capacity()) {
                    val amp = Math.abs(buffer.getShort(sampleIndex).toInt())
                    if (amp > peak) peak = amp
                }
            }
            list.add(peak)
            i = windowEnd
        }
    }

    /**
     * Position-aware downsample for progressive rendering.
     * Places decoded data at the correct bar positions based on how far through
     * the file we are. Bars beyond the decoded region get a baseline placeholder (5).
     */
    private fun downsamplePositioned(data: List<Int>, targetSize: Int, estimatedTotal: Int): List<Int> {
        if (data.isEmpty()) return List(targetSize) { 5 }
        val decodedFraction = (data.size.toDouble() / estimatedTotal).coerceIn(0.0, 1.0)
        val decodedBars = (decodedFraction * targetSize).toInt().coerceIn(1, targetSize)
        // Downsample only the decoded portion to fill the correct number of bars
        val filled = downsample(data, decodedBars)
        if (decodedBars >= targetSize) return filled
        // Pad remaining bars with baseline placeholder
        return filled + List(targetSize - decodedBars) { 5 }
    }

    private fun downsample(data: List<Int>, targetSize: Int): List<Int> {
        if (data.isEmpty()) return List(targetSize) { 0 }
        
        // If we have fewer data points than requested, interpolate
        if (targetSize > data.size && data.size > 1) {
            val result = ArrayList<Int>(targetSize)
            val step = (data.size - 1).toDouble() / (targetSize - 1)
            for (i in 0 until targetSize) {
                val pos = i * step
                val low = pos.toInt()
                val high = (low + 1).coerceAtMost(data.size - 1)
                val weight = pos - low
                val value = (data[low] * (1 - weight) + data[high] * weight).toInt()
                result.add(value)
            }
            // Normalize
            val maxVal = result.maxOrNull()?.coerceAtLeast(1) ?: 1
            return result.map { (it.toDouble() / maxVal * 100).toInt().coerceIn(5, 100) }
        }
        
        if (targetSize == data.size) return data
        if (targetSize > data.size) return data // Fallback for single point

        val result = ArrayList<Int>(targetSize)
        val chunkSize = data.size.toDouble() / targetSize

        val rawValues = ArrayList<Long>(targetSize)
        for (i in 0 until targetSize) {
            val start = (i * chunkSize).toInt()
            val end = ((i + 1) * chunkSize).toInt().coerceAtMost(data.size)

            var maxInChunk = 0L
            for (j in start until end) {
                if (data[j] > maxInChunk) maxInChunk = data[j].toLong()
            }
            rawValues.add(maxInChunk)
        }

        // Normaliseerime suhteliselt max väärtuse suhtes
        // Nii säilib dünaamika alati, sõltumata absoluutsest amplituudist
        val maxVal = rawValues.maxOrNull()?.coerceAtLeast(1L) ?: 1L

        for (v in rawValues) {
            val normalized = (v.toDouble() / maxVal * 100).toInt().coerceIn(5, 100)
            result.add(normalized)
        }
        return result
    }
    suspend fun generateFromPcm(pcmFile: File): List<Int> = withContext(Dispatchers.IO) {
        if (!pcmFile.exists() || pcmFile.length() == 0L) return@withContext emptyList()
        
        val waveform = ArrayList<Int>()
        val totalBytes = pcmFile.length()
        // 16-bit sample = 2 bytes.
        // Total samples = bytes / 2
        val totalSamples = totalBytes / 2
        val targetPoints = 100
        val samplesPerPoint = (totalSamples / targetPoints).toInt().coerceAtLeast(1)
        
        var maxAmp = 0
        var sampleCount = 0
        
        // Use a buffered input stream for speed
        val stream = java.io.BufferedInputStream(java.io.FileInputStream(pcmFile))
        val buffer = ByteArray(4096)
        
        try {
            while (true) {
                val read = stream.read(buffer)
                if (read == -1) break
                
                for (i in 0 until read step 2) {
                    if (i + 1 < read) {
                        val low = buffer[i].toInt() and 0xFF
                        val high = buffer[i+1].toInt() shl 8
                        val sample = (high or low).toShort()
                        val absSample = kotlin.math.abs(sample.toInt())
                        
                        if (absSample > maxAmp) {
                            maxAmp = absSample
                        }
                        sampleCount++
                        
                        if (sampleCount >= samplesPerPoint) {
                            waveform.add(maxAmp)
                            maxAmp = 0
                            sampleCount = 0
                        }
                    }
                }
            }
            // Add last point
            if (sampleCount > 0) {
                waveform.add(maxAmp)
            }
        } catch (e: Exception) {
            AppLog.e("Error generating waveform from PCM", e)
        } finally {
            stream.close()
        }
        
        return@withContext waveform
    }
}
