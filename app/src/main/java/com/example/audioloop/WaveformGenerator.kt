package com.example.audioloop

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.util.Log
import java.io.File
import java.nio.ByteBuffer
import kotlin.math.abs
import kotlin.math.sqrt

object WaveformGenerator {

    private const val TAG = "WaveformGenerator"

    /**
     * Extracts amplitude data from an audio file by decoding it.
     * @param file The audio file (AAC/M4A, WAV).
     * @param numBars The number of data points to return.
     * @return A list of amplitudes (scaled 0-100) or an empty list on failure.
     */
    fun extractWaveform(file: File, numBars: Int = 60): List<Int> {
        // 1. Check for pre-calculated .wave file
        val waveFile = File(file.parent, "${file.name}.wave")
        if (waveFile.exists()) {
             try {
                 val content = waveFile.readText()
                 if (content.isNotEmpty()) {
                     val list = content.split(",").mapNotNull { it.trim().toIntOrNull() }
                     if (list.isNotEmpty()) return list
                 }
             } catch (e: Exception) { e.printStackTrace() }
        }

        if (!file.exists() || file.length() < 100) return emptyList()

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
                return emptyList()
            }

            // Calculate skip factor: Target processing < 1 second.
            // approx 40 frames per second of audio.
            // 4 min = 240s = 10000 frames. skip 10 -> 1000 frames -> acceptable.
            val safeSkip = (durationUs / 1000000 / 10).toInt().coerceIn(0, 100)

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
                                // Skip frames optimization
                                var skipped = 0
                                while (skipped < safeSkip && extractor.advance()) {
                                    skipped++
                                }
                                if (skipped == 0) extractor.advance() // Always advance at least once if skip is 0, wait, logic below logic below handles advance
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
            return if (rawAmplitudes.isNotEmpty()) {
                downsample(rawAmplitudes, numBars)
            } else {
                List(numBars) { 10 } // Vaikus
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error generating waveform", e)
            return List(numBars) { 10 } // Fallback vaikus
        } finally {
            try {
                codec?.stop()
                codec?.release()
                extractor.release()
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    private fun processOutputBuffer(buffer: ByteBuffer, info: MediaCodec.BufferInfo, list: ArrayList<Int>) {
        // PCM 16-bit on 2 baiti proovi kohta (Little Endian)
        val shortBuffer = buffer.asShortBuffer()
        
        // Loeme bufferist (limit on piiratud info.size-ga)
        val limit = info.size / 2
        
        var sum = 0.0
        var count = 0
        
        // Optimeerimine: ei loe iga proovi, vaid võtame sammuga, et säästa mälu/aega
        // Kui fail on suur, siis piisab vähemast
        val step = 100 // Võtame iga 100-nda sample-i (umbes 440 samplet sekundis @ 44.1kHz)
        
        for (i in 0 until limit step step) {
            var sample = shortBuffer.get(i).toInt()
            
            // NOISE GATE: Kui signaal on väga nõrk (taustamüra), loeme selle nulliks (vaikus)
            // See teeb joone sirgeks nagu "Voog" salvestuse puhul
            if (kotlin.math.abs(sample) < 150) {
                sample = 0
            }
            
            sum += kotlin.math.abs(sample)
            count++
        }
        
        if (count > 0) {
            list.add((sum / count).toInt())
        }
    }

    private fun downsample(data: List<Int>, targetSize: Int): List<Int> {
        if (data.isEmpty()) return List(targetSize) { 0 }
        if (targetSize >= data.size) return data // Või padding?

        val result = ArrayList<Int>(targetSize)
        val chunkSize = data.size.toDouble() / targetSize

        for (i in 0 until targetSize) {
            val start = (i * chunkSize).toInt()
            val end = ((i + 1) * chunkSize).toInt().coerceAtMost(data.size)
            
            var sum = 0L
            var count = 0
            for (j in start until end) {
                sum += data[j]
                count++
            }
            
            val avg = if (count > 0) sum / count else 0L
            
            // Normaalime 0-100 (tavaliselt max PCM on ~32767)
            // Kasutame ruutjuurt dünaamika parandamiseks või logaritmi?
            // Lihtne lineaarne: 32768 -> 100
            // Aga vaikne kõne on madal. Võimendame veidi.
            
            // Boost factor 3.0
            val normalized = (avg / 32768.0 * 100 * 3.0).toInt().coerceIn(5, 100)
            result.add(normalized)
        }
        return result
    }
    fun generateFromPcm(pcmFile: File): List<Int> {
        if (!pcmFile.exists() || pcmFile.length() == 0L) return emptyList()
        
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
            e.printStackTrace()
        } finally {
            stream.close()
        }
        
        return waveform
    }
}
