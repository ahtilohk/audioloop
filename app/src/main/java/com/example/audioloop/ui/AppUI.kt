package com.example.audioloop.ui

import androidx.compose.animation.AnimatedVisibility
import kotlinx.coroutines.*
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
import android.media.MediaPlayer
import com.example.audioloop.AppIcons
import com.example.audioloop.RecordingItem
import com.example.audioloop.ui.theme.*
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import java.io.File
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import android.graphics.Paint

import androidx.compose.runtime.mutableFloatStateOf


@Composable
fun FileItem(
    modifier: Modifier = Modifier,
    item: RecordingItem,
    isPlaying: Boolean,
    isPaused: Boolean,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    selectionOrder: Int,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
    onToggleSelect: () -> Unit,
    onRename: () -> Unit,
    onTrim: () -> Unit,
    onMove: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    currentProgress: Float = 0f,
    currentTimeString: String = "00:00",
    onSeek: (Float) -> Unit = {},
    onReorder: (Int) -> Unit = {},
    isDragging: Boolean = false,
    themeColors: com.example.audioloop.ui.theme.AppColorPalette = com.example.audioloop.ui.theme.AppTheme.CYAN.palette,
    playlistPosition: Int = 0 // 0 means not in playlist, >0 is position in playlist
) {
    var menuExpanded by remember { mutableStateOf(false) }

    // Modern MD3 card states
    val backgroundColor = when {
        isDragging -> themeColors.primaryContainer.copy(alpha = 0.6f)
        isPlaying -> themeColors.primaryContainer.copy(alpha = 0.4f)
        isSelected -> themeColors.secondaryContainer.copy(alpha = 0.3f)
        else -> Zinc800.copy(alpha = 0.3f)
    }

    val borderColor = when {
        isDragging -> themeColors.primary
        isPlaying -> themeColors.primary.copy(alpha = 0.8f)
        isSelected -> themeColors.secondary.copy(alpha = 0.6f)
        else -> Zinc700.copy(alpha = 0.3f)
    }

    val scale by animateFloatAsState(targetValue = if (isDragging) 1.03f else 1f, label = "scale")
    val elevation by animateDpAsState(targetValue = if (isDragging) 8.dp else if (isPlaying) 4.dp else 2.dp, label = "elevation")

    Surface(
        onClick = {
            if (isSelectionMode) onToggleSelect() else if (isPlaying) onStop() else onPlay()
        },
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp, horizontal = 20.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        shape = RoundedCornerShape(16.dp),
        color = backgroundColor,
        border = BorderStroke(1.5.dp, borderColor),
        shadowElevation = elevation
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .padding(10.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {

                Box(
                    modifier = Modifier
                        .size(48.dp), // Minimum touch target
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = AppIcons.GripVertical,
                        contentDescription = "Drag to reorder",
                        tint = if (isDragging) themeColors.primary400 else Zinc600, // Visual feedback
                        modifier = Modifier.size(24.dp)
                    )
                }

            if (isSelectionMode) {
                Surface(
                    onClick = { onToggleSelect() },
                    modifier = Modifier.size(36.dp),
                    shape = CircleShape,
                    color = if (isSelected) themeColors.primary else Color.Transparent,
                    border = BorderStroke(
                        2.dp,
                        if (isSelected) themeColors.primary else Zinc500
                    )
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSelected) {
                            Text(
                                text = selectionOrder.toString(),
                                style = MaterialTheme.typography.labelMedium.copy(
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                    }
                }
            } else if (isPlaying) {
                // Modern Play/Pause Button
                if (!isPaused) {
                   FilledIconButton(
                        onClick = { onPause() },
                        modifier = Modifier.size(44.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = themeColors.primary,
                            contentColor = Color.White
                        )
                    ) {
                        // Pause Icon (Two vertical bars)
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Box(modifier = Modifier.size(width = 4.dp, height = 14.dp).background(Color.White, RoundedCornerShape(2.dp)))
                            Box(modifier = Modifier.size(width = 4.dp, height = 14.dp).background(Color.White, RoundedCornerShape(2.dp)))
                        }
                    }
                } else {
                    // Paused -> Show Play (Resume)
                    FilledIconButton(
                        onClick = { onResume() },
                        modifier = Modifier.size(44.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = themeColors.primary,
                            contentColor = Color.White
                        )
                    ) {
                        Icon(
                            imageVector = AppIcons.PlayArrow,
                            contentDescription = "Resume",
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

               // Stop Button
                FilledIconButton(
                    onClick = { onStop() },
                    modifier = Modifier.size(36.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = Red800.copy(alpha = 0.4f),
                        contentColor = Red400
                    )
                ) {
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .background(Red400, RoundedCornerShape(2.dp))
                    )
                }
            } else {
                // Not playing - show play button
                FilledIconButton(
                    onClick = { onPlay() },
                    modifier = Modifier.size(40.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = themeColors.primaryContainer,
                        contentColor = themeColors.onPrimaryContainer
                    )
                ) {
                    Icon(
                        imageVector = AppIcons.PlayArrow,
                        contentDescription = "Play",
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = item.name.substringBeforeLast("."),
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = if (isPlaying) Color.White else Zinc200,
                            fontWeight = if (isPlaying) FontWeight.SemiBold else FontWeight.Medium
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    // Selection/Playlist position badge
                    val badgeNumber = when {
                        isSelectionMode && isSelected -> selectionOrder
                        !isSelectionMode && playlistPosition > 0 -> playlistPosition
                        else -> 0
                    }
                    if (badgeNumber > 0) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = when {
                                isPlaying -> themeColors.primary
                                isSelectionMode -> themeColors.secondary
                                else -> themeColors.tertiary.copy(alpha = 0.8f)
                            }
                        ) {
                            Text(
                                text = "♫ $badgeNumber",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                ),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Text(
                    text = "${if(isPlaying) "Playing • " else ""}${item.durationString}",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = if (isPlaying) themeColors.primary else Zinc500
                    )
                )
            }

            if (!isSelectionMode && !isPlaying) {
                Box {
                    IconButton(
                        onClick = { menuExpanded = true },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = AppIcons.MoreVert,
                            contentDescription = "Menu",
                            tint = Zinc500,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                        modifier = Modifier.background(Zinc900).border(1.dp, Zinc800, RoundedCornerShape(4.dp))
                    ) {
                        DropdownMenuItem(
                            text = { Text("Rename", color = Zinc200) },
                            leadingIcon = { Icon(AppIcons.Edit, null, tint = Zinc400) },
                            onClick = { menuExpanded = false; onRename() }
                        )
                        DropdownMenuItem(
                            text = { Text("Trim", color = Zinc200) },
                            leadingIcon = { Icon(AppIcons.ContentCut, null, tint = Zinc400) },
                            onClick = { menuExpanded = false; onTrim() }
                        )
                        DropdownMenuItem(
                            text = { Text("Move", color = Zinc200) },
                            leadingIcon = { Icon(AppIcons.ArrowForward, null, tint = Zinc400) },
                            onClick = { menuExpanded = false; onMove() }
                        )
                        DropdownMenuItem(
                            text = { Text("Share", color = Zinc200) },
                            leadingIcon = { Icon(AppIcons.Share, null, tint = Zinc400) },
                            onClick = { menuExpanded = false; onShare() }
                        )
                        Divider(color = Zinc800)
                        DropdownMenuItem(
                            text = { Text("Delete", color = Red400) },
                            leadingIcon = { Icon(AppIcons.Delete, null, tint = Red400) },
                            onClick = { menuExpanded = false; onDelete() }
                        )
                    }
                }
            }
        }

        if (isPlaying) {
            Box(
                modifier = Modifier
                    .padding(start = 10.dp, end = 10.dp, bottom = 10.dp)
                    .fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(themeColors.primary900.copy(alpha = 0.3f))
                        .padding(8.dp)
                    // Waveform placeholder logic retained...
                ) {
                    Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                         // Waveform visual
                         Row(
                             modifier = Modifier
                                 .fillMaxWidth()
                                 .height(24.dp),
                             horizontalArrangement = Arrangement.SpaceBetween,
                             verticalAlignment = Alignment.Bottom
                         ) {
                             repeat(30) {
                                 Box(
                                     modifier = Modifier
                                         .weight(1f)
                                         .padding(horizontal = 0.5.dp)
                                         .fillMaxHeight(0.3f + (Math.random() * 0.7f).toFloat())
                                         .background(themeColors.primary400, CircleShape)
                                 )
                             }
                         }
                         
                         Spacer(modifier = Modifier.height(4.dp))
                         
                         // Slider & Time
                         Row(
                             modifier = Modifier.fillMaxWidth(),
                             verticalAlignment = Alignment.CenterVertically,
                             horizontalArrangement = Arrangement.spacedBy(8.dp)
                         ) {
                             Text(
                                 text = "$currentTimeString / ${item.durationString}",
                                 style = TextStyle(color = themeColors.primary300, fontSize = 10.sp, fontWeight = FontWeight.Medium),
                                 modifier = Modifier.width(80.dp)
                             )
                             Slider(
                                 value = currentProgress,
                                 onValueChange = onSeek,
                                 colors = SliderDefaults.colors(
                                     thumbColor = themeColors.primary200,
                                     activeTrackColor = themeColors.primary500,
                                     inactiveTrackColor = Zinc700.copy(alpha = 0.5f)
                                 ),
                                 modifier = Modifier.weight(1f).height(20.dp)
                             )
                         }
                    }
                }
            }
        }

    }
}

@Composable
fun CategoryManagementSheet(
    categories: List<String>,
    currentCategory: String,
    onSelect: (String) -> Unit,
    onAdd: (String) -> Unit,
    onRename: (String, String) -> Unit,
    onDelete: (String) -> Unit,
    onReorder: (List<String>) -> Unit,
    onClose: () -> Unit,
    themeColors: com.example.audioloop.ui.theme.AppColorPalette = com.example.audioloop.ui.theme.AppTheme.CYAN.palette
) {
    var newCategoryName by remember { mutableStateOf("") }
    var editingId by remember { mutableStateOf<String?>(null) }
    var editName by remember { mutableStateOf("") }
    val uiCategories = remember { mutableStateListOf<String>() }

    LaunchedEffect(categories) {
        uiCategories.clear()
        uiCategories.addAll(categories)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
            .clickable { onClose() },
        contentAlignment = Alignment.BottomCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.8f)
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .background(Zinc900)
                .clickable(enabled = false) {} // Prevent click through
        ) {
            // Drag Handle
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .width(40.dp)
                        .height(4.dp)
                        .background(Zinc600, CircleShape)
                )
            }

            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Categories",
                    style = TextStyle(color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                )
                IconButton(
                    onClick = onClose,
                    modifier = Modifier
                        .size(32.dp)
                        .background(Zinc800, CircleShape)
                ) {
                    Icon(AppIcons.Close, contentDescription = "Close", tint = Zinc400, modifier = Modifier.size(18.dp))
                }
            }

            // Add new
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                BasicTextField(
                    value = newCategoryName,
                    onValueChange = { newCategoryName = it },
                    singleLine = true,
                    textStyle = TextStyle(color = Color.White, fontSize = 14.sp),
                    cursorBrush = SolidColor(themeColors.primary500),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .background(Zinc800, RoundedCornerShape(12.dp))
                        .border(1.dp, Zinc700, RoundedCornerShape(12.dp))
                        .padding(horizontal = 16.dp),
                    decorationBox = { innerTextField ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (newCategoryName.isEmpty()) Text("New category name...", color = Zinc500, fontSize = 14.sp)
                            innerTextField()
                        }
                    }
                )
                Button(
                    onClick = {
                        if (newCategoryName.isNotBlank()) {
                            val newCat = newCategoryName.trim()
                            // Add to local list immediately for instant UI update
                            if (!uiCategories.contains(newCat)) {
                                uiCategories.add(newCat)
                            }
                            onAdd(newCat)
                            newCategoryName = ""
                        }
                    },
                    modifier = Modifier.height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = themeColors.primary600),
                    shape = RoundedCornerShape(12.dp),
                    enabled = newCategoryName.isNotBlank()
                ) {
                    Text("Add", color = Color.White)
                }
            }

            Divider(color = Zinc800, thickness = 1.dp, modifier = Modifier.padding(horizontal = 20.dp))

            // List
            val density = LocalDensity.current
            val scrollState = rememberLazyListState()
            val scope = rememberCoroutineScope()
            var draggingCategoryIndex by remember { mutableIntStateOf(-1) }
            var draggingCategory by remember { mutableStateOf<String?>(null) }
            var overlayOffsetY by remember { mutableFloatStateOf(0f) }
            var grabOffsetY by remember { mutableFloatStateOf(0f) }
            var draggingItemSizePx by remember { mutableFloatStateOf(0f) }
            var overscrollSpeed by remember { mutableFloatStateOf(0f) }

            fun checkForSwap() {
                if (uiCategories.size <= 1) return
                val currentVisibleItems = scrollState.layoutInfo.visibleItemsInfo
                if (currentVisibleItems.isEmpty()) return

                val overlayCenterY = overlayOffsetY + (draggingItemSizePx / 2f)
                var targetIndex = -1
                val hoveredItem = currentVisibleItems.find {
                    overlayCenterY >= it.offset && overlayCenterY <= it.offset + it.size
                }

                if (hoveredItem != null) {
                    targetIndex = hoveredItem.index
                } else {
                    val firstVisible = currentVisibleItems.first()
                    val lastVisible = currentVisibleItems.last()
                    if (overlayCenterY < firstVisible.offset) {
                        targetIndex = firstVisible.index
                    } else if (overlayCenterY > lastVisible.offset + lastVisible.size) {
                        targetIndex = lastVisible.index
                    }
                }

                if (targetIndex == 0) {
                    targetIndex = 1
                }

                if (targetIndex != -1 && targetIndex != draggingCategoryIndex) {
                    if (draggingCategoryIndex in uiCategories.indices && targetIndex in uiCategories.indices) {
                        val itemToMove = uiCategories.removeAt(draggingCategoryIndex)
                        uiCategories.add(targetIndex, itemToMove)
                        draggingCategoryIndex = targetIndex
                    }
                }
            }

            LaunchedEffect(overscrollSpeed) {
                if (overscrollSpeed != 0f) {
                    while (true) {
                        val scrolled = scrollState.scrollBy(overscrollSpeed)
                        if (scrolled != 0f) {
                            checkForSwap()
                        }
                        kotlinx.coroutines.delay(10)
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .pointerInput(Unit) {
                        val gripWidth = 60.dp.toPx()
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = true)
                            val y = down.position.y
                            val x = down.position.x
                            val hitItem = scrollState.layoutInfo.visibleItemsInfo.find {
                                y >= it.offset && y <= it.offset + it.size
                            }

                            if (hitItem != null && x <= gripWidth) {
                                val index = hitItem.index
                                if (index in uiCategories.indices && uiCategories[index] != "General") {
                                    down.consume()
                                    draggingCategoryIndex = index
                                    draggingCategory = uiCategories[index]

                                    val itemTop = hitItem.offset.toFloat()
                                    draggingItemSizePx = hitItem.size.toFloat()
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

                                            overscrollSpeed = when {
                                                newY < threshold -> -maxSpeed * ((threshold - newY) / threshold).coerceIn(0.1f, 1f)
                                                newY > viewportHeight - threshold -> maxSpeed * ((newY - (viewportHeight - threshold)) / threshold).coerceIn(0.1f, 1f)
                                                else -> 0f
                                            }
                                            checkForSwap()
                                        }
                                    }

                                    overscrollSpeed = 0f
                                    draggingCategoryIndex = -1
                                    draggingCategory = null
                                    draggingItemSizePx = 0f
                                    onReorder(uiCategories.toList())
                                }
                            }
                        }
                    }
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .navigationBarsPadding(),
                    state = scrollState,
                    contentPadding = PaddingValues(start = 20.dp, top = 20.dp, end = 20.dp, bottom = 40.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        Text(
                            text = "Manage your categories",
                            style = TextStyle(color = Zinc500, fontSize = 12.sp),
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }
                    itemsIndexed(uiCategories, key = { _, cat -> cat }) { index, cat ->
                        val isEditing = editingId == cat
                        val isDragging = draggingCategoryIndex == index
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !isEditing && !isDragging) {
                                    onSelect(cat)
                                    onClose()
                                }
                                .alpha(if (isDragging) 0f else 1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (currentCategory == cat) themeColors.primary900.copy(alpha = 0.3f) else Zinc800.copy(alpha = 0.5f))
                                .border(
                                    1.dp,
                                    if (currentCategory == cat) themeColors.primary500.copy(alpha = 0.5f) else Zinc700.copy(alpha = 0.5f),
                                    RoundedCornerShape(12.dp)
                                )
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                AppIcons.GripVertical,
                                contentDescription = null,
                                tint = if (cat == "General") Zinc700 else Zinc500,
                                modifier = Modifier.size(18.dp)
                            )

                            if (isEditing) {
                                BasicTextField(
                                    value = editName,
                                    onValueChange = { editName = it },
                                    singleLine = true,
                                    textStyle = TextStyle(color = Color.White, fontSize = 14.sp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .background(Zinc900, RoundedCornerShape(8.dp))
                                        .border(1.dp, themeColors.primary500, RoundedCornerShape(8.dp))
                                        .padding(8.dp),
                                    keyboardActions = KeyboardActions(onDone = {
                                        if (editName.isNotBlank() && editName != cat) {
                                            onRename(cat, editName.trim())
                                        }
                                        editingId = null
                                    })
                                )
                            } else {
                                Text(
                                    text = cat,
                                    style = TextStyle(
                                        color = if (currentCategory == cat) themeColors.primary300 else Zinc200,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium
                                    ),
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            if (cat != "General" && !isEditing) {
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    IconButton(
                                        onClick = {
                                            editingId = cat
                                            editName = cat
                                        },
                                        modifier = Modifier
                                            .size(32.dp)
                                            .background(Zinc700.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                    ) {
                                        Icon(AppIcons.Edit, contentDescription = "Edit", tint = Zinc400, modifier = Modifier.size(14.dp))
                                    }
                                    IconButton(
                                        onClick = { onDelete(cat) },
                                        modifier = Modifier
                                            .size(32.dp)
                                            .background(Zinc700.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                    ) {
                                        Icon(AppIcons.Delete, contentDescription = "Delete", tint = Zinc400, modifier = Modifier.size(14.dp))
                                    }
                                }
                            } else if (cat == "General") {
                                Box(
                                    modifier = Modifier
                                        .background(Zinc800, RoundedCornerShape(4.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text("default", color = Zinc500, fontSize = 10.sp)
                                }
                            } else if (isEditing) {
                                IconButton(
                                    onClick = {
                                        if (editName.isNotBlank() && editName != cat) {
                                            onRename(cat, editName.trim())
                                        }
                                        editingId = null
                                    },
                                    modifier = Modifier
                                        .size(32.dp)
                                        .background(themeColors.primary600, RoundedCornerShape(8.dp))
                                ) {
                                    Icon(AppIcons.Check, contentDescription = "Save", tint = Color.White, modifier = Modifier.size(14.dp))
                                }
                            }
                        }
                    }
                }

                if (draggingCategory != null) {
                    val cat = draggingCategory!!
                    Row(
                        modifier = Modifier
                            .offset { IntOffset(0, overlayOffsetY.roundToInt()) }
                            .shadow(8.dp, RoundedCornerShape(12.dp))
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Zinc800.copy(alpha = 0.7f))
                            .border(1.dp, Zinc700, RoundedCornerShape(12.dp))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(AppIcons.GripVertical, contentDescription = null, tint = Zinc500, modifier = Modifier.size(18.dp))
                        Text(
                            text = cat,
                            style = TextStyle(color = Zinc200, fontSize = 14.sp, fontWeight = FontWeight.Medium),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

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
    onTrimFile: (File, Long, Long, Boolean, Boolean) -> Unit,
    selectedSpeed: Float,
    selectedLoopCount: Int,
    isShadowing: Boolean,
    onShadowingChange: (Boolean) -> Unit,
    
    usePublicStorage: Boolean,
    onPublicStorageChange: (Boolean) -> Unit,
    sleepTimerRemainingMs: Long = 0L,
    selectedSleepMinutes: Int = 0,
    onSleepTimerChange: (Int) -> Unit = {},
    currentTheme: com.example.audioloop.ui.theme.AppTheme = com.example.audioloop.ui.theme.AppTheme.CYAN,
    onThemeChange: (com.example.audioloop.ui.theme.AppTheme) -> Unit = {}
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Zinc950)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
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
                Text(
                    text = "Loop & Learn",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        brush = Brush.horizontalGradient(
                            colors = listOf(themeColors.primary300, themeColors.primary400)
                        ),
                        fontWeight = FontWeight.Bold
                    )
                )

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
                            text = if (isSelectionMode) "Cancel" else "Playlist",
                            style = MaterialTheme.typography.labelLarge.copy(
                                color = if (isSelectionMode) Red400 else themeColors.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                    }
                }
            }

            // Category Navigation - MD3 Chips Style
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                LazyRow(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(categories) { cat ->
                        val isActive = currentCategory == cat
                        Surface(
                            onClick = { onCategoryChange(cat) },
                            shape = RoundedCornerShape(20.dp),
                            color = if (isActive)
                                themeColors.primaryContainer
                            else
                                Zinc800.copy(alpha = 0.4f),
                            border = if (isActive)
                                BorderStroke(1.5.dp, themeColors.primary)
                            else
                                BorderStroke(1.dp, Zinc700.copy(alpha = 0.5f))
                        ) {
                            Text(
                                text = cat,
                                style = MaterialTheme.typography.labelLarge.copy(
                                    color = if (isActive) themeColors.onPrimaryContainer else Zinc400,
                                    fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Medium
                                ),
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                            )
                        }
                    }
                }

                // Category Management Button
                FilledIconButton(
                    onClick = { showCategorySheet = true },
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = themeColors.secondaryContainer,
                        contentColor = themeColors.onSecondaryContainer
                    ),
                    modifier = Modifier.size(40.dp)
                ) {
                   Icon(
                       imageVector = AppIcons.Edit,
                       contentDescription = "Manage Categories",
                       modifier = Modifier.size(20.dp)
                   )
                }
            }

            HorizontalDivider(
                color = Zinc700.copy(alpha = 0.3f),
                thickness = 1.dp,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
            )

            // Recording Section - Modern MD3 Design
            AnimatedVisibility(visible = !isSelectionMode) {
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
                            .border(1.dp, Zinc700.copy(alpha = 0.3f), RoundedCornerShape(24.dp))
                            .padding(6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("Speech" to AppIcons.Mic, "Stream" to AppIcons.Radio).forEach { (m, icon) ->
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

                    // Record Button - Modern FAB-Inspired Design
                    Surface(
                        onClick = {
                            if (isRecording) {
                                onStopRecord()
                                isRecording = false
                            } else {
                                val dateFormat = java.text.SimpleDateFormat("yyyy.MM.dd HH:mm", java.util.Locale.getDefault())
                                val dateStr = dateFormat.format(java.util.Date())
                                val prefix = if (mode == "Speech") "Speech" else "Stream"
                                val name = "${prefix}_$dateStr"
                                val commenced = onStartRecord(name, mode == "Stream")
                                if (commenced) isRecording = true
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(72.dp),
                        shape = RoundedCornerShape(20.dp),
                        color = if (isRecording)
                            Red800.copy(alpha = 0.4f)
                        else
                            themeColors.primaryContainer,
                        border = BorderStroke(
                            2.dp,
                            if (isRecording) Red500 else themeColors.primary
                        ),
                        shadowElevation = if (isRecording) 0.dp else 4.dp
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                // Record indicator/icon
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .background(
                                            if (isRecording) Red500 else themeColors.primary,
                                            CircleShape
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isRecording) {
                                        // Pulsing stop icon
                                        Box(
                                            modifier = Modifier
                                                .size(16.dp)
                                                .background(Color.White, RoundedCornerShape(3.dp))
                                        )
                                    } else {
                                        Icon(
                                            imageVector = if (mode == "Speech") AppIcons.Mic else AppIcons.Radio,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                }

                                // Record button text
                                Column(horizontalAlignment = Alignment.Start) {
                                    Text(
                                        text = if (isRecording) "Recording..." else "Start Recording",
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            color = if (isRecording) Red400 else themeColors.onPrimaryContainer,
                                            fontWeight = FontWeight.Bold
                                        )
                                    )
                                    Text(
                                        text = if (isRecording) "Tap to stop" else "$mode mode",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = if (isRecording)
                                                Red400.copy(alpha = 0.7f)
                                            else
                                                themeColors.onPrimaryContainer.copy(alpha = 0.7f)
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
                border = BorderStroke(1.dp, Zinc700.copy(alpha = 0.3f))
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
                                val loopText = if (selectedLoopCount == -1) "∞" else "${selectedLoopCount}x"
                                val sleepText = if (sleepTimerRemainingMs > 0L) {
                                    val totalSec = (sleepTimerRemainingMs / 1000).toInt()
                                    String.format("%d:%02d", totalSec / 60, totalSec % 60)
                                } else "Off"

                                Text(
                                    text = "Speed ${selectedSpeed}x • Loop $loopText • Sleep $sleepText",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = Zinc500
                                    )
                                )
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
                            .border(1.dp, Zinc800, RoundedCornerShape(12.dp))
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
                                    val label = if (r == -1) "∞" else "${r}x"
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
                            com.example.audioloop.ui.theme.AppTheme.entries.forEach { theme ->
                                val isSelected = currentTheme == theme
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isSelected) theme.palette.primary600 else Zinc800)
                                        .border(
                                            width = if (isSelected) 2.dp else 1.dp,
                                            color = if (isSelected) theme.palette.primary400 else Zinc700,
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
                            com.example.audioloop.ui.theme.AppTheme.entries.forEach { theme ->
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
            }

            HorizontalDivider(color = Zinc800, thickness = 1.dp, modifier = Modifier.padding(horizontal = 16.dp))

            // File List
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                 Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isSelectionMode && selectedFiles.isNotEmpty()) {
                        Button(
                            onClick = {
                                // Sort files by selection order
                                val orderedSelection = selectedFiles.toList()
                                val filesToPlay = orderedSelection.mapNotNull { name ->
                                    recordingItems.find { it.name == name }
                                }
                                if (filesToPlay.isNotEmpty()) {
                                    // Track playlist files for visual indication
                                    playingPlaylistFiles = filesToPlay.map { it.name }
                                    onStartPlaylist(filesToPlay, selectedLoopCount == -1, selectedSpeed) {
                                        // Clear playlist tracking when playback completes
                                        playingPlaylistFiles = emptyList()
                                    }
                                    isSelectionMode = false
                                    selectedFiles = emptySet()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = themeColors.primary600),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("PLAY ${selectedFiles.size} SELECTED", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Text(
                            text = "Files ($currentCategory):",
                            style = TextStyle(color = Zinc200, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        )
                        if (!isSelectionMode) {
                            Button(
                                onClick = { filePickerLauncher.launch("audio/*") },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = themeColors.primary600),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.height(28.dp)
                            ) {
                                Text("+ Add file", fontSize = 12.sp, color = Color.White)
                            }
                        }
                    }
                } // End of Category Row
                
                val density = LocalDensity.current
                val scrollState = rememberLazyListState()
                val scope = rememberCoroutineScope()
                
                // Drag State - Overlay Approach
                var draggingItemIndex by remember { mutableIntStateOf(-1) }
                var draggingItem by remember { mutableStateOf<RecordingItem?>(null) }
                var overlayOffsetY by remember { mutableFloatStateOf(0f) }
                var grabOffsetY by remember { mutableFloatStateOf(0f) }
                
                // Auto-scroll logic
                var overscrollSpeed by remember { mutableFloatStateOf(0f) }
                
                fun checkForSwap() {
                    val currentVisibleItems = scrollState.layoutInfo.visibleItemsInfo
                    if (currentVisibleItems.isEmpty()) return
                    
                    val overlayCenterY = overlayOffsetY + (with(density) { 36.dp.toPx() }) // Approx center

                    var targetIndex = -1
                    
                    // 1. Try exact match (Hover)
                    val hoveredItem = currentVisibleItems.find { 
                        overlayCenterY >= it.offset && overlayCenterY <= it.offset + it.size
                    }
                    
                    if (hoveredItem != null) {
                        targetIndex = hoveredItem.index
                    } else {
                        // 2. Fallback: Check for out-of-bounds (Too high or Too low)
                        // This handles fast drags where the overlay flies past the items
                        val firstVisible = currentVisibleItems.first()
                        val lastVisible = currentVisibleItems.last()
                        
                        if (overlayCenterY < firstVisible.offset) {
                            targetIndex = firstVisible.index
                        } else if (overlayCenterY > lastVisible.offset + lastVisible.size) {
                            targetIndex = lastVisible.index
                        }
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
                            if (scrolled != 0f) {
                                checkForSwap()
                            }
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
                            
                            val hitItem = scrollState.layoutInfo.visibleItemsInfo.find { 
                                y >= it.offset && y <= it.offset + it.size
                            }
                            
                            if (hitItem != null && x <= gripWidth) {
                                down.consume()
                                
                                val index = hitItem.index
                                if (index in uiRecordingItems.indices) {
                                    draggingItemIndex = index
                                    draggingItem = uiRecordingItems[index]
                                    draggingItemName = draggingItem?.name
                                    
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
                                            val maxSpeed = 20.dp.toPx() // Faster scroll
                                            
                                            if (newY < threshold) {
                                                overscrollSpeed = -maxSpeed * ((threshold - newY) / threshold).coerceIn(0.1f, 1f)
                                            } else if (newY > viewportHeight - threshold) {
                                                overscrollSpeed = maxSpeed * ((newY - (viewportHeight - threshold)) / threshold).coerceIn(0.1f, 1f)
                                            } else {
                                                overscrollSpeed = 0f
                                            }
                                            checkForSwap()
                                        }
                                    }
                                    
                                    // End Drag
                                    overscrollSpeed = 0f
                                    onReorderFinished(uiRecordingItems.map { it.file })
                                    draggingItemIndex = -1
                                    draggingItem = null
                                    draggingItemName = null
                                }
                            }
                        }
                    }
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    state = scrollState,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                   itemsIndexed(uiRecordingItems, key = { _: Int, item: RecordingItem -> item.name }) { index, item ->
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
                            onPlay = {
                                onStartPlaylist(listOf(item), selectedLoopCount == -1, selectedSpeed, { /* onComplete */ })
                            },
                            onPause = onPausePlay,
                            onResume = onResumePlay,
                            onStop = { playingPlaylistFiles = emptyList(); onStopPlay() },
                            onToggleSelect = { toggleSelection(item.name) },
                            onRename = { itemToModify = item; showRenameDialog = true },
                            onTrim = { recordingToTrim = item; showTrimDialog = true },
                            onMove = { itemToModify = item; showMoveDialog = true },
                            onShare = { onShareFile(item) },
                            onDelete = { recordingToDelete = item; showDeleteDialog = true },
                            currentProgress = if (isPlaying) currentProgress else 0f,
                            currentTimeString = if (isPlaying) currentTimeString else "00:00",
                            onSeek = onSeekTo,
                            onReorder = { /* Legacy disabled */ },
                            isDragging = draggingItemIndex == index,
                            themeColors = themeColors,
                            playlistPosition = playingPlaylistFiles.indexOf(item.name) + 1
                        )
                   }
                   item { Spacer(modifier = Modifier.height(80.dp)) }
                }

                // Drag Overlay
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
        
        if (showCategorySheet) {
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
                onConfirm = { start: Long, end: Long, replace: Boolean, removeSelection: Boolean ->
                    onTrimFile(recordingToTrim!!.file, start, end, replace, removeSelection)
                    showTrimDialog = false
                },
                themeColors = themeColors
             )
        }
    }
}

// Dialog Helpers adapted from MainActivity

@Composable
fun RenameDialog(currentName: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit, themeColors: com.example.audioloop.ui.theme.AppColorPalette = com.example.audioloop.ui.theme.AppTheme.CYAN.palette) {
    var textState by remember { mutableStateOf(TextFieldValue(text = currentName, selection = TextRange(currentName.length))) }
    val focusRequester = remember { FocusRequester() }
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    
    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Zinc900),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Rename", style = TextStyle(color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold))
                Spacer(modifier = Modifier.height(16.dp))
                
                OutlinedTextField(
                    value = textState,
                    onValueChange = { textState = it },
                    label = { Text("New name") },
                    singleLine = true,
                    modifier = Modifier.focusRequester(focusRequester),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Zinc300,
                        focusedBorderColor = themeColors.primary500,
                        unfocusedBorderColor = Zinc600,
                        focusedLabelColor = themeColors.primary500,
                        unfocusedLabelColor = Zinc500
                    ),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
                )
                
                LaunchedEffect(Unit) { focusRequester.requestFocus() }
                
                Spacer(modifier = Modifier.height(20.dp))
                
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onDismiss) { Text("Cancel", color = Zinc400) }
                    Button(
                        onClick = { onConfirm(textState.text) },
                        colors = ButtonDefaults.buttonColors(containerColor = themeColors.primary600)
                    ) { Text("Save") }
                }
            }
        }
    }
}

