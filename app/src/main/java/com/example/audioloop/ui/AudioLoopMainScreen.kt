package com.example.audioloop.ui

import com.example.audioloop.ui.TrimAudioDialog
import com.example.audioloop.ui.RenameDialog
import com.example.audioloop.ui.MoveFileDialog
import com.example.audioloop.ui.DeleteConfirmDialog
import com.example.audioloop.ui.formatDuration
import com.example.audioloop.RecordingItem
import com.example.audioloop.WaveformGenerator
import com.example.audioloop.AppIcons
import kotlinx.coroutines.*
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.zIndex
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.gestures.*
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.unit.IntOffset
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.foundation.gestures.detectDragGestures
import android.media.MediaPlayer
import com.example.audioloop.ui.theme.*
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import java.io.File

import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import android.graphics.Paint

import androidx.compose.runtime.mutableFloatStateOf


@Composable
fun AudioLoopMainScreen(
    context: android.content.Context,
    recordingItems: List<RecordingItem>,
    categories: List<String>,    // State
    currentCategory: String,
    currentProgress: Float,
    currentTimeString: String,
    playingFileName: String,
    isPaused: Boolean,
    
    // Callbacks
    onPlayingFileNameChange: (String) -> Unit,
    onCategoryChange: (String) -> Unit,
    onAddCategory: (String) -> Unit,
    onRenameCategory: (String, String) -> Unit,
    onDeleteCategory: (String) -> Unit,

    onReorderCategory: (String, Int) -> Unit, // -1 up, +1 down
    onReorderCategories: (List<String>) -> Unit,
    
    onMoveFile: (RecordingItem, String) -> Unit,
    onReorderFile: (File, Int) -> Unit,
    onReorderFinished: (List<File>) -> Unit,
    
    onStartRecord: (String, Boolean) -> Boolean,
    onStopRecord: () -> Unit,
    
    onStartPlaylist: (List<RecordingItem>, Boolean, Float, () -> Unit) -> Unit,
    onPlaylistUpdate: () -> Unit,
    
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
    onTrimFile: (File, Long, Long, Boolean, Boolean, Long, Long) -> Unit,
    selectedSpeed: Float,
    selectedLoopCount: Int,
    isShadowing: Boolean,
    onShadowingChange: (Boolean) -> Unit,
    
    waveformCache: Map<String, List<Int>> = emptyMap(),
    onSeekAbsolute: (Int) -> Unit = {},
    onSplitFile: (RecordingItem) -> Unit = {},
    onNormalizeFile: (RecordingItem) -> Unit = {},
    onFadeFile: (RecordingItem, Long, Long) -> Unit = { _, _, _ -> },
    onMergeFiles: (List<RecordingItem>) -> Unit = {},
    usePublicStorage: Boolean,
    onPublicStorageChange: (Boolean) -> Unit,
    sleepTimerRemainingMs: Long = 0L,
    selectedSleepMinutes: Int = 0,
    onSleepTimerChange: (Int) -> Unit,
    currentTheme: com.example.audioloop.ui.theme.AppTheme,
    onThemeChange: (com.example.audioloop.ui.theme.AppTheme) -> Unit
) {
    // Get theme colors
    val themeColors = currentTheme.palette
    var settingsOpen by remember { mutableStateOf(false) }
    var mode by remember { mutableStateOf("Speech") }
    var isSelectionMode by remember { mutableStateOf(false) }
    var selectedFiles by remember { mutableStateOf(setOf<String>()) }
    var playingPlaylistFiles by remember { mutableStateOf(listOf<String>()) } // Track files in current playlist
    var showCategorySheet by remember { mutableStateOf(false) }
    var isRecording by remember { mutableStateOf(false) }
    
    // Local source of truth for the list to allow live updates
    val uiRecordingItems = remember { mutableStateListOf<RecordingItem>() }
    // Sync when source changes (and not dragging)
    LaunchedEffect(recordingItems) {
         // Only update if we are not currently dragging to avoid jumpiness
         // But usually if recordingItems changes, it means an external update or save finished.
         // We should probably respect it.
         uiRecordingItems.clear()
         uiRecordingItems.addAll(recordingItems)
    }

    // Dialog states
    var showRenameDialog by remember { mutableStateOf(false) }
    var showMoveDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showTrimDialog by remember { mutableStateOf(false) }
    
    var itemToModify by remember { mutableStateOf<RecordingItem?>(null) }
    var recordingToDelete by remember { mutableStateOf<RecordingItem?>(null) }
    var recordingToTrim by remember { mutableStateOf<RecordingItem?>(null) }
    var draggingItemName by remember { mutableStateOf<String?>(null) }
    var draggingItemIndex by remember { androidx.compose.runtime.mutableIntStateOf(-1) }
    var dragOffsetPx by remember { mutableFloatStateOf(0f) }
    
    val density = LocalDensity.current.density
    val itemHeightPx = 72 * density // Approximate height of an item


    val filePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) onImportFile(uri)
    }
    
    // val context = LocalContext.current // Using parameter instead

    // Helper to toggle selection
    fun toggleSelection(name: String) {
        selectedFiles = if (selectedFiles.contains(name)) {
            selectedFiles - name
        } else {
            selectedFiles + name
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .background(themeColors.primary900.copy(alpha = 0.4f)) // Subtle themed surface tint
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
        ) {
            // Header - Modern MD3 Design
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(
                        imageVector = AppIcons.GraphicEq, 
                        contentDescription = "Logo", 
                        tint = themeColors.primary400,
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        text = "Loop & Learn",
                        style = MaterialTheme.typography.titleMedium.copy(
                            brush = Brush.horizontalGradient(
                                colors = listOf(themeColors.primary300, themeColors.primary400)
                            ),
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 0.5.sp
                        )
                    )
                }

                // Playlist Selection Button
                Surface(
                    onClick = {
                        isSelectionMode = !isSelectionMode
                        if (!isSelectionMode) selectedFiles = emptySet()
                    },
                    shape = RoundedCornerShape(12.dp),
                    color = if (isSelectionMode)
                        Red500.copy(alpha = 0.15f)
                    else
                        themeColors.primaryContainer.copy(alpha = 0.3f),
                    border = BorderStroke(
                        1.dp,
                        if (isSelectionMode) Red400 else themeColors.primary
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isSelectionMode) AppIcons.Close else AppIcons.PlayArrow,
                            contentDescription = null,
                            tint = if (isSelectionMode) Red400 else themeColors.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = if (isSelectionMode) "Cancel" else "Select Playlist",
                            style = MaterialTheme.typography.labelLarge.copy(
                                color = if (isSelectionMode) Red400 else themeColors.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                    }
                }
            }



            // Category Navigation - Underline Tabs
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                LazyRow(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    items(categories) { cat ->
                        val isActive = currentCategory == cat
                        Column(
                            modifier = Modifier
                                .width(IntrinsicSize.Max)
                                .clickable { onCategoryChange(cat) }
                                .padding(horizontal = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = cat,
                                style = MaterialTheme.typography.labelLarge.copy(
                                    color = if (isActive) themeColors.primary300 else Zinc500,
                                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium
                                ),
                                modifier = Modifier.padding(vertical = 12.dp)
                            )
                            // Underline indicator
                            Box(
                                modifier = Modifier
                                    .height(2.dp)
                                    .fillMaxWidth()
                                    .background(
                                        if (isActive) themeColors.primary400
                                        else Color.Transparent,
                                        RoundedCornerShape(1.dp)
                                    )
                            )
                        }
                    }
                }

                // Category Management Button
                IconButton(
                    onClick = { showCategorySheet = true },
                    modifier = Modifier.size(36.dp)
                ) {
                   Icon(
                       imageVector = AppIcons.Edit,
                       contentDescription = "Manage Categories",
                       tint = Zinc500,
                       modifier = Modifier.size(18.dp)
                   )
                }
            }

            HorizontalDivider(
                color = Zinc700.copy(alpha = 0.3f),
                thickness = 1.dp,
                modifier = Modifier.padding(horizontal = 20.dp)
            )

            // Recording Section - Modern MD3 Design
            AnimatedVisibility(visible = !isSelectionMode && !showCategorySheet) {
                Column(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Mode Switcher - MD3 Segmented Button Style
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .background(Zinc800.copy(alpha = 0.3f), RoundedCornerShape(24.dp))
                            .border(1.dp, Zinc600, RoundedCornerShape(24.dp))
                            .padding(6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("Speech" to AppIcons.Mic, "Stream" to AppIcons.Stream).forEach { (m, icon) ->
                            val active = mode == m
                            Surface(
                                onClick = { mode = m },
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight(),
                                shape = RoundedCornerShape(18.dp),
                                color = if (active)
                                    themeColors.primary
                                else
                                    Color.Transparent
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = null,
                                        tint = if (active) Color.White else Zinc400,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = m,
                                        style = MaterialTheme.typography.labelLarge.copy(
                                            color = if (active) Color.White else Zinc400,
                                            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium
                                        )
                                    )
                                }
                            }
                        }
                    }

                    // Record Button - Studio Pro Design
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 8.dp)
                            .height(84.dp) // Professional height
                            .clip(RoundedCornerShape(20.dp))
                            .background(
                                if (isRecording) {
                                    // Recording: Deep Red Pulse
                                    Brush.radialGradient(
                                        colors = listOf(Red900, Color.Black),
                                        radius = 600f
                                    )
                                } else {
                                    // Idle: Studio Dark (Zinc900)
                                    SolidColor(Zinc900)
                                }
                            )
                            .border(
                                width = 2.dp, // Thicker border for premium look
                                color = if (isRecording) Red500 else Red800, // More visible red border
                                shape = RoundedCornerShape(20.dp)
                            )
                            .clickable {
                                if (isRecording) {
                                    onStopRecord()
                                    isRecording = false
                                } else {
                                    val dateFormat = java.text.SimpleDateFormat("yyyy.MM.dd HH:mm:ss", java.util.Locale.getDefault())
                                    val dateStr = dateFormat.format(java.util.Date())
                                    val prefix = if (mode == "Speech") "Speech" else "Stream"
                                    val name = "${prefix}_$dateStr"
                                    val commenced = onStartRecord(name, mode == "Stream")
                                    if (commenced) isRecording = true
                                }
                            },
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(24.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp)
                        ) {
                            // Record Indicator / Icon
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .shadow(
                                        elevation = if (isRecording) 12.dp else 0.dp,
                                        spotColor = Red500,
                                        shape = CircleShape
                                    )
                                    .background(
                                        color = if (isRecording) Red600 else Zinc800,
                                        shape = CircleShape
                                    )
                                    .border(
                                        width = 1.dp,
                                        color = if (isRecording) Red400 else Zinc600,
                                        shape = CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isRecording) {
                                    // Pulsing stop icon
                                    Box(
                                        modifier = Modifier
                                            .size(20.dp)
                                            .background(Color.White, RoundedCornerShape(4.dp))
                                    )
                                } else {
                                    // Mic icon (Red accent)
                                    Icon(
                                        imageVector = if (mode == "Speech") AppIcons.Mic else AppIcons.Stream,
                                        contentDescription = null,
                                        tint = Red500, // Red accent icon
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                            }

                            // Text Content
                            Column(
                                verticalArrangement = Arrangement.Center
                            ) {
                                // Timer Logic
                                var recordingDurationSeconds by remember { mutableLongStateOf(0L) }
                                
                                LaunchedEffect(isRecording) {
                                    if (isRecording) {
                                        val startTime = System.currentTimeMillis()
                                        while (isActive) {
                                            recordingDurationSeconds = (System.currentTimeMillis() - startTime) / 1000
                                            delay(1000)
                                        }
                                    } else {
                                        recordingDurationSeconds = 0L
                                    }
                                }

                                if (isRecording) {
                                    val hours = recordingDurationSeconds / 3600
                                    val minutes = (recordingDurationSeconds % 3600) / 60
                                    val seconds = recordingDurationSeconds % 60
                                    val timeString = String.format("%02d:%02d:%02d", hours, minutes, seconds)
                                    
                                    Text(
                                        text = timeString,
                                        style = MaterialTheme.typography.headlineSmall.copy(
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace,
                                            letterSpacing = 2.sp
                                        )
                                    )
                                    Text(
                                        text = "Recording...",
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            color = Red400,
                                            fontWeight = FontWeight.Medium
                                        )
                                    )
                                } else {
                                    Text(
                                        text = "Start Recording",
                                        style = MaterialTheme.typography.titleLarge.copy(
                                            color = Color.White,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 20.sp,
                                            letterSpacing = 0.5.sp
                                        )
                                    )
                                    Text(
                                        text = "$mode mode",
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            color = Zinc500,
                                            fontWeight = FontWeight.Medium
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Settings Section - Modern MD3 Card Design
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                shape = RoundedCornerShape(16.dp),
                color = Zinc800.copy(alpha = 0.3f),
                border = BorderStroke(1.dp, Zinc600)
            ) {
                Column {
                    // Settings Header (Collapsible)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { settingsOpen = !settingsOpen }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = AppIcons.Settings,
                            contentDescription = null,
                            tint = if (settingsOpen) themeColors.primary else Zinc400,
                            modifier = Modifier.size(20.dp)
                        )

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Playback Settings",
                                style = MaterialTheme.typography.labelLarge.copy(
                                    color = if (settingsOpen) themeColors.onPrimaryContainer else Zinc300,
                                    fontWeight = FontWeight.SemiBold
                                )
                            )

                            // Quick preview when collapsed
                            if (!settingsOpen) {
                                val loopText = if (selectedLoopCount == -1) "\u221E" else "${selectedLoopCount}x"
                                val sleepText = if (sleepTimerRemainingMs > 0L) {
                                    val totalSec = (sleepTimerRemainingMs / 1000).toInt()
                                    val minutes = totalSec / 60
                                    val seconds = totalSec % 60
                                    String.format("%02d:%02d", minutes, seconds)
                                } else "Off"

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Speed
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = AppIcons.Speed,
                                            contentDescription = "Speed",
                                            tint = themeColors.primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Text(
                                            text = "${selectedSpeed}x",
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                color = themeColors.primary,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        )
                                    }

                                    // Loop Count
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = AppIcons.Loop,
                                            contentDescription = "Loop Count",
                                            tint = themeColors.primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Text(
                                            text = loopText,
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                color = themeColors.primary,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        )
                                    }

                                    // Shadowing
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = AppIcons.Shadow,
                                            contentDescription = "Shadowing",
                                            tint = themeColors.primary,
                                            modifier = Modifier.size(16.dp).graphicsLayer { rotationZ = 90f }
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Text(
                                            text = if (isShadowing) "On" else "Off",
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                color = themeColors.primary,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        )
                                    }

                                    // Sleep Timer
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = AppIcons.Sleep,
                                            contentDescription = "Sleep Timer",
                                            tint = themeColors.primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Text(
                                            text = sleepText,
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                color = themeColors.primary,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        )
                                    }
                                }
                            }
                        }

                        Icon(
                            imageVector = if (settingsOpen) AppIcons.ChevronUp else AppIcons.ChevronDown,
                            contentDescription = null,
                            tint = if (settingsOpen) themeColors.primary else Zinc500,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                AnimatedVisibility(visible = settingsOpen) {
                    Column(
                        modifier = Modifier
                            .padding(top = 12.dp)
                            .fillMaxWidth()
                            .background(Zinc900.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                            .border(1.dp, Zinc600, RoundedCornerShape(12.dp))
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Speed
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Speed:", style = TextStyle(color = Zinc400, fontSize = 14.sp))
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                listOf(0.5f, 0.75f, 1.0f, 1.5f, 2.0f).forEach { s ->
                                    val active = selectedSpeed == s
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(if (active) themeColors.primary600 else Zinc800)
                                            .clickable { onSpeedChange(s) }
                                            .padding(horizontal = 8.dp, vertical = 6.dp)
                                    ) {
                                        Text("${s}x", style = TextStyle(color = if (active) Color.White else Zinc400, fontSize = 12.sp, fontWeight = FontWeight.Medium))
                                    }
                                }
                            }
                        }

                        // Repeats
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Repeats:", style = TextStyle(color = Zinc400, fontSize = 14.sp))
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                listOf(1, 5, -1).forEach { r ->
                                    val active = selectedLoopCount == r
                                    val label = if (r == -1) "\u221E" else "${r}x"
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(if (active) themeColors.primary600 else Zinc800)
                                            .clickable { onLoopCountChange(r) }
                                            .padding(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Text(label, style = TextStyle(color = if (active) Color.White else Zinc400, fontSize = 12.sp, fontWeight = FontWeight.Medium))
                                    }
                                }
                            }
                        }
                        
                        // Shadowing
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column {
                                Text("Shadowing Mode", style = TextStyle(color = Zinc300, fontSize = 14.sp))
                                Text("Smart pause & auto-repeat", style = TextStyle(color = Zinc600, fontSize = 10.sp))
                            }
                            Box(
                                modifier = Modifier
                                    .width(44.dp)
                                    .height(24.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (isShadowing) themeColors.primary600 else Zinc700)
                                    .clickable { onShadowingChange(!isShadowing) }
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(20.dp)
                                        .offset(x = if (isShadowing) 22.dp else 2.dp, y = 2.dp)
                                        .clip(CircleShape)
                                        .background(Color.White),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isShadowing) Icon(AppIcons.Check, contentDescription = null, tint = themeColors.primary600, modifier = Modifier.size(10.dp))
                                }
                            }
                        }

                        // Sleep Timer
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Sleep:", style = TextStyle(color = Zinc400, fontSize = 14.sp))
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                val timerActive = sleepTimerRemainingMs > 0L
                                val isOffSelected = selectedSleepMinutes == 0
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (isOffSelected) themeColors.primary600 else Zinc800)
                                        .clickable { onSleepTimerChange(0) }
                                        .padding(horizontal = 8.dp, vertical = 6.dp)
                                ) {
                                    Text("Off", style = TextStyle(color = if (isOffSelected) Color.White else Zinc400, fontSize = 12.sp, fontWeight = FontWeight.Medium))
                                }
                                listOf(5, 15, 30, 45, 60).forEach { m ->
                                    val isSelected = selectedSleepMinutes == m && timerActive
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(if (isSelected) themeColors.primary600 else Zinc800)
                                            .clickable { onSleepTimerChange(m) }
                                            .padding(horizontal = 8.dp, vertical = 6.dp)
                                    ) {
                                        Text("${m}m", style = TextStyle(color = if (isSelected) Color.White else Zinc400, fontSize = 12.sp, fontWeight = FontWeight.Medium))
                                    }
                                }
                            }
                        }
                        if (sleepTimerRemainingMs > 0L) {
                            val totalSec = (sleepTimerRemainingMs / 1000).toInt()
                            val min = totalSec / 60
                            val sec = totalSec % 60
                            val remaining = String.format("%d:%02d", min, sec)
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "Stops in $remaining",
                                    style = TextStyle(color = themeColors.primary300, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                )
                                Text(
                                    "Cancel",
                                    style = TextStyle(color = Red400, fontSize = 12.sp, fontWeight = FontWeight.Medium),
                                    modifier = Modifier.clickable { onSleepTimerChange(0) }
                                )
                            }
                        }



                        // Theme Selector
                        Text("Theme:", style = TextStyle(color = Zinc400, fontSize = 14.sp), modifier = Modifier.padding(top = 8.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            com.example.audioloop.ui.theme.AppTheme.values().forEach { theme ->
                                val isSelected = currentTheme == theme
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isSelected) theme.palette.primary600 else Zinc800)
                                        .border(
                                            width = if (isSelected) 2.dp else 1.dp,
                                            color = if (isSelected) theme.palette.primary400 else Zinc600,
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        .clickable { onThemeChange(theme) }
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(16.dp)
                                            .clip(CircleShape)
                                            .background(theme.palette.primary500)
                                    )
                                }
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            com.example.audioloop.ui.theme.AppTheme.values().forEach { theme ->
                                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                    Text(
                                        theme.displayName,
                                        style = TextStyle(
                                            color = if (currentTheme == theme) theme.palette.primary300 else Zinc500,
                                            fontSize = 9.sp,
                                            fontWeight = if (currentTheme == theme) FontWeight.Bold else FontWeight.Normal
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            } // End Column inside Surface
            } // End Surface (Playback Settings)

            // Categories Management Embedded Frame
            AnimatedVisibility(visible = showCategorySheet) {
                Box(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                    CategoryManagementSheet(
                        categories = categories,
                        currentCategory = currentCategory,
                        onSelect = onCategoryChange,
                        onAdd = onAddCategory,
                        onRename = onRenameCategory,
                        onDelete = onDeleteCategory,
                        onReorder = onReorderCategories,
                        onClose = { showCategorySheet = false },
                        themeColors = themeColors
                    )
                }
            }

            if (!showCategorySheet) {
                HorizontalDivider(color = Zinc800, thickness = 1.dp, modifier = Modifier.padding(horizontal = 16.dp))

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                            .padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isSelectionMode && selectedFiles.isNotEmpty()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        val orderedSelection = selectedFiles.toList()
                                        val filesToPlay = orderedSelection.mapNotNull { name ->
                                            recordingItems.find { it.name == name }
                                        }
                                        if (filesToPlay.isNotEmpty()) {
                                            playingPlaylistFiles = filesToPlay.map { it.name }
                                            onStartPlaylist(filesToPlay, selectedLoopCount == -1, selectedSpeed) {
                                                playingPlaylistFiles = emptyList()
                                            }
                                            isSelectionMode = false
                                            selectedFiles = emptySet()
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = themeColors.primary600),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("PLAY ${selectedFiles.size}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                                if (selectedFiles.size >= 2) {
                                    Button(
                                        onClick = {
                                            val orderedSelection = selectedFiles.toList()
                                            val filesToMerge = orderedSelection.mapNotNull { name ->
                                                recordingItems.find { it.name == name }
                                            }
                                            if (filesToMerge.size >= 2) {
                                                onMergeFiles(filesToMerge)
                                                isSelectionMode = false
                                                selectedFiles = emptySet()
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = themeColors.secondary),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("MERGE ${selectedFiles.size}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    }
                                }
                            }
                        } else {
                            Text(
                                text = "Files ($currentCategory):",
                                style = TextStyle(color = Zinc200, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            )
                            if (!isSelectionMode) {
                                Button(
                                    onClick = { filePickerLauncher.launch("audio/*") },
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = themeColors.primary600),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Text("+ Add file", fontSize = 12.sp, color = Color.White)
                                }
                            }
                        }
                    }

                    val density = LocalDensity.current
                    val scrollState = rememberLazyListState()
                    val scope = rememberCoroutineScope()
                    var draggingItemIndex by remember { mutableIntStateOf(-1) }
                    var draggingItem by remember { mutableStateOf<RecordingItem?>(null) }
                    var overlayOffsetY by remember { mutableFloatStateOf(0f) }
                    var grabOffsetY by remember { mutableFloatStateOf(0f) }
                    var overscrollSpeed by remember { mutableFloatStateOf(0f) }

                    fun checkForSwap() {
                        val currentVisibleItems = scrollState.layoutInfo.visibleItemsInfo
                        if (currentVisibleItems.isEmpty()) return
                        val overlayCenterY = overlayOffsetY + (with(density) { 36.dp.toPx() })
                        var targetIndex = -1
                        val hoveredItem = currentVisibleItems.find { overlayCenterY >= it.offset && overlayCenterY <= it.offset + it.size }
                        if (hoveredItem != null) {
                            targetIndex = hoveredItem.index
                        } else {
                            val firstVisible = currentVisibleItems.first()
                            val lastVisible = currentVisibleItems.last()
                            if (overlayCenterY < firstVisible.offset) targetIndex = firstVisible.index
                            else if (overlayCenterY > lastVisible.offset + lastVisible.size) targetIndex = lastVisible.index
                        }
                        if (targetIndex != -1 && targetIndex != draggingItemIndex) {
                            if (draggingItemIndex in uiRecordingItems.indices && targetIndex in uiRecordingItems.indices) {
                                val itemToMove = uiRecordingItems.removeAt(draggingItemIndex)
                                uiRecordingItems.add(targetIndex, itemToMove)
                                draggingItemIndex = targetIndex
                            }
                        }
                    }

                    LaunchedEffect(overscrollSpeed) {
                        if (overscrollSpeed != 0f) {
                            while (true) {
                                val scrolled = scrollState.scrollBy(overscrollSpeed)
                                if (scrolled != 0f) checkForSwap()
                                kotlinx.coroutines.delay(10)
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .pointerInput(Unit) {
                                val gripWidth = 60.dp.toPx()
                                awaitEachGesture {
                                    val down = awaitFirstDown(requireUnconsumed = false)
                                    val y = down.position.y
                                    val x = down.position.x
                                    val hitItem = scrollState.layoutInfo.visibleItemsInfo.find { y >= it.offset && y <= it.offset + it.size }
                                    if (hitItem != null && x <= gripWidth) {
                                        down.consume()
                                        val index = hitItem.index
                                        if (index in uiRecordingItems.indices) {
                                            draggingItemIndex = index
                                            draggingItem = uiRecordingItems[index]
                                            val itemTop = hitItem.offset.toFloat()
                                            overlayOffsetY = itemTop
                                            grabOffsetY = y - itemTop
                                            var isDragging = true
                                            while (isDragging) {
                                                val event = awaitPointerEvent()
                                                val change = event.changes.firstOrNull { it.id == down.id }
                                                if (change == null || !change.pressed) {
                                                    isDragging = false
                                                    break
                                                }
                                                if (change.positionChanged()) {
                                                    change.consume()
                                                    val newY = change.position.y
                                                    overlayOffsetY = newY - grabOffsetY
                                                    val viewportHeight = size.height.toFloat()
                                                    val threshold = 60.dp.toPx()
                                                    val maxSpeed = 20.dp.toPx()
                                                    if (newY < threshold) overscrollSpeed = -maxSpeed * ((threshold - newY) / threshold).coerceIn(0.1f, 1f)
                                                    else if (newY > viewportHeight - threshold) overscrollSpeed = maxSpeed * ((newY - (viewportHeight - threshold)) / threshold).coerceIn(0.1f, 1f)
                                                    else overscrollSpeed = 0f
                                                    checkForSwap()
                                                }
                                            }
                                            overscrollSpeed = 0f
                                            onReorderFinished(uiRecordingItems.map { it.file })
                                            draggingItemIndex = -1
                                            draggingItem = null
                                        }
                                    }
                                }
                            }
                    ) {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            state = scrollState,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            itemsIndexed(uiRecordingItems, key = { _, item -> item.name }) { index, item ->
                                val isPlaying = item.file.name == playingFileName
                                val isSelected = selectedFiles.contains(item.name)
                                val alpha = if (draggingItemIndex == index) 0f else 1f
                                FileItem(
                                    modifier = Modifier.alpha(alpha),
                                    item = item,
                                    isPlaying = isPlaying,
                                    isPaused = if (isPlaying) isPaused else false,
                                    isSelectionMode = isSelectionMode,
                                    isSelected = isSelected,
                                    selectionOrder = selectedFiles.indexOf(item.name) + 1,
                                    onPlay = { onStartPlaylist(listOf(item), selectedLoopCount == -1, selectedSpeed, {}) },
                                    onPause = onPausePlay,
                                    onResume = onResumePlay,
                                    onStop = { playingPlaylistFiles = emptyList(); onStopPlay() },
                                    onToggleSelect = { toggleSelection(item.name) },
                                    onRename = { itemToModify = item; showRenameDialog = true },
                                    onTrim = { recordingToTrim = item; showTrimDialog = true },
                                    onMove = { itemToModify = item; showMoveDialog = true },
                                    onShare = { onShareFile(item) },
                                    onDelete = { recordingToDelete = item; showDeleteDialog = true },
                                    onSplit = { onSplitFile(item) },
                                    onNormalize = { onNormalizeFile(item) },
                                    currentProgress = if (isPlaying) currentProgress else 0f,
                                    currentTimeString = if (isPlaying) currentTimeString else "00:00",
                                    onSeek = onSeekTo,
                                    onReorder = { },
                                    isDragging = draggingItemIndex == index,
                                    themeColors = themeColors,
                                    playlistPosition = playingPlaylistFiles.indexOf(item.name) + 1,
                                    waveformData = waveformCache[item.file.absolutePath] ?: emptyList(),
                                    onSeekAbsolute = onSeekAbsolute
                                )
                            }
                            item { Spacer(modifier = Modifier.height(80.dp)) }
                        }

                        if (draggingItem != null) {
                            val item = draggingItem!!
                            FileItem(
                                modifier = Modifier
                                    .offset { IntOffset(0, overlayOffsetY.roundToInt()) }
                                    .shadow(8.dp, RoundedCornerShape(12.dp)),
                                item = item,
                                isPlaying = item.file.name == playingFileName,
                                isPaused = if (item.file.name == playingFileName) isPaused else false,
                                isSelectionMode = isSelectionMode,
                                isSelected = selectedFiles.contains(item.name),
                                selectionOrder = selectedFiles.indexOf(item.name) + 1,
                                currentProgress = if (item.file.name == playingFileName) currentProgress else 0f,
                                currentTimeString = if (item.file.name == playingFileName) currentTimeString else "00:00",
                                onPlay = {},
                                onPause = {},
                                onResume = {},
                                onStop = {},
                                onToggleSelect = {},
                                onRename = {},
                                onTrim = {},
                                onMove = {},
                                onShare = {},
                                onDelete = {},
                                onSeek = {},
                                onReorder = {},
                                isDragging = true,
                                themeColors = themeColors,
                                playlistPosition = playingPlaylistFiles.indexOf(item.name) + 1
                            )
                        }
                    }
                }
            }
        }

        
        // Dialogs
        if (showRenameDialog && itemToModify != null) {
            RenameDialog(
                currentName = itemToModify!!.name,
                onDismiss = { showRenameDialog = false },
                onConfirm = { newName: String -> onRenameFile(itemToModify!!, newName); showRenameDialog = false },
                themeColors = themeColors
            )
        }

        if (showMoveDialog && itemToModify != null) {
            MoveFileDialog(
                categories = categories,
                onDismiss = { showMoveDialog = false },
                onSelect = { targetCat: String -> onMoveFile(itemToModify!!, targetCat); showMoveDialog = false }
            )
        }
        
        if (showDeleteDialog && recordingToDelete != null) {
            DeleteConfirmDialog(
                title = "Delete file?",
                text = "Are you sure you want to delete '${recordingToDelete!!.name}'?",
                onDismiss = { showDeleteDialog = false },
                onConfirm = { onDeleteFile(recordingToDelete!!); showDeleteDialog = false }
            )
        }
        
        if (showTrimDialog && recordingToTrim != null) {
             TrimAudioDialog(
                file = recordingToTrim!!.file,
                uri = recordingToTrim!!.uri,
                durationMs = recordingToTrim!!.durationMillis,
                onDismiss = { showTrimDialog = false },
                onConfirm = { start: Long, end: Long, replace: Boolean, removeSelection: Boolean, fadeInMs: Long, fadeOutMs: Long ->
                    onTrimFile(recordingToTrim!!.file, start, end, replace, removeSelection, fadeInMs, fadeOutMs)
                    showTrimDialog = false
                },
                themeColors = themeColors
             )
        }

    }
}
// Dialog Helpers adapted from MainActivity

// Dialog Definitions moved to ui/Dialogs.kt

// TrimAudioDialog moved to ui/TrimAudioDialog.kt



