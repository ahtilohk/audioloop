package com.example.audioloop.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audioloop.AppIcons
import com.example.audioloop.Playlist
import com.example.audioloop.RecordingItem
import com.example.audioloop.ui.theme.*
import kotlin.math.roundToInt

@Composable
fun PlaylistEditorScreen(
    playlist: Playlist,
    allCategories: List<String>,
    getFilesForCategory: (String) -> List<RecordingItem>,
    getCategoryForFile: (String) -> String,
    resolveFileName: (String) -> String,
    resolveFileDuration: (String) -> String,
    onSave: (Playlist) -> Unit,
    onClose: () -> Unit,
    themeColors: AppColorPalette
) {
    var name by remember { mutableStateOf(playlist.name) }
    var files = remember { mutableStateListOf<String>().apply { addAll(playlist.files) } }
    
    // Settings state
    var gapSeconds by remember { mutableStateOf(playlist.gapSeconds) }
    var shuffle by remember { mutableStateOf(playlist.shuffle) }
    var speed by remember { mutableStateOf(playlist.speed) }
    var loopCount by remember { mutableStateOf(playlist.loopCount) }
    var sleepMinutes by remember { mutableStateOf(playlist.sleepMinutes) }

    var showFilePicker by remember { mutableStateOf(false) }
    var settingsOpen by remember { mutableStateOf(false) }

    val scrollState = rememberLazyListState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Zinc950)
            .statusBarsPadding()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // ── Premium Header ──
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(themeColors.primary900.copy(alpha = 0.4f), Zinc950)
                        )
                    )
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier
                            .size(40.dp)
                            .background(Zinc900, CircleShape)
                            .border(1.dp, Zinc700, CircleShape)
                    ) {
                        Icon(AppIcons.ArrowBack, contentDescription = "Back", tint = Zinc300)
                    }

                    Text(
                        text = if (playlist.id.startsWith("new_")) "New Playlist" else "Edit Playlist",
                        style = TextStyle(
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                    )

                    Button(
                        onClick = {
                            onSave(
                                playlist.copy(
                                    name = name.ifBlank { "Untitled" },
                                    files = files.toList(),
                                    gapSeconds = gapSeconds,
                                    shuffle = shuffle,
                                    speed = speed,
                                    loopCount = loopCount,
                                    sleepMinutes = sleepMinutes
                                )
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = themeColors.primary600),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp)
                    ) {
                        Icon(AppIcons.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Save", fontWeight = FontWeight.Bold)
                    }
                }
            }

            LazyColumn(
                state = scrollState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 100.dp) // Space for floating buttons or safety
            ) {
                // ── Name Input ──
                item {
                    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                        Text(
                            "PLAYLIST NAME",
                            style = TextStyle(color = Zinc500, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                            modifier = Modifier.padding(bottom = 8.dp, start = 4.dp)
                        )
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = TextStyle(color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold),
                            placeholder = { Text("Enter name...", color = Zinc600) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = themeColors.primary,
                                unfocusedBorderColor = Zinc700,
                                focusedContainerColor = Zinc900,
                                unfocusedContainerColor = Zinc900
                            ),
                            shape = RoundedCornerShape(16.dp),
                            singleLine = true
                        )
                    }
                }

                // ── Playback Settings ──
                item {
                    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                        PlaybackSettingsCard(
                            settingsOpen = settingsOpen,
                            onToggleSettings = { settingsOpen = !settingsOpen },
                            selectedSpeed = speed,
                            onSpeedChange = { speed = it },
                            selectedLoopCount = loopCount,
                            onLoopCountChange = { loopCount = it },
                            isShadowing = false, // Not used in playlist context yet
                            onShadowingChange = {},
                            shadowPauseSeconds = 0,
                            onShadowPauseChange = {},
                            selectedSleepMinutes = sleepMinutes,
                            onSleepTimerChange = { sleepMinutes = it },
                            sleepTimerRemainingMs = 0,
                            gapSeconds = gapSeconds,
                            onGapChange = { gapSeconds = it },
                            themeColors = themeColors,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                // ── Shuffle Toggle ──
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 12.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (shuffle) themeColors.primary900.copy(alpha = 0.3f) else Zinc900)
                            .border(1.dp, if (shuffle) themeColors.primary600 else Zinc700, RoundedCornerShape(16.dp))
                            .clickable { shuffle = !shuffle }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier.size(36.dp).clip(CircleShape).background(if (shuffle) themeColors.primary600 else Zinc800),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("🔀", fontSize = 18.sp)
                        }
                        Spacer(Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Shuffle Playback", color = Color.White, fontWeight = FontWeight.Bold)
                            Text("Play tracks in random order", color = Zinc500, fontSize = 12.sp)
                        }
                        Switch(
                            checked = shuffle,
                            onCheckedChange = { shuffle = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = themeColors.primary,
                                uncheckedThumbColor = Zinc400,
                                uncheckedTrackColor = Zinc800
                            )
                        )
                    }
                }

                // ── Tracks Header ──
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Column {
                            Text(
                                "TRACKS",
                                style = TextStyle(color = Zinc500, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                            )
                            Text(
                                "${files.size} songs selected",
                                style = TextStyle(color = Zinc300, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            )
                        }
                        
                        TextButton(
                            onClick = { showFilePicker = true },
                            colors = ButtonDefaults.textButtonColors(contentColor = themeColors.primary400)
                        ) {
                            Icon(AppIcons.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Add Tracks", fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // ── Track List (Drag & Drop) ──
                if (files.isEmpty()) {
                    item {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(40.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("🎵", fontSize = 40.sp)
                            Spacer(Modifier.height(12.dp))
                            Text("No tracks added", color = Zinc500)
                        }
                    }
                } else {
                    itemsIndexed(files) { index, filePath ->
                        TrackItem(
                            index = index,
                            filePath = filePath,
                            resolveFileName = resolveFileName,
                            resolveFileDuration = resolveFileDuration,
                            getCategoryForFile = getCategoryForFile,
                            onRemove = { files.removeAt(index) },
                            themeColors = themeColors
                        )
                    }
                }
            }
        }

        // Floating File Picker
        AnimatedVisibility(
            visible = showFilePicker,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut()
        ) {
            FilePickerSheet(
                allCategories = allCategories,
                getFilesForCategory = getFilesForCategory,
                alreadySelected = files,
                onConfirm = { selected ->
                    val combined = (files + selected).distinct()
                    files.clear()
                    files.addAll(combined)
                    showFilePicker = false
                },
                onClose = { showFilePicker = false },
                themeColors = themeColors
            )
        }
    }
}

@Composable
private fun TrackItem(
    index: Int,
    filePath: String,
    resolveFileName: (String) -> String,
    resolveFileDuration: (String) -> String,
    getCategoryForFile: (String) -> String,
    onRemove: () -> Unit,
    themeColors: AppColorPalette
) {
    val fileName = resolveFileName(filePath)
    val duration = resolveFileDuration(filePath)
    val category = getCategoryForFile(filePath)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Zinc900)
            .border(1.dp, Zinc800, RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Track Handle / Index
        Box(
            modifier = Modifier.size(32.dp).clip(CircleShape).background(Zinc800),
            contentAlignment = Alignment.Center
        ) {
            Text("${index + 1}", color = Zinc400, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        
        Spacer(Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                fileName.substringBeforeLast("."),
                color = Color.White,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "$category \u00b7 $duration",
                color = Zinc500,
                fontSize = 11.sp
            )
        }

        IconButton(onClick = onRemove) {
            Icon(AppIcons.Close, contentDescription = "Remove", tint = Zinc600, modifier = Modifier.size(16.dp))
        }
    }
}