@Composable
fun MoveFileDialog(categories: List<String>, onDismiss: () -> Unit, onSelect: (String) -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Zinc900),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp).heightIn(max = 400.dp)) {
                Text("Select Category", style = TextStyle(color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold), modifier = Modifier.padding(bottom = 12.dp))
                LazyColumn {
                    items(categories) { cat ->
                        Text(
                            text = cat,
                            color = Zinc200,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(cat) }
                                .padding(vertical = 12.dp),
                            fontSize = 16.sp
                        )
                        HorizontalDivider(color = Zinc800)
                    }
                }
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text("Cancel", color = Zinc400) }
            }
        }
    }
}

@Composable
fun DeleteConfirmDialog(title: String, text: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Zinc900,
        titleContentColor = Color.White,
        textContentColor = Zinc300,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { 
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = Red600)
            ) { Text("Delete") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = Zinc400) } }
    )
}

private enum class TrimDragTarget {
    Start,
    End,
    Playhead
}

private enum class TrimMode {
    Keep,
    Remove
}

@Composable
fun TrimAudioDialog(
    file: File,
    uri: android.net.Uri,
    durationMs: Long,
    onDismiss: () -> Unit,
    onConfirm: (start: Long, end: Long, replace: Boolean, removeSelection: Boolean) -> Unit,
    themeColors: com.example.audioloop.ui.theme.AppColorPalette = com.example.audioloop.ui.theme.AppTheme.CYAN.palette
) {
    var range by remember { mutableStateOf(0f..durationMs.toFloat()) }
    val context = LocalContext.current
    val previewPlayer = remember(file) { MediaPlayer() }
    var isPreviewPlaying by remember { mutableStateOf(false) }
    var previewPositionMs by remember { mutableLongStateOf(0L) }
    var trimMode by remember { mutableStateOf(TrimMode.Keep) }
    var playerReady by remember { mutableStateOf(false) }
    var playerInitError by remember { mutableStateOf(false) }
    var isDraggingHandle by remember { mutableStateOf(false) }
    
    fun resolvePreviewPosition(rawMs: Float): Long {
        val total = durationMs.toFloat()
        val clamped = rawMs.coerceIn(0f, total)
        return if (trimMode == TrimMode.Keep) {
            clamped.coerceIn(range.start, range.endInclusive).toLong()
        } else {
            val start = range.start
            val end = range.endInclusive
            if (clamped in start..end) {
                val distStart = clamped - start
                val distEnd = end - clamped
                if (distStart <= distEnd) start.toLong() else end.toLong()
            } else {
                clamped.toLong()
            }
        }
    }
    
    // Waveform Loading
    val waveform = produceState<List<Int>?>(initialValue = null, key1 = file) {
        value = withContext(Dispatchers.IO) {
            com.example.audioloop.WaveformGenerator.extractWaveform(file, 60)
        }
    }

    // Initialize MediaPlayer using ContentResolver (professional approach)
    // This works reliably with both internal storage and MediaStore
    LaunchedEffect(uri) {
        isPreviewPlaying = false
        previewPositionMs = 0L
        playerReady = false
        playerInitError = false

        // Retry up to 10 times with increasing delays (for files still being finalized)
        val delays = listOf(0L, 200L, 400L, 600L, 800L, 1000L, 1500L, 2000L, 2500L, 3000L)
        for ((attempt, delayMs) in delays.withIndex()) {
            if (delayMs > 0) delay(delayMs)
            try {
                val success = withContext(Dispatchers.IO) {
                    // Use ContentResolver to open file - works with all storage types
                    val pfd = context.contentResolver.openFileDescriptor(uri, "r")
                    if (pfd != null) {
                        previewPlayer.reset()
                        previewPlayer.setDataSource(pfd.fileDescriptor)
                        previewPlayer.prepare()
                        pfd.close()
                        true
                    } else {
                        false
                    }
                }
                if (success) {
                    playerReady = true
                    return@LaunchedEffect
                }
            } catch (e: Exception) {
                android.util.Log.w("TrimDialog", "Attempt ${attempt + 1} failed: ${e.message}")
                if (attempt == delays.lastIndex) {
                    playerInitError = true
                }
            }
        }
    }

    DisposableEffect(file) {
        onDispose {
            try {
                previewPlayer.release()
            } catch (_: Exception) { }
        }
    }

    LaunchedEffect(playerInitError) {
        if (playerInitError) {
            android.widget.Toast.makeText(context, "Unable to load audio file for trimming", android.widget.Toast.LENGTH_SHORT).show()
            onDismiss()
        }
    }
    
    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Zinc900),
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, Zinc800),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            if (playerReady) {
                Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    "Trim Audio",
                    style = TextStyle(color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                )
                Text(
                    "Drag the L and R handles to refine the selection. Tap or drag the waveform to set playback.",
                    style = TextStyle(color = Zinc400, fontSize = 12.sp)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    "Choose Keep to save the highlighted range, or Remove to cut it out from the middle.",
                    style = TextStyle(color = Zinc500, fontSize = 11.sp)
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Trim mode", color = Zinc400, fontSize = 11.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        val keepSelected = trimMode == TrimMode.Keep
                        val removeSelected = trimMode == TrimMode.Remove
                        Button(
                            onClick = { trimMode = TrimMode.Keep },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (keepSelected) themeColors.primary700 else Zinc800
                            ),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text("Keep", color = Color.White, fontSize = 12.sp)
                        }
                        OutlinedButton(
                            onClick = { trimMode = TrimMode.Remove },
                            border = BorderStroke(1.dp, if (removeSelected) themeColors.primary400 else Zinc700),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                "Remove",
                                color = if (removeSelected) Color.White else Zinc300,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(18.dp))
                
                // Visual Trimmer with Waveform
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .background(
                            Brush.verticalGradient(listOf(Zinc800, Zinc900)),
                            RoundedCornerShape(12.dp)
                        )
                        .border(1.dp, Zinc700, RoundedCornerShape(12.dp))
                        .padding(horizontal = 6.dp, vertical = 4.dp)
                ) {
                    val widthPx = constraints.maxWidth.toFloat()
                    val totalDuration = durationMs.toFloat()
                    val heightPx = constraints.maxHeight.toFloat()
                    val handleHitWidth = with(LocalDensity.current) { 56.dp.toPx() } // Large hit zone for thumb
                    val handleBarWidth = with(LocalDensity.current) { 7.dp.toPx() }
                    val handleBarRadius = with(LocalDensity.current) { 3.5.dp.toPx() }
                    val labelAreaHeight = with(LocalDensity.current) { 22.dp.toPx() }
                    val waveTop = labelAreaHeight
                    val waveBottom = heightPx
                    
                    var startX by remember { mutableFloatStateOf(0f) }
                    var endX by remember { mutableFloatStateOf(widthPx) }
                    val selectionStartX = min(startX, endX)
                    val selectionEndX = max(startX, endX)
                    val labelTextSize = with(LocalDensity.current) { 12.sp.toPx() }
                    val labelPadding = with(LocalDensity.current) { 6.dp.toPx() }
                    val labelGap = with(LocalDensity.current) { 8.dp.toPx() }
                    val handleTextPaint = remember {
                        Paint().apply {
                            isAntiAlias = true
                            textAlign = Paint.Align.CENTER
                        }
                    }
                    val startHandleColor = themeColors.primary500
                    val endHandleColor = Red500
                    val selectionColor = themeColors.primary400
                    val remainingColor = Zinc700
                    val labelBackgroundColor = Zinc800.copy(alpha = 0.95f)
                    val labelTextColor = Color.White
                    val startHandleMs = if (widthPx > 0f) {
                        ((startX / widthPx) * totalDuration).coerceIn(0f, totalDuration)
                    } else {
                        0f
                    }
                    val endHandleMs = if (widthPx > 0f) {
                        ((endX / widthPx) * totalDuration).coerceIn(0f, totalDuration)
                    } else {
                        totalDuration
                    }
                    val selectionStartMs = if (widthPx > 0f) {
                        ((selectionStartX / widthPx) * totalDuration).coerceIn(0f, totalDuration)
                    } else {
                        0f
                    }
                    val selectionEndMs = if (widthPx > 0f) {
                        ((selectionEndX / widthPx) * totalDuration).coerceIn(0f, totalDuration)
                    } else {
                        totalDuration
                    }

                    LaunchedEffect(widthPx) {
                        startX = startX.coerceIn(0f, widthPx)
                        endX = endX.coerceIn(0f, widthPx)
                        range = selectionStartMs..selectionEndMs
                    }

                    LaunchedEffect(selectionStartMs, selectionEndMs, trimMode) {
                        val clampedPreviewMs = resolvePreviewPosition(previewPositionMs.toFloat())
                        if (isPreviewPlaying) {
                            previewPlayer.pause()
                            previewPlayer.seekTo(clampedPreviewMs.toInt())
                            isPreviewPlaying = false
                        }
                        previewPositionMs = clampedPreviewMs
                    }

                    LaunchedEffect(isPreviewPlaying, selectionStartMs, selectionEndMs, trimMode) {
                        if (isPreviewPlaying) {
                            while (isActive && previewPlayer.isPlaying) {
                                val current = previewPlayer.currentPosition.toLong()
                                previewPositionMs = current
                                if (trimMode == TrimMode.Keep) {
                                    if (current >= selectionEndMs.toLong()) {
                                        previewPlayer.pause()
                                        previewPlayer.seekTo(selectionStartMs.toInt())
                                        isPreviewPlaying = false
                                        break
                                    }
                                } else {
                                    if (current in selectionStartMs.toLong()..selectionEndMs.toLong()) {
                                        previewPlayer.seekTo(selectionEndMs.toInt())
                                        previewPositionMs = selectionEndMs.toLong()
                                    } else if (current >= totalDuration.toLong()) {
                                        previewPlayer.pause()
                                        previewPlayer.seekTo(0)
                                        previewPositionMs = 0L
                                        isPreviewPlaying = false
                                        break
                                    }
                                }
                                delay(50)
                            }
                        }
                    }

                    // Render Waveform and UI
                    val density = LocalDensity.current.density
                    
                    if (waveform.value == null) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = themeColors.primary500, modifier = Modifier.size(24.dp))
                        }
                    } else {
                        val bars = waveform.value!!
                        val barWidth = widthPx / bars.size
                        
                        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                            val waveAreaHeight = size.height - labelAreaHeight

                            // 1. Waveform bars
                            bars.forEachIndexed { index, amplitude ->
                                val x = index * barWidth
                                val barHeight = (amplitude / 100f) * waveAreaHeight
                                val isSelected = x >= selectionStartX && x <= selectionEndX
                                val barColor = if (trimMode == TrimMode.Keep) {
                                    if (isSelected) selectionColor else remainingColor
                                } else {
                                    if (isSelected) Zinc600 else selectionColor
                                }
                                drawLine(
                                    color = barColor,
                                    start = Offset(x + barWidth / 2, waveTop + (waveAreaHeight - barHeight) / 2),
                                    end = Offset(x + barWidth / 2, waveTop + (waveAreaHeight + barHeight) / 2),
                                    strokeWidth = (barWidth * 0.6f).coerceAtLeast(1f)
                                )
                            }

                            // 2. Dim unselected regions
                            if (trimMode == TrimMode.Keep) {
                                drawRect(
                                    color = Zinc900.copy(alpha = 0.4f),
                                    topLeft = Offset(0f, waveTop),
                                    size = androidx.compose.ui.geometry.Size(selectionStartX, waveAreaHeight)
                                )
                                drawRect(
                                    color = Zinc900.copy(alpha = 0.4f),
                                    topLeft = Offset(selectionEndX, waveTop),
                                    size = androidx.compose.ui.geometry.Size(size.width - selectionEndX, waveAreaHeight)
                                )
                            } else {
                                drawRect(
                                    color = Zinc900.copy(alpha = 0.4f),
                                    topLeft = Offset(selectionStartX, waveTop),
                                    size = androidx.compose.ui.geometry.Size(selectionEndX - selectionStartX, waveAreaHeight)
                                )
                            }

                            // 3. Playhead
                            val playheadX = if (totalDuration > 0f) {
                                ((previewPositionMs.toFloat() / totalDuration) * size.width).coerceIn(0f, size.width)
                            } else {
                                0f
                            }
                            drawLine(
                                color = Color.White.copy(alpha = if (playheadX in selectionStartX..selectionEndX) 0.7f else 0.3f),
                                start = Offset(playheadX, waveTop),
                                end = Offset(playheadX, waveBottom),
                                strokeWidth = 1.5.dp.toPx()
                            )

                            // 4. Edge-bar handles
                            drawRoundRect(
                                color = startHandleColor,
                                topLeft = Offset(startX - handleBarWidth / 2, waveTop),
                                size = androidx.compose.ui.geometry.Size(handleBarWidth, waveAreaHeight),
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(handleBarRadius)
                            )
                            drawRoundRect(
                                color = endHandleColor,
                                topLeft = Offset(endX - handleBarWidth / 2, waveTop),
                                size = androidx.compose.ui.geometry.Size(handleBarWidth, waveAreaHeight),
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(handleBarRadius)
                            )
                            // Grip indicators (3 horizontal lines per handle)
                            val gripY = waveTop + waveAreaHeight / 2
                            val gripSpacing = 3.dp.toPx()
                            val gripHalfW = 2.dp.toPx()
                            for (i in -1..1) {
                                val y = gripY + i * gripSpacing
                                drawLine(Color.White.copy(alpha = 0.85f), Offset(startX - gripHalfW, y), Offset(startX + gripHalfW, y), strokeWidth = 1.5.dp.toPx())
                                drawLine(Color.White.copy(alpha = 0.85f), Offset(endX - gripHalfW, y), Offset(endX + gripHalfW, y), strokeWidth = 1.5.dp.toPx())
                            }

                            // 5. Time labels
                            drawIntoCanvas { canvas ->
                                handleTextPaint.textSize = labelTextSize
                                handleTextPaint.color = labelTextColor.toArgb()
                                val startLabel = formatDuration(startHandleMs.toLong())
                                val endLabel = formatDuration(endHandleMs.toLong())
                                val startLabelWidth = handleTextPaint.measureText(startLabel)
                                val endLabelWidth = handleTextPaint.measureText(endLabel)
                                val labelHeight = labelTextSize + (labelPadding * 2)
                                val startLabelBound = (startLabelWidth / 2) + labelPadding
                                val endLabelBound = (endLabelWidth / 2) + labelPadding
                                val startMin = startLabelBound
                                val startMax = widthPx - startLabelBound
                                val endMin = endLabelBound
                                val endMax = widthPx - endLabelBound
                                var startLabelX = startX.coerceIn(startMin, startMax)
                                var endLabelX = endX.coerceIn(endMin, endMax)
                                val requiredGap = startLabelBound + endLabelBound + labelGap
                                if (endLabelX - startLabelX < requiredGap) {
                                    val mid = (startLabelX + endLabelX) / 2f
                                    startLabelX = (mid - requiredGap / 2f).coerceIn(startMin, startMax)
                                    endLabelX = (mid + requiredGap / 2f).coerceIn(endMin, endMax)
                                    if (endLabelX - startLabelX < requiredGap) {
                                        startLabelX = (endLabelX - requiredGap).coerceIn(startMin, startMax)
                                        endLabelX = (startLabelX + requiredGap).coerceIn(endMin, endMax)
                                    }
                                }
                                val startRectLeft = startLabelX - (startLabelWidth / 2) - labelPadding
                                val startRectRight = startLabelX + (startLabelWidth / 2) + labelPadding
                                val endRectLeft = endLabelX - (endLabelWidth / 2) - labelPadding
                                val endRectRight = endLabelX + (endLabelWidth / 2) + labelPadding
                                val labelTop = (labelAreaHeight - labelHeight) / 2f
                                drawRoundRect(
                                    color = labelBackgroundColor,
                                    topLeft = Offset(startRectLeft, labelTop),
                                    size = androidx.compose.ui.geometry.Size(
                                        startRectRight - startRectLeft,
                                        labelHeight
                                    ),
                                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(8.dp.toPx())
                                )
                                drawRoundRect(
                                    color = labelBackgroundColor,
                                    topLeft = Offset(endRectLeft, labelTop),
                                    size = androidx.compose.ui.geometry.Size(
                                        endRectRight - endRectLeft,
                                        labelHeight
                                    ),
                                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(8.dp.toPx())
                                )
                                val labelBaseline = labelTop + labelPadding + labelTextSize
                                canvas.nativeCanvas.drawText(startLabel, startLabelX, labelBaseline, handleTextPaint)
                                canvas.nativeCanvas.drawText(endLabel, endLabelX, labelBaseline, handleTextPaint)
                            }
                        }
                    }
                    
                    // Touch/Drag Layer
                    androidx.compose.foundation.Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                awaitEachGesture {
                                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                                    down.consume()
                                    var playheadX = if (totalDuration > 0f) {
                                        ((previewPositionMs.toFloat() / totalDuration) * widthPx).coerceIn(0f, widthPx)
                                    } else {
                                        0f
                                    }
                                    // Extended hit zones at edges - generous zones for thumb interaction
                                    // When handle is near edge, extend hit zone all the way to the edge
                                    val edgeThreshold = handleHitWidth * 0.75f
                                    val startHitLeft = if (startX < edgeThreshold) 0f else startX - handleHitWidth / 2
                                    val startHitRight = startX + handleHitWidth / 2
                                    val endHitLeft = endX - handleHitWidth / 2
                                    val endHitRight = if (endX > widthPx - edgeThreshold) widthPx else endX + handleHitWidth / 2
                                    val touchX = down.position.x
                                    // Use distance-based detection for more reliable touch
                                    val distToStart = kotlin.math.abs(touchX - startX)
                                    val distToEnd = kotlin.math.abs(touchX - endX)
                                    val isNearStart = touchX >= startHitLeft && touchX <= startHitRight
                                    val isNearEnd = touchX >= endHitLeft && touchX <= endHitRight
                                    val dragTarget = when {
                                        isNearStart && isNearEnd -> {
                                            if (kotlin.math.abs(down.position.x - startX) <= kotlin.math.abs(down.position.x - endX))
                                                TrimDragTarget.Start else TrimDragTarget.End
                                        }
                                        isNearStart -> TrimDragTarget.Start
                                        isNearEnd -> TrimDragTarget.End
                                        else -> TrimDragTarget.Playhead
                                    }
                                    // Disable scroll when dragging a handle
                                    if (dragTarget != TrimDragTarget.Playhead) {
                                        isDraggingHandle = true
                                    }
                                    try {
                                        if (dragTarget == TrimDragTarget.Playhead) {
                                            playheadX = down.position.x.coerceIn(0f, widthPx)
                                            val newPreviewMs = if (widthPx > 0f) {
                                                ((playheadX / widthPx) * totalDuration)
                                                    .coerceIn(selectionStartMs, selectionEndMs)
                                            } else {
                                                0f
                                            }
                                            if (isPreviewPlaying) {
                                                previewPlayer.pause()
                                                isPreviewPlaying = false
                                            }
                                            previewPositionMs = newPreviewMs.toLong()
                                            previewPlayer.seekTo(previewPositionMs.toInt())
                                        }
                                        var lastX = down.position.x
                                        while (true) {
                                            val event = awaitPointerEvent(PointerEventPass.Initial)
                                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                            if (!change.pressed) {
                                                break
                                            }
                                            change.consume()
                                            val dragAmount = change.position.x - lastX
                                            lastX = change.position.x
                                            if (dragAmount == 0f) {
                                                continue
                                            }
                                            when (dragTarget) {
                                                TrimDragTarget.Start -> {
                                                    startX = (startX + dragAmount).coerceIn(0f, widthPx)
                                                }
                                                TrimDragTarget.End -> {
                                                    endX = (endX + dragAmount).coerceIn(0f, widthPx)
                                                }
                                                TrimDragTarget.Playhead -> {
                                                    playheadX = (playheadX + dragAmount).coerceIn(0f, widthPx)
                                                    val newPreviewMs = if (widthPx > 0f) {
                                                        resolvePreviewPosition((playheadX / widthPx) * totalDuration)
                                                    } else {
                                                        0L
                                                    }
                                                    if (isPreviewPlaying) {
                                                        previewPlayer.pause()
                                                        isPreviewPlaying = false
                                                    }
                                                    previewPositionMs = newPreviewMs
                                                    previewPlayer.seekTo(previewPositionMs.toInt())
                                                }
                                            }
                                            if (dragTarget != TrimDragTarget.Playhead) {
                                                val newSelectionStart = min(startX, endX)
                                                val newSelectionEnd = max(startX, endX)
                                                val newStartMs = if (widthPx > 0f) {
                                                    ((newSelectionStart / widthPx) * totalDuration).coerceIn(0f, totalDuration)
                                                } else {
                                                    0f
                                                }
                                                val newEndMs = if (widthPx > 0f) {
                                                    ((newSelectionEnd / widthPx) * totalDuration).coerceIn(0f, totalDuration)
                                                } else {
                                                    totalDuration
                                                }
                                                range = newStartMs..newEndMs
                                            }
                                        }
                                    } finally {
                                        // Always re-enable scroll when gesture ends
                                        isDraggingHandle = false
                                    }
                                }
                            }
                    ) {
                        // Invisible touch layer, visual handles are drawn above
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        Text("Start", color = Zinc500, fontSize = 11.sp)
                        Surface(
                            color = Zinc800,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, Zinc700)
                        ) {
                            Text(
                                formatDuration(range.start.toLong()),
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("End", color = Zinc500, fontSize = 11.sp)
                        Surface(
                            color = Zinc800,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, Zinc700)
                        ) {
                            Text(
                                formatDuration(range.endInclusive.toLong()),
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))

                // Professional Preview Time Display
                val selectionDurationMs = if (trimMode == TrimMode.Keep) {
                    (range.endInclusive - range.start).toLong()
                } else {
                    durationMs - (range.endInclusive - range.start).toLong()
                }
                val progressFraction = if (selectionDurationMs > 0) {
                    ((previewPositionMs - range.start.toLong()).toFloat() / (range.endInclusive - range.start).toLong()).coerceIn(0f, 1f)
                } else 0f

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Two-column time display with labels
                    Row(
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Current position column
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "Position",
                                color = Zinc500,
                                style = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Medium)
                            )
                            Text(
                                formatDuration(previewPositionMs),
                                color = if (isPreviewPlaying) themeColors.primary400 else Color.White,
                                style = TextStyle(
                                    fontSize = 28.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            )
                        }

                        Text(
                            "  /  ",
                            color = Zinc600,
                            style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Light),
                            modifier = Modifier.padding(bottom = 4.dp)
                        )

                        // New file length column
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "New length",
                                color = Zinc500,
                                style = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Medium)
                            )
                            Text(
                                formatDuration(selectionDurationMs),
                                color = Zinc400,
                                style = TextStyle(
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Medium,
                                    fontFamily = FontFamily.Monospace
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Progress bar
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .background(Zinc700, RoundedCornerShape(2.dp))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(progressFraction)
                                .fillMaxHeight()
                                .background(
                                    if (isPreviewPlaying) themeColors.primary500 else Zinc500,
                                    RoundedCornerShape(2.dp)
                                )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Button(
                        onClick = {
                            if (isPreviewPlaying) {
                                previewPlayer.pause()
                                isPreviewPlaying = false
                            } else {
                                val baseStartMs = previewPositionMs.toFloat()
                                val startMs = resolvePreviewPosition(baseStartMs)
                                previewPositionMs = startMs
                                previewPlayer.seekTo(startMs.toInt())
                                previewPlayer.start()
                                isPreviewPlaying = true
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = if (isPreviewPlaying) themeColors.primary700 else Zinc800),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp)
                    ) {
                        Text(if (isPreviewPlaying) "Pause" else "Play Preview", color = Color.White, fontSize = 14.sp)
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    OutlinedButton(
                        onClick = {
                            previewPlayer.pause()
                            val stopMs = if (trimMode == TrimMode.Keep) {
                                range.start.toLong()
                            } else {
                                0L
                            }
                            previewPositionMs = stopMs
                            previewPlayer.seekTo(stopMs.toInt())
                            isPreviewPlaying = false
                        },
                        border = BorderStroke(1.dp, Zinc700),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Text("Stop", color = Zinc300, fontSize = 14.sp)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Divider(color = Zinc800, thickness = 1.dp)

                Spacer(modifier = Modifier.height(20.dp))
                
                // Actions: 2 equal buttons + Cancel below
                Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { onConfirm(range.start.toLong(), range.endInclusive.toLong(), false, trimMode == TrimMode.Remove) }, 
                            colors = ButtonDefaults.buttonColors(containerColor = Zinc700),
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, Zinc600),
                            modifier = Modifier.weight(1f).height(50.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp)
                        ) {
                             Text("Save Copy", color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium))
                        }
                        
                        Button(
                            onClick = { onConfirm(range.start.toLong(), range.endInclusive.toLong(), true, trimMode == TrimMode.Remove) },
                            colors = ButtonDefaults.buttonColors(containerColor = themeColors.primary700),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.weight(1f).height(50.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp)
                        ) {
                             Text("Replace Original", color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium))
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                        Text("Cancel", color = Zinc400)
                    }
                }
            }
            if (!playerReady && !playerInitError) {
                // Loading state while file is being prepared
                Column(
                    modifier = Modifier.padding(40.dp).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(color = themeColors.primary700)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Loading audio...", color = Zinc400, fontSize = 14.sp)
                }
            }
        }
    }
}
}

fun formatDuration(ms: Long): String {
    val m = java.util.concurrent.TimeUnit.MILLISECONDS.toMinutes(ms)
    val s = java.util.concurrent.TimeUnit.MILLISECONDS.toSeconds(ms) % 60
    return String.format("%02d:%02d", m, s)
}
