package com.example.audioloop

import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.lazy.rememberLazyListState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

import android.media.MediaMetadataRetriever
import android.media.MediaExtractor
import android.media.MediaFormat
import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Toast

import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider

import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.rememberCoroutineScope

// --- ABIFUNKTSIOONID JA VAHEMÄLU ---
private val waveformCache = mutableStateMapOf<String, List<Int>>()

private fun getWaveformFile(audioFile: java.io.File): java.io.File {
    return java.io.File(audioFile.parent, "${audioFile.name}.wave")
}

private fun saveWaveformToDisk(audioFile: java.io.File, waveform: List<Int>) {
    try {
        val waveFile = getWaveformFile(audioFile)
        waveFile.writeText(waveform.joinToString(","))
    } catch (e: Exception) { e.printStackTrace() }
}

private fun loadWaveformFromDisk(audioFile: java.io.File): List<Int>? {
    return try {
        val waveFile = getWaveformFile(audioFile)
        if (!waveFile.exists() || waveFile.lastModified() < audioFile.lastModified()) return null
        val content = waveFile.readText()
        if (content.isBlank()) return null
        content.split(",").map { it.toInt() }
    } catch (e: Exception) { null }
}

private fun precomputeWaveformAsync(scope: CoroutineScope, file: java.io.File, fullBars: Int = 160) {
    val key = file.absolutePath
    if (waveformCache.containsKey(key)) return
    scope.launch(Dispatchers.IO) {
        val cached = loadWaveformFromDisk(file)
        if (cached != null && cached.isNotEmpty()) {
            withContext(Dispatchers.Main) { waveformCache[key] = cached }
            return@launch
        }
        val waveform = generateWaveform(file, numBars = fullBars)
        saveWaveformToDisk(file, waveform)
        withContext(Dispatchers.Main) { waveformCache[key] = waveform }
    }
}

fun sanitizeName(name: String): String {
    val sb = StringBuilder()
    for (c in name) {
        if (c == '/' || c == '\\' || c == '*' || c == '?' || c == '"' || c == '<' || c == '>' || c == '|') continue
        if (c == ':') sb.append('꞉') else sb.append(c)
    }
    return sb.toString().trim()
}

fun generateWaveform(file: File, numBars: Int = 60): List<Int> {
    if (!file.exists() || file.length() < 10) return List(numBars) { (20..80).random() }
    return try {
        val result = MutableList(numBars) { 0 }
        java.io.RandomAccessFile(file, "r").use { raf ->
            val fileLength = raf.length()
            val headerSkip = 100L
            val available = (fileLength - headerSkip).coerceAtLeast(1)
            val step = available / numBars
            for (i in 0 until numBars) {
                val pos = headerSkip + (i * step)
                if (pos < fileLength) {
                    raf.seek(pos)
                    val byteVal = raf.readByte().toInt() and 0xFF
                    val height = (byteVal / 2.5).toInt().coerceIn(10, 100)
                    result[i] = height
                } else { result[i] = 50 }
            }
        }
        result
    } catch (e: Exception) { List(numBars) { (20..80).random() } }
}

data class RecordingItem(val file: File, val name: String, val durationString: String, val durationMillis: Long)

// --- MAIN ACTIVITY ---
class MainActivity : ComponentActivity(), CoroutineScope by MainScope() {

    private var pendingRecordingName = ""
    private var pendingCategory = ""
    private lateinit var mediaProjectionManager: android.media.projection.MediaProjectionManager

