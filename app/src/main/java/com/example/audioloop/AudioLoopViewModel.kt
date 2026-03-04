package com.example.audioloop

import android.app.Application
import android.content.Context
import android.media.AudioAttributes
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.compose.runtime.mutableStateMapOf
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.audioloop.ui.theme.AppTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

/**
 * ViewModel for AudioLoop. Holds all UI state and business logic
 * that was previously scattered across MainActivity.
 *
 * Benefits:
 * - Survives configuration changes (rotation, theme switch)
 * - Testable without Android framework
 * - Clear separation of concerns
 */
class AudioLoopViewModel(application: Application) : AndroidViewModel(application) {

    private val ctx: Context get() = getApplication()
    private val filesDir: File get() = ctx.filesDir

    // ── State ──
    private val _uiState = MutableStateFlow(AudioLoopUiState())
    val uiState: StateFlow<AudioLoopUiState> = _uiState.asStateFlow()

    // Waveform cache (observable map for Compose)
    val waveformCache = mutableStateMapOf<String, List<Int>>()

    // ── Internal refs ──
    private var mediaPlayer: MediaPlayer? = null
    lateinit var mediaSessionManager: MediaSessionManager
        private set
    lateinit var practiceStats: PracticeStatsManager
        private set
    lateinit var coachEngine: CoachEngine
        private set
    private lateinit var playlistManager: PlaylistManager
    lateinit var driveBackupManager: DriveBackupManager
        private set

    private var shadowingJob: Job? = null
    private var sleepTimerJob: Job? = null
    private var progressJob: Job? = null
    private var sessionTimerJob: Job? = null
    private var sessionStartTimeMs: Long = 0L

    // ── Initialization ──

    fun initialize() {
        // Init managers
        practiceStats = PracticeStatsManager(ctx)
        coachEngine = CoachEngine(practiceStats)
        playlistManager = PlaylistManager(ctx)
        driveBackupManager = DriveBackupManager(ctx)

        // Init MediaSession
        mediaSessionManager = MediaSessionManager(
            context = ctx,
            onPlay = {
                if (_uiState.value.isPaused && mediaPlayer != null) {
                    resumePlaying()
                    mediaSessionManager.updatePlaybackState(isPlaying = true, isPaused = false)
                }
            },
            onPause = {
                if (mediaPlayer?.isPlaying == true) {
                    pausePlaying()
                    mediaSessionManager.updatePlaybackState(isPlaying = false, isPaused = true)
                }
            },
            onStop = {
                stopPlaying()
                _uiState.update { it.copy(playingFileName = "", currentlyPlayingPlaylistId = null) }
                mediaSessionManager.updatePlaybackState(isPlaying = false, isPaused = false)
                mediaSessionManager.abandonAudioFocus()
            }
        )
        mediaSessionManager.initialize()

        // Load persisted state
        val theme = getThemePref(ctx)
        val isCoachExpanded = getSmartCoachExpandedPref(ctx)

        // Init backup
        var signedIn = false
        var email = ""
        if (driveBackupManager.initFromLastAccount()) {
            signedIn = true
            email = driveBackupManager.getSignedInEmail() ?: ""
        }

        _uiState.update {
            it.copy(
                currentTheme = theme,
                isSmartCoachExpanded = isCoachExpanded,
                playlists = playlistManager.loadAll(),
                isBackupSignedIn = signedIn,
                backupEmail = email
            )
        }
        refreshPracticeStats()
    }

    // ── Category & File Management ──

