package ee.ahtilohk.audioloop

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
import android.content.Intent
import androidx.compose.runtime.mutableStateMapOf
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import ee.ahtilohk.audioloop.ui.theme.AppTheme
import ee.ahtilohk.audioloop.ui.PlaybackManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.runBlocking
import ee.ahtilohk.audioloop.data.AudioRepository
import ee.ahtilohk.audioloop.data.SettingsManager
import ee.ahtilohk.audioloop.data.AudioProcessingManager
import ee.ahtilohk.audioloop.data.AudioMetadataHelper
import ee.ahtilohk.audioloop.data.AudioResult
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import ee.ahtilohk.audioloop.worker.AudioProcessingWorker

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
    private val repository = AudioRepository(ctx)
    private val settingsManager = SettingsManager(ctx)
    private val audioProcessingManager = AudioProcessingManager(ctx)
    private val workManager = WorkManager.getInstance(ctx)

    // ── State ──
    private val _uiState = MutableStateFlow(AudioLoopUiState())
    val uiState: StateFlow<AudioLoopUiState> = _uiState.asStateFlow()


    // Waveform cache (observable map for Compose)
    val waveformCache = mutableStateMapOf<String, List<Int>>()

    // ── Internal refs ──
    lateinit var mediaSessionManager: MediaSessionManager
        private set
    lateinit var practiceStats: PracticeStatsManager
        private set
    lateinit var coachEngine: CoachEngine
        private set
    lateinit var billingManager: BillingManager
        private set
    lateinit var playlistManager: PlaylistManager
        private set
    lateinit var driveBackupManager: DriveBackupManager
        private set
    lateinit var playbackManager: PlaybackManager
        private set

    // Backup for Undo
    private var lastDeletedFile: File? = null
    private var lastDeletedNoteFile: File? = null
    private var lastDeletedWaveFile: File? = null
    private var lastDeletedOriginalPath: File? = null
    private var lastDeletedCategory: String? = null

    private var shadowingJob: Job? = null
    private var sleepTimerJob: Job? = null
    private var progressJob: Job? = null
    private var sessionTimerJob: Job? = null
    private var sessionStartTimeMs: Long = 0L

    // Service binding
    private var audioService: AudioService? = null
    private val mediaPlayer: MediaPlayer? get() = audioService?.getMediaPlayer()
    private val recordingReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                AudioService.ACTION_RECORDING_SAVED -> {
                    setRecording(false)
                    syncDatabase()
                }
                AudioService.ACTION_START_RECORDING_EVENT -> {
                    setRecording(true)
                }
                AudioService.ACTION_AMPLITUDE_UPDATE -> {
                    // Amplitude update received
                }
            }
        }
    }

    private val serviceConnection = object : android.content.ServiceConnection {
        override fun onServiceConnected(name: android.content.ComponentName?, service: android.os.IBinder?) {
            val binder = service as AudioService.AudioBinder
            audioService = binder.getService()
            audioService?.setMediaSessionManager(mediaSessionManager)
            playbackManager.setService(audioService)
        }
        override fun onServiceDisconnected(name: android.content.ComponentName?) {
            audioService = null
            playbackManager.setService(null)
        }
    }

    // ── Initialization ──

    fun setReady(ready: Boolean) {
        _uiState.update { it.copy(isReady = ready) }
    }

    fun initialize() {
        // Init managers
        practiceStats = PracticeStatsManager(ctx)
        coachEngine = CoachEngine(ctx, practiceStats)
        playlistManager = PlaylistManager(ctx)
        driveBackupManager = DriveBackupManager(ctx)
        billingManager = BillingManager(ctx, viewModelScope)
        billingManager.startConnection()

        // Sync Pro status and products
        viewModelScope.launch {
            billingManager.isProUser.collect { isPro ->
                _uiState.update { it.copy(isProUser = isPro) }
            }
        }
        viewModelScope.launch {
            billingManager.products.collect { prods ->
                _uiState.update { it.copy(billingProducts = prods) }
            }
        }

        // Initialize PlaybackManager before MediaSession
        playbackManager = PlaybackManager(
            context = ctx,
            scope = viewModelScope,
            mediaSessionManager = MediaSessionManager(ctx, {}, {}, {}), // Temporary, will be replaced
            onSessionStart = { onSessionStart() },
            onSessionEnd = { onSessionEnd() },
            onPlaylistComplete = { p -> 
                playlistManager.incrementPlayCount(p.id)
                _uiState.update { it.copy(playlists = playlistManager.loadAll()) }
            },
            showSnackbar = { msg, isErr -> showSnackbar(msg, isErr) }
        )

        // Init MediaSession
        mediaSessionManager = MediaSessionManager(
            context = ctx,
            onPlay = { playbackManager.resumePlaying() },
            onPause = { playbackManager.pausePlaying() },
            onStop = { 
                playbackManager.stopPlaying()
                mediaSessionManager.abandonAudioFocus()
            }
        )
        mediaSessionManager.initialize()
        
        // Re-inject proper MediaSessionManager into playbackManager
        // In a real DI system this would be cleaner
        val field = PlaybackManager::class.java.getDeclaredField("mediaSessionManager")
        field.isAccessible = true
        field.set(playbackManager, mediaSessionManager)

        // Load persisted state
        val theme = settingsManager.getTheme()
        val mode = settingsManager.getThemeMode()
        val lang = settingsManager.getLanguage()
        val isCoachExpanded = settingsManager.isSmartCoachExpanded()
        val isCoachEnabled = settingsManager.isSmartCoachEnabled()
        val savedCats = settingsManager.loadCategoryOrder()
        val initialCats = if (savedCats.isEmpty()) listOf("General") else savedCats

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
                themeMode = mode,
                appLanguage = lang,
                categories = initialCats,
                isSmartCoachExpanded = isCoachExpanded,
                isSmartCoachEnabled = isCoachEnabled,
                isBackupSignedIn = signedIn,
                backupEmail = email,
                showOnboarding = settingsManager.isFirstLaunch()
            )
        }

        // Observe PlaybackManager state
        viewModelScope.launch {
            playbackManager.playbackState.collect { playback ->
                _uiState.update { it.copy(
                    playingFileName = playback.playingFileName,
                    isPaused = playback.isPaused,
                    currentProgress = playback.currentProgress,
                    currentTimeString = playback.currentTimeString,
                    loopMode = playback.loopMode,
                    playbackSpeed = playback.playbackSpeed,
                    playbackPitch = playback.playbackPitch,
                    isShadowingMode = playback.isShadowingMode,
                    shadowPauseSeconds = playback.shadowPauseSeconds,
                    shadowCountdownText = playback.shadowCountdownText,
                    abLoopStart = playback.abLoopStart,
                    abLoopEnd = playback.abLoopEnd,
                    currentlyPlayingPlaylistId = playback.currentlyPlayingPlaylistId,
                    currentPlaylistIteration = playback.currentPlaylistIteration
                ) }
            }
        }

        // Apply locale on start
        val appLocales = androidx.core.os.LocaleListCompat.forLanguageTags(lang)
        androidx.appcompat.app.AppCompatDelegate.setApplicationLocales(appLocales)
        // ... existing init ...
        val intent = Intent(ctx, AudioService::class.java)
        ctx.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)

        // Sync and Observe DB
        syncDatabaseAndObserve()

        // Scan public Music/AudioLoop folder only on first launch after reinstall
        if (settingsManager.isFirstLaunch()) {
            viewModelScope.launch {
                try {
                    when (val result = repository.importFromPublicStorage()) {
                        is AudioResult.Success -> {
                            if (result.data > 0) {
                                withContext(Dispatchers.Main) {
                                    showSnackbar(ctx.getString(R.string.msg_found_imported, result.data))
                                    refreshFileList()
                                }
                            }
                        }
                        else -> {}
                    }
                } finally {
                    settingsManager.setFirstLaunchComplete()
                }
            }
        }

        // Register receiver for recording completion and state sync
        val filter = android.content.IntentFilter().apply {
            addAction(AudioService.ACTION_RECORDING_SAVED)
            addAction(AudioService.ACTION_START_RECORDING_EVENT)
            addAction(AudioService.ACTION_AMPLITUDE_UPDATE)
        }
        androidx.core.content.ContextCompat.registerReceiver(ctx, recordingReceiver, filter, androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    private fun syncDatabaseAndObserve() {
        // 1. Observe items for current category (Uses flatMapLatest to cancel old flows)
        viewModelScope.launch {
            _uiState.map { it.currentCategory }
                .distinctUntilChanged()
                .flatMapLatest { category -> repository.getRecordingsByCategory(category) }
                .collect { items ->
                    _uiState.update { it.copy(savedItems = items) }
                }
        }
        
        // 2. Observe all categories from DB and combine with disk discovery
        viewModelScope.launch {
            repository.getCategories().collect { dbCategories ->
                _uiState.update { state ->
                    val combined = (state.categories + dbCategories).distinct()
                    state.copy(categories = combined)
                }
            }
        }

        // 3. Observe ALL recordings for reactive state
        viewModelScope.launch {
            repository.getAllRecordings().collect { items ->
                _uiState.update { it.copy(allRecordings = items) }
            }
        }

        // 4. Periodic or initial discovery
        viewModelScope.launch {
            repository.discoverRecordings(_uiState.value.categories)
        }

        // 5. Reactive filtering and sorting (Eliminates runBlocking in the UI thread)
        viewModelScope.launch {
            combine(
                _uiState.map { it.searchQuery }.distinctUntilChanged(),
                _uiState.map { it.sortMode }.distinctUntilChanged(),
                _uiState.map { it.searchCategory }.distinctUntilChanged(),
                _uiState.map { it.savedItems }.distinctUntilChanged(),
                _uiState.map { it.currentCategory }.distinctUntilChanged()
            ) { query, sort, cat, saved, current ->
                val base = if (query.isNotBlank()) {
                    if (cat != null) {
                         repository.getRecordingsByCategorySync(cat)
                    } else {
                         repository.getAllRecordingsSync()
                    }
                } else {
                    saved
                }

                val filtered = if (query.isBlank()) {
                    base
                } else {
                    val q = query.lowercase()
                    base.filter {
                        it.name.lowercase().contains(q) || it.note.lowercase().contains(q)
                    }
                }

                when (sort) {
                    SortMode.NAME_ASC -> filtered.sortedBy { it.name.lowercase() }
                    SortMode.NAME_DESC -> filtered.sortedByDescending { it.name.lowercase() }
                    SortMode.DATE_DESC -> filtered.sortedByDescending { it.file.lastModified() }
                    SortMode.DATE_ASC -> filtered.sortedBy { it.file.lastModified() }
                    SortMode.LENGTH_DESC -> filtered.sortedByDescending { it.durationMillis }
                    SortMode.LENGTH_ASC -> filtered.sortedBy { it.durationMillis }
                }
            }.flowOn(Dispatchers.IO).collect { filtered ->
                _uiState.update { it.copy(filteredItems = filtered) }
            }
        }
    }

    // Category and File management is now handled via Repository observation and discovery.

    fun changeCategory(newCategory: String) {
        _uiState.update { it.copy(currentCategory = newCategory) }
        refreshFileList()
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
        refreshFileList()
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
        refreshFileList()
    }

    fun reorderCategory(cat: String, direction: Int) {
        val cats = _uiState.value.categories.toMutableList()
        val idx = cats.indexOf(cat)
        if (idx != -1) {
            val newIdx = idx + direction
            if (newIdx in cats.indices) {
                java.util.Collections.swap(cats, idx, newIdx)
                settingsManager.saveCategoryOrder(cats)
                _uiState.update { it.copy(categories = cats) }
            }
        }
    }

    fun reorderCategories(newOrder: List<String>) {
        _uiState.update { it.copy(categories = newOrder) }
        settingsManager.saveCategoryOrder(newOrder)
    }

    // ── File Operations ──

    fun refreshFileList() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            repository.discoverRecordings(_uiState.value.categories)
            _uiState.update { it.copy(isLoading = false) }
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
            viewModelScope.launch {
                repository.deleteRecording(item.file.absolutePath)
                repository.insertRecording(item.copy(file = targetFile), targetCategory)
            }
            updateOrderAfterMove(item.file.name, _uiState.value.currentCategory, targetCategory)
            showSnackbar(ctx.getString(R.string.msg_moved))
        } else {
            try {
                item.file.inputStream().use { input -> targetFile.outputStream().use { output -> input.copyTo(output) } }
                item.file.delete()
                viewModelScope.launch {
                    repository.deleteRecording(item.file.absolutePath)
                    repository.insertRecording(item.copy(file = targetFile), targetCategory)
                }
                updateOrderAfterMove(item.file.name, _uiState.value.currentCategory, targetCategory)
                showSnackbar(ctx.getString(R.string.msg_moved))
            } catch (e: Exception) {
                showSnackbar(ctx.getString(R.string.msg_error_moving), isError = true)
            }
        }
    }

    private fun updateOrderAfterMove(fileName: String, fromCategory: String, toCategory: String) {
        val oldOrder = loadFileOrder(fromCategory).toMutableList()
        oldOrder.remove(fileName)
        saveFileOrder(fromCategory, oldOrder)

        val newOrder = loadFileOrder(toCategory).toMutableList()
        if (!newOrder.contains(fileName)) {
            newOrder.add(0, fileName)
            settingsManager.saveFileOrder(toCategory, newOrder)
        }
    }

    fun reorderFile(file: File, direction: Int) {
        // Moved to drag-and-drop
    }

    fun reorderFinished(orderedFiles: List<File>) {
        if (orderedFiles.isEmpty()) return
        val names = orderedFiles.map { it.name }
        settingsManager.saveFileOrder(_uiState.value.currentCategory, names)
        refreshFileList()
    }

    fun renameFile(item: RecordingItem, newName: String) {
        viewModelScope.launch {
            when (val result = repository.renameRecording(item, newName)) {
                is AudioResult.Success -> {
                    refreshFileList()
                    // Optional: show info snackbar
                }
                is AudioResult.Error -> {
                    showSnackbar(result.message, isError = true)
                }
                else -> {}
            }
        }
    }

    fun deleteFile(item: RecordingItem) {
        viewModelScope.launch {
            val file = item.file
            val noteFile = getNoteFile(file)
            val waveFile = getWaveformFile(file)
            
            // 1. Prepare Trash folder
            val trashDir = File(filesDir, ".trash").apply { mkdirs() }
            val timestamp = System.currentTimeMillis()
            val trashFile = File(trashDir, "${timestamp}_${file.name}")
            val trashNote = File(trashDir, "${timestamp}_${file.name}.note")
            val trashWave = File(trashDir, "${timestamp}_${file.name}.wave")

            // 2. Backup current state for Undo
            lastDeletedFile = trashFile
            lastDeletedNoteFile = if (noteFile.exists()) trashNote else null
            lastDeletedWaveFile = if (waveFile.exists()) trashWave else null
            lastDeletedOriginalPath = file
            lastDeletedCategory = _uiState.value.currentCategory

            // 3. Move to trash
            val moved = file.renameTo(trashFile)
            if (moved) {
                if (noteFile.exists()) noteFile.renameTo(trashNote)
                if (waveFile.exists()) waveFile.renameTo(trashWave)
                
                repository.deleteRecording(file.absolutePath)
                showSnackbar(
                    message = ctx.getString(R.string.msg_deleted, item.name),
                    actionLabel = ctx.getString(R.string.btn_undo)
                )
            } else {
                showSnackbar(ctx.getString(R.string.msg_error_deleting), isError = true)
            }
        }
    }

    fun undoDelete() {
        val original = lastDeletedOriginalPath ?: return
        val trash = lastDeletedFile ?: return
        
        if (trash.exists()) {
            trash.renameTo(original)
            lastDeletedNoteFile?.let { if (it.exists()) it.renameTo(getNoteFile(original)) }
            lastDeletedWaveFile?.let { if (it.exists()) it.renameTo(getWaveformFile(original)) }
            
            lastDeletedFile = null
            lastDeletedOriginalPath = null
            
            refreshFileList()
            showSnackbar(ctx.getString(R.string.msg_restored))
        }
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
            ctx.startActivity(android.content.Intent.createChooser(intent, ctx.getString(R.string.menu_share)).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            showSnackbar(ctx.getString(R.string.msg_error_sharing, e.message ?: ""), isError = true)
        }
    }

    fun importFile(uri: Uri) {
        val category = _uiState.value.currentCategory
        _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch {
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
                
                withContext(Dispatchers.IO) {
                    ctx.contentResolver.openInputStream(uri)?.use { input ->
                        FileOutputStream(targetFile).use { output -> input.copyTo(output) }
                    }
                }
                
                val (durStr, durMs) = AudioMetadataHelper.getDuration(targetFile)
                val newItem = RecordingItem(targetFile, targetFile.name, durStr, durMs, Uri.fromFile(targetFile), "")
                
                when (val result = repository.insertRecording(newItem, category)) {
                    is AudioResult.Success -> {
                        showSnackbar(ctx.getString(R.string.msg_imported, safeName))
                        precomputeWaveformAsync(targetFile)
                    }
                    is AudioResult.Error -> {
                        showSnackbar(result.message, isError = true)
                    }
                    else -> {}
                }
            } catch (e: Exception) {
                showSnackbar(ctx.getString(R.string.msg_error_importing), isError = true)
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun saveNote(item: RecordingItem, note: String) {
        val noteFile = getNoteFile(item.file)
        if (note.isBlank()) noteFile.delete() else noteFile.writeText(note)
        refreshFileList()
    }

    // ── Search / Filter ── (Getter versions removed in favor of reactive flow)
    fun getAllRecordings(): List<RecordingItem> = _uiState.value.allRecordings

    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    // Deprecated: use uiState.filteredItems
    fun getFilteredItems(): List<RecordingItem> = _uiState.value.filteredItems

    fun setSortMode(mode: SortMode) {
        _uiState.update { it.copy(sortMode = mode) }
    }

    fun setSearchCategory(cat: String?) {
        _uiState.update { it.copy(searchCategory = cat) }
    }

    // ── Playback ──

    fun playFile(item: RecordingItem) = playbackManager.playFile(item)

    fun startPlaylistPlayback(files: List<RecordingItem>, loop: Boolean, speed: Float, onComplete: () -> Unit) {
        playbackManager.startPlaylistPlayback(files, if (loop) -1 else 1, speed, 1.0f, _uiState.value.isShadowingMode, onComplete)
    }

    fun playPlaylistFromPlaylist(playlist: Playlist) {
        viewModelScope.launch {
            val allRecordings = repository.getAllRecordingsSync()
            val resolvedItems = playlistManager.resolveFiles(playlist, allRecordings)
            if (resolvedItems.isNotEmpty()) {
                playbackManager.updateCurrentlyPlayingPlaylist(playlist.id, 1)
                playbackManager.playPlaylist(
                    allFiles = resolvedItems,
                    currentIndex = 0,
                    loopCountProvider = { playlist.loopCount },
                    speedProvider = { playlist.speed },
                    pitchProvider = { playlist.pitch },
                    shadowingProvider = { _uiState.value.isShadowingMode },
                    gapSeconds = playlist.gapSeconds,
                    onComplete = {
                        playbackManager.updateCurrentlyPlayingPlaylist(null, 1)
                        playlistManager.incrementPlayCount(playlist.id)
                        _uiState.update { it.copy(playlists = playlistManager.loadAll()) }
                    }
                )
                if (playlist.sleepMinutes > 0) setSleepTimer(playlist.sleepMinutes)
            } else {
                playbackManager.updateCurrentlyPlayingPlaylist(null, 1)
                showSnackbar(ctx.getString(R.string.msg_playlist_empty), isError = true)
            }
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
            sessionTimerJob = null
            _uiState.update { it.copy(currentSessionElapsedMs = 0L) }
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

    // ── Audio Processing ──

    fun trimAudioFile(
        file: File, start: Long, end: Long, replace: Boolean,
        removeSelection: Boolean, fadeInMs: Long = 0, fadeOutMs: Long = 0,
        normalize: Boolean = false, onSuccess: () -> Unit
    ) {
        stopPlaying()
        val ext = file.extension
        logEditEvent("trim")
        _uiState.update { it.copy(isLoading = true) }

        val workId = audioProcessingManager.trim(
            file, start, end, removeSelection, replace, normalize, fadeInMs, fadeOutMs
        )

        audioProcessingManager.getWorkInfoFlow(workId).let { flow ->
            viewModelScope.launch {
                flow.collect { workInfo ->
                    if (workInfo != null) {
                        if (workInfo.state.isFinished) {
                            _uiState.update { it.copy(isLoading = false) }
                            if (workInfo.state == androidx.work.WorkInfo.State.SUCCEEDED) {
                                withContext(Dispatchers.Main) {
                                    if (replace) {
                                        waveformCache.remove(file.absolutePath)
                                        getWaveformFile(file).delete()
                                        showSnackbar(ctx.getString(R.string.msg_studio_replaced))
                                    } else {
                                        showSnackbar(ctx.getString(R.string.msg_studio_saved))
                                    }
                                    onSuccess()
                                    refreshFileList()
                                }
                            } else {
                                withContext(Dispatchers.Main) {
                                    showSnackbar(ctx.getString(R.string.msg_processing_failed), isError = true)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    fun splitFile(item: RecordingItem) {
        logEditEvent("split")
        val category = _uiState.value.currentCategory
        val outputDir = if (category == "General") filesDir else File(filesDir, category).also { it.mkdirs() }

        val workId = audioProcessingManager.split(item.file, outputDir)

        viewModelScope.launch {
            audioProcessingManager.getWorkInfoFlow(workId).collect { workInfo ->
                if (workInfo != null && workInfo.state.isFinished) {
                    _uiState.update { it.copy(isLoading = false) }
                    withContext(Dispatchers.Main) {
                        if (workInfo.state == androidx.work.WorkInfo.State.SUCCEEDED) {
                            val paths = workInfo.outputData.getStringArray("created_files") ?: emptyArray()
                            paths.forEach { precomputeWaveformAsync(File(it)) }
                            showSnackbar(ctx.getString(R.string.msg_split_done, paths.size))
                            refreshFileList()
                        } else {
                            showSnackbar(ctx.getString(R.string.msg_processing_failed), isError = true)
                        }
                    }
                }
            }
        }
    }

    fun normalizeFile(item: RecordingItem) {
        logEditEvent("normalize")
        _uiState.update { it.copy(isLoading = true) }
        val workId = audioProcessingManager.normalize(item.file)

        viewModelScope.launch {
            audioProcessingManager.getWorkInfoFlow(workId).collect { workInfo ->
                if (workInfo != null && workInfo.state.isFinished) {
                    _uiState.update { it.copy(isLoading = false) }
                    withContext(Dispatchers.Main) {
                        if (workInfo.state == androidx.work.WorkInfo.State.SUCCEEDED) {
                            waveformCache.remove(item.file.absolutePath)
                            getWaveformFile(item.file).delete()
                            precomputeWaveformAsync(item.file)
                            showSnackbar(ctx.getString(R.string.msg_done))
                            refreshFileList()
                        } else {
                            showSnackbar(ctx.getString(R.string.msg_normalization_failed), isError = true)
                        }
                    }
                }
            }
        }
    }

    fun autoTrimFile(item: RecordingItem) {
        logEditEvent("auto_trim")
        _uiState.update { it.copy(isLoading = true) }
        val workId = audioProcessingManager.autoTrim(item.file)

        viewModelScope.launch {
            audioProcessingManager.getWorkInfoFlow(workId).collect { workInfo ->
                if (workInfo != null && workInfo.state.isFinished) {
                    _uiState.update { it.copy(isLoading = false) }
                    withContext(Dispatchers.Main) {
                        if (workInfo.state == androidx.work.WorkInfo.State.SUCCEEDED) {
                            waveformCache.remove(item.file.absolutePath)
                            getWaveformFile(item.file).delete()
                            precomputeWaveformAsync(item.file)
                            showSnackbar(ctx.getString(R.string.msg_done))
                            refreshFileList()
                        } else {
                            showSnackbar(ctx.getString(R.string.msg_auto_trim_failed), isError = true)
                        }
                    }
                }
            }
        }
    }

    fun fadeFile(item: RecordingItem, fadeInMs: Long, fadeOutMs: Long) {
        logEditEvent("fade")
        processFileInPlace(item.file, R.string.msg_applying_fade) { input, output ->
            AudioProcessor.applyFade(input, output, fadeInMs = fadeInMs, fadeOutMs = fadeOutMs)
        }
    }

    fun mergeFiles(items: List<RecordingItem>) {
        logEditEvent("merge")
        val files = items.map { it.file }
        if (files.size < 2) return
        val category = _uiState.value.currentCategory
        _uiState.update { it.copy(isLoading = true) }

        val ext = if (files.all { it.extension.equals("wav", ignoreCase = true) }) "wav" else "m4a"
        val baseName = files.first().nameWithoutExtension
        val outputDir = if (category == "General") filesDir else File(filesDir, category).also { it.mkdirs() }
        var counter = 1
        var outputFile = File(outputDir, "${baseName}_merged.$ext")
        while (outputFile.exists()) { counter++; outputFile = File(outputDir, "${baseName}_merged_$counter.$ext") }

        val workId = audioProcessingManager.merge(files, outputFile)

        viewModelScope.launch {
            audioProcessingManager.getWorkInfoFlow(workId).collect { workInfo ->
                if (workInfo != null && workInfo.state.isFinished) {
                    _uiState.update { it.copy(isLoading = false) }
                    withContext(Dispatchers.Main) {
                        if (workInfo.state == androidx.work.WorkInfo.State.SUCCEEDED) {
                            val path = workInfo.outputData.getString("output_path") ?: ""
                            if (path.isNotEmpty()) precomputeWaveformAsync(File(path))
                            showSnackbar(ctx.getString(R.string.msg_done))
                            refreshFileList()
                        } else {
                            showSnackbar(ctx.getString(R.string.msg_merge_failed), isError = true)
                        }
                    }
                }
            }
        }
    }

    private fun processFileInPlace(file: File, toastMsgRes: Int, processor: suspend (File, File) -> Boolean) {
        _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            showSnackbar(ctx.getString(toastMsgRes))
            val ext = file.extension.ifEmpty { "m4a" }
            val tempFile = File(file.parent, "temp_proc_${System.currentTimeMillis()}.$ext")
            val success = processor(file, tempFile)

            if (success && tempFile.exists()) {
                val finalFile = File(file.parent, "${file.nameWithoutExtension}.$ext")
                waveformCache.remove(file.absolutePath)
                getWaveformFile(file).delete()
                
                val deleted = withContext(Dispatchers.IO) {
                    if (file.exists()) {
                        file.delete()
                    } else true
                }

                if (!deleted) {
                    file.delete()
                    showSnackbar(ctx.getString(R.string.msg_access_denied), isError = true)
                    _uiState.update { it.copy(isLoading = false) }
                    return@launch
                }

                if (withContext(Dispatchers.IO) { tempFile.renameTo(finalFile) }) {
                    precomputeWaveformAsync(finalFile)
                    showSnackbar(ctx.getString(R.string.msg_done))
                    refreshFileList()
                } else {
                    tempFile.delete()
                    showSnackbar(ctx.getString(R.string.msg_error_finalizing), isError = true)
                }
            } else {
                tempFile.delete()
                showSnackbar(ctx.getString(R.string.msg_processing_failed), isError = true)
            }
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    // ── Theme ──

    fun changeTheme(theme: AppTheme) {
        _uiState.update { it.copy(currentTheme = theme) }
        settingsManager.saveTheme(theme)
        ee.ahtilohk.audioloop.widget.WidgetStateHelper.updateWidget(ctx, themeName = theme.name)
    }

    fun changeLanguage(langCode: String) {
        _uiState.update { it.copy(appLanguage = langCode) }
        settingsManager.saveLanguage(langCode)
        
        // Apply locale using AppCompatDelegate (API 33+ or backward compatible)
        val appLocales = androidx.core.os.LocaleListCompat.forLanguageTags(langCode)
        androidx.appcompat.app.AppCompatDelegate.setApplicationLocales(appLocales)
    }

    fun changeThemeMode(mode: ThemeMode) {
        _uiState.update { it.copy(themeMode = mode) }
        settingsManager.saveThemeMode(mode)
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
                val theme = settingsManager.getTheme()
                val newOrder = settingsManager.loadCategoryOrder()
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


    // ── Practice Coach ──

    fun toggleSmartCoach() {
        val expanded = !_uiState.value.isSmartCoachExpanded
        _uiState.update { it.copy(isSmartCoachExpanded = expanded) }
        settingsManager.saveSmartCoachExpanded(expanded)
    }

    fun toggleSmartCoachEnabled() {
        val enabled = !_uiState.value.isSmartCoachEnabled
        _uiState.update { it.copy(isSmartCoachEnabled = enabled) }
        settingsManager.saveSmartCoachEnabled(enabled)
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
        viewModelScope.launch {
            try {
                val cached = loadWaveformFromDisk(file)
                if (cached != null && cached.isNotEmpty()) {
                    waveformCache[key] = cached
                    return@launch
                }
                val waveform = WaveformGenerator.extractWaveform(file, fullBars)
                saveWaveformToDisk(file, waveform)
                waveformCache[key] = waveform
            } catch (_: Exception) {}
        }
    }

    // Old file discovery and utility methods removed.
    // Replaced by data layer helpers in AudioRepository and AudioMetadataHelper.

    // Preferences and ordering logic moved to SettingsManager

    // --- UI Actions ---


    fun setSearchVisible(visible: Boolean) {
        _uiState.update { it.copy(isSearchVisible = visible) }
    }

    fun setRecording(recording: Boolean) {
        _uiState.update { it.copy(isRecording = recording) }
    }

    fun setSelectionMode(enabled: Boolean) {
        _uiState.update { it.copy(isSelectionMode = enabled) }
        if (!enabled) clearSelection()
    }

    fun toggleFileSelection(name: String) {
        val current = _uiState.value.selectedFiles.toMutableSet()
        if (current.contains(name)) current.remove(name) else current.add(name)
        _uiState.update { it.copy(selectedFiles = current) }
    }

    fun clearSelection() {
        _uiState.update { it.copy(selectedFiles = emptySet()) }
    }

    fun setPlaybackSpeed(speed: Float) {
        val wasPlayedBefore = practiceStats.hasEventOccurred("aha_speed_used")
        _uiState.update { it.copy(playbackSpeed = speed) }
        updatePlaybackParams(speed, _uiState.value.playbackPitch)
        if (speed != 1.0f) {
            practiceStats.logEvent("aha_speed_used", "speed=$speed")
            if (!wasPlayedBefore) {
                showSnackbar(getApplication<Application>().getString(R.string.aha_speed_success))
            }
        }
    }

    fun setLoopMode(mode: Int) {
        val wasPlayedBefore = practiceStats.hasEventOccurred("aha_loop_used")
        _uiState.update { it.copy(loopMode = mode) }
        if (mode != 0) {
            practiceStats.logEvent("aha_loop_used", "mode=$mode")
            if (!wasPlayedBefore) {
                showSnackbar(getApplication<Application>().getString(R.string.aha_loop_success))
            }
        }
    }

    fun setShadowingMode(enabled: Boolean) {
        _uiState.update { it.copy(isShadowingMode = enabled) }
    }

    fun setShadowPauseSeconds(seconds: Int) {
        _uiState.update { it.copy(shadowPauseSeconds = seconds) }
    }

    fun setSettingsOpen(open: Boolean) {
        _uiState.update { it.copy(settingsOpen = open) }
    }

    fun setShowCategorySheet(show: Boolean) {
        _uiState.update { it.copy(showCategorySheet = show) }
    }

    fun setShowPlaylistSheet(show: Boolean) {
        _uiState.update { it.copy(showPlaylistSheet = show) }
    }

    fun setShowBackupSheet(show: Boolean) {
        _uiState.update { it.copy(showBackupSheet = show) }
    }

    fun openRenameDialog(item: RecordingItem) {
        _uiState.update { it.copy(showRenameDialog = true, itemToModify = item) }
    }

    fun closeRenameDialog() {
        _uiState.update { it.copy(showRenameDialog = false, itemToModify = null) }
    }

    fun openMoveDialog(item: RecordingItem) {
        _uiState.update { it.copy(showMoveDialog = true, itemToModify = item) }
    }

    fun closeMoveDialog() {
        _uiState.update { it.copy(showMoveDialog = false, itemToModify = null) }
    }

    fun openDeleteDialog(item: RecordingItem) {
        _uiState.update { it.copy(showDeleteDialog = true, recordingToDelete = item) }
    }

    fun closeDeleteDialog() {
        _uiState.update { it.copy(showDeleteDialog = false, recordingToDelete = null) }
    }

    fun openTrimDialog(item: RecordingItem) {
        _uiState.update { it.copy(showTrimDialog = true, recordingToTrim = item) }
    }

    fun closeTrimDialog() {
        _uiState.update { it.copy(showTrimDialog = false, recordingToTrim = null) }
    }

    fun openNoteDialog(item: RecordingItem) {
        _uiState.update { it.copy(showNoteDialog = true, recordingToNote = item) }
    }

    fun closeNoteDialog() {
        _uiState.update { it.copy(showNoteDialog = false, recordingToNote = null) }
    }

    fun openInfoDialog(item: RecordingItem) {
        _uiState.update { it.copy(showInfoDialog = true, recordingToInfo = item) }
    }

    fun closeInfoDialog() {
        _uiState.update { it.copy(showInfoDialog = false, recordingToInfo = null) }
    }

    fun openPlaylistView(playlistId: String) {
        _uiState.update { it.copy(showPlaylistView = true, viewingPlaylistId = playlistId) }
    }

    fun closePlaylistView() {
        _uiState.update { it.copy(showPlaylistView = false, viewingPlaylistId = null) }
    }

    fun openPlaylistEditor(playlist: Playlist?) {
        _uiState.update { it.copy(editingPlaylist = playlist) }
    }

    fun closePlaylistEditor() {
        _uiState.update { it.copy(editingPlaylist = null) }
    }

    // --- Onboarding Actions ---

    // Playback logic moved to PlaybackManager

    fun nextOnboardingStep() {
        _uiState.update { it.copy(onboardingStep = it.onboardingStep + 1) }
    }

    fun getPublicStoragePref(): Boolean = settingsManager.getUsePublicStorage()
    fun setPublicStoragePref(enabled: Boolean) = settingsManager.setUsePublicStorage(enabled)

    fun selectOnboardingUseCase(useCase: String) {
        // 1. Define category based on use case
        val categoryKey = when(useCase) {
            "musician" -> "Music"
            "student" -> "Studies/Languages"
            "podcaster" -> "Content"
            else -> "General"
        }
        
        // 2. Update categories if "General" is the only one (clean start)
        if (_uiState.value.categories == listOf("General")) {
            val initial = listOf(categoryKey, "General").distinct()
            viewModelScope.launch {
                settingsManager.saveCategoryOrder(initial)
                _uiState.update { it.copy(categories = initial, currentCategory = categoryKey) }
                syncDatabase()
            }
        } else {
            _uiState.update { it.copy(currentCategory = categoryKey) }
        }

        // 3. Move to next step
        nextOnboardingStep()
    }

    fun finishOnboarding() {
        settingsManager.setFirstLaunchComplete()
        _uiState.update { it.copy(showOnboarding = false) }
    }

    override fun onCleared() {
        stopPlaying()
        try { ctx.unregisterReceiver(recordingReceiver) } catch (_: Exception) {}
        if (::mediaSessionManager.isInitialized) mediaSessionManager.release()
        try { ctx.unbindService(serviceConnection) } catch (_: Exception) {}
        super.onCleared()
    }

    fun setShowExportSegmentDialog(show: Boolean) {
        _uiState.update { it.copy(showExportSegmentDialog = show) }
    }

    fun startExportLoop(item: RecordingItem, start: Long, end: Long, fadeInMs: Long, fadeOutMs: Long, normalize: Boolean) {
        _uiState.update { it.copy(
            showExportSegmentDialog = true,
            itemToModify = item,
            exportSegmentParams = ExportParams(start, end, fadeInMs, fadeOutMs, normalize)
        ) }
    }

    fun finalizeExport(targetCategory: String? = null, targetPlaylistId: String? = null, newPlaylistName: String? = null) {
        val item = _uiState.value.itemToModify ?: return
        val params = _uiState.value.exportSegmentParams ?: return
        if (_uiState.value.isLoading) return

        _uiState.update { it.copy(showExportSegmentDialog = false, showTrimDialog = false, isLoading = true) }
        
        val categoryPrefix = targetCategory ?: _uiState.value.currentCategory
        
        // Wrap trimAudioFile functionality manually to handle playlist insertion
        stopPlaying()
        logEditEvent("export_segment")

        val workId = audioProcessingManager.trim(
            item.file, params.startMs, params.endMs,
            removeSelection = false, replace = false, normalize = params.normalize,
            fadeInMs = params.fadeInMs, fadeOutMs = params.fadeOutMs
        )

        viewModelScope.launch {
            audioProcessingManager.getWorkInfoFlow(workId)
                .mapNotNull { if (it?.state?.isFinished == true) it else null }
                .first()
                .let { workInfo ->
                    _uiState.update { it.copy(isLoading = false) }
                    if (workInfo.state == androidx.work.WorkInfo.State.SUCCEEDED) {
                        val newPath = workInfo.outputData.getString("output_path") ?: ""
                        if (newPath.isNotEmpty()) {
                            val newFile = File(newPath)
                            // 1. If targetCategory differs from current, move it
                            if (targetCategory != null && targetCategory != _uiState.value.currentCategory) {
                                // Move is handled by processing in worker implicitly to specific folders 
                                // if we pass output_dir, but trim worker saves to same folder.
                                // Let's move it if needed.
                                val targetDir = if (targetCategory == "General") filesDir else File(filesDir, targetCategory).apply { mkdirs() }
                                val finalDest = File(targetDir, newFile.name)
                                if (newFile.renameTo(finalDest)) {
                                    // Refresh logic
                                }
                            }
                            
                            // 2. Playlist Logic
                            val playlistPath = if (categoryPrefix == "General") newFile.name else "$categoryPrefix/${newFile.name}"
                            
                            if (newPlaylistName != null) {
                                val newList = Playlist(
                                    id = System.currentTimeMillis().toString(),
                                    name = newPlaylistName,
                                    files = listOf(playlistPath),
                                    createdAt = System.currentTimeMillis()
                                )
                                playlistManager.save(newList)
                            } else if (targetPlaylistId != null) {
                                val p = playlistManager.loadAll().find { it.id == targetPlaylistId }
                                if (p != null) {
                                    playlistManager.save(p.copy(files = p.files + playlistPath))
                                }
                            }

                            withContext(Dispatchers.Main) {
                                showSnackbar(ctx.getString(R.string.msg_studio_saved))
                                refreshFileList()
                                _uiState.update { it.copy(playlists = playlistManager.loadAll()) }
                            }
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            showSnackbar(ctx.getString(R.string.msg_processing_failed), isError = true)
                        }
                    }
                }
            }
        }

    fun syncDatabase() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            repository.discoverRecordings(_uiState.value.categories)
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    fun triggerPublicStorageImport() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            repository.importFromPublicStorage()
            refreshFileList()
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    fun clearAllData() {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isLoading = true) }
            
            // 1. Delete all local files
            val list = filesDir.listFiles()
            list?.forEach { it.deleteRecursively() }
            
            // 2. The repository discovery will cleanup DB automatically 
            // but let's be more explicit if possible. 
            // In this architecture, discovery + cleanupStale handles it.
            repository.discoverRecordings(emptyList()) 
            
            // 3. Reset categories
            settingsManager.saveCategoryOrder(listOf("General"))
            
            withContext(Dispatchers.Main) {
                _uiState.update { 
                    it.copy(
                        isLoading = false,
                        categories = listOf("General"),
                        currentCategory = "General"
                    )
                }
                refreshFileList()
            }
        }
    }
    fun setUpgradeSheetVisible(visible: Boolean) {
        _uiState.update { it.copy(showUpgradeSheet = visible) }
    }

    fun purchasePro(activity: android.app.Activity, product: com.android.billingclient.api.ProductDetails) {
        billingManager.launchPurchaseFlow(activity, product)
    }

    fun restorePurchases() {
        billingManager.queryPurchases()
        showSnackbar(ctx.getString(R.string.msg_restoring_purchases))
    }

    fun isPro(): Boolean = _uiState.value.isProUser

    fun setShowPrivacyPolicy(show: Boolean) {
        _uiState.update { it.copy(showPrivacyPolicyDialog = show) }
    }

    fun exportAllData() {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val exportFile = File(ctx.cacheDir, "AudioLoop_Data_Export.zip")
                if (exportFile.exists()) exportFile.delete()

                val rootDir = filesDir
                val filesToExport = rootDir.walkTopDown().filter { it.isFile && !it.name.startsWith(".") }.toList()

                if (filesToExport.isEmpty()) {
                    withContext(Dispatchers.Main) { showSnackbar(ctx.getString(R.string.msg_no_data_to_export), isError = true) }
                    return@launch
                }

                java.util.zip.ZipOutputStream(java.io.FileOutputStream(exportFile)).use { zos ->
                    filesToExport.forEach { file ->
                        val relativePath = file.absolutePath.removePrefix(rootDir.absolutePath + File.separator)
                        zos.putNextEntry(java.util.zip.ZipEntry(relativePath))
                        file.inputStream().use { it.copyTo(zos) }
                        zos.closeEntry()
                    }
                }

                val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.provider", exportFile)
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/zip"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, "AudioLoop Data Export")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                ctx.startActivity(Intent.createChooser(intent, ctx.getString(R.string.menu_export_data)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))

            } catch (e: Exception) {
                withContext(Dispatchers.Main) { showSnackbar(ctx.getString(R.string.msg_export_failed, e.message ?: ""), isError = true) }
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    fun showSnackbar(message: String, isError: Boolean = false) {
        _uiState.update { it.copy(snackbarMessage = SnackbarMessage(message, isError = isError)) }
    }

    fun clearSnackbar() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }

    fun refreshFileList() {
        syncDatabase()
    }
}

