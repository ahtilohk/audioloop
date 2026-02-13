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
fun CategoryManagementSheet(
    categories: List<String>,
    currentCategory: String,
    onSelect: (String) -> Unit,
    onAdd: (String) -> Unit,
    onRename: (String, String) -> Unit,
    onDelete: (String) -> Unit,
    onReorder: (List<String>) -> Unit,
    onClose: () -> Unit,
    themeColors: com.example.audioloop.ui.theme.AppColorPalette = com.example.audioloop.ui.theme.AppTheme.SLATE.palette
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

            HorizontalDivider(color = Zinc800, thickness = 1.dp, modifier = Modifier.padding(horizontal = 20.dp))

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
