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
                // Read WAV header fields in little-endian
                val headerBytes = ByteArray(36)
                raf.read(headerBytes)
                val hdr = ByteBuffer.wrap(headerBytes).order(ByteOrder.LITTLE_ENDIAN)
                hdr.position(22)
                val channels = hdr.getShort().toInt().and(0xFFFF)
                val sampleRate = hdr.getInt() // offset 24
                hdr.position(34)
                val bitsPerSample = hdr.getShort().toInt().and(0xFFFF)

                // Find 'data' chunk
                raf.seek(12) // Skip RIFF header
                val chunkHeader = ByteArray(4)
                val sizeBuf = ByteArray(4)
                while (raf.read(chunkHeader) != -1) {
                    val chunkId = String(chunkHeader)
                    raf.read(sizeBuf)
                    val chunkSize = ByteBuffer.wrap(sizeBuf).order(ByteOrder.LITTLE_ENDIAN).getInt()
                    
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

    suspend fun removeSegmentWav(inputFile: File, outputFile: File, startMs: Long, endMs: Long): Boolean = withContext(Dispatchers.IO) {
        try {
            val rafVal = RandomAccessFile(inputFile, "r")
            rafVal.use { raf ->
                // Read WAV header fields in little-endian
                val headerBytes = ByteArray(36)
                raf.read(headerBytes)
                val hdr = ByteBuffer.wrap(headerBytes).order(ByteOrder.LITTLE_ENDIAN)
                hdr.position(22)
                val channels = hdr.getShort().toInt().and(0xFFFF)
                val sampleRate = hdr.getInt() // offset 24
                hdr.position(34)
                val bitsPerSample = hdr.getShort().toInt().and(0xFFFF)

                raf.seek(12)
                val chunkHeader = ByteArray(4)
                val sizeBuf = ByteArray(4)
                while (raf.read(chunkHeader) != -1) {
                    val chunkId = String(chunkHeader)
                    raf.read(sizeBuf)
                    val chunkSize = ByteBuffer.wrap(sizeBuf).order(ByteOrder.LITTLE_ENDIAN).getInt()

                    if (chunkId == "data") {
                        val dataStartPos = raf.filePointer
                        val bytesPerSecond = sampleRate * channels * (bitsPerSample / 8)
                        val blockAlign = channels * (bitsPerSample / 8)

                        val startByte = (startMs / 1000.0 * bytesPerSecond).toLong()
                        val endByte = (endMs / 1000.0 * bytesPerSecond).toLong()
                        val startByteAligned = (startByte - (startByte % blockAlign)).coerceIn(0, chunkSize.toLong())
                        val endByteAligned = (endByte - (endByte % blockAlign)).coerceIn(0, chunkSize.toLong())

                        if (endByteAligned <= startByteAligned) {
                            return@withContext false
                        }

                        val keepBytes = startByteAligned + (chunkSize.toLong() - endByteAligned)
                        if (keepBytes <= 0) return@withContext false

                        outputFile.outputStream().use { fos ->
                            val header = ByteBuffer.allocate(44)
                            header.order(ByteOrder.LITTLE_ENDIAN)

                            val totalDataLen = keepBytes
                            val totalFileLen = totalDataLen + 36

                            header.put("RIFF".toByteArray())
                            header.putInt(totalFileLen.toInt())
                            header.put("WAVE".toByteArray())
                            header.put("fmt ".toByteArray())
                            header.putInt(16)
                            header.putShort(1)
                            header.putShort(channels.toShort())
                            header.putInt(sampleRate)
                            header.putInt(sampleRate * channels * (bitsPerSample / 8))
                            header.putShort(blockAlign.toShort())
                            header.putShort(bitsPerSample.toShort())
                            header.put("data".toByteArray())
                            header.putInt(totalDataLen.toInt())

                            fos.write(header.array())

                            val buffer = ByteArray(4096)
                            var totalWritten = 0L

                            raf.seek(dataStartPos)
                            while (totalWritten < startByteAligned) {
                                val toRead = minOf(buffer.size.toLong(), startByteAligned - totalWritten).toInt()
                                val bytesRead = raf.read(buffer, 0, toRead)
                                if (bytesRead == -1) break
                                fos.write(buffer, 0, bytesRead)
                                totalWritten += bytesRead
                            }

                            raf.seek(dataStartPos + endByteAligned)
                            var secondWritten = 0L
                            val secondLength = chunkSize.toLong() - endByteAligned
                            while (secondWritten < secondLength) {
                                val toRead = minOf(buffer.size.toLong(), secondLength - secondWritten).toInt()
                                val bytesRead = raf.read(buffer, 0, toRead)
                                if (bytesRead == -1) break
                                fos.write(buffer, 0, bytesRead)
                                secondWritten += bytesRead
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
