package com.example.audioloop.data

import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

object AudioMetadataHelper {

    suspend fun getDuration(file: File): Pair<String, Long> = withContext(Dispatchers.IO) {
        var millis = 0L

        if (file.extension.equals("wav", ignoreCase = true)) {
            try {
                java.io.RandomAccessFile(file, "r").use { raf ->
                    val headerBytes = ByteArray(36)
                    raf.read(headerBytes)
                    val hdr = java.nio.ByteBuffer.wrap(headerBytes).order(java.nio.ByteOrder.LITTLE_ENDIAN)
                    hdr.position(22)
                    val channels = hdr.getShort().toInt().and(0xFFFF)
                    val sampleRate = hdr.getInt()
                    hdr.position(34)
                    val bitsPerSample = hdr.getShort().toInt().and(0xFFFF)
                    if (sampleRate > 0 && channels > 0 && bitsPerSample > 0) {
                        val bytesPerSecond = sampleRate.toLong() * channels * (bitsPerSample / 8)
                        if (bytesPerSecond > 0) {
                            raf.seek(12)
                            val chunkHeader = ByteArray(4)
                            val sizeBuf = ByteArray(4)
                            while (raf.read(chunkHeader) == 4) {
                                val chunkId = String(chunkHeader)
                                raf.read(sizeBuf)
                                val chunkSize = java.nio.ByteBuffer.wrap(sizeBuf).order(java.nio.ByteOrder.LITTLE_ENDIAN).getInt()
                                if (chunkId == "data") {
                                    millis = (chunkSize.toLong().and(0xFFFFFFFFL) * 1000L) / bytesPerSecond
                                    break
                                } else {
                                    raf.skipBytes(chunkSize)
                                }
                            }
                        }
                    }
                }
                if (millis > 0) return@withContext Pair(formatTime(millis), millis)
            } catch (_: Exception) {}
        }

        val mmr = MediaMetadataRetriever()
        try {
            mmr.setDataSource(file.absolutePath)
            millis = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        } catch (_: Exception) {}
        finally { try { mmr.release() } catch (_: Exception) {} }

        if (millis == 0L) {
            var mp: MediaPlayer? = null
            try {
                mp = MediaPlayer()
                mp.setDataSource(file.absolutePath)
                mp.prepare()
                millis = mp.duration.toLong()
            } catch (_: Exception) {}
            finally { try { mp?.release() } catch (_: Exception) {} }
        }

        if (millis == 0L) {
            var extractor: MediaExtractor? = null
            try {
                extractor = MediaExtractor()
                extractor.setDataSource(file.absolutePath)
                for (i in 0 until extractor.trackCount) {
                    val format = extractor.getTrackFormat(i)
                    val mime = format.getString(MediaFormat.KEY_MIME)
                    if (mime?.startsWith("audio/") == true && format.containsKey(MediaFormat.KEY_DURATION)) {
                        millis = format.getLong(MediaFormat.KEY_DURATION) / 1000
                        break
                    }
                }
            } catch (_: Exception) {}
            finally { try { extractor?.release() } catch (_: Exception) {} }
        }

        if (millis == 0L) Pair("00:00", 0L)
        else Pair(formatTime(millis), millis)
    }

    fun formatTime(millis: Long): String {
        val minutes = TimeUnit.MILLISECONDS.toMinutes(millis)
        val seconds = TimeUnit.MILLISECONDS.toSeconds(millis) % 60
        return String.format(java.util.Locale.US, "%02d:%02d", minutes, seconds)
    }
}
