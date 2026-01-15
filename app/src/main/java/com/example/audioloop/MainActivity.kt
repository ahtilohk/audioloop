package com.example.audioloop

import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.lazy.rememberLazyListState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.isActive

import android.media.MediaMetadataRetriever
import android.media.MediaExtractor
import android.media.MediaFormat
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.unit.IntSize
import android.Manifest
import android.content.BroadcastReceiver
import android.content.ContentValues
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
import android.provider.MediaStore
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
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import com.linc.audiowaveform.AudioWaveform
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
import androidx.compose.material.icons.filled.ArrowDropDown

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
import com.example.audioloop.ui.theme.AudioLoopTheme

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
            AudioLoopTheme {
                val coroutineScope = rememberCoroutineScope()
                var uiCategory by remember { mutableStateOf("General") }
                var categories by remember { mutableStateOf(listOf("General")) }
                var playingFileName by remember { mutableStateOf("") }
                var currentProgress by remember { mutableFloatStateOf(0f) }
                var currentTimeString by remember { mutableStateOf("00:00") }
                var savedItems by remember { mutableStateOf<List<RecordingItem>>(emptyList()) }
                var playbackSpeed by remember { mutableFloatStateOf(1.0f) }
                var loopMode by remember { mutableIntStateOf(-1) }
                var isShadowingMode by remember { mutableStateOf(false) }

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
                    com.example.audioloop.ui.AudioLoopMainScreen(
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
                           // Use providers to access fresh state
                            playPlaylist(files, 0, { loopMode }, { playbackSpeed }, { isShadowingMode }, { playingFileName = it }, onComplete)
                        },
                        onPlaylistUpdate = { },
                        onSpeedChange = { speed -> playbackSpeed = speed; setPlaybackSpeed(speed) },
                        onLoopCountChange = { loops -> loopMode = loops },
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
                                    val newFile = files.firstOrNull()?.file // Eeldame et sorteeritud kuupäeva järgi (uusim ees)
                                    if (newFile != null) precomputeWaveformAsync(coroutineScope, newFile)
                                }
                            }
                        },
                        selectedSpeed = playbackSpeed,
                        selectedLoopCount = loopMode,
                        isShadowing = isShadowingMode,
                        onShadowingChange = { isShadowingMode = it }
                    )
                }
        }
    }

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
                        // Kustuta algne, nimeta temp ümber
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
                // Siia jõuavad ainult päriselt eksisteerivad failid.
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
        loopCountProvider: () -> Int,
        speedProvider: () -> Float,
        shadowingProvider: () -> Boolean,
        onNext: (String) -> Unit,
        onComplete: () -> Unit
    ) {
        if (allFiles.isEmpty() || currentIndex < 0) { onComplete(); return }
        
        // Dynamically fetch current settings
        val loopCount = loopCountProvider()
        val speed = speedProvider()
        val isShadowing = shadowingProvider()

        if (currentIndex >= allFiles.size) {
            // Loop check
            if (loopCount == -1) playPlaylist(allFiles, 0, loopCountProvider, speedProvider, shadowingProvider, onNext, onComplete)
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
                setOnCompletionListener { 
                    if (isShadowing) {
                       // Shadowing Logic: Pause -> Repeat Same
                       val duration = it.duration.toLong()
                       val pauseDuration = if (duration < 15000L) duration else 5000L
                       
                       // Launch coroutine for delay
                       shadowingJob = launch(Dispatchers.Main) {
                           onNext("Pausing for repeat...") // Visual cue? Or keep filename? Let's keep filename but maybe logs.
                           delay(pauseDuration)
                           if (isActive) {
                               playPlaylist(allFiles, currentIndex, loopCountProvider, speedProvider, shadowingProvider, onNext, onComplete)
                           }
                       }
                    } else {
                       playPlaylist(allFiles, currentIndex + 1, loopCountProvider, speedProvider, shadowingProvider, onNext, onComplete)
                    }
                }
                setOnErrorListener { _, what, extra ->
                    Toast.makeText(this@MainActivity, "Playback error: $what / $extra", Toast.LENGTH_SHORT).show()
                    playPlaylist(allFiles, currentIndex + 1, loopCountProvider, speedProvider, shadowingProvider, onNext, onComplete)
                    true
                }
                prepareAsync()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Error opening file: ${e.message}", Toast.LENGTH_SHORT).show()
            playPlaylist(allFiles, currentIndex + 1, loopCountProvider, speedProvider, shadowingProvider, onNext, onComplete)
        }
    }

    private var shadowingJob: kotlinx.coroutines.Job? = null

    private fun stopPlaying() {
        shadowingJob?.cancel()
        shadowingJob = null
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
    }

    private fun pausePlaying() {
        shadowingJob?.cancel() // Cancel wait if pending
        shadowingJob = null
        mediaPlayer?.pause()
    }
    fun resumePlaying() { mediaPlayer?.start() }
    fun seekTo(pos: Float) { mediaPlayer?.let { if (it.duration > 0) it.seekTo((it.duration * pos).toInt()) } }
    fun setPlaybackSpeed(speed: Float) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            mediaPlayer?.let { if (it.isPlaying) it.playbackParams = it.playbackParams.setSpeed(speed) }
        }
    }
}
