package com.example.audioloop

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer

object AudioTrimmer {

    /**
     * Trims a media file from [startMs] to [endMs] and saves it to [outputFile].
     * Uses MediaExtractor and MediaMuxer to copy samples without re-encoding (lossless).
     */
    suspend fun trimAudio(
        inputFile: File,
        outputFile: File,
        startMs: Long,
        endMs: Long
    ): Boolean = withContext(Dispatchers.IO) {
        val extractor = MediaExtractor()
        var muxer: MediaMuxer? = null
        
        try {
            extractor.setDataSource(inputFile.absolutePath)
            
            // Find audio track
            var trackIndex = -1
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME)
                if (mime?.startsWith("audio/") == true) {
                    trackIndex = i
                    extractor.selectTrack(trackIndex)
                    
                    // Init muxer
                    muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
                    muxer.addTrack(format)
                    muxer.start()
                    break
                }
            }
            
            if (trackIndex == -1 || muxer == null) {
                Log.e("AudioTrimmer", "No audio track found")
                return@withContext false
            }
            
            val bufferInfo = MediaCodec.BufferInfo()
            // Max typical frame size for AAC is small, but use 256KB to be safe
            val buffer = ByteBuffer.allocate(256 * 1024) 
            
            // Seek to start
            extractor.seekTo(startMs * 1000, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
            
            while (true) {
                val sampleSize = extractor.readSampleData(buffer, 0)
                if (sampleSize < 0) break
                
                val presentationTimeUs = extractor.sampleTime
                if (presentationTimeUs > endMs * 1000) break // Reached end
                
                if (presentationTimeUs >= startMs * 1000) {
                    bufferInfo.offset = 0
                    bufferInfo.size = sampleSize
                    bufferInfo.presentationTimeUs = presentationTimeUs
                    bufferInfo.flags = extractor.sampleFlags
                    muxer.writeSampleData(0, buffer, bufferInfo)
                }
                
                extractor.advance()
            }
            
            return@withContext true
        } catch (e: Exception) {
            e.printStackTrace()
            outputFile.delete() // Cleanup on fail
            return@withContext false
        } finally {
            try { extractor.release() } catch (e: Exception) {}
            try { muxer?.stop(); muxer?.release() } catch (e: Exception) {}
        }
    }
}