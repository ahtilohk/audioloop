package ee.ahtilohk.audioloop.data

import android.content.Context
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import ee.ahtilohk.audioloop.worker.AudioProcessingWorker
import kotlinx.coroutines.flow.Flow
import java.io.File
import java.util.UUID

/**
 * Manages background audio processing tasks using WorkManager.
 * Centralizes all complex audio operations that could be long-running.
 */
import javax.inject.Inject
import javax.inject.Singleton
import dagger.hilt.android.qualifiers.ApplicationContext

@Singleton
class AudioProcessingManager @Inject constructor(@ApplicationContext private val context: Context) {
    private val workManager = WorkManager.getInstance(context)

    fun trim(
        file: File,
        startMs: Long,
        endMs: Long,
        removeSelection: Boolean,
        replace: Boolean,
        normalize: Boolean,
        fadeInMs: Long,
        fadeOutMs: Long,
        speed: Float = 1.0f
    ): UUID {
        val workRequest = OneTimeWorkRequestBuilder<AudioProcessingWorker>()
            .setInputData(workDataOf(
                "type" to "trim",
                "file_path" to file.absolutePath,
                "start" to startMs,
                "end" to endMs,
                "remove_selection" to removeSelection,
                "replace" to replace,
                "normalize" to normalize,
                "fade_in" to fadeInMs,
                "fade_out" to fadeOutMs,
                "speed" to speed
            ))
            .build()
        workManager.enqueue(workRequest)
        return workRequest.id
    }

    fun split(file: File, outputDir: File): UUID {
        val workRequest = OneTimeWorkRequestBuilder<AudioProcessingWorker>()
            .setInputData(workDataOf(
                "type" to "split",
                "file_path" to file.absolutePath,
                "output_dir_path" to outputDir.absolutePath
            ))
            .build()
        workManager.enqueue(workRequest)
        return workRequest.id
    }

    fun normalize(file: File): UUID {
        val workRequest = OneTimeWorkRequestBuilder<AudioProcessingWorker>()
            .setInputData(workDataOf(
                "type" to "normalize",
                "file_path" to file.absolutePath
            ))
            .build()
        workManager.enqueue(workRequest)
        return workRequest.id
    }

    fun autoTrim(file: File): UUID {
        val workRequest = OneTimeWorkRequestBuilder<AudioProcessingWorker>()
            .setInputData(workDataOf(
                "type" to "autotrim",
                "file_path" to file.absolutePath
            ))
            .build()
        workManager.enqueue(workRequest)
        return workRequest.id
    }

    fun merge(files: List<File>, outputFile: File): UUID {
        val workRequest = OneTimeWorkRequestBuilder<AudioProcessingWorker>()
            .setInputData(workDataOf(
                "type" to "merge",
                "file_paths" to files.map { it.absolutePath }.toTypedArray(),
                "output_file_path" to outputFile.absolutePath
            ))
            .build()
        workManager.enqueue(workRequest)
        return workRequest.id
    }

    fun timeStretch(file: File, speed: Float): UUID {
        val workRequest = OneTimeWorkRequestBuilder<AudioProcessingWorker>()
            .setInputData(workDataOf(
                "type" to "timestretch",
                "file_path" to file.absolutePath,
                "speed" to speed
            ))
            .build()
        workManager.enqueue(workRequest)
        return workRequest.id
    }

    fun getWorkInfoFlow(id: UUID): Flow<WorkInfo?> = workManager.getWorkInfoByIdFlow(id)
}
