package com.example.audioloop

import android.net.Uri
import java.io.File

data class RecordingItem(
    val file: File,
    val name: String,
    val durationString: String,
    val durationMillis: Long,
    val uri: Uri,
    val note: String = "" // text note (lyrics, verse, translation etc.)
)
