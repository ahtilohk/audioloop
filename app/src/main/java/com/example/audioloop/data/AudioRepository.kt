package com.example.audioloop.data

import android.content.Context
import android.net.Uri
import com.example.audioloop.RecordingItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File

class AudioRepository(private val context: Context) {
    private val database = AppDatabase.getDatabase(context)
    private val dao = database.recordingDao()
    private val filesDir: File get() = context.filesDir

    private suspend fun getNotesDir(): File = withContext(Dispatchers.IO) {
        File(filesDir, ".notes").apply { if (!exists()) mkdirs() }
    }
    private suspend fun getNoteFile(audioFile: File): File = withContext(Dispatchers.IO) {
        File(getNotesDir(), "${audioFile.name}.note.txt")
    }
    private suspend fun getWaveformFile(audioFile: File): File = withContext(Dispatchers.IO) {
        File(audioFile.parent, "${audioFile.name}.wave")
    }

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

    fun getCategories(): Flow<List<String>> = dao.getDistinctCategories()

    suspend fun getAllRecordingsSync(): List<RecordingItem> = withContext(Dispatchers.IO) {
        dao.getAllRecords().map { it.toRecordingItem() }
    }

    suspend fun getRecordingsByCategorySync(category: String): List<RecordingItem> = withContext(Dispatchers.IO) {
        dao.getRecordingsByCategorySync(category).map { it.toRecordingItem() }
    }

    suspend fun insertRecording(item: RecordingItem, category: String, isPublic: Boolean = false): AudioResult<Unit> = withContext(Dispatchers.IO) {
        try {
            dao.insertRecording(item.toEntity(category, isPublic))
            AudioResult.Success(Unit)
        } catch (e: Exception) {
            AudioResult.Error("Failed to insert recording", e)
        }
    }

    suspend fun updateNote(filePath: String, note: String): AudioResult<Unit> = withContext(Dispatchers.IO) {
        try {
            val entity = dao.getByPath(filePath)
            if (entity != null) {
                dao.updateRecording(entity.copy(note = note))
                AudioResult.Success(Unit)
            } else {
                AudioResult.Error("Recording not found: $filePath")
            }
        } catch (e: Exception) {
            AudioResult.Error("Failed to update note", e)
        }
    }

    suspend fun renameRecording(item: RecordingItem, newName: String): AudioResult<File> = withContext(Dispatchers.IO) {
        try {
            val oldFile = item.file
            val ext = oldFile.extension
            val newFile = File(oldFile.parent, if (ext.isNotEmpty()) "$newName.$ext" else newName)
            
            if (newFile.exists()) return@withContext AudioResult.Error("File already exists")
            
            val oldNote = getNoteFile(oldFile)
            val oldWave = getWaveformFile(oldFile)

            if (oldFile.renameTo(newFile)) {
                // Also rename sidecars if they exist
                if (oldNote.exists()) oldNote.renameTo(getNoteFile(newFile))
                if (oldWave.exists()) oldWave.renameTo(getWaveformFile(newFile))

                val entity = dao.getByPath(oldFile.absolutePath)
                if (entity != null) {
                    dao.deleteByPath(oldFile.absolutePath)
                    dao.insertRecording(entity.copy(
                        filePath = newFile.absolutePath,
                        name = newFile.name
                    ))
                    AudioResult.Success(newFile)
                } else {
                    AudioResult.Error("Database entry missing for ${oldFile.name}")
                }
            } else {
                AudioResult.Error("Failed to rename file on disk")
            }
        } catch (e: Exception) {
            AudioResult.Error("Error during rename", e)
        }
    }

    suspend fun discoverRecordings(categories: List<String>): AudioResult<Unit> = withContext(Dispatchers.IO) {
        try {
            categories.forEach { cat ->
                syncCategory(cat)
                syncPublicStorage(cat)
            }
            cleanupStaleEntries()
            AudioResult.Success(Unit)
        } catch (e: Exception) {
            AudioResult.Error("Discovery failed", e)
        }
    }

    private suspend fun syncPublicStorage(category: String) {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.Q) return
        
