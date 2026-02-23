package com.example.audioloop

data class Playlist(
    val id: String,
    val name: String,
    val files: List<String>,    // relative paths: "file.mp3" (General) or "Category/file.mp3"
    val createdAt: Long,
    val gapSeconds: Int = 0,    // pause between tracks
    val shuffle: Boolean = false,
    val playCount: Int = 0      // times fully played through
)
