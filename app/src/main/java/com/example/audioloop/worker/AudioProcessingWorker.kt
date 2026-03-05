package com.example.audioloop.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import java.io.File
import com.example.audioloop.AudioProcessor
import com.example.audioloop.AudioTrimmer
import com.example.audioloop.WavAudioTrimmer

import com.example.audioloop.AudioMerger
import com.example.audioloop.SilenceSplitter
import com.example.audioloop.RecordingItem

class AudioProcessingWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val type = inputData.getString("type") ?: return Result.failure()
        
        try {
            when (type) {
                "trim" -> {
                    val filePath = inputData.getString("file_path") ?: return Result.failure()
                    val file = File(filePath)
                    if (!file.exists()) return Result.failure()
                    val ext = file.extension
                    val ts = System.currentTimeMillis()
                    var currentWorkFile = File(file.parent, "temp_trim_${ts}.$ext")

                    val start = inputData.getLong("start", 0)
                    val end = inputData.getLong("end", 0)
                    val removeSelection = inputData.getBoolean("remove_selection", false)
                    val isWav = ext.lowercase() == "wav"
                    
                    val ok = if (isWav) {
                        if (removeSelection) WavAudioTrimmer.removeSegmentWav(file, currentWorkFile, start, end)
                        else WavAudioTrimmer.trimWav(file, currentWorkFile, start, end)
                    } else {
                        if (removeSelection) AudioTrimmer.removeSegmentAudio(file, currentWorkFile, start, end)
                        else AudioTrimmer.trimAudio(file, currentWorkFile, start, end)
                    }
                    if (!ok) return Result.failure()

                    val normalize = inputData.getBoolean("normalize", false)
                    if (normalize) {
                        val normFile = File(file.parent, "temp_norm_${ts}.$ext")
                        if (AudioProcessor.normalize(currentWorkFile, normFile)) {
                            currentWorkFile.delete()
                            currentWorkFile = normFile
                        }
                    }

                    val fadeInMs = inputData.getLong("fade_in", 0)
                    val fadeOutMs = inputData.getLong("fade_out", 0)
                    if (fadeInMs > 0 || fadeOutMs > 0) {
                        val fadeFile = File(file.parent, "temp_fade_${ts}.$ext")
                        if (AudioProcessor.applyFade(currentWorkFile, fadeFile, fadeInMs, fadeOutMs)) {
                            currentWorkFile.delete()
                            currentWorkFile = fadeFile
                        }
                    }

                    return finalizeProcessedFile(file, currentWorkFile, inputData.getBoolean("replace", false))
                }
                "split" -> {
                    val filePath = inputData.getString("file_path") ?: return Result.failure()
                    val outputDirPath = inputData.getString("output_dir_path") ?: return Result.failure()
                    val file = File(filePath)
                    val outputDir = File(outputDirPath)
                    if (!file.exists()) return Result.failure()
                    
                    val segments = SilenceSplitter.detectSegments(file)
                    if (segments.isEmpty()) return Result.failure()
                    
                    val createdFiles = SilenceSplitter.splitFile(file, outputDir, segments, file.nameWithoutExtension)
                    if (createdFiles.isEmpty()) return Result.failure()
                    
                    return Result.success(workDataOf(
                        "created_files" to createdFiles.map { it.absolutePath }.toTypedArray()
                    ))
                }
                "merge" -> {
                    val filePaths = inputData.getStringArray("file_paths") ?: return Result.failure()
                    val outputFilePath = inputData.getString("output_file_path") ?: return Result.failure()
                    val files = filePaths.map { File(it) }
                    val outputFile = File(outputFilePath)
                    
                    if (AudioMerger.mergeFiles(files, outputFile)) {
                        return Result.success(workDataOf("output_path" to outputFile.absolutePath))
                    }
                }
                "normalize" -> {
                    val filePath = inputData.getString("file_path") ?: return Result.failure()
                    val file = File(filePath)
                    val ts = System.currentTimeMillis()
                    val tempFile = File(file.parent, "temp_norm_${ts}.${file.extension}")
                    
                    if (AudioProcessor.normalize(file, tempFile)) {
                        return finalizeProcessedFile(file, tempFile, true)
                    }
                }
                "autotrim" -> {
                    val filePath = inputData.getString("file_path") ?: return Result.failure()
                    val file = File(filePath)
                    val bounds = SilenceSplitter.detectContentBounds(file) ?: return Result.failure()
                    val (startMs, endMs) = bounds
                    
                    val ts = System.currentTimeMillis()
                    val tempFile = File(file.parent, "temp_autotrim_${ts}.${file.extension}")
                    val isWav = file.extension.lowercase() == "wav"
                    
                    val success = if (isWav) WavAudioTrimmer.trimWav(file, tempFile, startMs, endMs)
                                  else AudioTrimmer.trimAudio(file, tempFile, startMs, endMs)
                    
                    if (success) {
                        return finalizeProcessedFile(file, tempFile, true)
                    }
                }
                else -> return Result.failure()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return Result.failure()
    }

    private fun finalizeProcessedFile(originalFile: File, processedFile: File, replace: Boolean): Result {
        if (!processedFile.exists()) return Result.failure()
        val ext = originalFile.extension
        val finalTarget: File = if (replace) {
            originalFile 
        } else {
            val originalName = originalFile.nameWithoutExtension
            val baseName = if (originalName.contains("_processed_")) originalName.substringBeforeLast("_processed_") else originalName
            var counter = 1
            var target = File(originalFile.parent, "${baseName}_processed_$counter.$ext")
            while (target.exists()) {
                counter++
                target = File(originalFile.parent, "${baseName}_processed_$counter.$ext")
            }
            target
        }

        if (replace && originalFile.exists()) {
            if (!originalFile.delete()) {
                processedFile.delete()
                return Result.failure()
            }
        }

        if (processedFile.renameTo(finalTarget)) {
            return Result.success(workDataOf("output_path" to finalTarget.absolutePath))
        } else {
            processedFile.delete()
            return Result.failure()
        }
    }
}
