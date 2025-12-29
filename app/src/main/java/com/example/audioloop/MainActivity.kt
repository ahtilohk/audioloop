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
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.unit.IntSize
import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.AudioAttributes
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
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalDensity
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

// --- ABIFUNKTSIOONID JA VAHEMÃ„LU ---
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
        if (!waveFile.exists()) return null
        val content = waveFile.readText()
        if (content.isBlank()) return null
        content.split(",").map { it.toInt() }
    } catch (e: Exception) { null }
}

private fun precomputeWaveformAsync(scope: CoroutineScope, file: java.io.File, fullBars: Int = 160) {
    val key = file.absolutePath
    if (waveformCache.containsKey(key)) return
    scope.launch(Dispatchers.IO) {
        try {
            val cached = loadWaveformFromDisk(file)
            if (cached != null && cached.isNotEmpty()) {
                withContext(Dispatchers.Main) { waveformCache[key] = cached }
                return@launch
            }
            val waveform = generateWaveform(file, numBars = fullBars)
            saveWaveformToDisk(file, waveform)
            withContext(Dispatchers.Main) { waveformCache[key] = waveform }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

fun sanitizeName(name: String): String {
    val sb = StringBuilder()
    for (c in name) {
        if (c == '/' || c == '\\' || c == '*' || c == '?' || c == '"' || c == '<' || c == '>' || c == '|') continue
        if (c == ':') sb.append('_') else sb.append(c)
    }
    return sb.toString().trim()
}

    // Generate waveform using MediaCodec (via WaveformGenerator)
    private fun generateWaveform(file: File, numBars: Int = 60): List<Int> {
        return WaveformGenerator.extractWaveform(file, numBars)
    }

    // --- LOCALE HELPERS ---
    fun setLocale(context: Context, languageCode: String): Context {
        val locale = java.util.Locale(languageCode)
        java.util.Locale.setDefault(locale)
        val config = android.content.res.Configuration()
        config.setLocale(locale)
        return context.createConfigurationContext(config)
    }
    
    fun saveLanguage(context: Context, lang: String) {
        val prefs = context.getSharedPreferences("AudioLoopPrefs", Context.MODE_PRIVATE)
        prefs.edit().putString("app_lang", lang).apply()
    }
    
    fun getSavedLanguage(context: Context): String {
        val prefs = context.getSharedPreferences("AudioLoopPrefs", Context.MODE_PRIVATE)
        return prefs.getString("app_lang", "et") ?: "et" // Default to Estonian
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
            Toast.makeText(this, "Permission required for recording", Toast.LENGTH_SHORT).show()
        }
    }

    private var mediaPlayer: MediaPlayer? = null
    private val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) Toast.makeText(this, "Permission granted", Toast.LENGTH_SHORT).show()
        else Toast.makeText(this, "Permission denied", Toast.LENGTH_SHORT).show()
    }

    @OptIn(ExperimentalFoundationApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as android.media.projection.MediaProjectionManager
        } catch (e: Exception) {
            e.printStackTrace()
            // Fallback or exit if strictly required? It's needed for VOOG.
            // We'll let it slide and handle nulls later if possible, but for now just catch to prevent immediate crash.
        }

        setContent {
            AppTheme {
                val coroutineScope = rememberCoroutineScope()
                var uiCategory by remember { mutableStateOf("General") }
                var categories by remember { mutableStateOf(listOf("General")) }
                var playingFileName by remember { mutableStateOf("") }
                var currentProgress by remember { mutableFloatStateOf(0f) }
                var currentTimeString by remember { mutableStateOf("00:00") }
                var savedItems by remember { mutableStateOf<List<RecordingItem>>(emptyList()) }

                val context = LocalContext.current
                // Force English always as per user request
                val currentLanguage = "en" 
                
                // Force update on language change can be handled by recreation, avoiding Context wrapper issues.
                // key(currentLanguage) {
                     // CompositionLocalProvider causes issues on API 34+ with Dialogs/Surface
                     // So we use standard context.
                        // All UI content here
                        // ...

                // BROADCAST RECEIVER
                DisposableEffect(Unit) {
                    val receiver = object : BroadcastReceiver() {
                        override fun onReceive(context: Context?, intent: Intent?) {
                            if (intent?.action == RecordingService.ACTION_RECORDING_SAVED) {
                                coroutineScope.launch(Dispatchers.IO) {
                                    try {
                                        val ctx = context ?: return@launch
                                        val firstTry = getSavedRecordings(uiCategory, ctx.filesDir)
                                        withContext(Dispatchers.Main) { savedItems = firstTry }
                                        delay(500)
                                        val secondTry = getSavedRecordings(uiCategory, ctx.filesDir)
                                        val newFile = secondTry.firstOrNull()?.file
                                        if (newFile != null) {
                                            precomputeWaveformAsync(this, newFile)
                                            // Auto-Export Logic (DISABLED to fix System UI ANR)
                                            // exportFileToMusic(newFile, uiCategory)
                                            // withContext(Dispatchers.Main) {
                                            //    Toast.makeText(ctx, "Saved & Copied to Music/AudioLoop", Toast.LENGTH_SHORT).show()
                                            // }
                                        }
                                        withContext(Dispatchers.Main) { savedItems = secondTry }
                                    } catch (e: Exception) { e.printStackTrace() }
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
                        // Update categories
                        val realDirs = filesDir.listFiles()?.filter { it.isDirectory }?.map { it.name } ?: emptyList()
                        val savedOrder = loadCategoryOrder()
                        
                        val newOrder = ArrayList<String>()
                        // 1. Add known ones in order
                        savedOrder.forEach { if (realDirs.contains(it)) newOrder.add(it) }
                        // 2. Add new ones
                        realDirs.forEach { if (!newOrder.contains(it)) newOrder.add(it) }
                        // 3. Ensure General exists and is first if list empty (or fallback)
                        if (!newOrder.contains("General")) newOrder.add(0, "General") 
                        
                        // Move General to top if it's not (Optional, but good practice for "Default")
                        // But user might want to reorder it. Let's strictly follow saved logic BUT ensure it exists.
                        
                        withContext(Dispatchers.Main) { categories = newOrder }

                        // Update items
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
                        categories = categories,
                        currentCategory = uiCategory,
                        currentProgress = currentProgress,
                        currentTimeString = currentTimeString,
                        playingFileName = playingFileName,
                        onPlayingFileNameChange = { playingFileName = it },
                        onCategoryChange = { newCat -> uiCategory = newCat },
                        onAddCategory = { catName ->
                            val dir = File(filesDir, catName)
                            if (!dir.exists()) dir.mkdirs()
                            
                            // Add to list and save
                            val newCats = categories.toMutableList()
                            if (!newCats.contains(catName)) {
                                newCats.add(catName)
                                categories = newCats
                                saveCategoryOrder(newCats)
                            }
                            uiCategory = catName
                        },
                        onRenameCategory = { oldName, newName ->
                            renameCategory(oldName, newName)
                            if (uiCategory == oldName) uiCategory = newName
                            
                            // Update order list
                            val newCats = categories.toMutableList()
                            val idx = newCats.indexOf(oldName)
                            if (idx != -1) {
                                newCats[idx] = newName
                                categories = newCats
                                saveCategoryOrder(newCats)
                            }
                            
                            savedItems = getSavedRecordings(uiCategory, filesDir)
                        },
                        onDeleteCategory = { catName ->
                            deleteCategory(catName)
                            // Update list
                            val newCats = categories.toMutableList()
                            newCats.remove(catName)
                            categories = newCats
                            saveCategoryOrder(newCats)
                            
                            uiCategory = "General"
                        },
                        onReorderCategory = { cat, dir -> 
                            val idx = categories.indexOf(cat)
                            if (idx != -1) {
                                val newIdx = idx + dir
                                if (newIdx in 0 until categories.size) {
                                    val mutable = categories.toMutableList()
                                    java.util.Collections.swap(mutable, idx, newIdx)
                                    categories = mutable
                                    saveCategoryOrder(mutable)
                                }
                            }
                        },
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
                        onTrimFile = { file, start, end, replace ->
                            trimAudioFile(file, start, end, replace) {
                                savedItems = getSavedRecordings(uiCategory, filesDir)
                                if (replace) precomputeWaveformAsync(coroutineScope, file)
                                else {
                                    // Uus fail tekkis, leiame selle
                                    val files = getSavedRecordings(uiCategory, filesDir)
                                    val newFile = files.firstOrNull()?.file // Eeldame et sorteeritud kuupÃ¤eva jÃ¤rgi (uusim ees)
                                    if (newFile != null) precomputeWaveformAsync(coroutineScope, newFile)
                                }
                            }
                        }
                    )
                }
        }
    }

    }


    // --- FIX: getDuration mis kasutab MediaPlayerit kui Metadata ebaÃµnnestub ---
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
        // Varuplaan 2: MediaExtractor (KÃµige madalam tase, tÃ¶Ã¶tab ka siis kui metaandmed puuduvad)
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
        val finalName = if (category == "General") fileName else {
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
        val finalName = if (pendingCategory == "General") pendingRecordingName else {
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
            if (safeName.isBlank()) safeName = "untitled_audio"
            val finalFileName = if (extension.isNotEmpty()) "$safeName.$extension" else "$safeName.m4a"
            val folder = if (category == "General") filesDir else File(filesDir, category).apply { mkdirs() }
            val targetFile = File(folder, finalFileName)
            contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(targetFile).use { output -> input.copyTo(output) }
            }
            Toast.makeText(this, "Imported: $safeName", Toast.LENGTH_SHORT).show()
            
            // Start waveform computation immediately
            precomputeWaveformAsync(this, targetFile)
            
            // Note: UI needs to refresh separately if observing file system, 
            // or user navigates back/forth.
        } catch (e: Exception) { Toast.makeText(this, "Error importing", Toast.LENGTH_SHORT).show() }
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
            Toast.makeText(this, "Adding numbers to files...", Toast.LENGTH_SHORT).show()
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
        val targetDir = if (targetCategory == "General") filesDir else File(filesDir, targetCategory)
        if (!targetDir.exists()) targetDir.mkdirs()
        val targetFile = File(targetDir, file.name)
        if (file.renameTo(targetFile)) {
            Toast.makeText(this, "Moved", Toast.LENGTH_SHORT).show()
        } else {
            try {
                file.inputStream().use { input -> targetFile.outputStream().use { output -> input.copyTo(output) } }
                file.delete()
                Toast.makeText(this, "Moved", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) { Toast.makeText(this, "Error moving", Toast.LENGTH_SHORT).show() }
        }
    }
    
    // -- Category Order Helpers --
    private fun getCategoryOrderFile(): File {
        return File(filesDir, "category_order.json") // Simple JSON array
    }
    
    private fun saveCategoryOrder(categories: List<String>) {
        try {
            val jsonArray = org.json.JSONArray()
            categories.forEach { jsonArray.put(it) }
            getCategoryOrderFile().writeText(jsonArray.toString())
        } catch (e: Exception) { e.printStackTrace() }
    }
    
    private fun loadCategoryOrder(): List<String> {
        val list = mutableListOf<String>()
        try {
            val file = getCategoryOrderFile()
            if (file.exists() && file.length() > 0) {
                val content = file.readText()
                if (content.isNotBlank()) {
                    val array = org.json.JSONArray(content)
                    for (i in 0 until array.length()) {
                        list.add(array.getString(i))
                    }
                }
            }
        } catch (e: Exception) { e.printStackTrace() }
        return list
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
            startActivity(Intent.createChooser(intent, "Share audio via"))
        } catch (e: Exception) {
            Toast.makeText(this, "Error sharing: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun exportFileToMusic(file: File, category: String) {
        val resolver = applicationContext.contentResolver
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, file.name)
            put(MediaStore.MediaColumns.MIME_TYPE, "audio/mp4") // m4a is mp4 container
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Ensure unique internal folder vs public folder
                val relativePath = if (category == "General") "Music/AudioLoop" else "Music/AudioLoop/$category"
                put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }

        try {
            val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            }

            val uri = resolver.insert(collection, contentValues)
            if (uri != null) {
                resolver.openOutputStream(uri)?.use { output ->
                    file.inputStream().use { input -> input.copyTo(output) }
                }
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    contentValues.clear()
                    contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                    resolver.update(uri, contentValues, null, null)
                }
                // Optional: Notify user or just stay silent? 
                // User asked for "Auto export", so maybe a subtle indicator is enough.
                // We already show "Saved" toast from Service. Let's add specific logic in Receiver to Toast "Saved & Exported"
            }
        } catch (e: Exception) {
            e.printStackTrace()
            // Don't disturb user if export fails, it's a secondary feature
        }
    }

    private fun deleteCategory(catName: String) {
        if (catName == "General") return
        File(filesDir, catName).deleteRecursively()
    }

    private fun renameCategory(oldName: String, newName: String) {
        if (oldName == "General") return
        val oldDir = File(filesDir, oldName)
        val newDir = File(filesDir, sanitizeName(newName))
        oldDir.renameTo(newDir)
    }

    private fun trimAudioFile(file: File, start: Long, end: Long, replace: Boolean, onSuccess: () -> Unit) {
        stopPlaying()
        val ext = file.extension
        val isWav = ext.equals("wav", ignoreCase = true)
        val tempFile = File(file.parent, "temp_trim_${System.currentTimeMillis()}.$ext")
        
        launch(Dispatchers.IO) {
            val success = if (isWav) {
                WavAudioTrimmer.trimWav(file, tempFile, start, end)
            } else {
                AudioTrimmer.trimAudio(file, tempFile, start, end)
            }
            
            withContext(Dispatchers.Main) {
                if (success) {
                    if (replace) {
                        // Kustuta algne, nimeta temp Ã¼mber
                        if (file.delete()) {
                            tempFile.renameTo(file)
                            waveformCache.remove(file.absolutePath)
                            getWaveformFile(file).delete()
                            onSuccess()
                        } else {
                            tempFile.delete()
                            Toast.makeText(this@MainActivity, "Could not replace original file!", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        // Incremental naming: File_trim_1.m4a, File_trim_2.m4a...
                        val originalName = file.nameWithoutExtension
                        // Strip existing "_trim_X" if present to avoid "Song_trim_1_trim_2"
                        val baseName = if (originalName.contains("_trim_")) {
                             originalName.substringBeforeLast("_trim_")
                        } else {
                             originalName
                        }
                        
                        var counter = 1
                        var newName = "${baseName}_trim_$counter.$ext"
                        var newFile = File(file.parent, newName)
                        
                        while (newFile.exists()) {
                            counter++
                            newName = "${baseName}_trim_$counter.$ext"
                            newFile = File(file.parent, newName)
                        }

                        if (tempFile.renameTo(newFile)) {
                            onSuccess()
                            Toast.makeText(this@MainActivity, "Saved: $newName", Toast.LENGTH_SHORT).show()
                        } else {
                            tempFile.delete()
                        }
                    }
                } else {
                    tempFile.delete()
                    Toast.makeText(this@MainActivity, "Error trimming (invalid format?)", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }



    private fun getSavedRecordings(category: String, rootDir: File): List<RecordingItem> {
        try {
            val targetDir = if (category == "General") rootDir else File(rootDir, category)
            if (!targetDir.exists()) return emptyList()
            val files = targetDir.listFiles { _, name ->
                name.endsWith(".m4a", ignoreCase = true) || name.endsWith(".mp3", ignoreCase = true) || name.endsWith(".wav", ignoreCase = true)
            } ?: return emptyList()

            return files.mapNotNull { file ->
                // Siia jÃµuavad ainult pÃ¤riselt eksisteerivad failid.
                // 00:00 tekib ainult siis kui MediaRecorder ei kirjuta andmeid (vt RecordingService parandust)
                val (aegTekst, aegNumber) = getDuration(file)
                RecordingItem(file = file, name = file.name, durationString = aegTekst, durationMillis = aegNumber)
            }.sortedByDescending { it.file.lastModified() }
        } catch (e: Exception) {
            e.printStackTrace()
            return emptyList()
        }
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
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )
                // Fallback FD logic
                val fis = java.io.FileInputStream(fileToPlay)
                setDataSource(fis.fd)
                fis.close()

                setOnPreparedListener { mp ->
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        try { mp.playbackParams = mp.playbackParams.setSpeed(speed) } catch (e: Exception) { }
                    }
                    mp.isLooping = false
                    mp.start()
                }
                setOnCompletionListener { playPlaylist(allFiles, currentIndex + 1, loopCount, speed, onNext, onComplete) }
                setOnErrorListener { _, what, extra ->
                    Toast.makeText(this@MainActivity, "Playback error: $what / $extra", Toast.LENGTH_SHORT).show()
                    playPlaylist(allFiles, currentIndex + 1, loopCount, speed, onNext, onComplete)
                    true
                }
                prepareAsync()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Error opening file: ${e.message}", Toast.LENGTH_SHORT).show()
            playPlaylist(allFiles, currentIndex + 1, loopCount, speed, onNext, onComplete)
        }
    }

    fun stopPlaying() { mediaPlayer?.release(); mediaPlayer = null }
    fun pausePlaying() { mediaPlayer?.pause() }
    fun resumePlaying() { mediaPlayer?.start() }
    fun seekTo(pos: Float) { mediaPlayer?.let { if (it.duration > 0) it.seekTo((it.duration * pos).toInt()) } }
    fun setPlaybackSpeed(speed: Float) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            mediaPlayer?.let { if (it.isPlaying) it.playbackParams = it.playbackParams.setSpeed(speed) }
        }
    }
}

