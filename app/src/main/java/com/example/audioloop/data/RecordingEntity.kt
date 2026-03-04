package com.example.audioloop.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "recordings")
data class RecordingEntity(
    @PrimaryKey
    val filePath: String,
    val name: String,
    val durationString: String,
    val durationMillis: Long,
    val note: String = "",
    val category: String = "General",
    val timestamp: Long = System.currentTimeMillis(),
    val isPublic: Boolean = false,
    val mediaStoreUri: String? = null
)
