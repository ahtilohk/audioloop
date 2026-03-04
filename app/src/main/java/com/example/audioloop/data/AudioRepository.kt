package com.example.audioloop.data

import android.content.Context
import android.net.Uri
import com.example.audioloop.RecordingItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.File

class AudioRepository(private val context: Context) {
    private val database = AppDatabase.getDatabase(context)
    private val dao = database.recordingDao()

    fun getAllRecordings(): Flow<List<RecordingItem>> {
        return dao.getAllRecordings().map { entities ->
            entities.map { it.toRecordingItem() }
        }
    }

    fun getRecordingsByCategory(category: String): Flow<List<RecordingItem>> {
        return dao.getRecordingsByCategory(category).map { entities ->
            entities.map { it.toRecordingItem() }
        }
    }

    suspend fun insertRecording(item: RecordingItem, category: String, isPublic: Boolean = false) {
        dao.insertRecording(item.toEntity(category, isPublic))
    }

    suspend fun updateNote(filePath: String, note: String) {
        val entity = dao.getByPath(filePath)
        if (entity != null) {
            dao.updateRecording(entity.copy(note = note))
        }
    }

    suspend fun deleteRecording(path: String) {
        dao.deleteByPath(path)
    }

    private fun RecordingEntity.toRecordingItem() = RecordingItem(
        file = File(filePath),
        name = name,
        durationString = durationString,
        durationMillis = durationMillis,
        uri = mediaStoreUri?.let { Uri.parse(it) } ?: Uri.EMPTY,
        note = note
    )

    private fun RecordingItem.toEntity(category: String, isPublic: Boolean) = RecordingEntity(
        filePath = file.absolutePath,
        name = name,
        durationString = durationString,
        durationMillis = durationMillis,
        note = note,
        category = category,
        isPublic = isPublic,
        mediaStoreUri = if (uri != Uri.EMPTY) uri.toString() else null
    )
}