// ... KUI SUL ON SIIN VEEL UI KOOD (AppTheme, AudioLoopApp jt), SIIS JÃ„TA SEE ALLES! ...
// Kui kustutasid kogemata Ã¤ra, siis anna teada â€“ saadan UI osa eraldi.

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

@Composable
fun LiveWaveformCard(
    currentFileName: String,
    durationMs: Long,
    amplitudes: List<Int>,
    onStop: () -> Unit
) {
    val durationText = String.format("%02d:%02d", 
        java.util.concurrent.TimeUnit.MILLISECONDS.toMinutes(durationMs),
        java.util.concurrent.TimeUnit.MILLISECONDS.toSeconds(durationMs) % 60
    )

    val orangeColor = Color(0xFFFF5722)
    val containerColor = Color(0xFFFFCCBC) // Light Orange

    Card(
        colors = CardDefaults.cardColors(containerColor = containerColor),
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(), 
                horizontalArrangement = Arrangement.SpaceBetween, 
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Recording...", color = orangeColor, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Text(
                        text = currentFileName, 
                        fontWeight = FontWeight.Bold, 
                        fontSize = 16.sp, 
                        color = Color(0xFFBF360C),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = durationText, 
                    fontSize = 24.sp, 
                    fontWeight = FontWeight.Bold, 
                    color = orangeColor,
                    modifier = Modifier.wrapContentWidth(Alignment.End)
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Live Waveform Canvas
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                    .clip(RoundedCornerShape(8.dp))
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val barWidth = 6.dp.toPx()
                    val gap = 2.dp.toPx()
                    val totalBars = (size.width / (barWidth + gap)).toInt()
                    
                    // VÃµtame tagantpoolt 'totalBars' arvu
                    val visibleAmplitudes = amplitudes.takeLast(totalBars)
                    
                    visibleAmplitudes.forEachIndexed { index, amp ->
                        // Normaalime (maxAmp on nt 32767, aga tavaline kÃµne on ~5000-15000)
                        // VÃµimendame visuaalselt
                        val normalizedHeight = (amp / 10000f).coerceIn(0.1f, 1f) * size.height
                        
                        // Joonistame paremalt vasakule
                        val x = size.width - ((visibleAmplitudes.size - 1 - index) * (barWidth + gap)) - barWidth
                        
                        val yStart = (size.height - normalizedHeight) / 2f
                        
                        drawLine(
                            color = orangeColor, // Warm Orange
                            start = Offset(x, yStart),
                            end = Offset(x, yStart + normalizedHeight),
                            strokeWidth = barWidth,
                            cap = StrokeCap.Round
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onStop, 
                colors = ButtonDefaults.buttonColors(containerColor = orangeColor), 
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("STOP RECORDING")
            }
        }
    }
}

