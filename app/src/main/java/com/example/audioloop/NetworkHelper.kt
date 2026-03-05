package com.example.audioloop

import kotlinx.coroutines.delay
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import okio.BufferedSink
import okio.source
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

object NetworkHelper {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()

    suspend fun <T> executeWithRetry(
        times: Int = 3,
        initialDelay: Long = 1000,
        maxDelay: Long = 10000,
        factor: Double = 2.0,
        block: suspend () -> T
    ): T {
        var currentDelay = initialDelay
        repeat(times - 1) { attempt ->
            try {
                return block()
            } catch (e: Exception) {
                if (e is IOException) {
                    delay(currentDelay)
                    currentDelay = (currentDelay * factor).toLong().coerceAtMost(maxDelay)
                } else {
                    throw e
                }
            }
        }
        return block() // Last attempt
    }

    fun newClient() = client

    /**
     * Creates a RequestBody from a file with progress reporting.
     */
    fun createProgressRequestBody(
        file: File,
        contentType: String,
        onProgress: (Long, Long) -> Unit
    ): RequestBody {
        return object : RequestBody() {
            override fun contentType(): MediaType? = contentType.toMediaType()
            override fun contentLength(): Long = file.length()

            override fun writeTo(sink: BufferedSink) {
                file.source().use { source ->
                    var totalBytesRead = 0L
                    var readCount: Long
                    val totalSize = contentLength()
                    
                    while (source.read(sink.buffer(), 8192).also { readCount = it } != -1L) {
                        sink.flush()
                        totalBytesRead += readCount
                        onProgress(totalBytesRead, totalSize)
                    }
                }
            }
        }
    }
}
