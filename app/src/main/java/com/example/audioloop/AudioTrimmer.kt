package com.example.audioloop

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

object AudioTrimmer {

    fun trimAudio(inputFile: File, outputFile: File, startMs: Long, endMs: Long): Boolean {
        // Kontrollime faili tüüpi
        return if (inputFile.name.endsWith(".wav", ignoreCase = true)) {
            trimWavFile(inputFile, outputFile, startMs, endMs)
        } else {
            trimMp4File(inputFile, outputFile, startMs, endMs)
        }
    }

    // --- 1. WAV FAILIDE LÕIKAMINE (Voogsalvestus) ---
    private fun trimWavFile(inputFile: File, outputFile: File, startMs: Long, endMs: Long): Boolean {
        try {
            val fileLength = inputFile.length()
            // WAV päis on tavaliselt 44 baiti
            if (fileLength < 44) return false

            val raf = RandomAccessFile(inputFile, "r")
            raf.seek(24) // Sample rate asukoht
            val sampleRate = raf.readInt().reverseBytes() // Little Endian lugemine
            val byteRate = raf.readInt().reverseBytes()
            val blockAlign = raf.readShort().reverseBytes()
            val bitsPerSample = raf.readShort().reverseBytes()
            raf.close()

            // Arvutame, mitu baiti tuleb vahele jätta ja mitu kopeerida
            // Valem: aeg * byteRate / 1000
            val startByte = 44 + (startMs * byteRate / 1000)
            val endByte = 44 + (endMs * byteRate / 1000)
            val lengthToCopy = endByte - startByte

            if (lengthToCopy <= 0) return false

            val inputStream = FileInputStream(inputFile)
            val outputStream = FileOutputStream(outputFile)

            // 1. Kirjutame esialgu tühja päise (44 baiti)
            outputStream.write(ByteArray(44))

            // 2. Hüppame õigesse kohta originaalis
            inputStream.skip(startByte)

            // 3. Kopeerime andmed
            val buffer = ByteArray(1024 * 4)
            var bytesCopied: Long = 0
            while (bytesCopied < lengthToCopy) {
                val remaining = lengthToCopy - bytesCopied
                val sizeToRead = if (remaining < buffer.size) remaining.toInt() else buffer.size
                val read = inputStream.read(buffer, 0, sizeToRead)
                if (read == -1) break
                outputStream.write(buffer, 0, read)
                bytesCopied += read
            }

            inputStream.close()
            outputStream.close()

            // 4. Uuendame WAV päist uue faili pikkusega
            updateWavHeader(outputFile, sampleRate, 2, 16) // Eeldame stereo 16-bit (InternalAudioRecorder standard)

            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    // --- 2. MP4/M4A FAILIDE LÕIKAMINE (Mikrofon) ---
    private fun trimMp4File(inputFile: File, outputFile: File, startMs: Long, endMs: Long): Boolean {
        try {
            val extractor = MediaExtractor()
            extractor.setDataSource(inputFile.absolutePath)

            val trackIndex = selectAudioTrack(extractor)
            if (trackIndex < 0) return false

            extractor.selectTrack(trackIndex)
            val format = extractor.getTrackFormat(trackIndex)

            val muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val muxerTrackIndex = muxer.addTrack(format)
            muxer.start()

            extractor.seekTo(startMs * 1000, MediaExtractor.SEEK_TO_CLOSEST_SYNC)

            val bufferSize = if (format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
                format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE)
            } else {
                1024 * 1024
            }

            val buffer = ByteBuffer.allocate(bufferSize)
            val bufferInfo = MediaCodec.BufferInfo()

            while (true) {
                val sampleSize = extractor.readSampleData(buffer, 0)
                if (sampleSize < 0) break

                val presentationTimeUs = extractor.sampleTime
                if (presentationTimeUs > endMs * 1000) break

                bufferInfo.offset = 0
                bufferInfo.size = sampleSize
                bufferInfo.flags = extractor.sampleFlags
                bufferInfo.presentationTimeUs = presentationTimeUs

                muxer.writeSampleData(muxerTrackIndex, buffer, bufferInfo)
                extractor.advance()
            }

            muxer.stop()
            muxer.release()
            extractor.release()
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    private fun selectAudioTrack(extractor: MediaExtractor): Int {
        for (i in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(i)
            val mime = format.getString(MediaFormat.KEY_MIME)
            if (mime?.startsWith("audio/") == true) {
                return i
            }
        }
        return -1
    }

    // Abifunktsioonid bititöötluseks
    private fun Int.reverseBytes(): Int = java.lang.Integer.reverseBytes(this)
    private fun Short.reverseBytes(): Short = java.lang.Short.reverseBytes(this)

    private fun updateWavHeader(file: File, sampleRate: Int, channels: Int, bitDepth: Int) {
        try {
            val fileSize = file.length()
            val totalDataLen = fileSize - 8
            val totalAudioLen = fileSize - 44
            val byteRate = (sampleRate * channels * bitDepth) / 8

            val randomAccessFile = RandomAccessFile(file, "rw")
            randomAccessFile.seek(0)

            val header = ByteArray(44)
            val buffer = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)

            buffer.put("RIFF".toByteArray())
            buffer.putInt(totalDataLen.toInt())
            buffer.put("WAVE".toByteArray())
            buffer.put("fmt ".toByteArray())
            buffer.putInt(16)
            buffer.putShort(1.toShort()) // PCM
            buffer.putShort(channels.toShort())
            buffer.putInt(sampleRate)
            buffer.putInt(byteRate)
            buffer.putShort(((channels * bitDepth) / 8).toShort()) // BlockAlign
            buffer.putShort(bitDepth.toShort())
            buffer.put("data".toByteArray())
            buffer.putInt(totalAudioLen.toInt())

            randomAccessFile.write(header)
            randomAccessFile.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}