    private val screenCaptureLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK && result.data != null) {
            startInternalAudioService(result.resultCode, result.data!!)
        } else {
            Toast.makeText(this, "Salvestamiseks on vaja luba", Toast.LENGTH_SHORT).show()
        }
    }

    private var mediaPlayer: MediaPlayer? = null
    private val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) Toast.makeText(this, "Luba antud", Toast.LENGTH_SHORT).show()
        else Toast.makeText(this, "Luba puudub", Toast.LENGTH_SHORT).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as android.media.projection.MediaProjectionManager

        setContent {
            AppTheme {
                val coroutineScope = rememberCoroutineScope()
                var uiCategory by remember { mutableStateOf("Üldine") }
                var playingFileName by remember { mutableStateOf("") }
                var currentProgress by remember { mutableFloatStateOf(0f) }
                var currentTimeString by remember { mutableStateOf("00:00") }
                var savedItems by remember { mutableStateOf<List<RecordingItem>>(emptyList()) }

                val context = LocalContext.current

                // BROADCAST RECEIVER
                DisposableEffect(Unit) {
                    val receiver = object : BroadcastReceiver() {
                        override fun onReceive(context: Context?, intent: Intent?) {
                            if (intent?.action == RecordingService.ACTION_RECORDING_SAVED) {
                                coroutineScope.launch(Dispatchers.IO) {
                                    val firstTry = getSavedRecordings(uiCategory, context!!.filesDir)
                                    withContext(Dispatchers.Main) { savedItems = firstTry }
                                    delay(500)
                                    val secondTry = getSavedRecordings(uiCategory, context.filesDir)
                                    secondTry.firstOrNull()?.let { precomputeWaveformAsync(this, it.file) }
                                    withContext(Dispatchers.Main) { savedItems = secondTry }
                                }
                            }
                        }
                    }
                    val filter = IntentFilter(RecordingService.ACTION_RECORDING_SAVED)
                    ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
                    onDispose { context.unregisterReceiver(receiver) }
                }

                LaunchedEffect(uiCategory) {
                    withContext(Dispatchers.IO) {
                        val items = getSavedRecordings(uiCategory, filesDir)
                        items.forEach { precomputeWaveformAsync(this, it.file) }
                        withContext(Dispatchers.Main) { savedItems = items }
                    }
                }

                LaunchedEffect(playingFileName) {
                    if (playingFileName.isNotEmpty()) {
                        while (true) {
                            if (mediaPlayer?.isPlaying == true) {
                                val current = mediaPlayer?.currentPosition ?: 0
                                val total = mediaPlayer?.duration ?: 1
                                currentProgress = current.toFloat() / total.toFloat()
                                currentTimeString = formatTime(current.toLong())
                            }
                            delay(100)
                        }
                    } else {
                        currentProgress = 0f
                        currentTimeString = "00:00"
                    }
                }

                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    AudioLoopApp(
                        recordingItems = savedItems,
                        categories = getCategories(),
                        currentCategory = uiCategory,
                        currentProgress = currentProgress,
                        currentTimeString = currentTimeString,
                        playingFileName = playingFileName,
                        onPlayingFileNameChange = { playingFileName = it },
                        onCategoryChange = { newCat -> uiCategory = newCat },
                        onAddCategory = { catName ->
                            val dir = File(filesDir, catName)
                            if (!dir.exists()) dir.mkdirs()
                            uiCategory = catName
                        },
                        onRenameCategory = { oldName, newName ->
                            renameCategory(oldName, newName)
                            if (uiCategory == oldName) uiCategory = newName
                            savedItems = getSavedRecordings(uiCategory, filesDir)
                        },
                        onDeleteCategory = { catName ->
                            deleteCategory(catName)
                            uiCategory = "Üldine"
                        },
                        onReorderCategory = { _, _ -> },
                        onMoveFile = { item, targetCat ->
                            moveFileToCategory(item.file, targetCat)
                            savedItems = getSavedRecordings(uiCategory, filesDir)
                        },
                        onReorderFile = { file, direction ->
                            reorderFile(file, direction)
                            savedItems = getSavedRecordings(uiCategory, filesDir)
                        },
                        onStartRecord = { name, useRaw ->
                            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                                if (useRaw && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                    pendingRecordingName = name
                                    pendingCategory = uiCategory
                                    val captureIntent = mediaProjectionManager.createScreenCaptureIntent()
                                    screenCaptureLauncher.launch(captureIntent)
                                    true
                                } else {
                                    startRecording(name, uiCategory, false)
                                    true
                                }
                            } else {
                                requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                false
                            }
                        },
                        onStopRecord = { stopRecording() },
                        onStartPlaylist = { files, loop, speed, onComplete ->
                            playPlaylist(files, 0, loop, speed, { playingFileName = it }, onComplete)
                        },
                        onPlaylistUpdate = { },
                        onSpeedChange = { speed -> setPlaybackSpeed(speed) },
                        onLoopCountChange = { },
                        onSeekTo = { pos -> seekTo(pos) },
                        onPausePlay = { pausePlaying() },
                        onResumePlay = { resumePlaying() },
                        onStopPlay = { stopPlaying() },
                        onDeleteFile = { item ->
                            if (item.file.delete()) savedItems = getSavedRecordings(uiCategory, filesDir)
                        },
                        onShareFile = { item -> shareFile(item.file) },
                        onRenameFile = { item, newName ->
                            renameFile(item, newName)
                            savedItems = getSavedRecordings(uiCategory, filesDir)
                        },
                        onImportFile = { uri ->
                            importFileFromUri(uri, uiCategory)
                            savedItems = getSavedRecordings(uiCategory, filesDir)
                            savedItems.forEach { item -> precomputeWaveformAsync(coroutineScope, item.file) }
                        },
                        onTrimFile = { file, start, end ->
                            trimAudioFile(file, start, end) {
                                savedItems = getSavedRecordings(uiCategory, filesDir)
                                precomputeWaveformAsync(coroutineScope, file)
                            }
                        }
                    )
                }
            }
        }
    }

    private fun getCategories(): List<String> {
        val categoryList = mutableListOf("Üldine")
        filesDir.listFiles()?.filter { it.isDirectory }?.sortedBy { it.name }?.forEach { categoryList.add(it.name) }
        return categoryList
    }

    // --- FIX: getDuration mis kasutab MediaPlayerit kui Metadata ebaõnnestub ---
    private fun getDuration(file: File): Pair<String, Long> {
        if (!file.exists() || file.length() < 10) return Pair("00:00", 0L)
        var millis = 0L
        val mmr = MediaMetadataRetriever()
        try {
            mmr.setDataSource(file.absolutePath)
            val durationStr = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            millis = durationStr?.toLongOrNull() ?: 0L
        } catch (e: Exception) { }
        finally { try { mmr.release() } catch (e: Exception) {} }

        // Varuplaan: MediaPlayer
        if (millis == 0L) {
            try {
                val mp = MediaPlayer()
                mp.setDataSource(file.absolutePath)
                mp.prepare()
                millis = mp.duration.toLong()
                mp.release()
            } catch (e: Exception) { }
        }
        // Varuplaan 2: MediaExtractor (Kõige madalam tase, töötab ka siis kui metaandmed puuduvad)
        if (millis == 0L) {
            try {
                val extractor = MediaExtractor()
                extractor.setDataSource(file.absolutePath)
                for (i in 0 until extractor.trackCount) {
                    val format = extractor.getTrackFormat(i)
                    val mime = format.getString(MediaFormat.KEY_MIME)
                    if (mime?.startsWith("audio/") == true) {
                        if (format.containsKey(MediaFormat.KEY_DURATION)) {
                            millis = format.getLong(MediaFormat.KEY_DURATION) / 1000 // algselt mikrosekundites
                            break
                        }
                    }
                }
                extractor.release()
            } catch (e: Exception) { }
        }

        if (millis == 0L) return Pair("00:00", 0L)
        return Pair(formatTime(millis), millis)
    }

    private fun formatTime(millis: Long): String {
        val minutes = TimeUnit.MILLISECONDS.toMinutes(millis)
        val seconds = TimeUnit.MILLISECONDS.toSeconds(millis) % 60
        return String.format("%02d:%02d", minutes, seconds)
    }

    private fun startRecording(fileName: String, category: String, useRawAudio: Boolean) {
        val finalName = if (category == "Üldine") fileName else {
            val folder = File(filesDir, category)
            if (!folder.exists()) folder.mkdirs()
            "$category/$fileName"
        }
        val intent = Intent(this, RecordingService::class.java).apply {
            action = RecordingService.ACTION_START
            putExtra(RecordingService.EXTRA_FILENAME, finalName)
            val source = if (useRawAudio && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                MediaRecorder.AudioSource.UNPROCESSED
            } else { MediaRecorder.AudioSource.MIC }
            putExtra(RecordingService.EXTRA_AUDIO_SOURCE, source)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent) else startService(intent)
    }

    private fun stopRecording() {
        val intent = Intent(this, RecordingService::class.java).apply { action = RecordingService.ACTION_STOP }
        startService(intent)
    }

    private fun startInternalAudioService(resultCode: Int, data: Intent) {
        val finalName = if (pendingCategory == "Üldine") pendingRecordingName else {
            val folder = File(filesDir, pendingCategory)
            if (!folder.exists()) folder.mkdirs()
            "$pendingCategory/$pendingRecordingName"
        }
        val serviceIntent = Intent(this, RecordingService::class.java).apply {
            action = RecordingService.ACTION_START_INTERNAL
            putExtra(RecordingService.EXTRA_FILENAME, finalName)
            putExtra(RecordingService.EXTRA_RESULT_CODE, resultCode)
            putExtra(RecordingService.EXTRA_DATA, data)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(serviceIntent) else startService(serviceIntent)
    }

    private fun importFileFromUri(uri: Uri, category: String) {
        try {
            var fileName = "import_${System.currentTimeMillis()}"
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx != -1) fileName = cursor.getString(idx)
                }
            }
            val extension = fileName.substringAfterLast('.', "")
            val nameWithoutExt = if (extension.isNotEmpty()) fileName.substringBeforeLast('.') else fileName
            var safeName = sanitizeName(nameWithoutExt)
            if (safeName.isBlank()) safeName = "nimetu_audio"
            val finalFileName = if (extension.isNotEmpty()) "$safeName.$extension" else "$safeName.m4a"
            val folder = if (category == "Üldine") filesDir else File(filesDir, category).apply { mkdirs() }
            val targetFile = File(folder, finalFileName)
            contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(targetFile).use { output -> input.copyTo(output) }
            }
            Toast.makeText(this, "Imporditud: $safeName", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) { Toast.makeText(this, "Viga importimisel", Toast.LENGTH_SHORT).show() }
    }

    private fun reorderFile(file: File, direction: Int) {
        val parent = file.parentFile ?: return
        val files = parent.listFiles { _, name ->
            name.endsWith(".m4a", ignoreCase = true) || name.endsWith(".mp3", ignoreCase = true) || name.endsWith(".wav", ignoreCase = true)
        }?.sortedBy { it.name }?.toMutableList() ?: return
        val index = files.indexOfFirst { it.name == file.name }
        val targetIndex = index + direction
        if (index == -1 || targetIndex < 0 || targetIndex >= files.size) return
        val fileA = files[index]
        val fileB = files[targetIndex]
        fun hasPrefix(f: File): Boolean = f.name.matches(Regex("^\\d{3}_.*"))
        fun stripPrefix(name: String): String = name.replace(Regex("^\\d{3}_"), "")
        val needsNormalization = files.any { !hasPrefix(it) }
        if (needsNormalization) {
            Toast.makeText(this, "Lisan failidele numbrid...", Toast.LENGTH_SHORT).show()
            val tempFiles = files.mapIndexed { i, f ->
                val pureName = stripPrefix(f.name)
                val tempFile = File(parent, "tmp_${System.currentTimeMillis()}_$i.tmp")
                f.renameTo(tempFile)
                Pair(tempFile, pureName)
            }
            tempFiles.forEachIndexed { i, (temp, originalName) ->
                val prefix = String.format("%03d", i + 1)
                val newFile = File(parent, "${prefix}_$originalName")
                temp.renameTo(newFile)
            }
            return
        }
        val nameA = stripPrefix(fileA.name)
        val nameB = stripPrefix(fileB.name)
        val prefixA = fileA.name.substringBefore("_")
        val prefixB = fileB.name.substringBefore("_")
        val tempA = File(parent, "swap_temp_a.tmp")
        val tempB = File(parent, "swap_temp_b.tmp")
        fileA.renameTo(tempA); fileB.renameTo(tempB)
        val newFileA = File(parent, "${prefixB}_$nameA")
        val newFileB = File(parent, "${prefixA}_$nameB")
        tempA.renameTo(newFileA); tempB.renameTo(newFileB)
    }

    private fun moveFileToCategory(file: File, targetCategory: String) {
        val targetDir = if (targetCategory == "Üldine") filesDir else File(filesDir, targetCategory)
        if (!targetDir.exists()) targetDir.mkdirs()
        val targetFile = File(targetDir, file.name)
        if (file.renameTo(targetFile)) {
            Toast.makeText(this, "Liigutatud", Toast.LENGTH_SHORT).show()
        } else {
            try {
                file.inputStream().use { input -> targetFile.outputStream().use { output -> input.copyTo(output) } }
                file.delete()
                Toast.makeText(this, "Liigutatud", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) { Toast.makeText(this, "Viga liigutamisel", Toast.LENGTH_SHORT).show() }
        }
    }

    private fun renameFile(item: RecordingItem, newName: String) {
        val oldFile = item.file
        val ext = oldFile.extension
        val newFileName = if (newName.endsWith(".$ext")) newName else "$newName.$ext"
        val newFile = File(oldFile.parent, newFileName)
        oldFile.renameTo(newFile)
    }

    private fun shareFile(file: File) {
        try {
            val uri = FileProvider.getUriForFile(this, "${applicationContext.packageName}.provider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "audio/*"
                putExtra(Intent.EXTRA_STREAM, uri)
                clipData = android.content.ClipData.newRawUri(null, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, "Jaga faili"))
        } catch (e: Exception) { }
    }

    private fun deleteCategory(catName: String) {
        if (catName == "Üldine") return
        File(filesDir, catName).deleteRecursively()
    }

    private fun renameCategory(oldName: String, newName: String) {
        if (oldName == "Üldine") return
        val oldDir = File(filesDir, oldName)
        val newDir = File(filesDir, sanitizeName(newName))
        oldDir.renameTo(newDir)
    }

    private fun trimAudioFile(file: File, start: Long, end: Long, onSuccess: () -> Unit) {
        stopPlaying()
        val ext = file.extension
        val tempFile = File(file.parent, "temp_trim_${System.currentTimeMillis()}.$ext")
        launch(Dispatchers.IO) {
            val success = AudioTrimmer.trimAudio(file, tempFile, start, end)
            withContext(Dispatchers.Main) {
                if (success) {
                    if (file.delete()) {
                        tempFile.renameTo(file)
                        waveformCache.remove(file.absolutePath)
                        getWaveformFile(file).delete()
                        onSuccess()
                    } else tempFile.delete()
                } else tempFile.delete()
            }
        }
    }

    // --- KÕIK rootDir VEAD PARANDATUD ---
    private fun getSavedRecordings(category: String, rootDir: File): List<RecordingItem> {
        val targetDir = if (category == "Üldine") rootDir else File(rootDir, category)
        if (!targetDir.exists()) return emptyList()
        val files = targetDir.listFiles { _, name ->
            name.endsWith(".m4a", ignoreCase = true) || name.endsWith(".mp3", ignoreCase = true) || name.endsWith(".wav", ignoreCase = true)
        } ?: return emptyList()

        return files.mapNotNull { file ->
            // Siia jõuavad ainult päriselt eksisteerivad failid.
            // 00:00 tekib ainult siis kui MediaRecorder ei kirjuta andmeid (vt RecordingService parandust)
            val (aegTekst, aegNumber) = getDuration(file)
            RecordingItem(file = file, name = file.name, durationString = aegTekst, durationMillis = aegNumber)
        }.sortedByDescending { it.file.lastModified() }
    }

    private fun playPlaylist(
        allFiles: List<File>,
        currentIndex: Int,
        loopCount: Int,
        speed: Float,
        onNext: (String) -> Unit,
        onComplete: () -> Unit
    ) {
        if (allFiles.isEmpty() || currentIndex < 0) { onComplete(); return }
        if (currentIndex >= allFiles.size) {
            if (loopCount == -1) playPlaylist(allFiles, 0, loopCount, speed, onNext, onComplete)
            else onComplete()
            return
        }
        stopPlaying()
        val fileToPlay = allFiles[currentIndex]
        onNext(fileToPlay.name)

        try {
            mediaPlayer = MediaPlayer().apply {
                setDataSource(fileToPlay.absolutePath)
                setOnPreparedListener { mp ->
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        try { mp.playbackParams = mp.playbackParams.setSpeed(speed) } catch (e: Exception) { }
                    }
                    mp.isLooping = false
                    mp.start()
                }
                setOnCompletionListener { playPlaylist(allFiles, currentIndex + 1, loopCount, speed, onNext, onComplete) }
                setOnErrorListener { _, _, _ ->
                    playPlaylist(allFiles, currentIndex + 1, loopCount, speed, onNext, onComplete)
                    true
                }
                prepareAsync()
            }
        } catch (e: Exception) { playPlaylist(allFiles, currentIndex + 1, loopCount, speed, onNext, onComplete) }
    }

    private fun stopPlaying() { mediaPlayer?.release(); mediaPlayer = null }
    private fun pausePlaying() { mediaPlayer?.pause() }
    private fun resumePlaying() { mediaPlayer?.start() }
    private fun seekTo(pos: Float) { mediaPlayer?.let { if (it.duration > 0) it.seekTo((it.duration * pos).toInt()) } }
    private fun setPlaybackSpeed(speed: Float) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            mediaPlayer?.let { if (it.isPlaying) it.playbackParams = it.playbackParams.setSpeed(speed) }
        }
    }
}

// ... KUI SUL ON SIIN VEEL UI KOOD (AppTheme, AudioLoopApp jt), SIIS JÄTA SEE ALLES! ...
// Kui kustutasid kogemata ära, siis anna teada – saadan UI osa eraldi.

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val darkTheme = isSystemInDarkTheme()
    val colorScheme = if (darkTheme) {
        darkColorScheme(
            primary = Color(0xFFD0BCFF),
            secondary = Color(0xFFCCC2DC),
            tertiary = Color(0xFFEFB8C8),
            background = Color(0xFF1C1B1F),
            surface = Color(0xFF1C1B1F),
            onPrimary = Color(0xFF381E72),
            onSurface = Color(0xFFE6E1E5),
            surfaceVariant = Color(0xFF49454F),
            error = Color(0xFFF2B8B5),
            primaryContainer = Color(0xFF4F378B),
            onPrimaryContainer = Color(0xFFEADDFF),
            secondaryContainer = Color(0xFF4A4458),
            onSecondaryContainer = Color(0xFFE8DEF8)
        )
    } else {
        lightColorScheme(
            primary = Color(0xFF6650a4),
            secondary = Color(0xFF625b71),
            tertiary = Color(0xFF7D5260),
            background = Color(0xFFFFFBFE),
            surface = Color(0xFFFFFBFE),
            onPrimary = Color.White,
            onSurface = Color(0xFF1C1B1F),
            surfaceVariant = Color(0xFFF0F0F0),
            error = Color(0xFFB3261E),
            primaryContainer = Color(0xFFEADDFF),
            onPrimaryContainer = Color(0xFF21005D),
            secondaryContainer = Color(0xFFE8DEF8),
            onSecondaryContainer = Color(0xFF1D192B)
        )
    }
    MaterialTheme(colorScheme = colorScheme, content = content)
}

// --- DIALOOGID JA KOMPONENDID --- //

@Composable
fun TrimDialog(
    item: RecordingItem,
    cachedWaveform: List<Int>?,
    onDismiss: () -> Unit,
    onConfirm: (File, Long, Long) -> Unit,
    requestFullWaveform: (File) -> Unit
) {
    var sliderPosition by remember { mutableStateOf(0f..1f) }
    var amplitudes by remember {
        mutableStateOf(cachedWaveform?.takeIf { it.isNotEmpty() }
            ?: List(60) { (20..80).random() })
    }

    val activeColor = MaterialTheme.colorScheme.primary
    val inactiveColor = MaterialTheme.colorScheme.outlineVariant
    val totalMillis = item.durationMillis.toFloat()
    val startMs = (sliderPosition.start * totalMillis).toLong()
    val endMs = (sliderPosition.endInclusive * totalMillis).toLong()

    fun fmt(ms: Long): String {
        val min = java.util.concurrent.TimeUnit.MILLISECONDS.toMinutes(ms)
        val sec = java.util.concurrent.TimeUnit.MILLISECONDS.toSeconds(ms) % 60
        return String.format("%02d:%02d", min, sec)
    }

    LaunchedEffect(cachedWaveform) {
        if (cachedWaveform != null && cachedWaveform.isNotEmpty()) {
            amplitudes = cachedWaveform
        } else {
            requestFullWaveform(item.file)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Lõika faili: ${item.name}") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Kestus: ${item.durationString}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(12.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp)) {
                        val barCount = amplitudes.size.coerceAtLeast(1)
                        val barWidth = size.width / (barCount * 1.0f)
                        val maxHeight = size.height
                        amplitudes.forEachIndexed { index, amp ->
                            val x = index * barWidth + barWidth / 2f
                            val normalized = (amp.coerceIn(0, 100) / 100f)
                            val barHeight = normalized * maxHeight
                            val yStart = (maxHeight - barHeight) / 2f
                            val yEnd = yStart + barHeight
                            val progress = index.toFloat() / barCount
                            val isActive = progress >= sliderPosition.start && progress <= sliderPosition.endInclusive
                            val color = if (isActive) activeColor else inactiveColor
                            drawLine(
                                color = color,
                                start = Offset(x, yStart),
                                end = Offset(x, yEnd),
                                strokeWidth = (barWidth * 0.45f).coerceAtLeast(1f),
                                cap = StrokeCap.Round
                            )
                        }
                    }

                    RangeSlider(
                        value = sliderPosition,
                        onValueChange = { sliderPosition = it },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                        valueRange = 0f..1f
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Algus: ${fmt(startMs)}", fontSize = 12.sp)
                    Text("Lõpp: ${fmt(endMs)}", fontSize = 12.sp)
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text("Uus pikkus: ${fmt(endMs - startMs)}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(item.file, startMs, endMs); onDismiss() }) { Text("Lõika ja Salvesta") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Loobu") } }
    )
}

@Composable
fun RenameDialog(currentName: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var textState by remember { mutableStateOf(TextFieldValue(text = currentName, selection = TextRange(currentName.length))) }
    val focusRequester = remember { FocusRequester() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Muuda nime") },
        text = {
            OutlinedTextField(
                value = textState,
                onValueChange = { textState = it },
                label = { Text("Uus nimi") },
                singleLine = true,
                modifier = Modifier.focusRequester(focusRequester),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done)
            )
            LaunchedEffect(Unit) { focusRequester.requestFocus() }
        },
        confirmButton = { Button(onClick = {
            val safeText = sanitizeName(textState.text)
            onConfirm(safeText)
        }) { Text("Salvesta") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Loobu") } }
    )
}

@Composable
fun DeleteConfirmDialog(title: String, text: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { Button(onClick = onConfirm, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("Kustuta") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Loobu") } }
    )
}

@Composable
fun CategoryManageDialog(
    categoryName: String,
    allCategories: List<String>,
    onDismiss: () -> Unit,
    onRename: (String) -> Unit,
    onDelete: () -> Unit,
    onMoveLeft: () -> Unit,
    onMoveRight: () -> Unit
) {
    var textState by remember { mutableStateOf(TextFieldValue(text = categoryName, selection = TextRange(categoryName.length))) }
    val focusRequester = remember { FocusRequester() }
    val listState = rememberLazyListState()

    LaunchedEffect(allCategories) {
        val index = allCategories.indexOf(categoryName)
        if (index >= 0) {
            listState.animateScrollToItem(index)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Halda kategooriat") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                OutlinedTextField(
                    value = textState,
                    onValueChange = { textState = it },
                    label = { Text("Nimi") },
                    singleLine = true,
                    modifier = Modifier.focusRequester(focusRequester),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done)
                )
                LaunchedEffect(Unit) { focusRequester.requestFocus() }

                Spacer(modifier = Modifier.height(24.dp))
                Text("Asukoht nimekirjas:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(8.dp))

                LazyRow(
                    state = listState,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    itemsIndexed(allCategories) { index, cat ->
                        val isCurrent = cat == categoryName
                        val bgColor = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                        val txtColor = if (isCurrent) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        val weight = if (isCurrent) FontWeight.Bold else FontWeight.Normal

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.background(bgColor, RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 6.dp)) {
                                Text(text = cat, fontSize = 12.sp, color = txtColor, fontWeight = weight)
                            }
                            if (index < allCategories.lastIndex) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("›", fontSize = 14.sp, color = MaterialTheme.colorScheme.outline)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Text("Muuda järjekorda:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    Button(
                        onClick = onMoveLeft,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                        enabled = allCategories.indexOf(categoryName) > 1
                    ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Vasakule") }

                    Button(
                        onClick = onMoveRight,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                        enabled = allCategories.indexOf(categoryName) < allCategories.size - 1
                    ) { Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Paremale") }
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = onDelete, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error), modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Delete, contentDescription = null); Spacer(modifier = Modifier.width(8.dp)); Text("Kustuta kategooria")
                }
            }
        },
        confirmButton = { Button(onClick = {
            val safeText = sanitizeName(textState.text)
            onRename(safeText)
        }) { Text("Salvesta") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Loobu") } }
    )
}

@Composable
fun FileManageDialog(
    itemName: String,
    onDismiss: () -> Unit,
    onRename: (String) -> Unit,
    onTrim: () -> Unit
) {
    var textState by remember { mutableStateOf(TextFieldValue(text = itemName, selection = TextRange(itemName.length))) }
    val focusRequester = remember { FocusRequester() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Halda faili") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                OutlinedTextField(
                    value = textState,
                    onValueChange = { textState = it },
                    label = { Text("Nimi") },
                    singleLine = true,
                    modifier = Modifier.focusRequester(focusRequester),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done)
                )
                LaunchedEffect(Unit) { focusRequester.requestFocus() }

                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onTrim,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("✂", fontSize = 18.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Lõika (Trim)")
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val safeText = sanitizeName(textState.text)
                onRename(safeText)
            }) { Text("Salvesta") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Loobu") } }
    )
}

@Composable
fun MoveFileDialog(categories: List<String>, onDismiss: () -> Unit, onSelect: (String) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Vali uus kategooria") },
        text = {
            Column(modifier = Modifier.heightIn(max = 300.dp)) {
                LazyColumn {
                    items(categories) { category ->
                        Text(
                            text = category,
                            fontSize = 18.sp,
                            modifier = Modifier.fillMaxWidth().clickable { onSelect(category) }.padding(vertical = 12.dp, horizontal = 8.dp)
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Loobu") } }
    )
}

@Composable
fun SelectableCheckbox(isSelected: Boolean, selectionOrder: Int, onToggle: () -> Unit) {
    Box(
        modifier = Modifier.size(24.dp).clip(CircleShape)
            .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
            .border(2.dp, if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray, CircleShape)
            .clickable { onToggle() },
        contentAlignment = Alignment.Center
    ) {
        if (isSelected) {
            Text(text = "$selectionOrder", color = MaterialTheme.colorScheme.onPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun PauseIcon(color: Color) {
    Row(modifier = Modifier.size(24.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.width(6.dp).height(18.dp).padding(end=2.dp).background(color))
        Spacer(modifier = Modifier.width(4.dp))
        Box(modifier = Modifier.width(6.dp).height(18.dp).background(color))
    }
}

@Composable
fun StopIcon() {
    Box(modifier = Modifier.size(20.dp).background(MaterialTheme.colorScheme.error, shape = RoundedCornerShape(2.dp)))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioLoopApp(
    recordingItems: List<RecordingItem>,
    categories: List<String>,
    currentCategory: String,
    currentProgress: Float,
    currentTimeString: String,
    playingFileName: String,
    onPlayingFileNameChange: (String) -> Unit,
    onCategoryChange: (String) -> Unit,
    onAddCategory: (String) -> Unit,
    onRenameCategory: (String, String) -> Unit,
    onDeleteCategory: (String) -> Unit,
    onReorderCategory: (String, Int) -> Unit,
    onMoveFile: (RecordingItem, String) -> Unit,
    onReorderFile: (File, Int) -> Unit,
    onStartRecord: (String, Boolean) -> Boolean,
    onStopRecord: () -> Unit,
    onStartPlaylist: (List<File>, Int, Float, () -> Unit) -> Unit,
    onPlaylistUpdate: (List<File>) -> Unit,
    onSpeedChange: (Float) -> Unit,
    onLoopCountChange: (Int) -> Unit,
    onSeekTo: (Float) -> Unit,
    onPausePlay: () -> Unit,
    onResumePlay: () -> Unit,
    onStopPlay: () -> Unit,
    onDeleteFile: (RecordingItem) -> Unit,
    onShareFile: (RecordingItem) -> Unit,
    onRenameFile: (RecordingItem, String) -> Unit,
    onImportFile: (Uri) -> Unit,
    onTrimFile: (File, Long, Long) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current

    var isRecording by remember { mutableStateOf(false) }
    var isPaused by remember { mutableStateOf(false) }
    var isSelectionMode by remember { mutableStateOf(false) }
    val selectedFiles = remember { mutableStateListOf<File>() }

    var recordingName by remember { mutableStateOf("") }
    var isRawMode by remember { mutableStateOf(false) }

    var selectedLoopCount by remember { mutableIntStateOf(-1) }
    var selectedSpeed by remember { mutableFloatStateOf(1.0f) }

    var showRenameDialog by remember { mutableStateOf(false) }
    var showAddCategoryDialog by remember { mutableStateOf(false) }
    var showMoveFileDialog by remember { mutableStateOf(false) }
    var showCategoryManageDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var showFileManageDialog by remember { mutableStateOf(false) }
    var showTrimDialog by remember { mutableStateOf(false) }

    var itemToModify by remember { mutableStateOf<RecordingItem?>(null) }
    var categoryToManage by remember { mutableStateOf("") }

    var liveTimeName by remember { mutableStateOf("") }
    fun generateTimeName(prefix: String): String {
        val sdf = java.text.SimpleDateFormat("dd.MM.yy HH:mm:ss", java.util.Locale.getDefault())
        return "$prefix ${sdf.format(java.util.Date())}"
    }

    LaunchedEffect(isRawMode) {
        while (true) {
            val prefix = if (isRawMode) "Voog" else "Kõne"
            liveTimeName = generateTimeName(prefix)
            delay(1000)
        }
    }

    val filePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) onImportFile(uri)
    }

    if (showTrimDialog && itemToModify != null) {
        val cached = waveformCache[itemToModify!!.file.absolutePath]
        TrimDialog(
            item = itemToModify!!,
            cachedWaveform = cached,
            onDismiss = { showTrimDialog = false },
            onConfirm = { file, start, end -> onTrimFile(file, start, end) },
            requestFullWaveform = { file -> precomputeWaveformAsync(coroutineScope, file) }
        )
    }

    if (showFileManageDialog && itemToModify != null) {
        FileManageDialog(
            itemName = itemToModify!!.name,
            onDismiss = { showFileManageDialog = false },
            onRename = { newName -> onRenameFile(itemToModify!!, newName); showFileManageDialog = false },
            onTrim = { showFileManageDialog = false; showTrimDialog = true }
        )
    }

    if (showRenameDialog && itemToModify != null) {
        RenameDialog(currentName = itemToModify!!.name, onDismiss = { showRenameDialog = false }, onConfirm = { newName -> onRenameFile(itemToModify!!, newName); showRenameDialog = false })
    }
    if (showAddCategoryDialog) {
        RenameDialog(currentName = "", onDismiss = { showAddCategoryDialog = false }, onConfirm = { newName -> onAddCategory(newName); showAddCategoryDialog = false })
    }
    if (showCategoryManageDialog) {
        CategoryManageDialog(
            categoryName = categoryToManage,
            allCategories = categories,
            onDismiss = { showCategoryManageDialog = false },
            onRename = { newName -> onRenameCategory(categoryToManage, newName); showCategoryManageDialog = false },
            onDelete = { showCategoryManageDialog = false; showDeleteConfirmDialog = true },
            onMoveLeft = { onReorderCategory(categoryToManage, -1) },
            onMoveRight = { onReorderCategory(categoryToManage, 1) }
        )
    }
    if (showDeleteConfirmDialog) {
        DeleteConfirmDialog(title = "Kustuta kategooria?", text = "Kustutad kausta '$categoryToManage' ja kõik selle failid.", onDismiss = { showDeleteConfirmDialog = false }, onConfirm = { onDeleteCategory(categoryToManage); showDeleteConfirmDialog = false })
    }
    if (showMoveFileDialog && itemToModify != null) {
        MoveFileDialog(categories = categories, onDismiss = { showMoveFileDialog = false }, onSelect = { targetCat -> onMoveFile(itemToModify!!, targetCat); showMoveFileDialog = false })
    }

    Column(modifier = Modifier.fillMaxSize().padding(top = 50.dp, start = 16.dp, end = 16.dp, bottom = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("AUDIOLOOP", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.weight(1f))
            TextButton(onClick = {
                if (isSelectionMode) { isSelectionMode = false; selectedFiles.clear(); if (playingFileName.isNotEmpty()) { onStopPlay(); onPlayingFileNameChange("") } }
                else { isSelectionMode = true }
            }) {
                if (isSelectionMode) Text("TÜHISTA PLAYLIST", fontSize = 12.sp, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                else Text("VALI PLAYLIST", fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            LazyRow(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                itemsIndexed(categories) { _, category ->
                    val isSelected = (category == currentCategory)
                    val bgColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                    val textColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface

                    Box(modifier = Modifier.background(bgColor, RoundedCornerShape(16.dp)).clickable { if (!isSelected) { isSelectionMode = false; selectedFiles.clear(); onCategoryChange(category) } }.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(category, color = textColor, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            if (isSelected && category != "Üldine") {
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(imageVector = Icons.Default.Edit, contentDescription = "Halda", tint = textColor, modifier = Modifier.size(16.dp).clickable { categoryToManage = category; showCategoryManageDialog = true })
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(onClick = { showAddCategoryDialog = true }, modifier = Modifier.size(30.dp).background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(50))) {
                Icon(Icons.Default.Add, contentDescription = "Lisa", modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
            }
        }

        if (!isSelectionMode) {
            OutlinedTextField(
                value = recordingName,
                onValueChange = { recordingName = it },
                label = { Text("Faili nimi") },
                supportingText = {
                    if (recordingName.isBlank()) {
                        Text(text = "Vaikimisi: $liveTimeName", color = MaterialTheme.colorScheme.primary)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                trailingIcon = {
                    if (recordingName.isNotEmpty()) {
                        IconButton(onClick = { recordingName = "" }) { Icon(Icons.Default.Clear, contentDescription = "Tühjenda") }
                    }
                }
            )
            Spacer(modifier = Modifier.height(4.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                FilterChip(
                    selected = !isRawMode,
                    onClick = { isRawMode = false },
                    label = { Text("Kõne (Mic)") },
                    leadingIcon = { if (!isRawMode) Icon(Icons.Default.Person, null, modifier = Modifier.size(18.dp)) }
                )
                Spacer(modifier = Modifier.width(12.dp))
                FilterChip(
                    selected = isRawMode,
                    onClick = { isRawMode = true },
                    label = { Text("Voog (YT, Msg)") },
                    leadingIcon = { if (isRawMode) Icon(Icons.Default.Star, null, modifier = Modifier.size(18.dp)) }
                )
            }
            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = {
                    if (isRecording) {
                        isRecording = false; onStopRecord(); recordingName = ""; focusManager.clearFocus()
                    } else {
                        val finalName = if (recordingName.isBlank()) liveTimeName else recordingName
                        if (onStartRecord(finalName, isRawMode)) isRecording = true
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = if (isRecording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary),
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) { Text(if (isRecording) "LÕPETA SALVESTUS" else "SALVESTA UUS") }

            Spacer(modifier = Modifier.height(15.dp))
        }

        Text("Kordused:", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            LoopOptionButton("1x", selectedLoopCount == 1) { selectedLoopCount = 1; onLoopCountChange(1) }
            LoopOptionButton("5x", selectedLoopCount == 5) { selectedLoopCount = 5; onLoopCountChange(5) }
            LoopOptionButton("∞", selectedLoopCount == -1) { selectedLoopCount = -1; onLoopCountChange(-1) }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text("Kiirus:", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SpeedOptionButton("0.5x", selectedSpeed == 0.5f) { selectedSpeed = 0.5f; onSpeedChange(0.5f) }
            SpeedOptionButton("1.0x", selectedSpeed == 1.0f) { selectedSpeed = 1.0f; onSpeedChange(1.0f) }
            SpeedOptionButton("1.5x", selectedSpeed == 1.5f) { selectedSpeed = 1.5f; onSpeedChange(1.5f) }
            SpeedOptionButton("2.0x", selectedSpeed == 2.0f) { selectedSpeed = 2.0f; onSpeedChange(2.0f) }
        }

        Spacer(modifier = Modifier.height(10.dp))
        HorizontalDivider()

        Row(modifier = Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 5.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            if (isSelectionMode && selectedFiles.isNotEmpty()) {
                if (playingFileName.isNotEmpty()) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(modifier = Modifier.weight(1f), onClick = { if (isPaused) { onResumePlay(); isPaused = false } else { onPausePlay(); isPaused = true } }) { Text(if (isPaused) "JÄTKA" else "PAUS") }
                        Button(modifier = Modifier.weight(1f), onClick = { onStopPlay(); onPlayingFileNameChange(""); isPaused = false }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("KATKESTA") }
                    }
                } else {
                    Button(onClick = {
                        val orderedPlaylist = selectedFiles.toList()
                        val fullFileName = orderedPlaylist.firstOrNull()?.name ?: ""
                        onPlayingFileNameChange(fullFileName)
                        isPaused = false
                        onStartPlaylist(orderedPlaylist, selectedLoopCount, selectedSpeed) { onPlayingFileNameChange(""); isPaused = false }
                    }, modifier = Modifier.fillMaxWidth()) { Text("MÄNGI ${selectedFiles.size} VALITUT") }
                }
            } else {
                Text("Failid ($currentCategory):", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                if (!isSelectionMode) {
                    Button(onClick = { filePickerLauncher.launch("audio/*") }, modifier = Modifier.height(35.dp)) { Text("+ Lisa fail") }
                }
            }
        }

        LazyColumn(modifier = Modifier.fillMaxSize().padding(top = 8.dp), contentPadding = PaddingValues(bottom = 150.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(recordingItems) { item ->
                val isThisPlaying = (playingFileName == item.name)
                val isSelected = selectedFiles.contains(item.file)

                val cardColor = when {
                    isThisPlaying -> MaterialTheme.colorScheme.primaryContainer
                    isSelected -> MaterialTheme.colorScheme.surfaceVariant
                    else -> MaterialTheme.colorScheme.surface
                }
                val borderStroke = if (isThisPlaying) BorderStroke(3.dp, MaterialTheme.colorScheme.primary) else null

                Card(
                    colors = CardDefaults.cardColors(containerColor = cardColor),
                    border = borderStroke,
                    elevation = CardDefaults.cardElevation(defaultElevation = if(isThisPlaying) 6.dp else 1.dp),
                    modifier = Modifier.fillMaxWidth().clickable {
                        if (isSelectionMode) {
                            if (selectedFiles.contains(item.file)) selectedFiles.remove(item.file) else selectedFiles.add(item.file)
                        } else if (!isThisPlaying) {
                            if (playingFileName.isNotEmpty()) onStopPlay()
                            onPlayingFileNameChange(item.name)
                            isPaused = false
                            onStartPlaylist(listOf(item.file), selectedLoopCount, selectedSpeed) { onPlayingFileNameChange(""); isPaused = false }
                        }
                    }
                ) {
                    Row(modifier = Modifier.padding(12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        if (isSelectionMode) {
                            val selectionOrder = selectedFiles.indexOf(item.file) + 1
                            SelectableCheckbox(isSelected = isSelected, selectionOrder = selectionOrder, onToggle = { if (selectedFiles.contains(item.file)) selectedFiles.remove(item.file) else selectedFiles.add(item.file) })
                        } else if (isThisPlaying) {
                            IconButton(onClick = { onStopPlay(); onPlayingFileNameChange("") }) { StopIcon() }
                        } else {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Icon(Icons.Default.KeyboardArrowUp, "Üles", modifier = Modifier.size(28.dp).clip(CircleShape).clickable { onReorderFile(item.file, -1) }.padding(2.dp))
                                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp).clickable {
                                    if (playingFileName.isNotEmpty()) onStopPlay()
                                    onPlayingFileNameChange(item.name)
                                    isPaused = false
                                    onStartPlaylist(listOf(item.file), selectedLoopCount, selectedSpeed) { onPlayingFileNameChange(""); isPaused = false }
                                }) {
                                    Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.PlayArrow, "Mängi", tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(32.dp)) }
                                }
                                Icon(Icons.Default.KeyboardArrowDown, "Alla", modifier = Modifier.size(28.dp).clip(CircleShape).clickable { onReorderFile(item.file, 1) }.padding(2.dp))
                            }
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = item.name.substringBeforeLast("."),
                                fontWeight = if (isThisPlaying) FontWeight.Black else FontWeight.Bold,
                                fontSize = 16.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                lineHeight = 20.sp,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = if (isThisPlaying) { if (isPaused) "PAUS" else currentTimeString } else { item.durationString },
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (!isSelectionMode && !isThisPlaying) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.AutoMirrored.Filled.ArrowForward, "Liiguta", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.clickable { itemToModify = item; showMoveFileDialog = true }.padding(6.dp).size(20.dp))
                                        Icon(Icons.Default.Edit, "Muuda", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.clickable { itemToModify = item; showFileManageDialog = true }.padding(6.dp).size(20.dp))
                                        Icon(Icons.Default.Share, "Jaga", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.clickable { onShareFile(item) }.padding(6.dp).size(20.dp))
                                        Icon(Icons.Default.Delete, "Kustuta", tint = MaterialTheme.colorScheme.error, modifier = Modifier.clickable { onDeleteFile(item) }.padding(6.dp).size(20.dp))
                                    }
                                }
                            }
                        }
                    }

                    if (isThisPlaying) {
                        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                            Slider(
                                value = currentProgress,
                                onValueChange = { onSeekTo(it) },
                                modifier = Modifier.height(20.dp),
                                colors = SliderDefaults.colors(thumbColor = MaterialTheme.colorScheme.primary, activeTrackColor = MaterialTheme.colorScheme.primary)
                            )
                            Text(
                                text = "$currentTimeString / ${item.durationString}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.align(Alignment.End).padding(top = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LoopOptionButton(text: String, isSelected: Boolean, onClick: () -> Unit) {
    val backgroundColor = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
    val contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary
    val border = if (isSelected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.primary)

    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = backgroundColor, contentColor = contentColor),
        border = border,
        shape = RoundedCornerShape(50),
        modifier = Modifier.height(35.dp)
    ) {
        Text(text, fontSize = 12.sp)
    }
}

@Composable
fun SpeedOptionButton(text: String, isSelected: Boolean, onClick: () -> Unit) {
    val backgroundColor = if (isSelected) MaterialTheme.colorScheme.secondary else Color.Transparent
    val contentColor = if (isSelected) MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.secondary
    val border = if (isSelected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.secondary)

    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = backgroundColor, contentColor = contentColor),
        border = border,
        shape = RoundedCornerShape(20),
        contentPadding = PaddingValues(horizontal = 8.dp),
        modifier = Modifier.height(30.dp)
    ) {
        Text(text, fontSize = 11.sp)
    }
}