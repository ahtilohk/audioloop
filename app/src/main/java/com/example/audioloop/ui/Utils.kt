package com.example.audioloop.ui

fun formatDuration(ms: Long): String {
    val m = java.util.concurrent.TimeUnit.MILLISECONDS.toMinutes(ms)
    val s = java.util.concurrent.TimeUnit.MILLISECONDS.toSeconds(ms) % 60
    return String.format("%02d:%02d", m, s)
}
