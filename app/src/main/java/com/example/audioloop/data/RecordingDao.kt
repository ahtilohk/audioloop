package com.example.audioloop.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface RecordingDao {
    @Query("SELECT * FROM recordings ORDER BY timestamp DESC")
    fun getAllRecordings(): Flow<List<RecordingEntity>>

    @Query("SELECT * FROM recordings")
    suspend fun getAllRecords(): List<RecordingEntity>

    @Query("SELECT * FROM recordings WHERE category = :category ORDER BY timestamp DESC")
    fun getRecordingsByCategory(category: String): Flow<List<RecordingEntity>>

    @Query("SELECT * FROM recordings WHERE category = :category ORDER BY timestamp DESC")
    suspend fun getRecordingsByCategorySync(category: String): List<RecordingEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecording(recording: RecordingEntity)

    @Delete
    suspend fun deleteRecording(recording: RecordingEntity)

    @Query("DELETE FROM recordings WHERE filePath = :filePath")
    suspend fun deleteByPath(filePath: String)

    @Update
    suspend fun updateRecording(recording: RecordingEntity)

    @Query("SELECT * FROM recordings WHERE filePath = :path LIMIT 1")
    suspend fun getByPath(path: String): RecordingEntity?

    @Query("SELECT DISTINCT category FROM recordings")
    fun getDistinctCategories(): Flow<List<String>>

    @Query("SELECT EXISTS(SELECT 1 FROM recordings WHERE filePath = :path)")
    suspend fun exists(path: String): Boolean
}