// --- DIALOOGID JA KOMPONENDID --- //

// --- HELIFUNKTSIOONID SIIN ---

@Composable
fun TrimAudioDialog(
    file: File,
    durationMs: Long,
    onConfirm: (Long, Long, Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var range by remember { mutableStateOf(0f..durationMs.toFloat()) }
    val context = LocalContext.current
    
    // Preview Player State
    var previewPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var isPreviewing by remember { mutableStateOf(false) }
    var currentPos by remember { mutableLongStateOf(0L) } // Track playback position
    val scope = rememberCoroutineScope()

    DisposableEffect(Unit) {
        onDispose {
            previewPlayer?.release()
            previewPlayer = null
        }
    }
    
    // Progress Timer
    LaunchedEffect(isPreviewing) {
        if (isPreviewing) {
            while(isPreviewing && previewPlayer != null) {
                try {
                    if (previewPlayer!!.isPlaying) {
                        currentPos = previewPlayer!!.currentPosition.toLong()
                    }
                } catch(e:Exception){}
                delay(50) 
            }
        } else {
            currentPos = range.start.toLong() // Reset
        }
    }

    fun togglePreview() {
        if (isPreviewing) {
            previewPlayer?.stop()
            previewPlayer?.release()
            previewPlayer = null
            isPreviewing = false
        } else {
            // Check file validity
            if (!file.exists() || file.length() < 10) {
                Toast.makeText(context, "File not ready, wait a moment...", Toast.LENGTH_SHORT).show()
                return
            }
            try {
                // Copy to temp file to avoid lock/access issues
                val tempPreview = File(context.cacheDir, "preview_temp.m4a")
                file.copyTo(tempPreview, overwrite = true)
                
                // Use Uri for better compatibility
                val uri = android.net.Uri.fromFile(tempPreview)
                
                val mp = MediaPlayer().apply {
                    reset()
                    setDataSource(context, uri)
                    prepare()
                    seekTo(range.start.toInt())
                    start()
                }
                previewPlayer = mp
                isPreviewing = true
                currentPos = range.start.toLong()
                
                // Stop automatically at end of range
                // Use dynamic check loop to allow changing end time while playing
                scope.launch {
                   while (isPreviewing && previewPlayer == mp) {
                        try {
                           if (mp.isPlaying) {
                               currentPos = mp.currentPosition.toLong()
                           }
                           
                           // Check dynamic range
                           val end = range.endInclusive.toInt()
                           val current = mp.currentPosition
                           
                           if (current >= end) {
                               if (mp.isPlaying) {
                                   mp.pause()
                                   mp.seekTo(range.start.toInt())
                               }
                               isPreviewing = false
                               break
                           }
                        } catch(e:Exception){
                            e.printStackTrace()
                            break
                        }
                        delay(50)
                    }
                }
                
                mp.setOnCompletionListener { 
                    isPreviewing = false
                }
                mp.setOnErrorListener { _, what, extra ->
                    Toast.makeText(context, "Media player error: $what, $extra", Toast.LENGTH_SHORT).show()
                    isPreviewing = false
                    true
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(context, "Error previewing: ${e.message} (Size: ${file.length()})", Toast.LENGTH_LONG).show()
                isPreviewing = false
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically, 
                horizontalArrangement = Arrangement.Start, 
                modifier = Modifier.fillMaxWidth()
            ) {
                IconButton(onClick = { togglePreview() }) {
                    Icon(
                        if (isPreviewing) Icons.Default.Stop else Icons.Default.PlayArrow,
                        contentDescription = "Eelkuulamine",
                        tint = if (isPreviewing) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    )
                }
                
                Spacer(modifier = Modifier.width(8.dp))
                
                Column {
                    Text(
                        text = if (isPreviewing) "Playing..." else "Preview", 
                        fontSize = 12.sp, 
                        color = Color.Gray
                    )
                    Text(
                        text = if (isPreviewing) 
                            "${formatDuration(currentPos)} / ${formatDuration(range.endInclusive.toLong())}"
                        else 
                            "${formatDuration(range.start.toLong())} - ${formatDuration(range.endInclusive.toLong())}",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        },
        text = {
            Column {
                // Info text (Live Progress)
                if (!isPreviewing) {
                     Spacer(modifier = Modifier.height(24.dp)) 
                }

                // Waveform Visualization
                // Start Loading / Caching - Hoisted for Step Calculation
                val points = remember { waveformCache[file.absolutePath] ?: emptyList() }
                
                // Dynamic Step Calculation
                // Rule: If bar duration < 1s, use bar duration. Else use 1s.
                val stepSize = remember(points, durationMs) {
                    if (points.isNotEmpty()) {
                        val barDuration = durationMs.toDouble() / points.size
                        if (barDuration < 1000.0) barDuration.toLong().coerceAtLeast(10L) else 1000L
                    } else {
                        1000L
                    }
                }

                Box(modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp)
                    .background(Color.Black.copy(alpha = 0.05f))
                ) {
                    if (points.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("Loading waveform...", fontSize = 12.sp, color = Color.Gray)
                        }
                        LaunchedEffect(file) {
                            // Check cache again with absolute path
                            if (waveformCache[file.absolutePath] == null) {
                                val w = withContext(Dispatchers.IO) {
                                    WaveformGenerator.extractWaveform(file, 100)
                                }
                                if (w.isNotEmpty()) waveformCache[file.absolutePath] = w
                            }
                        }
                    } else {
                         Canvas(modifier = Modifier.fillMaxSize()) {
                            val barWidth = size.width / points.size
                            
                            // Dynamic Normalization
                            val rawMax = points.maxOfOrNull { kotlin.math.abs(it) } ?: 1
                            val maxAmp = if (rawMax > 100) rawMax.toFloat() else 100f
                            
                            val startX = (range.start / durationMs) * size.width
                            val endX = (range.endInclusive / durationMs) * size.width
                            
                            // Dimming
                            drawRect(
                                color = Color(0xFFFF5722).copy(alpha = 0.1f),
                                topLeft = Offset(0f, 0f),
                                size = androidx.compose.ui.geometry.Size(startX, size.height)
                            )
                            drawRect(
                                color = Color(0xFFFF5722).copy(alpha = 0.1f),
                                topLeft = Offset(endX, 0f),
                                size = androidx.compose.ui.geometry.Size(size.width - endX, size.height)
                            )

                            // Bars
                            points.forEachIndexed { index, amp ->
                                val x = index * barWidth
                                val height = (kotlin.math.abs(amp) / maxAmp) * size.height
                                val y = (size.height - height) / 2
                                val centerX = x + barWidth / 2
                                
                                val isSelected = centerX >= startX && centerX <= endX
                                val color = if (isSelected) Color(0xFFFF5722) else Color(0xFFFF5722).copy(alpha = 0.3f)
                                
                                drawLine(
                                    color = color,
                                    start = Offset(centerX, y),
                                    end = Offset(centerX, y + height),
                                    strokeWidth = barWidth * 0.8f,
                                    cap = StrokeCap.Round
                                )
                            }
                            
                            // Playback Progress Line
                            if (isPreviewing) {
                                val progressX = (currentPos.toFloat() / durationMs) * size.width
                                drawLine(
                                    color = Color.Red,
                                    start = Offset(progressX, 0f),
                                    end = Offset(progressX, size.height),
                                    strokeWidth = 2.dp.toPx()
                                )
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // --- SLIDER CONTROLS ---
                // Custom implementation for large touch targets
                // --- SLIDER CONTROLS ---
                // Custom implementation for large touch targets
                var sliderWidth by remember { mutableFloatStateOf(1f) }
                
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp)
                        .onGloballyPositioned { coordinates ->
                            val s = coordinates.size
                            sliderWidth = s.width.toFloat()
                        }
                ) {
                    val width = sliderWidth
                    val density = LocalDensity.current
                    
                    // Internal padding to ensure handles at 0/100% are fully reachable and not clipped
                    val sidePadding = with(density) { 24.dp.toPx() }
                    val hitThreshold = with(density) { 60.dp.toPx() }
                    val availableWidth = width - (sidePadding * 2)
                    
                    // Capture latest values for the closure
                    val currentRange by rememberUpdatedState(range)
                    val currentDuration by rememberUpdatedState(durationMs)
                    val currentSidePadding by rememberUpdatedState(sidePadding)
                    val currentAvailableWidth by rememberUpdatedState(availableWidth)
                    val currentHitThreshold by rememberUpdatedState(hitThreshold)
                    
                    // Visual positions
                    val startX = sidePadding + (range.start / durationMs) * availableWidth
                    val endX = sidePadding + (range.endInclusive / durationMs) * availableWidth
                    
                    var isDraggingStart by remember { mutableStateOf(false) }
                    var isDraggingEnd by remember { mutableStateOf(false) }
                    
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            // CRITICAL Slider Fix: Use `width` as key to reset pointerInput when layout changes
                            .pointerInput(width) {
                                detectDragGestures(
                                    onDragStart = { offset ->
                                        // 1. Re-calculate positions using LATEST state
                                        val pad = currentSidePadding
                                        val avW = currentAvailableWidth
                                        val curDur = currentDuration.toFloat()
                                        val curR = currentRange
                                        val thresh = currentHitThreshold
                                        
                                        // If width is valid
                                        val curAvW: Float = avW
                                        if (curAvW > 0.1f) {
                                            val sX = pad + (curR.start / curDur) * avW
                                            val eX = pad + (curR.endInclusive / curDur) * avW
                                            
                                            // 2. Find Closest Handle
                                            val distStart = kotlin.math.abs(offset.x - sX)
                                            val distEnd = kotlin.math.abs(offset.x - eX)
                                            
                                            // Prioritize dragging the handle if we are close enough
                                            if (distStart < thresh && distEnd < thresh) {
                                                if (distStart <= distEnd) isDraggingStart = true else isDraggingEnd = true
                                            } else if (distStart < thresh) {
                                                isDraggingStart = true
                                            } else if (distEnd < thresh) {
                                                isDraggingEnd = true
                                            }
                                        }
                                    },
                                    onDragEnd = { isDraggingStart = false; isDraggingEnd = false },
                                    onDragCancel = { isDraggingStart = false; isDraggingEnd = false },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        val dx = dragAmount.x
                                        val pad = currentSidePadding
                                        val avW = currentAvailableWidth
                                        val curDur = currentDuration.toFloat()
                                        val curR = currentRange
                                        
                                        // Revert math: timeDelta = (dx / availableWidth) * duration
                                        val curAvW: Float = currentAvailableWidth
                                        if (curAvW > 0.1f) {
                                            val dxF: Float = dx
                                            val curDurF: Float = currentDuration.toFloat()
                                            val timeDelta: Float = (dxF / curAvW) * curDurF
                                            
                                            if (isDraggingStart) {
                                                val newStart = (curR.start + timeDelta).coerceIn(0f, curR.endInclusive - 1000f)
                                                range = newStart..curR.endInclusive
                                            } else if (isDraggingEnd) {
                                                val newEnd = (curR.endInclusive + timeDelta).coerceIn(curR.start + 1000f, curDur.toFloat())
                                                range = curR.start..newEnd
                                            }
                                        }
                                    }
                                )
                            }
                    ) {
                        // Track Base
                        drawLine(
                            color = Color.Gray.copy(alpha=0.5f),
                            start = Offset(sidePadding, size.height/2),
                            end = Offset(size.width - sidePadding, size.height/2),
                            strokeWidth = 4.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                        
                        // Active Track
                        drawLine(
                            color = Color(0xFFFF5722),
                            start = Offset(startX, size.height/2),
                            end = Offset(endX, size.height/2),
                            strokeWidth = 4.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                        
                        // Handles
                        fun drawCustomHandle(x: Float) {
                            drawCircle(
                                color = Color.White,
                                radius = 16.dp.toPx(), // Visual size 32dp
                                center = Offset(x, size.height/2),
                                style = Fill
                            )
                            drawCircle(
                                color = Color(0xFFFF5722),
                                radius = 16.dp.toPx(),
                                center = Offset(x, size.height/2),
                                style = Stroke(width = 3.dp.toPx())
                            )
                        }
                        
                        drawCustomHandle(startX)
                        drawCustomHandle(endX)
                    }
                } // End BoxWithConstraints
                

                Spacer(modifier = Modifier.height(16.dp))

                // Unified Control Row (Algus ... LÃµpp)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Start Group
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(
                            onClick = { val n = (range.start - stepSize).coerceAtLeast(0f); range = n..range.endInclusive },
                            contentPadding = PaddingValues(0.dp),
                            modifier = Modifier.size(40.dp)
                        ) { Text("<", fontSize = 18.sp) }
                        
                        Text("Start", modifier = Modifier.padding(horizontal = 8.dp))
                        
                        OutlinedButton(
                            onClick = { val n = (range.start + stepSize).coerceAtMost(range.endInclusive - stepSize); range = n..range.endInclusive },
                            contentPadding = PaddingValues(0.dp),
                            modifier = Modifier.size(40.dp)
                        ) { Text(">", fontSize = 18.sp) }
                    }

                    // End Group
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(
                            onClick = { val n = (range.endInclusive - stepSize).coerceAtLeast(range.start + stepSize); range = range.start..n },
                            contentPadding = PaddingValues(0.dp),
                            modifier = Modifier.size(40.dp)
                        ) { Text("<", fontSize = 18.sp) }
                        
                        Text("End", modifier = Modifier.padding(horizontal = 8.dp))
                        
                        OutlinedButton(
                            onClick = { val n = (range.endInclusive + stepSize).coerceAtMost(durationMs.toFloat()); range = range.start..n },
                            contentPadding = PaddingValues(0.dp),
                            modifier = Modifier.size(40.dp)
                        ) { Text(">", fontSize = 18.sp) }
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(formatDuration(range.start.toLong()))
                    Text(formatDuration(range.endInclusive.toLong()))
                }
                Text("Duration: ${formatDuration((range.endInclusive - range.start).toLong())}", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
            }
        },
        confirmButton = {
            Column(horizontalAlignment = Alignment.End) {
                Button(
                    onClick = { onConfirm(range.start.toLong(), range.endInclusive.toLong(), false) }
                ) { Text("Save New File") }
                
                Spacer(modifier = Modifier.height(4.dp))
                
                OutlinedButton(
                    onClick = { onConfirm(range.start.toLong(), range.endInclusive.toLong(), true) }
                ) { Text("Replace Original") }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

fun formatDuration(ms: Long): String {
    val m = java.util.concurrent.TimeUnit.MILLISECONDS.toMinutes(ms)
    val s = java.util.concurrent.TimeUnit.MILLISECONDS.toSeconds(ms) % 60
    return String.format("%02d:%02d", m, s)
}


@Composable
fun RenameDialog(currentName: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var textState by remember { mutableStateOf(TextFieldValue(text = currentName, selection = TextRange(currentName.length))) }
    val focusRequester = remember { FocusRequester() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename") },
        text = {
            OutlinedTextField(
                value = textState,
                onValueChange = { textState = it },
                label = { Text("New name") },
                singleLine = true,
                modifier = Modifier.focusRequester(focusRequester),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done)
            )
            LaunchedEffect(Unit) { focusRequester.requestFocus() }
        },
        confirmButton = { Button(onClick = {
            val safeText = sanitizeName(textState.text)
            onConfirm(safeText)
        }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun DeleteConfirmDialog(title: String, text: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { Button(onClick = onConfirm, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("Delete") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
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
        title = { Text("Manage Category") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                OutlinedTextField(
                    value = textState,
                    onValueChange = { textState = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.focusRequester(focusRequester),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done)
                )
                LaunchedEffect(Unit) { focusRequester.requestFocus() }

                Spacer(modifier = Modifier.height(24.dp))
                Text("Position in list:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                                Text(">", fontSize = 14.sp, color = MaterialTheme.colorScheme.outline)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Text("Change order:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    Button(
                        onClick = onMoveLeft,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                        enabled = allCategories.indexOf(categoryName) > 1
                    ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Left") }

                    Button(
                        onClick = onMoveRight,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                        enabled = allCategories.indexOf(categoryName) < allCategories.size - 1
                    ) { Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Right") }
                }

                if (categoryName != "General") {
                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = onDelete, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error), modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Delete, contentDescription = null); Spacer(modifier = Modifier.width(8.dp)); Text("Delete category")
                    }
                }
            }
        },
        confirmButton = { Button(onClick = {
            val safeText = sanitizeName(textState.text)
            onRename(safeText)
        }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
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
        title = { Text("Manage file") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                OutlinedTextField(
                    value = textState,
                    onValueChange = { textState = it },
                    label = { Text("Name") },
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
                    Text("\u2702", fontSize = 18.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Trim")
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val safeText = sanitizeName(textState.text)
                onRename(safeText)
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun MoveFileDialog(categories: List<String>, onDismiss: () -> Unit, onSelect: (String) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select new category") },
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
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
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
    onTrimFile: (File, Long, Long, Boolean) -> Unit
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
    var recordingToDelete by remember { mutableStateOf<RecordingItem?>(null) }
    var recordingToTrim by remember { mutableStateOf<RecordingItem?>(null) } // Trim state
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    fun saveToDownloads(context: Context, file: File) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = android.content.ContentValues()
            values.put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, file.name)
            values.put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "audio/mp4")
            values.put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS)
            val resolver = context.contentResolver
            try {
                val uri = resolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                if (uri != null) {
                    resolver.openOutputStream(uri)?.use { output ->
                        file.inputStream().use { input -> input.copyTo(output) }
                    }
                    Toast.makeText(context, "Salvestatud: Downloads/${file.name}", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Viga: Ei saanud faili luua", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(context, "Viga salvestamisel: ${e.message}", Toast.LENGTH_LONG).show()
            }
        } else {
            Toast.makeText(context, "Supported only on Android 10+", Toast.LENGTH_SHORT).show()
        }
    }

    var liveTimeName by remember { mutableStateOf("") }
    fun generateTimeName(prefix: String): String {
        val sdf = java.text.SimpleDateFormat("dd.MM.yy HH:mm:ss", java.util.Locale.getDefault())
        return "$prefix ${sdf.format(java.util.Date())}"
    }

    val liveAmplitudes = remember { mutableStateListOf<Int>() }
    var liveDurationMs by remember { mutableLongStateOf(0L) }
    
    // Live Waveform Receiver
    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == RecordingService.ACTION_AMPLITUDE_UPDATE) {
                    val amp = intent.getIntExtra(RecordingService.EXTRA_AMPLITUDE, 0)
                    val dur = intent.getLongExtra(RecordingService.EXTRA_DURATION_MS, 0L)
                    liveAmplitudes.add(amp)
                    if (liveAmplitudes.size > 200) liveAmplitudes.removeAt(0)
                    liveDurationMs = dur
                }
            }
        }
        val filter = IntentFilter(RecordingService.ACTION_AMPLITUDE_UPDATE)
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        onDispose { context.unregisterReceiver(receiver) }
    }

    val modeStream = stringResource(R.string.mode_stream)
    val modeSpeech = stringResource(R.string.mode_speech)
    LaunchedEffect(isRawMode) {
        while (true) {
            val prefix = if (isRawMode) modeStream else modeSpeech
            liveTimeName = generateTimeName(prefix)
            delay(1000)
        }
    }

    val filePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) onImportFile(uri)
    }

    if (showTrimDialog && itemToModify != null) {
        val file = itemToModify!!.file
        
        TrimAudioDialog(
            file = file,
            durationMs = itemToModify!!.durationMillis,
            onDismiss = { showTrimDialog = false },
            onConfirm = { start, end, replace -> onTrimFile(file, start, end, replace) }
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
        DeleteConfirmDialog(title = "Delete category?", text = "This will delete folder '$categoryToManage' and all its files.", onDismiss = { showDeleteConfirmDialog = false }, onConfirm = { onDeleteCategory(categoryToManage); showDeleteConfirmDialog = false })
    }
    if (showMoveFileDialog && itemToModify != null) {
        MoveFileDialog(categories = categories, onDismiss = { showMoveFileDialog = false }, onSelect = { targetCat -> onMoveFile(itemToModify!!, targetCat); showMoveFileDialog = false })
    }
    // Delete Confirmation Dialog
    if (recordingToDelete != null) {
        DeleteConfirmDialog(
            title = "Delete file?",
            text = "Are you sure you want to delete file '${recordingToDelete!!.file.name}'?",
            onConfirm = {
                onDeleteFile(recordingToDelete!!)
                recordingToDelete = null
            },
            onDismiss = { recordingToDelete = null }
        )
    }

    if (recordingToTrim != null) {
        val file = recordingToTrim!!.file
        
        TrimAudioDialog(
           file = file,
           durationMs = recordingToTrim!!.durationMillis,
           onConfirm = { start, end, replace ->
               // Delegate to onTrimFile
               onTrimFile(file, start, end, replace)
               recordingToTrim = null
           },
           onDismiss = { recordingToTrim = null }
        )
    }

    Column(modifier = Modifier.fillMaxSize().padding(top = 50.dp, start = 16.dp, end = 16.dp, bottom = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.app_name), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                Spacer(modifier = Modifier.width(8.dp))
                
            }
            
            TextButton(onClick = {
                if (isSelectionMode) { isSelectionMode = false; selectedFiles.clear(); if (playingFileName.isNotEmpty()) { onStopPlay(); onPlayingFileNameChange("") } }
                else { isSelectionMode = true }
            }) {
                if (isSelectionMode) Text(stringResource(R.string.btn_cancel), fontSize = 12.sp, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                else Text("SELECT PLAYLIST", fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
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
                            if (isSelected && category != "General") {
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(imageVector = Icons.Default.Edit, contentDescription = "Manage", tint = textColor, modifier = Modifier.size(16.dp).clickable { categoryToManage = category; showCategoryManageDialog = true })
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(onClick = { showAddCategoryDialog = true }, modifier = Modifier.size(30.dp).background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(50))) {
                Icon(Icons.Default.Add, contentDescription = "Add", modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
            }
        }

        if (isRecording) {
            LiveWaveformCard(
                currentFileName = if (recordingName.isBlank()) liveTimeName else recordingName,
                durationMs = liveDurationMs,
                amplitudes = liveAmplitudes,
                onStop = { 
                    isRecording = false
                    onStopRecord()
                    recordingName = ""
                    liveAmplitudes.clear()
                    liveDurationMs = 0L
                    focusManager.clearFocus()
                }
            )
            Spacer(modifier = Modifier.height(15.dp))
        } else if (!isSelectionMode) {
            OutlinedTextField(
                value = recordingName,
                onValueChange = { recordingName = it },
                label = { Text(stringResource(R.string.menu_rename)) },
                supportingText = {
                    if (recordingName.isBlank()) {
                        Text(text = "Default: $liveTimeName", color = MaterialTheme.colorScheme.primary)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                trailingIcon = {
                    if (recordingName.isNotEmpty()) {
                        IconButton(onClick = { recordingName = "" }) { Icon(Icons.Default.Clear, contentDescription = "Clear") }
                    }
                }
            )
            Spacer(modifier = Modifier.height(4.dp))
 
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    FilterChip(
                        selected = !isRawMode,
                        onClick = { isRawMode = false },
                        label = { Text(stringResource(R.string.mode_speech)) },
                        leadingIcon = { if (!isRawMode) Icon(Icons.Default.Person, null, modifier = Modifier.size(18.dp)) }
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    FilterChip(
                        selected = isRawMode,
                        onClick = { isRawMode = true },
                        label = { Text(stringResource(R.string.mode_stream)) },
                        leadingIcon = { if (isRawMode) Icon(Icons.Default.Star, null, modifier = Modifier.size(18.dp)) }
                    )
                }
            Spacer(modifier = Modifier.height(8.dp))
 
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .clip(RoundedCornerShape(32.dp))
                    .background(MaterialTheme.colorScheme.primary)
                    .clickable {
                        val finalName = if (recordingName.isBlank()) liveTimeName else recordingName
                        if (onStartRecord(finalName, isRawMode)) {
                            isRecording = true
                            liveAmplitudes.clear()
                            liveDurationMs = 0L
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .background(Color.Red, CircleShape) 
                            .border(1.dp, Color.White, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        stringResource(R.string.btn_record_new), 
                        color = MaterialTheme.colorScheme.onPrimary, 
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        letterSpacing = 1.sp
                    )
                }
            }
 
            Spacer(modifier = Modifier.height(15.dp))
        }

        Text("Repeats:", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            LoopOptionButton("1x", selectedLoopCount == 1) { selectedLoopCount = 1; onLoopCountChange(1) }
            LoopOptionButton("5x", selectedLoopCount == 5) { selectedLoopCount = 5; onLoopCountChange(5) }
            LoopOptionButton("\u221E", selectedLoopCount == -1) { selectedLoopCount = -1; onLoopCountChange(-1) }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text("Speed:", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                        Button(modifier = Modifier.weight(1f), onClick = { if (isPaused) { onResumePlay(); isPaused = false } else { onPausePlay(); isPaused = true } }) { Text(if (isPaused) "RESUME" else "PAUSE") }
                        Button(modifier = Modifier.weight(1f), onClick = { onStopPlay(); onPlayingFileNameChange(""); isPaused = false }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("CANCEL") }
                    }
                } else {
                    Button(onClick = {
                        val orderedPlaylist = selectedFiles.toList()
                        val fullFileName = orderedPlaylist.firstOrNull()?.name ?: ""
                        onPlayingFileNameChange(fullFileName)
                        isPaused = false
                        onStartPlaylist(orderedPlaylist, selectedLoopCount, selectedSpeed) { onPlayingFileNameChange(""); isPaused = false }
                    }, modifier = Modifier.fillMaxWidth()) { Text("PLAY ${selectedFiles.size} SELECTED") }
                }
            } else {
                Text("Files ($currentCategory):", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                if (!isSelectionMode) {
                    Button(onClick = { filePickerLauncher.launch("audio/*") }, modifier = Modifier.height(35.dp)) { Text("+ Add file") }
                }
            }
        }

        LazyColumn(modifier = Modifier.fillMaxSize().padding(top = 8.dp), contentPadding = PaddingValues(bottom = 150.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (recordingItems.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier.fillParentMaxSize().padding(top = 100.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(
                            imageVector = Icons.Default.GraphicEq,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.size(120.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "It's quiet here...",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Start recording a new clip!",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
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
                    modifier = Modifier.fillMaxWidth().combinedClickable(
                        onLongClick = {
                            if (!isSelectionMode) {
                                itemToModify = item
                                showFileManageDialog = true
                            }
                        },
                        onClick = {
                            if (isSelectionMode) {
                                if (selectedFiles.contains(item.file)) selectedFiles.remove(item.file) else selectedFiles.add(item.file)
                            } else if (!isThisPlaying) {
                                if (playingFileName.isNotEmpty()) onStopPlay()
                                onPlayingFileNameChange(item.name)
                                isPaused = false
                                onStartPlaylist(listOf(item.file), selectedLoopCount, selectedSpeed) { onPlayingFileNameChange(""); isPaused = false }
                            }
                        }
                    )
                ) {
                    Row(modifier = Modifier.padding(12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        if (isSelectionMode) {
                            val selectionOrder = selectedFiles.indexOf(item.file) + 1
                            SelectableCheckbox(isSelected = isSelected, selectionOrder = selectionOrder, onToggle = { if (selectedFiles.contains(item.file)) selectedFiles.remove(item.file) else selectedFiles.add(item.file) })
                        } else if (isThisPlaying) {
                            IconButton(onClick = { onStopPlay(); onPlayingFileNameChange("") }) { StopIcon() }
                        } else {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Icon(Icons.Default.KeyboardArrowUp, "Up", modifier = Modifier.size(28.dp).clip(CircleShape).clickable { onReorderFile(item.file, -1) }.padding(2.dp))
                                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp).clickable {
                                    if (playingFileName.isNotEmpty()) onStopPlay()
                                    onPlayingFileNameChange(item.name)
                                    isPaused = false
                                    onStartPlaylist(listOf(item.file), selectedLoopCount, selectedSpeed) { onPlayingFileNameChange(""); isPaused = false }
                                }) {
                                    Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.PlayArrow, "Play", tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(32.dp)) }
                                }
                                Icon(Icons.Default.KeyboardArrowDown, "Down", modifier = Modifier.size(28.dp).clip(CircleShape).clickable { onReorderFile(item.file, 1) }.padding(2.dp))
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
                                    text = if (isThisPlaying) { if (isPaused) "PAUSE" else currentTimeString } else { item.durationString },
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (!isSelectionMode && !isThisPlaying) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        // 1. Download (Keep Quick Access)
                                        Icon(
                                            Icons.Default.ArrowDownward, 
                                            contentDescription = "Download",
                                            tint = MaterialTheme.colorScheme.primary, 
                                            modifier = Modifier
                                                .clickable { saveToDownloads(context, item.file) }
                                                .padding(8.dp)
                                                .size(24.dp)
                                        )

                                        // 2. More Options Menu
                                        var showMenu by remember { mutableStateOf(false) }
                                        Box {
                                            Icon(
                                                Icons.Default.MoreVert,
                                                contentDescription = "Options",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier
                                                    .clickable { showMenu = true }
                                                    .padding(8.dp)
                                                    .size(24.dp)
                                            )
                                            DropdownMenu(
                                                expanded = showMenu,
                                                onDismissRequest = { showMenu = false }
                                            ) {
                                                DropdownMenuItem(
                                                    text = { Text(stringResource(R.string.menu_rename)) },
                                                    leadingIcon = { Icon(Icons.Default.Edit, null) },
                                                    onClick = { showMenu = false; itemToModify = item; showFileManageDialog = true }
                                                )
                                                DropdownMenuItem(
                                                    text = { Text(stringResource(R.string.menu_trim)) },
                                                    leadingIcon = { Icon(Icons.Default.ContentCut, null) },
                                                    onClick = { showMenu = false; recordingToTrim = item }
                                                )
                                                DropdownMenuItem(
                                                    text = { Text(stringResource(R.string.menu_move)) },
                                                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.ArrowForward, null) },
                                                    onClick = { showMenu = false; itemToModify = item; showMoveFileDialog = true }
                                                )
                                                DropdownMenuItem(
                                                    text = { Text(stringResource(R.string.menu_share)) },
                                                    leadingIcon = { Icon(Icons.Default.Share, null) },
                                                    onClick = { showMenu = false; onShareFile(item) }
                                                )
                                                HorizontalDivider()
                                                DropdownMenuItem(
                                                    text = { Text(stringResource(R.string.btn_delete), color = MaterialTheme.colorScheme.error) },
                                                    leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
                                                    onClick = { showMenu = false; recordingToDelete = item }
                                                )
                                            }
                                        }
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
