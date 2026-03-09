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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audioloop.AppIcons
import com.example.audioloop.Playlist
import com.example.audioloop.RecordingItem
import com.example.audioloop.ui.theme.*
import com.example.audioloop.R
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
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
    val context = LocalContext.current
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
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // ── Premium Header ──
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), MaterialTheme.colorScheme.background)
                        )
                    )
                    .padding(horizontal = 20.dp, vertical = 12.dp)
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
                            .background(MaterialTheme.colorScheme.surface, CircleShape)
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                    ) {
                        Icon(AppIcons.ArrowBack, contentDescription = stringResource(R.string.a11y_close), tint = MaterialTheme.colorScheme.onSurface)
                    }

                    Text(
                        text = if (playlist.id.startsWith("new_")) stringResource(R.string.title_new_playlist) else stringResource(R.string.title_edit_playlist),
                        style = TextStyle(
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        ),
                        modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Button(
                        onClick = {
                            onSave(
                                playlist.copy(
                                    name = name.ifBlank { context.getString(R.string.label_untitled) },
                                    files = files.toList(),
                                    gapSeconds = gapSeconds,
                                    shuffle = shuffle,
                                    speed = speed,
                                    loopCount = loopCount,
                                    sleepMinutes = sleepMinutes
                                )
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        shape = RoundedCornerShape(16.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp)
                    ) {
                        Icon(AppIcons.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.btn_save), fontWeight = FontWeight.Bold)
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
                            stringResource(R.string.label_playlist_name_header),
                            style = TextStyle(color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                            modifier = Modifier.padding(bottom = 8.dp, start = 4.dp)
                        )
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = TextStyle(color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold),
                            placeholder = { Text(stringResource(R.string.hint_enter_name), color = MaterialTheme.colorScheme.outline) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = themeColors.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                                focusedContainerColor = MaterialTheme.colorScheme.surface,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surface
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
                            .background(if (shuffle) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f) else MaterialTheme.colorScheme.surface)
                            .border(1.dp, if (shuffle) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
                            .clickable { shuffle = !shuffle }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier.size(36.dp).clip(CircleShape).background(if (shuffle) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("🔀", fontSize = 18.sp)

                        }
                        Spacer(Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.label_shuffle_playback), color = Color.White, fontWeight = FontWeight.Bold)
                            Text(stringResource(R.string.label_shuffle_desc), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                        }
                        Switch(
                            checked = shuffle,
                            onCheckedChange = { shuffle = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = themeColors.primary,
                                uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
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
                                stringResource(R.string.label_tracks_header),
                                style = TextStyle(color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                            )
                            Text(
                                stringResource(R.string.label_playlist_tracks, files.size),
                                style = TextStyle(color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            )
                        }
                        
                        TextButton(
                            onClick = { showFilePicker = true },
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(AppIcons.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.btn_add_files), fontWeight = FontWeight.Bold)
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
                            Text(stringResource(R.string.label_no_tracks), color = MaterialTheme.colorScheme.onSurfaceVariant)
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
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(16.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Track Handle / Index
        Box(
            modifier = Modifier.size(32.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Text("${index + 1}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp
            )
        }

        IconButton(onClick = onRemove) {
            Icon(AppIcons.Close, contentDescription = stringResource(R.string.a11y_remove), tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(16.dp))
        }
    }
}
