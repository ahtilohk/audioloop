package com.example.audioloop

data class Playlist(
    val id: String,
    val name: String,
    val files: List<String>,    // relative paths: "file.mp3" (General) or "Category/file.mp3"
    val createdAt: Long,
    val gapSeconds: Int = 0,    // pause between tracks
    val shuffle: Boolean = false,
    val playCount: Int = 0,     // times fully played through
    val speed: Float = 1.0f,
    val pitch: Float = 1.0f,
    val loopCount: Int = 1,     // 1 = once, -1 = infinite
    val sleepMinutes: Int = 0   // 0 = off
)