        try {
            val collection = android.provider.MediaStore.Audio.Media.getContentUri(android.provider.MediaStore.VOLUME_EXTERNAL)
            val projection = arrayOf(
                android.provider.MediaStore.Audio.Media._ID,
                android.provider.MediaStore.Audio.Media.DISPLAY_NAME,
                android.provider.MediaStore.Audio.Media.DURATION,
                android.provider.MediaStore.Audio.Media.DATA,
                android.provider.MediaStore.Audio.Media.RELATIVE_PATH
            )
            val targetPath = if (category == "General") "Music/AudioLoop/" else "Music/AudioLoop/$category/"
            // Filter out system temp files and our own processing temps
            // Using exact RELATIVE_PATH match to avoid duplicates from sub-folders
            val selection = "${android.provider.MediaStore.Audio.Media.RELATIVE_PATH} = ? AND ${android.provider.MediaStore.Audio.Media.DISPLAY_NAME} NOT LIKE 'temp_%' AND ${android.provider.MediaStore.Audio.Media.DISPLAY_NAME} NOT LIKE 'trim_%'"
            val selectionArgs = arrayOf(targetPath)

            context.contentResolver.query(collection, projection, selection, selectionArgs, null)?.use { cursor ->
                val idCol = cursor.getColumnIndex(android.provider.MediaStore.Audio.Media._ID)
                val nameCol = cursor.getColumnIndex(android.provider.MediaStore.Audio.Media.DISPLAY_NAME)
                val durCol = cursor.getColumnIndex(android.provider.MediaStore.Audio.Media.DURATION)
                val dataCol = cursor.getColumnIndex(android.provider.MediaStore.Audio.Media.DATA)

                while (cursor.moveToNext()) {
                    val path = cursor.getString(dataCol) ?: continue
                    if (!dao.exists(path)) {
                        val id = cursor.getLong(idCol)
                        val name = cursor.getString(nameCol)
                        val durationMs = cursor.getLong(durCol)
                        val file = File(path)
                        val uri = android.content.ContentUris.withAppendedId(collection, id)
                        val (durStr, durMs) = AudioMetadataHelper.getDuration(file)
                        
                        val item = RecordingItem(file, name, durStr, durMs, uri, "")
                        dao.insertRecording(item.toEntity(category, true))
                    }
                }
            }
        } catch (e: Exception) { e.printStackTrace() }
    }

    private suspend fun syncCategory(category: String) {
        val targetDir = if (category == "General") filesDir else File(filesDir, category)
        if (!targetDir.exists()) return

        val files = targetDir.listFiles { _, name ->
            val isAudio = name.endsWith(".m4a", ignoreCase = true) || 
                          name.endsWith(".mp3", ignoreCase = true) || 
                          name.endsWith(".wav", ignoreCase = true)
            isAudio && !name.startsWith("temp_", ignoreCase = true)
        }

        files?.forEach { file ->
            if (!dao.exists(file.absolutePath)) {
                val (durStr, durMs) = AudioMetadataHelper.getDuration(file)
                val item = RecordingItem(
                    file = file,
                    name = file.name,
                    durationString = durStr,
                    durationMillis = durMs,
                    uri = Uri.fromFile(file),
                    note = ""
                )
                dao.insertRecording(item.toEntity(category, false))
            }
        }
    }

    private suspend fun cleanupStaleEntries() {
        val allEntries = dao.getAllRecords()
        allEntries.forEach { entity ->
            val file = File(entity.filePath)
            if (!file.exists()) {
                dao.deleteByPath(entity.filePath)
            }
        }
    }

    suspend fun deleteRecording(path: String): AudioResult<Unit> = withContext(Dispatchers.IO) {
        try {
            val file = File(path)
            val noteFile = getNoteFile(file)
            val waveFile = getWaveformFile(file)

            if (file.exists() && !file.delete()) {
                return@withContext AudioResult.Error("Failed to delete file from disk")
            }
            if (noteFile.exists()) noteFile.delete()
            if (waveFile.exists()) waveFile.delete()

            dao.deleteByPath(path)
            AudioResult.Success(Unit)
        } catch (e: Exception) {
            AudioResult.Error("Failed to delete recording", e)
        }
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

    suspend fun importFromPublicStorage(): AudioResult<Int> = withContext(Dispatchers.IO) {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.Q) return@withContext AudioResult.Success(0)
        
        try {
            val collection = android.provider.MediaStore.Audio.Media.getContentUri(android.provider.MediaStore.VOLUME_EXTERNAL)
            val projection = arrayOf(
                android.provider.MediaStore.Audio.Media._ID,
                android.provider.MediaStore.Audio.Media.DISPLAY_NAME,
                android.provider.MediaStore.Audio.Media.RELATIVE_PATH
            )
            val selection = "${android.provider.MediaStore.Audio.Media.RELATIVE_PATH} LIKE ?"
            val selectionArgs = arrayOf("Music/AudioLoop/%")

            var importedCount = 0
            context.contentResolver.query(collection, projection, selection, selectionArgs, null)?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Media._ID)
                val nameCol = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Media.DISPLAY_NAME)
                val pathCol = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Media.RELATIVE_PATH)

                while (cursor.moveToNext()) {
                    val mediaId = cursor.getLong(idCol)
                    val name = cursor.getString(nameCol) ?: continue
                    val relPath = cursor.getString(pathCol) ?: continue

                    val categoryName = relPath
                        .removePrefix("Music/AudioLoop/")
                        .trimEnd('/')
                        .split("/")
                        .firstOrNull()
                        ?.takeIf { it.isNotBlank() }
                        ?: "General"

                    val destFolder = if (categoryName == "General") filesDir
                                     else File(filesDir, categoryName).apply { mkdirs() }
                    val destFile = File(destFolder, name)

                    if (destFile.exists()) continue

                    val uri = android.content.ContentUris.withAppendedId(collection, mediaId)
                    try {
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            java.io.FileOutputStream(destFile).use { output -> input.copyTo(output) }
                        }
                        val (durStr, durMs) = AudioMetadataHelper.getDuration(destFile)
                        val item = RecordingItem(destFile, name, durStr, durMs, Uri.fromFile(destFile), "")
                        dao.insertRecording(item.toEntity(categoryName, false))
                        importedCount++
                    } catch (_: Exception) {}
                }
            }
            AudioResult.Success(importedCount)
        } catch (e: Exception) {
            AudioResult.Error("Failed to import from public storage", e)
        }
    }
}