    fun loadCategoriesAndFiles(category: String? = null) {
        val cat = category ?: _uiState.value.currentCategory
        viewModelScope.launch(Dispatchers.IO) {
            val realDirs = filesDir.listFiles()
                ?.filter { it.isDirectory && !it.name.startsWith(".") }
                ?.map { it.name } ?: emptyList()
            val savedOrder = loadCategoryOrder()

            val newOrder = ArrayList<String>()
            savedOrder.forEach { if (realDirs.contains(it)) newOrder.add(it) }
            realDirs.forEach { if (!newOrder.contains(it)) newOrder.add(it) }

            // Scan public storage categories
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                try {
                    val collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
                    val projection = arrayOf(MediaStore.Audio.Media.RELATIVE_PATH)
                    val selection = "${MediaStore.Audio.Media.RELATIVE_PATH} LIKE ?"
                    val selectionArgs = arrayOf("Music/AudioLoop/%")
                    ctx.contentResolver.query(collection, projection, selection, selectionArgs, null)?.use { cursor ->
                        val pathCol = cursor.getColumnIndex(MediaStore.Audio.Media.RELATIVE_PATH)
                        while (cursor.moveToNext()) {
                            val path = cursor.getString(pathCol) ?: continue
                            val parts = path.removePrefix("Music/AudioLoop/").trimEnd('/').split("/")
                            val categoryName = parts.firstOrNull()?.takeIf { it.isNotBlank() }
                            if (categoryName != null && !newOrder.contains(categoryName) && categoryName != "General") {
                                newOrder.add(categoryName)
                            }
                        }
                    }
                } catch (e: Exception) { e.printStackTrace() }
            }

            if (!newOrder.contains("General")) newOrder.add(0, "General")

            val items = getSavedRecordings(cat)
            items.forEach { precomputeWaveformAsync(it.file) }

            _uiState.update {
                it.copy(
                    categories = newOrder,
                    currentCategory = cat,
                    savedItems = items
                )
            }
        }
    }

    fun changeCategory(newCategory: String) {
        _uiState.update { it.copy(currentCategory = newCategory) }
        loadCategoriesAndFiles(newCategory)
    }

    fun addCategory(catName: String) {
        val dir = File(filesDir, catName)
        if (!dir.exists()) dir.mkdirs()
        val newCats = _uiState.value.categories.toMutableList()
        if (!newCats.contains(catName)) {
            newCats.add(catName)
            saveCategoryOrder(newCats)
        }
        _uiState.update { it.copy(categories = newCats, currentCategory = catName) }
        loadCategoriesAndFiles(catName)
    }

    fun renameCategory(oldName: String, newName: String) {
        if (oldName == "General") return
        val oldDir = File(filesDir, oldName)
        val newDir = File(filesDir, sanitizeName(newName))
        oldDir.renameTo(newDir)

        val newCats = _uiState.value.categories.toMutableList()
        val idx = newCats.indexOf(oldName)
        if (idx != -1) {
            newCats[idx] = newName
            saveCategoryOrder(newCats)
        }

        val currentCat = if (_uiState.value.currentCategory == oldName) newName else _uiState.value.currentCategory
        _uiState.update { it.copy(categories = newCats, currentCategory = currentCat) }
        refreshFileList()
    }

    fun deleteCategory(catName: String) {
        if (catName == "General") return
        File(filesDir, catName).deleteRecursively()
        val newCats = _uiState.value.categories.toMutableList()
        newCats.remove(catName)
        saveCategoryOrder(newCats)
        _uiState.update { it.copy(categories = newCats, currentCategory = "General") }
        loadCategoriesAndFiles("General")
    }

    fun reorderCategory(cat: String, direction: Int) {
        val cats = _uiState.value.categories.toMutableList()
        val idx = cats.indexOf(cat)
        if (idx != -1) {
            val newIdx = idx + direction
            if (newIdx in cats.indices) {
                java.util.Collections.swap(cats, idx, newIdx)
                saveCategoryOrder(cats)
                _uiState.update { it.copy(categories = cats) }
            }
        }
    }

    fun reorderCategories(newOrder: List<String>) {
        saveCategoryOrder(newOrder)
        _uiState.update { it.copy(categories = newOrder) }
    }

    // ── File Operations ──

    fun refreshFileList() {
        viewModelScope.launch(Dispatchers.IO) {
            val items = getSavedRecordings(_uiState.value.currentCategory)
            withContext(Dispatchers.Main) {
                _uiState.update { it.copy(savedItems = items) }
            }
        }
    }

    fun moveFile(item: RecordingItem, targetCategory: String) {
        val targetDir = if (targetCategory == "General") filesDir else File(filesDir, targetCategory)
        if (!targetDir.exists()) targetDir.mkdirs()

        val targetFile = File(targetDir, item.file.name)
        val noteFile = getNoteFile(item.file)
        val waveFile = getWaveformFile(item.file)

        if (item.file.renameTo(targetFile)) {
            if (noteFile.exists()) noteFile.renameTo(getNoteFile(targetFile))
            if (waveFile.exists()) waveFile.renameTo(getWaveformFile(targetFile))
            updateOrderAfterMove(item.file.name, _uiState.value.currentCategory, targetCategory)
            showSnackbar("Moved")
        } else {
            try {
                item.file.inputStream().use { input -> targetFile.outputStream().use { output -> input.copyTo(output) } }
                item.file.delete()
                updateOrderAfterMove(item.file.name, _uiState.value.currentCategory, targetCategory)
                showSnackbar("Moved")
            } catch (e: Exception) {
                showSnackbar("Error moving", isError = true)
            }
        }
        refreshFileList()
    }

    private fun updateOrderAfterMove(fileName: String, fromCategory: String, toCategory: String) {
        val oldOrder = loadFileOrder(fromCategory).toMutableList()
        oldOrder.remove(fileName)
        saveFileOrder(fromCategory, oldOrder)

        val newOrder = loadFileOrder(toCategory).toMutableList()
        if (!newOrder.contains(fileName)) {
            newOrder.add(0, fileName)
            saveFileOrder(toCategory, newOrder)
        }
    }

    fun reorderFile(file: File, direction: Int) {
        // Moved to drag-and-drop
    }

    fun reorderFinished(orderedFiles: List<File>) {
        if (orderedFiles.isEmpty()) return
        val names = orderedFiles.map { it.name }
        saveFileOrder(_uiState.value.currentCategory, names)
        refreshFileList()
    }

    fun renameFile(item: RecordingItem, newName: String) {
        val oldFile = item.file
        val ext = oldFile.extension
        val newFileName = if (newName.endsWith(".$ext")) newName else "$newName.$ext"
        val newFile = File(oldFile.parent, newFileName)
        val oldNote = getNoteFile(oldFile)
        val oldWave = getWaveformFile(oldFile)
        oldFile.renameTo(newFile)
        if (oldNote.exists()) oldNote.renameTo(getNoteFile(newFile))
        if (oldWave.exists()) oldWave.renameTo(getWaveformFile(newFile))
        refreshFileList()
    }

    fun deleteFile(item: RecordingItem) {
        getNoteFile(item.file).delete()
        getWaveformFile(item.file).delete()
        if (item.file.delete()) refreshFileList()
    }

    fun shareFile(item: RecordingItem) {
        try {
            val uri = if (item.uri != Uri.EMPTY) item.uri
            else FileProvider.getUriForFile(ctx, "${ctx.packageName}.provider", item.file)
            val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "audio/*"
                putExtra(android.content.Intent.EXTRA_STREAM, uri)
                clipData = android.content.ClipData.newRawUri(null, uri)
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            ctx.startActivity(android.content.Intent.createChooser(intent, "Share audio via").addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            showSnackbar("Error sharing: ${e.message}", isError = true)
        }
    }

    fun importFile(uri: Uri) {
        val category = _uiState.value.currentCategory
        try {
            var fileName = "import_${System.currentTimeMillis()}"
            ctx.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
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
            ctx.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(targetFile).use { output -> input.copyTo(output) }
            }
            showSnackbar("Imported: $safeName")
            precomputeWaveformAsync(targetFile)
            refreshFileList()
        } catch (e: Exception) {
            showSnackbar("Error importing", isError = true)
        }
    }

    fun saveNote(item: RecordingItem, note: String) {
        val noteFile = getNoteFile(item.file)
        if (note.isBlank()) noteFile.delete() else noteFile.writeText(note)
        refreshFileList()
    }

    // ── Search / Filter ──

    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun getFilteredItems(): List<RecordingItem> {
        val state = _uiState.value
        if (state.searchQuery.isBlank()) return state.savedItems
        val q = state.searchQuery.lowercase()
        return state.savedItems.filter {
            it.name.lowercase().contains(q) || it.note.lowercase().contains(q)
        }
    }

    // ── Playback ──

    fun playFile(item: RecordingItem) {
        _uiState.update { it.copy(playingFileName = item.name, isPaused = false) }
        playPlaylist(listOf(item), 0,
            loopCountProvider = { _uiState.value.loopMode },
            speedProvider = { _uiState.value.playbackSpeed },
            pitchProvider = { _uiState.value.playbackPitch },
            shadowingProvider = { _uiState.value.isShadowingMode },
            onNext = { name -> _uiState.update { it.copy(playingFileName = name, isPaused = false) } },
            onComplete = { _uiState.update { it.copy(playingFileName = "", isPaused = false) } }
        )
    }

    fun startPlaylistPlayback(
        files: List<RecordingItem>,
        loop: Boolean,
        speed: Float,
        onComplete: () -> Unit
    ) {
        playPlaylist(files, 0,
            loopCountProvider = { _uiState.value.loopMode },
            speedProvider = { _uiState.value.playbackSpeed },
            pitchProvider = { _uiState.value.playbackPitch },
            shadowingProvider = { _uiState.value.isShadowingMode },
            onNext = { name -> _uiState.update { it.copy(playingFileName = name, isPaused = false) } },
            onComplete = {
                _uiState.update { it.copy(playingFileName = "", isPaused = false) }
                onComplete()
            }
        )
    }

    fun playPlaylistFromPlaylist(playlist: Playlist) {
        val allRecordings = getAllRecordings()
        val resolvedItems = playlistManager.resolveFiles(playlist, allRecordings)
        if (resolvedItems.isNotEmpty()) {
            _uiState.update {
                it.copy(currentlyPlayingPlaylistId = playlist.id, currentPlaylistIteration = 1)
            }
            playPlaylist(
                allFiles = resolvedItems,
                currentIndex = 0,
                loopCountProvider = { playlist.loopCount },
                speedProvider = { playlist.speed },
                pitchProvider = { playlist.pitch },
                shadowingProvider = { _uiState.value.isShadowingMode },
                onNext = { name -> _uiState.update { it.copy(playingFileName = name) } },
                onIterationChange = { iter -> _uiState.update { it.copy(currentPlaylistIteration = iter) } },
                gapSeconds = playlist.gapSeconds,
                onComplete = {
                    _uiState.update { it.copy(currentlyPlayingPlaylistId = null, currentPlaylistIteration = 1, playingFileName = "") }
                    playlistManager.incrementPlayCount(playlist.id)
                    _uiState.update { it.copy(playlists = playlistManager.loadAll()) }
                }
            )
            if (playlist.sleepMinutes > 0) setSleepTimer(playlist.sleepMinutes)
        } else {
            _uiState.update { it.copy(currentlyPlayingPlaylistId = null) }
            showSnackbar("Playlist is empty or files missing", isError = true)
        }
    }

    fun changeSpeed(speed: Float) {
        _uiState.update { it.copy(playbackSpeed = speed) }
        updatePlaybackParams(speed, _uiState.value.playbackPitch)
    }

    fun changePitch(pitch: Float) {
        _uiState.update { it.copy(playbackPitch = pitch) }
        updatePlaybackParams(_uiState.value.playbackSpeed, pitch)
    }

    fun changeLoopMode(mode: Int) {
        _uiState.update { it.copy(loopMode = mode) }
    }

    fun changeShadowingMode(enabled: Boolean) {
        _uiState.update { it.copy(isShadowingMode = enabled) }
    }

    fun changeShadowPause(seconds: Int) {
        _uiState.update { it.copy(shadowPauseSeconds = seconds) }
    }

    fun seekTo(pos: Float) {
        try { mediaPlayer?.let { if (it.duration > 0) it.seekTo((it.duration * pos).toInt()) } }
        catch (_: IllegalStateException) {}
    }

    fun seekAbsolute(ms: Int) {
        try { mediaPlayer?.seekTo(ms) } catch (_: IllegalStateException) {}
    }

    fun pausePlaying() {
        val wasShadowCountdown = _uiState.value.shadowCountdownText.isNotEmpty()
        shadowingJob?.cancel()
        shadowingJob = null
        _uiState.update { it.copy(shadowCountdownText = "") }
        try {
            if (mediaPlayer?.isPlaying == true) {
                mediaPlayer?.pause()
                _uiState.update { it.copy(isPaused = true) }
                mediaSessionManager.updatePlaybackState(isPlaying = false, isPaused = true)
            } else if (wasShadowCountdown) {
                stopPlaying()
                _uiState.update { it.copy(playingFileName = "", currentlyPlayingPlaylistId = null) }
            }
        } catch (_: IllegalStateException) {
            stopPlaying()
            _uiState.update { it.copy(playingFileName = "", currentlyPlayingPlaylistId = null) }
        }
    }

    fun resumePlaying() {
        if (_uiState.value.shadowCountdownText.isNotEmpty()) {
            shadowingJob?.cancel()
            shadowingJob = null
            _uiState.update { it.copy(shadowCountdownText = "") }
        }
        try {
            val mp = mediaPlayer ?: return
            if (!mp.isPlaying) {
                mp.start()
                _uiState.update { it.copy(isPaused = false) }
                mediaSessionManager.updatePlaybackState(isPlaying = true, isPaused = false)
            }
        } catch (_: IllegalStateException) {}
    }

    fun stopPlayingAndReset() {
        stopPlaying()
        _uiState.update { it.copy(playingFileName = "", currentlyPlayingPlaylistId = null) }
    }

    private fun stopPlaying(endSession: Boolean = true) {
        shadowingJob?.cancel()
        shadowingJob = null
        _uiState.update { it.copy(shadowCountdownText = "") }
        if (endSession) onSessionEnd()
        try { mediaPlayer?.stop() } catch (_: IllegalStateException) {}
        try { mediaPlayer?.release() } catch (_: Exception) {}
        mediaPlayer = null
        _uiState.update { it.copy(isPaused = false) }
        if (::mediaSessionManager.isInitialized) {
            mediaSessionManager.updatePlaybackState(isPlaying = false, isPaused = false)
            mediaSessionManager.abandonAudioFocus()
        }
    }

    fun startProgressTracking() {
        progressJob?.cancel()
        progressJob = viewModelScope.launch {
            while (true) {
                val state = _uiState.value
                if (state.playingFileName.isNotEmpty() && mediaPlayer?.isPlaying == true) {
                    val current = mediaPlayer?.currentPosition ?: 0
                    val total = mediaPlayer?.duration ?: 1
                    _uiState.update {
                        it.copy(
                            currentProgress = current.toFloat() / total.toFloat(),
                            currentTimeString = formatTime(current.toLong())
                        )
                    }
                } else if (state.playingFileName.isEmpty()) {
                    _uiState.update { it.copy(currentProgress = 0f, currentTimeString = "00:00") }
                }
                delay(100)
            }
        }
    }

    private fun startSessionTimer() {
        sessionTimerJob?.cancel()
        sessionTimerJob = viewModelScope.launch {
            while (sessionStartTimeMs > 0L) {
                _uiState.update {
                    it.copy(currentSessionElapsedMs = System.currentTimeMillis() - sessionStartTimeMs)
                }
                delay(1000L)
            }
            _uiState.update { it.copy(currentSessionElapsedMs = 0L) }
        }
    }

    private fun playPlaylist(
        allFiles: List<RecordingItem>,
        currentIndex: Int,
        loopCountProvider: () -> Int,
        speedProvider: () -> Float,
        pitchProvider: () -> Float,
        shadowingProvider: () -> Boolean,
        onNext: (String) -> Unit,
        onIterationChange: (Int) -> Unit = {},
        currentIteration: Int = 1,
        gapSeconds: Int = 0,
        onComplete: () -> Unit
    ) {
        if (allFiles.isEmpty() || currentIndex < 0) { onComplete(); return }

        val loopCount = loopCountProvider()
        val speed = speedProvider()
        val pitch = pitchProvider()
        val isShadowing = shadowingProvider()

        if (currentIndex >= allFiles.size) {
            when {
                loopCount == -1 -> playPlaylist(allFiles, 0, loopCountProvider, speedProvider, pitchProvider, shadowingProvider, onNext, onIterationChange, currentIteration, gapSeconds, onComplete)
                currentIteration < loopCount -> {
                    val next = currentIteration + 1
                    onIterationChange(next)
                    playPlaylist(allFiles, 0, loopCountProvider, speedProvider, pitchProvider, shadowingProvider, onNext, onIterationChange, next, gapSeconds, onComplete)
                }
                else -> {
                    stopPlaying()
                    _uiState.update { it.copy(playingFileName = "") }
                    onComplete()
                }
            }
            return
        }

        stopPlaying(endSession = false)

        if (currentIndex == 0 && sessionStartTimeMs == 0L) {
            onSessionStart()
        }

        val itemToPlay = allFiles[currentIndex]
        onNext(itemToPlay.name)

        try {
            val newPlayer = MediaPlayer()
            mediaPlayer = newPlayer
            newPlayer.apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )
                if (itemToPlay.uri != Uri.EMPTY) {
                    setDataSource(ctx, itemToPlay.uri)
                } else {
                    java.io.FileInputStream(itemToPlay.file).use { fis -> setDataSource(fis.fd) }
                }

                setOnPreparedListener { mp ->
                    if (mp != mediaPlayer) return@setOnPreparedListener
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        try { mp.playbackParams = mp.playbackParams.setSpeed(speed).setPitch(pitch) } catch (_: Exception) {}
                    }
                    mp.isLooping = false
                    if (!mediaSessionManager.requestAudioFocus()) {
                        stopPlaying()
                        _uiState.update { it.copy(playingFileName = "") }
                        onComplete()
                        return@setOnPreparedListener
                    }
                    mp.start()
                    _uiState.update { it.copy(isPaused = false) }
                    mediaSessionManager.updateMetadata(itemToPlay.name, mp.duration.toLong())
                    mediaSessionManager.updatePlaybackState(isPlaying = true, isPaused = false)
                }

                setOnCompletionListener {
                    if (isShadowing) {
                        val duration = it.duration.toLong()
                        val pauseDuration = if (_uiState.value.shadowPauseSeconds > 0) {
                            _uiState.value.shadowPauseSeconds * 1000L
                        } else {
                            duration.coerceAtMost(15000L)
                        }
                        shadowingJob = viewModelScope.launch(Dispatchers.Main) {
                            _uiState.update { s -> s.copy(isPaused = true) }
                            var remaining = pauseDuration
                            while (remaining > 0 && isActive) {
                                val secs = (remaining / 1000) + 1
                                _uiState.update { s -> s.copy(shadowCountdownText = "Repeat in ${secs}s...") }
                                delay(1000L.coerceAtMost(remaining))
                                remaining -= 1000L
                            }
                            _uiState.update { s -> s.copy(shadowCountdownText = "") }
                            if (isActive) {
                                playPlaylist(allFiles, currentIndex, loopCountProvider, speedProvider, pitchProvider, shadowingProvider, onNext, onIterationChange, currentIteration, gapSeconds, onComplete)
                            }
                        }
                    } else {
                        if (gapSeconds > 0 && currentIndex + 1 < allFiles.size) {
                            shadowingJob = viewModelScope.launch(Dispatchers.Main) {
                                _uiState.update { s -> s.copy(isPaused = true) }
                                delay(gapSeconds * 1000L)
                                if (isActive) {
                                    playPlaylist(allFiles, currentIndex + 1, loopCountProvider, speedProvider, pitchProvider, shadowingProvider, onNext, onIterationChange, currentIteration, gapSeconds, onComplete)
                                }
                            }
                        } else {
                            playPlaylist(allFiles, currentIndex + 1, loopCountProvider, speedProvider, pitchProvider, shadowingProvider, onNext, onIterationChange, currentIteration, gapSeconds, onComplete)
                        }
                    }
                }

                setOnErrorListener { _, what, extra ->
                    showSnackbar("Playback error: $what / $extra", isError = true)
                    playPlaylist(allFiles, currentIndex + 1, loopCountProvider, speedProvider, pitchProvider, shadowingProvider, onNext, onIterationChange, currentIteration, gapSeconds, onComplete)
                    true
                }
                prepareAsync()
            }
        } catch (e: Exception) {
            try { mediaPlayer?.release() } catch (_: Exception) {}
            mediaPlayer = null
            showSnackbar("Error opening file: ${e.message}", isError = true)
            playPlaylist(allFiles, currentIndex + 1, loopCountProvider, speedProvider, pitchProvider, shadowingProvider, onNext, onIterationChange, currentIteration, gapSeconds, onComplete)
        }
    }

    private fun updatePlaybackParams(speed: Float, pitch: Float) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                mediaPlayer?.let { if (it.isPlaying) it.playbackParams = it.playbackParams.setSpeed(speed).setPitch(pitch) }
            } catch (_: IllegalStateException) {}
        }
    }

    // ── Sleep Timer ──

    fun setSleepTimer(minutes: Int) {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        _uiState.update { it.copy(selectedSleepMinutes = minutes) }
        if (minutes <= 0) {
            _uiState.update { it.copy(sleepTimerRemainingMs = 0L) }
            return
        }
        _uiState.update { it.copy(sleepTimerRemainingMs = minutes * 60_000L) }
        sleepTimerJob = viewModelScope.launch(Dispatchers.Main) {
            while (isActive && _uiState.value.sleepTimerRemainingMs > 0L) {
                delay(1000L)
                _uiState.update { it.copy(sleepTimerRemainingMs = it.sleepTimerRemainingMs - 1000L) }
                if (_uiState.value.sleepTimerRemainingMs <= 0L) {
                    _uiState.update { it.copy(sleepTimerRemainingMs = 0L) }
                    fadeOutAndStop()
                    break
                }
            }
        }
    }

    private suspend fun fadeOutAndStop() {
        mediaPlayer?.let { mp ->
            try { mp.setVolume(1f, 1f) } catch (_: Exception) {}
            if (mp.isPlaying) {
                for (i in 20 downTo 0) {
                    val vol = i / 20f
                    try { mp.setVolume(vol, vol) } catch (_: Exception) {}
                    delay(150L)
                }
            }
        }
        stopPlaying()
        _uiState.update { it.copy(playingFileName = "", currentlyPlayingPlaylistId = null) }
        mediaSessionManager.updatePlaybackState(isPlaying = false, isPaused = false)
    }

    // ── Audio Processing ──

    fun trimAudioFile(
        file: File, start: Long, end: Long, replace: Boolean,
        removeSelection: Boolean, fadeInMs: Long = 0, fadeOutMs: Long = 0,
        normalize: Boolean = false, onSuccess: () -> Unit
    ) {
        stopPlaying()
        val ext = file.extension
        val isWav = ext.equals("wav", ignoreCase = true)

        viewModelScope.launch(Dispatchers.IO) {
            val ts = System.currentTimeMillis()
            var currentWorkFile = File(file.parent, "temp_trim_$ts.$ext")

            var ok = if (isWav) {
                if (removeSelection) WavAudioTrimmer.removeSegmentWav(file, currentWorkFile, start, end)
                else WavAudioTrimmer.trimWav(file, currentWorkFile, start, end)
            } else {
                if (removeSelection) AudioTrimmer.removeSegmentAudio(file, currentWorkFile, start, end)
                else AudioTrimmer.trimAudio(file, currentWorkFile, start, end)
            }

            if (ok) {
                if (normalize) {
                    val normFile = File(file.parent, "temp_norm_$ts.$ext")
                    if (AudioProcessor.normalize(currentWorkFile, normFile)) {
                        currentWorkFile.delete()
                        currentWorkFile = normFile
                    } else {
                        normFile.delete()
                        showSnackbar("Normalization failed", isError = true)
                    }
                }
                if (fadeInMs > 0 || fadeOutMs > 0) {
                    val fadeFile = File(file.parent, "temp_fade_$ts.$ext")
                    if (AudioProcessor.applyFade(currentWorkFile, fadeFile, fadeInMs, fadeOutMs)) {
                        currentWorkFile.delete()
                        currentWorkFile = fadeFile
                    } else {
                        fadeFile.delete()
                        showSnackbar("Fade failed", isError = true)
                    }
                }
            }

            withContext(Dispatchers.Main) {
                if (ok && currentWorkFile.exists()) {
                    val finalTarget: File = if (replace) file else {
                        val originalName = file.nameWithoutExtension
                        val baseName = if (originalName.contains("_trim_")) originalName.substringBeforeLast("_trim_") else originalName
                        var counter = 1
                        var target = File(file.parent, "${baseName}_trim_$counter.$ext")
                        while (target.exists()) { counter++; target = File(file.parent, "${baseName}_trim_$counter.$ext") }
                        target
                    }
                    if (replace && file.exists()) {
                        if (!file.delete()) {
                            currentWorkFile.delete()
                            showSnackbar("Access denied: Original file in use", isError = true)
                            return@withContext
                        }
                    }
                    if (currentWorkFile.renameTo(finalTarget)) {
                        if (replace) {
                            waveformCache.remove(finalTarget.absolutePath)
                            getWaveformFile(finalTarget).delete()
                            showSnackbar("Studio: Original replaced")
                        } else {
                            showSnackbar("Studio: Saved as ${finalTarget.name}")
                        }
                        onSuccess()
                        refreshFileList()
                    } else {
                        currentWorkFile.delete()
                        showSnackbar("Error finalizing file!", isError = true)
                    }
                } else {
                    currentWorkFile.delete()
                    showSnackbar("Studio: Processing failed", isError = true)
                }
            }
        }
    }

    fun splitFile(item: RecordingItem) {
        logEditEvent("split")
        viewModelScope.launch {
            showSnackbar("Splitting by silence...")
            val segments = withContext(Dispatchers.IO) { SilenceSplitter.detectSegments(item.file) }
            if (segments.size <= 1) {
                showSnackbar("No silence gaps found to split on")
                return@launch
            }
            val baseName = item.file.nameWithoutExtension
            val category = _uiState.value.currentCategory
            val outputDir = if (category == "General") filesDir else File(filesDir, category).also { it.mkdirs() }
            val created = withContext(Dispatchers.IO) { SilenceSplitter.splitFile(item.file, outputDir, segments, baseName) }
            created.forEach { precomputeWaveformAsync(it) }
            showSnackbar("Split into ${created.size} segments")
            refreshFileList()
        }
    }

    fun normalizeFile(item: RecordingItem) {
        logEditEvent("normalize")
        processFileInPlace(item.file, "Normalizing...") { input, output ->
            AudioProcessor.normalize(input, output)
        }
    }

    fun autoTrimFile(item: RecordingItem) {
        logEditEvent("auto_trim")
        viewModelScope.launch {
            showSnackbar("Detecting silence...")
            val bounds = SilenceSplitter.detectContentBounds(item.file)
            if (bounds == null) {
                showSnackbar("No silence detected to trim")
                return@launch
            }
            val (contentStart, contentEnd) = bounds
            val (_, totalMs) = getDuration(item.file)
            val startMs = contentStart
            val endMs = contentEnd.coerceAtMost(totalMs)
            if (startMs < 100 && totalMs - endMs < 100) {
                showSnackbar("No significant silence to trim")
                return@launch
            }
            stopPlaying()
            val ext = item.file.extension
            val isWav = ext.equals("wav", ignoreCase = true)
            val tempFile = File(item.file.parent, "temp_autotrim_${System.currentTimeMillis()}.$ext")
            val success = withContext(Dispatchers.IO) {
                if (isWav) WavAudioTrimmer.trimWav(item.file, tempFile, startMs, endMs)
                else AudioTrimmer.trimAudio(item.file, tempFile, startMs, endMs)
            }
            if (success && tempFile.exists()) {
                waveformCache.remove(item.file.absolutePath)
                getWaveformFile(item.file).delete()
                item.file.delete()
                tempFile.renameTo(item.file)
                precomputeWaveformAsync(item.file)
                showSnackbar("Done!")
                refreshFileList()
            } else {
                tempFile.delete()
                showSnackbar("Auto-trim failed", isError = true)
            }
        }
    }

    fun fadeFile(item: RecordingItem, fadeInMs: Long, fadeOutMs: Long) {
        logEditEvent("fade")
        processFileInPlace(item.file, "Applying fade...") { input, output ->
            AudioProcessor.applyFade(input, output, fadeInMs = fadeInMs, fadeOutMs = fadeOutMs)
        }
    }

    fun mergeFiles(items: List<RecordingItem>) {
        logEditEvent("merge")
        val files = items.map { it.file }
        if (files.size < 2) return
        val category = _uiState.value.currentCategory
        viewModelScope.launch {
            showSnackbar("Merging ${files.size} files...")
            val ext = if (files.all { it.extension.equals("wav", ignoreCase = true) }) "wav" else "m4a"
            val baseName = files.first().nameWithoutExtension
            val outputDir = if (category == "General") filesDir else File(filesDir, category).also { it.mkdirs() }
            var counter = 1
            var outputFile = File(outputDir, "${baseName}_merged.$ext")
            while (outputFile.exists()) { counter++; outputFile = File(outputDir, "${baseName}_merged_$counter.$ext") }
            val success = withContext(Dispatchers.IO) { AudioMerger.mergeFiles(files, outputFile) }
            if (success) {
                precomputeWaveformAsync(outputFile)
                showSnackbar("Merged into ${outputFile.name}")
                refreshFileList()
            } else {
                showSnackbar("Merge failed", isError = true)
            }
        }
    }

    private fun processFileInPlace(file: File, toastMsg: String, processor: suspend (File, File) -> Boolean) {
        viewModelScope.launch {
            showSnackbar(toastMsg)
            val ext = file.extension.ifEmpty { "m4a" }
            val tempFile = File(file.parent, "temp_proc_${System.currentTimeMillis()}.$ext")
            val success = withContext(Dispatchers.IO) { processor(file, tempFile) }

            if (success && tempFile.exists()) {
                val finalFile = File(file.parent, "${file.nameWithoutExtension}.$ext")
                waveformCache.remove(file.absolutePath)
                getWaveformFile(file).delete()
                if (file.exists()) {
                    if (!file.delete()) {
                        tempFile.delete()
                        showSnackbar("Access denied: Original file in use", isError = true)
                        return@launch
                    }
                }
                if (tempFile.renameTo(finalFile)) {
                    precomputeWaveformAsync(finalFile)
                    showSnackbar("Done!")
                    refreshFileList()
                } else {
                    tempFile.delete()
                    showSnackbar("Error finalizing file!", isError = true)
                }
            } else {
                tempFile.delete()
                showSnackbar("Processing failed", isError = true)
            }
        }
    }

    // ── Theme ──

    fun changeTheme(theme: AppTheme) {
        _uiState.update { it.copy(currentTheme = theme) }
        saveThemePref(ctx, theme)
        com.example.audioloop.widget.WidgetStateHelper.updateWidget(ctx, themeName = theme.name)
    }

    // ── Backup & Restore ──

    fun handleSignInResult(account: com.google.android.gms.auth.api.signin.GoogleSignInAccount?) {
        if (account != null) {
            driveBackupManager.handleSignInResult(account)
            _uiState.update { it.copy(isBackupSignedIn = true, backupEmail = account.email ?: "", backupProgress = "") }
        }
    }

    fun handleSignInError(errorMsg: String) {
        _uiState.update { it.copy(backupProgress = errorMsg, isBackupRunning = false) }
    }

    fun signOutBackup() {
        viewModelScope.launch {
            driveBackupManager.signOut()
            _uiState.update { it.copy(isBackupSignedIn = false, backupEmail = "", backupList = emptyList(), backupProgress = "") }
        }
    }

    fun setBackupRunning(running: Boolean) {
        _uiState.update { it.copy(isBackupRunning = running) }
    }

    fun createBackup() {
        viewModelScope.launch {
            _uiState.update { it.copy(isBackupRunning = true) }
            val result = driveBackupManager.createAndUploadBackup { progress ->
                _uiState.update { it.copy(backupProgress = progress) }
            }
            result.onSuccess { name ->
                _uiState.update { it.copy(backupProgress = "Backup saved: $name") }
                delay(3000)
                _uiState.update { it.copy(backupProgress = "") }
            }.onFailure { e ->
                _uiState.update { it.copy(backupProgress = "Backup failed: ${e.localizedMessage}") }
                delay(5000)
                _uiState.update { it.copy(backupProgress = "") }
            }
            _uiState.update { it.copy(isBackupRunning = false) }
        }
    }

    fun listBackups() {
        viewModelScope.launch {
            _uiState.update { it.copy(isBackupRunning = true, backupProgress = "Loading backups...") }
            val result = driveBackupManager.listBackups()
            result.onSuccess { list ->
                _uiState.update { it.copy(backupList = list, backupProgress = if (list.isEmpty()) "No backups found" else "") }
            }.onFailure { e ->
                _uiState.update { it.copy(backupProgress = "${e.localizedMessage}") }
            }
            _uiState.update { it.copy(isBackupRunning = false) }
        }
    }

    fun restoreFromBackup(backupId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isBackupRunning = true, backupList = emptyList()) }
            val result = driveBackupManager.downloadAndRestore(backupId) { progress ->
                _uiState.update { it.copy(backupProgress = progress) }
            }
            result.onSuccess {
                _uiState.update { it.copy(backupProgress = "Restoring settings...") }
                val theme = getThemePref(ctx)
                val newOrder = loadCategoryOrder()
                val cats = if ("General" in newOrder) newOrder else listOf("General") + newOrder
                _uiState.update { it.copy(currentTheme = theme, categories = cats) }
                refreshFileList()
                _uiState.update { it.copy(backupProgress = "Restore complete!") }
                delay(3000)
                _uiState.update { it.copy(backupProgress = "") }
            }.onFailure { e ->
                _uiState.update { it.copy(backupProgress = "Restore failed: ${e.localizedMessage}") }
                delay(5000)
                _uiState.update { it.copy(backupProgress = "") }
            }
            _uiState.update { it.copy(isBackupRunning = false) }
        }
    }

    fun deleteBackup(backupId: String) {
        viewModelScope.launch {
            driveBackupManager.deleteBackup(backupId)
            val list = driveBackupManager.listBackups().getOrDefault(emptyList())
            _uiState.update { it.copy(backupList = list, backupProgress = "Backup deleted") }
            delay(2000)
            _uiState.update { it.copy(backupProgress = "") }
        }
    }

    // ── Playlists ──

    fun savePlaylist(playlist: Playlist) {
        playlistManager.save(playlist)
        _uiState.update { it.copy(playlists = playlistManager.loadAll()) }
    }

    fun deletePlaylist(playlist: Playlist) {
        playlistManager.delete(playlist.id)
        _uiState.update { it.copy(playlists = playlistManager.loadAll()) }
    }

    fun getAllRecordings(): List<RecordingItem> {
        return _uiState.value.categories.flatMap { cat -> getSavedRecordings(cat) }
    }

    // ── Practice Coach ──

    fun toggleSmartCoach() {
        val expanded = !_uiState.value.isSmartCoachExpanded
        _uiState.update { it.copy(isSmartCoachExpanded = expanded) }
        saveSmartCoachExpandedPref(ctx, expanded)
    }

    fun setShowPracticeStats(show: Boolean) {
        _uiState.update { it.copy(showPracticeStats = show) }
    }

    fun startRecommendedSession(suggestedMinutes: Int) {
        if (_uiState.value.playingFileName.isNotEmpty()) {
            stopPlaying()
            _uiState.update { it.copy(playingFileName = "") }
        } else {
            practiceStats.logEvent("recommended_session_start", "${suggestedMinutes}min")
            val items = _uiState.value.savedItems
            if (items.isNotEmpty()) {
                _uiState.update { it.copy(playingFileName = items.first().file.name) }
                playPlaylist(
                    allFiles = items, currentIndex = 0,
                    loopCountProvider = { _uiState.value.loopMode },
                    speedProvider = { _uiState.value.playbackSpeed },
                    pitchProvider = { _uiState.value.playbackPitch },
                    shadowingProvider = { _uiState.value.isShadowingMode },
                    onNext = { name -> _uiState.update { it.copy(playingFileName = name) } },
                    gapSeconds = 0,
                    onComplete = {
                        _uiState.update { it.copy(playingFileName = "") }
                        refreshPracticeStats()
                    }
                )
            }
        }
    }

    fun setPracticeGoal(newGoal: Int) {
        practiceStats.setWeeklyGoal(newGoal)
        refreshPracticeStats()
    }

    private fun refreshPracticeStats() {
        _uiState.update {
            it.copy(
                practiceWeeklyMinutes = practiceStats.weeklyMinutes(),
                practiceWeeklyGoal = practiceStats.weeklyGoalMinutes(),
                practiceStreak = practiceStats.streak(),
                practiceTodayMinutes = practiceStats.todayMinutes(),
                practiceWeeklySessions = practiceStats.weeklySessions(),
                practiceWeeklyEdits = practiceStats.weeklyEdits(),
                practiceGoalProgress = practiceStats.goalProgress(),
                practiceRecommendation = coachEngine.recommend()
            )
        }
    }

    private fun onSessionStart() {
        sessionStartTimeMs = System.currentTimeMillis()
        startSessionTimer()
    }

    private fun onSessionEnd() {
        if (sessionStartTimeMs > 0) {
            val durationMs = System.currentTimeMillis() - sessionStartTimeMs
            practiceStats.logSession(durationMs)
            sessionStartTimeMs = 0L
            sessionTimerJob?.cancel()
            refreshPracticeStats()
        }
    }

    private fun logEditEvent(type: String) {
        practiceStats.logEdit(type)
        refreshPracticeStats()
    }

    // ── Snackbar ──

    fun showSnackbar(message: String, isError: Boolean = false, actionLabel: String? = null) {
        _uiState.update {
            it.copy(snackbarMessage = SnackbarMessage(
                text = message,
                isError = isError,
                actionLabel = actionLabel
            ))
        }
    }

    fun clearSnackbar() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }

    // ── File Helpers (private) ──

    private fun getNotesDir(): File {
        val dir = File(filesDir, ".notes")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun getNoteFile(audioFile: File): File = File(getNotesDir(), "${audioFile.name}.note.txt")

    private fun getNoteForFile(audioFile: File): String {
        val noteFile = getNoteFile(audioFile)
        return if (noteFile.exists()) try { noteFile.readText() } catch (_: Exception) { "" } else ""
    }

    private fun getWaveformFile(audioFile: File): File = File(audioFile.parent, "${audioFile.name}.wave")

    private fun saveWaveformToDisk(audioFile: File, waveform: List<Int>) {
        try {
            val waveFile = getWaveformFile(audioFile)
            waveFile.writeText(waveform.joinToString(","))
        } catch (_: Exception) {}
    }

    private fun loadWaveformFromDisk(audioFile: File): List<Int>? {
        return try {
            val waveFile = getWaveformFile(audioFile)
            if (!waveFile.exists()) return null
            val content = waveFile.readText()
            if (content.isBlank()) return null
            content.split(",").map { it.toInt() }
        } catch (_: Exception) { null }
    }

    fun precomputeWaveformAsync(file: File, fullBars: Int = 120, force: Boolean = false) {
        val key = file.absolutePath
        if (force) {
            waveformCache.remove(key)
            try { getWaveformFile(file).delete() } catch (_: Exception) {}
        }
        if (waveformCache.containsKey(key)) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val cached = loadWaveformFromDisk(file)
                if (cached != null && cached.isNotEmpty()) {
                    withContext(Dispatchers.Main) { waveformCache[key] = cached }
                    return@launch
                }
                val waveform = WaveformGenerator.extractWaveform(file, fullBars)
                saveWaveformToDisk(file, waveform)
                withContext(Dispatchers.Main) { waveformCache[key] = waveform }
            } catch (_: Exception) {}
        }
    }

    private fun getSavedRecordings(category: String): List<RecordingItem> {
        val items = mutableListOf<RecordingItem>()

        // Internal storage
        try {
            val targetDir = if (category == "General") filesDir else File(filesDir, category)
            if (targetDir.exists()) {
                val files = targetDir.listFiles { _, name ->
                    name.endsWith(".m4a", ignoreCase = true) || name.endsWith(".mp3", ignoreCase = true) || name.endsWith(".wav", ignoreCase = true)
                }
                files?.forEach { file ->
                    val (durStr, durMs) = getDuration(file)
                    val uri = try {
                        FileProvider.getUriForFile(ctx, "${ctx.packageName}.provider", file)
                    } catch (_: Exception) { Uri.EMPTY }
                    val note = getNoteForFile(file)
                    items.add(RecordingItem(file, file.name, durStr, durMs, uri, note))
                }
            }
        } catch (e: Exception) { e.printStackTrace() }

        // Public storage (MediaStore)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
                val projection = arrayOf(
                    MediaStore.Audio.Media._ID,
                    MediaStore.Audio.Media.DISPLAY_NAME,
                    MediaStore.Audio.Media.DURATION,
                    MediaStore.Audio.Media.DATA,
                    MediaStore.Audio.Media.RELATIVE_PATH
                )
                val targetPath = if (category == "General") "Music/AudioLoop/" else "Music/AudioLoop/$category/"
                val selection = "${MediaStore.Audio.Media.RELATIVE_PATH} LIKE ?"
                val selectionArgs = arrayOf("$targetPath%")

                ctx.contentResolver.query(collection, projection, selection, selectionArgs, null)?.use { cursor ->
                    val idCol = cursor.getColumnIndex(MediaStore.Audio.Media._ID)
                    val nameCol = cursor.getColumnIndex(MediaStore.Audio.Media.DISPLAY_NAME)
                    val durCol = cursor.getColumnIndex(MediaStore.Audio.Media.DURATION)
                    val dataCol = cursor.getColumnIndex(MediaStore.Audio.Media.DATA)

                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(idCol)
                        val name = cursor.getString(nameCol)
                        val durationMs = cursor.getLong(durCol)
                        val path = cursor.getString(dataCol)

                        if (items.any { it.name == name }) continue

                        val file = File(path)
                        val uri = android.content.ContentUris.withAppendedId(collection, id)
                        val (durStr, durMs) = if (file.exists()) getDuration(file) else Pair(formatTime(durationMs), durationMs)
                        val note = getNoteForFile(file)
                        items.add(RecordingItem(file, name, durStr, durMs, uri, note))
                    }
                }
            } catch (e: Exception) { e.printStackTrace() }
        }

        // Sort and organize
        val fileMap = items.associateBy { it.name }
        val order = loadFileOrder(category)
        val orderedItems = mutableListOf<RecordingItem>()
        val visited = mutableSetOf<String>()

        order.forEach { name ->
            if (fileMap.containsKey(name)) {
                orderedItems.add(fileMap[name]!!)
                visited.add(name)
            }
        }

        val newItems = items.filter { !visited.contains(it.name) }.sortedByDescending { it.file.lastModified() }
        return newItems + orderedItems
    }

    fun getDuration(file: File): Pair<String, Long> {
        if (!file.exists() || file.length() < 10) return Pair("00:00", 0L)
        var millis = 0L

        if (file.extension.equals("wav", ignoreCase = true)) {
            try {
                java.io.RandomAccessFile(file, "r").use { raf ->
                    val headerBytes = ByteArray(36)
                    raf.read(headerBytes)
                    val hdr = java.nio.ByteBuffer.wrap(headerBytes).order(java.nio.ByteOrder.LITTLE_ENDIAN)
                    hdr.position(22)
                    val channels = hdr.getShort().toInt().and(0xFFFF)
                    val sampleRate = hdr.getInt()
                    hdr.position(34)
                    val bitsPerSample = hdr.getShort().toInt().and(0xFFFF)
                    if (sampleRate > 0 && channels > 0 && bitsPerSample > 0) {
                        val bytesPerSecond = sampleRate.toLong() * channels * (bitsPerSample / 8)
                        if (bytesPerSecond > 0) {
                            raf.seek(12)
                            val chunkHeader = ByteArray(4)
                            val sizeBuf = ByteArray(4)
                            while (raf.read(chunkHeader) == 4) {
                                val chunkId = String(chunkHeader)
                                raf.read(sizeBuf)
                                val chunkSize = java.nio.ByteBuffer.wrap(sizeBuf).order(java.nio.ByteOrder.LITTLE_ENDIAN).getInt()
                                if (chunkId == "data") {
                                    millis = (chunkSize.toLong().and(0xFFFFFFFFL) * 1000L) / bytesPerSecond
                                    break
                                } else {
                                    raf.skipBytes(chunkSize)
                                }
                            }
                        }
                    }
                }
                if (millis > 0) return Pair(formatTime(millis), millis)
            } catch (_: Exception) {}
        }

        val mmr = MediaMetadataRetriever()
        try {
            mmr.setDataSource(file.absolutePath)
            millis = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        } catch (_: Exception) {}
        finally { try { mmr.release() } catch (_: Exception) {} }

        if (millis == 0L) {
            var mp: MediaPlayer? = null
            try {
                mp = MediaPlayer()
                mp.setDataSource(file.absolutePath)
                mp.prepare()
                millis = mp.duration.toLong()
            } catch (_: Exception) {}
            finally { try { mp?.release() } catch (_: Exception) {} }
        }

        if (millis == 0L) {
            var extractor: MediaExtractor? = null
            try {
                extractor = MediaExtractor()
                extractor.setDataSource(file.absolutePath)
                for (i in 0 until extractor.trackCount) {
                    val format = extractor.getTrackFormat(i)
                    val mime = format.getString(MediaFormat.KEY_MIME)
                    if (mime?.startsWith("audio/") == true && format.containsKey(MediaFormat.KEY_DURATION)) {
                        millis = format.getLong(MediaFormat.KEY_DURATION) / 1000
                        break
                    }
                }
            } catch (_: Exception) {}
            finally { try { extractor?.release() } catch (_: Exception) {} }
        }

        if (millis == 0L) return Pair("00:00", 0L)
        return Pair(formatTime(millis), millis)
    }

    private fun formatTime(millis: Long): String {
        val minutes = TimeUnit.MILLISECONDS.toMinutes(millis)
        val seconds = TimeUnit.MILLISECONDS.toSeconds(millis) % 60
        return String.format("%02d:%02d", minutes, seconds)
    }

    // ── Preferences Helpers ──

    private fun saveThemePref(context: Context, theme: AppTheme) {
        val prefs = context.getSharedPreferences("AudioLoopPrefs", Context.MODE_PRIVATE)
        prefs.edit().putString("app_theme", theme.name).apply()
    }

    private fun getThemePref(context: Context): AppTheme {
        val prefs = context.getSharedPreferences("AudioLoopPrefs", Context.MODE_PRIVATE)
        val name = prefs.getString("app_theme", "SLATE") ?: "SLATE"
        return try { AppTheme.valueOf(name) } catch (_: Exception) { AppTheme.SLATE }
    }

    private fun saveSmartCoachExpandedPref(context: Context, expanded: Boolean) {
        val prefs = context.getSharedPreferences("AudioLoopPrefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("smart_coach_expanded", expanded).apply()
    }

    private fun getSmartCoachExpandedPref(context: Context): Boolean {
        val prefs = context.getSharedPreferences("AudioLoopPrefs", Context.MODE_PRIVATE)
        return prefs.getBoolean("smart_coach_expanded", false)
    }

    fun isFirstLaunch(): Boolean {
        val prefs = ctx.getSharedPreferences("AudioLoopPrefs", Context.MODE_PRIVATE)
        return prefs.getBoolean("is_first_launch", true)
    }

    fun setFirstLaunchComplete() {
        val prefs = ctx.getSharedPreferences("AudioLoopPrefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("is_first_launch", false).apply()
    }

    fun getPublicStoragePref(): Boolean {
        val prefs = ctx.getSharedPreferences("AudioLoopPrefs", Context.MODE_PRIVATE)
        return prefs.getBoolean("use_public_storage", true)
    }

    // ── File/Category Order Persistence ──

    private fun getOrderFile(category: String): File {
        val dir = if (category == "General") filesDir else File(filesDir, category)
        if (!dir.exists()) dir.mkdirs()
        return File(dir, "ordering.json")
    }

    private fun saveFileOrder(category: String, order: List<String>) {
        try {
            val file = getOrderFile(category)
            val jsonArray = org.json.JSONArray()
            order.forEach { jsonArray.put(it) }
            file.writeText(jsonArray.toString())
        } catch (_: Exception) {}
    }

    private fun loadFileOrder(category: String): List<String> {
        val list = mutableListOf<String>()
        try {
            val file = getOrderFile(category)
            if (file.exists()) {
                val content = file.readText()
                if (content.isNotBlank()) {
                    val array = org.json.JSONArray(content)
                    for (i in 0 until array.length()) list.add(array.getString(i))
                }
            }
        } catch (_: Exception) {}
        return list
    }

    private fun getCategoryOrderFile(): File = File(filesDir, "category_order.json")

    private fun saveCategoryOrder(categories: List<String>) {
        try {
            val jsonArray = org.json.JSONArray()
            categories.forEach { jsonArray.put(it) }
            getCategoryOrderFile().writeText(jsonArray.toString())
        } catch (_: Exception) {}
    }

    private fun loadCategoryOrder(): List<String> {
        val list = mutableListOf<String>()
        try {
            val file = getCategoryOrderFile()
            if (file.exists() && file.length() > 0) {
                val content = file.readText()
                if (content.isNotBlank()) {
                    val array = org.json.JSONArray(content)
                    for (i in 0 until array.length()) list.add(array.getString(i))
                }
            }
        } catch (_: Exception) {}
        return list
    }

    // ── Cleanup ──

    override fun onCleared() {
        stopPlaying()
        if (::mediaSessionManager.isInitialized) mediaSessionManager.release()
        super.onCleared()
    }
}
