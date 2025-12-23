package com.example.audioloop

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

object WavAudioTrimmer {

    suspend fun trimWav(inputFile: File, outputFile: File, startMs: Long, endMs: Long): Boolean = withContext(Dispatchers.IO) {
        try {
            val rafVal = RandomAccessFile(inputFile, "r")
            rafVal.use { raf ->
                // Basic WAV Header parsing
                raf.seek(22) // Num Channels
                val channels = raf.readShort().toInt().and(0xFFFF) // Unsigned short
                
                raf.seek(24) // Sample Rate
                val sampleRate = raf.readInt().let { Integer.reverseBytes(it) } // Little endian
                
                raf.seek(34) // Bits Per Sample
                val bitsPerSample = raf.readShort().toInt().and(0xFFFF)
                
                // Find 'data' chunk
                raf.seek(12) // Skip RIFF header
                // Search for 'data'
                var chunkHeader = ByteArray(4)
                while (raf.read(chunkHeader) != -1) {
                    val chunkId = String(chunkHeader)
                    val chunkSize = raf.readInt().let { Integer.reverseBytes(it) }
                    
                    if (chunkId == "data") {
                        val dataStartPos = raf.filePointer
                        
                        // Calculate byte offsets
                        val bytesPerSecond = sampleRate * channels * (bitsPerSample / 8)
                        val startByte = (startMs / 1000.0 * bytesPerSecond).toLong()
                        // Align to block align
                        val blockAlign = channels * (bitsPerSample / 8)
                        val startByteAligned = startByte - (startByte % blockAlign)
                        
                        val endByte = (endMs / 1000.0 * bytesPerSecond).toLong()
                        val lengthBytes = endByte - startByteAligned
                        val safeLengthBytes = lengthBytes.coerceAtMost(chunkSize.toLong() - startByteAligned) // Don't read past chunk
                        
                        if (safeLengthBytes <= 0) return@withContext false

                        // Write new WAV file
                        outputFile.outputStream().use { fos ->
                            // 1. Write Header
                            // We construct a new header
                            val header = ByteBuffer.allocate(44)
                            header.order(ByteOrder.LITTLE_ENDIAN)
                            
                            val totalDataLen = safeLengthBytes
                            val totalFileLen = totalDataLen + 36
                            
                            header.put("RIFF".toByteArray())
                            header.putInt(totalFileLen.toInt())
                            header.put("WAVE".toByteArray())
                            header.put("fmt ".toByteArray())
                            header.putInt(16) // Subchunk1Size (16 for PCM)
                            header.putShort(1) // AudioFormat (1 for PCM)
                            header.putShort(channels.toShort())
                            header.putInt(sampleRate)
                            header.putInt(sampleRate * channels * (bitsPerSample / 8)) // ByteRate
                            header.putShort((channels * (bitsPerSample / 8)).toShort()) // BlockAlign
                            header.putShort(bitsPerSample.toShort())
                            header.put("data".toByteArray())
                            header.putInt(totalDataLen.toInt())
                            
                            fos.write(header.array())
                            
                            // 2. Copy Data
                            raf.seek(dataStartPos + startByteAligned)
                            val buffer = ByteArray(4096)
                            var bytesRead: Int
                            var totalWritten = 0L
                            
                            while (totalWritten < safeLengthBytes) {
                                val toRead = minOf(buffer.size.toLong(), safeLengthBytes - totalWritten).toInt()
                                bytesRead = raf.read(buffer, 0, toRead)
                                if (bytesRead == -1) break
                                fos.write(buffer, 0, bytesRead)
                                totalWritten += bytesRead
                            }
                        }
                        return@withContext true
                    } else {
                        raf.skipBytes(chunkSize)
                    }
                }
            }
            return@withContext false
        } catch (e: Exception) {
            e.printStackTrace()
            outputFile.delete()
            return@withContext false
        }
    }
}
