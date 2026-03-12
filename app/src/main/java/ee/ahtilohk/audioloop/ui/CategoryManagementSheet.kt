package ee.ahtilohk.audioloop.ui

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
import ee.ahtilohk.audioloop.AppIcons
import ee.ahtilohk.audioloop.RecordingItem
import ee.ahtilohk.audioloop.ui.theme.*
import ee.ahtilohk.audioloop.R
import androidx.compose.ui.res.stringResource
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
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
    themeColors: ee.ahtilohk.audioloop.ui.theme.AppColorPalette = ee.ahtilohk.audioloop.ui.theme.AppTheme.SLATE.palette
) {
    var newCategoryName by remember { mutableStateOf("") }
    var editingId by remember { mutableStateOf<String?>(null) }
    var editName by remember { mutableStateOf("") }
    var categoryToDelete by remember { mutableStateOf<String?>(null) }
    val uiCategories = remember { mutableStateListOf<String>() }
    val haptic = LocalHapticFeedback.current

    LaunchedEffect(categories) {
        uiCategories.clear()
        uiCategories.addAll(categories)
    }

    // Delete confirmation dialog
    categoryToDelete?.let { cat ->
        val generalLabel = stringResource(R.string.label_general)
        DeleteConfirmDialog(
            title = stringResource(R.string.title_delete_category),
            text = stringResource(R.string.msg_delete_category).replace("General", generalLabel),
            onDismiss = { categoryToDelete = null },
            onConfirm = {
                onDelete(cat)
                categoryToDelete = null
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
            .padding(vertical = 8.dp)
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.label_categories),
                    style = TextStyle(color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                )
                Text(
                    text = stringResource(R.string.label_drag_reorder_hint),
                    style = TextStyle(color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                )
            }
            IconButton(
                onClick = onClose,
                modifier = Modifier
                    .size(36.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
            ) {
                Icon(AppIcons.Close, contentDescription = stringResource(R.string.a11y_close), tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
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
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(16.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
                    .padding(horizontal = 20.dp),
                decorationBox = { innerTextField ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (newCategoryName.isEmpty()) Text(stringResource(R.string.hint_category_name), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                        innerTextField()
                    }
                }
            )
            Button(
                onClick = {
                    if (newCategoryName.isNotBlank()) {
                        val newCat = newCategoryName.trim()
                        if (!uiCategories.contains(newCat)) {
                            uiCategories.add(newCat)
                        }
                        onAdd(newCat)
                        newCategoryName = ""
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    }
                },
                modifier = Modifier.height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                shape = RoundedCornerShape(16.dp),
                enabled = newCategoryName.isNotBlank()
            ) {
                Text(stringResource(R.string.btn_add), color = Color.White)
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant, thickness = 1.dp, modifier = Modifier.padding(horizontal = 20.dp))

        // List with drag reorder
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

            // Only swap when centre crosses 30% into the neighbouring item
            for (item in currentVisibleItems) {
                val itemTop = item.offset.toFloat()
                val itemBottom = itemTop + item.size.toFloat()
                val threshold = item.size * 0.30f

                val isAboveSelf = item.index < draggingCategoryIndex &&
                        overlayCenterY < itemBottom - threshold
                val isBelowSelf = item.index > draggingCategoryIndex &&
                        overlayCenterY > itemTop + threshold

                if (isAboveSelf || isBelowSelf) {
                    targetIndex = item.index
                    break
                }
            }

            // Prevent swapping "General" (index 0) out of its position
            if (targetIndex >= 0 && targetIndex != draggingCategoryIndex) {
                if (targetIndex == 0 && draggingCategoryIndex != 0) return
                if (draggingCategoryIndex == 0 && targetIndex > 0) return

                if (draggingCategoryIndex in uiCategories.indices && targetIndex in uiCategories.indices) {
                    val itemToMove = uiCategories.removeAt(draggingCategoryIndex)
                    uiCategories.add(targetIndex, itemToMove)
                    draggingCategoryIndex = targetIndex
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
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
                .heightIn(max = 400.dp)
                .pointerInput(Unit) {
                    val gripWidth = 72.dp.toPx()
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val y = down.position.y
                        val x = down.position.x
                        val hitItem = scrollState.layoutInfo.visibleItemsInfo.find {
                            y >= it.offset && y <= it.offset + it.size
                        }

                        if (hitItem != null && x <= gripWidth) {
                            val index = hitItem.index
                            if (index in uiCategories.indices && uiCategories[index] != "General") {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
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
                modifier = Modifier.fillMaxWidth(),
                state = scrollState,
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(uiCategories, key = { _, cat -> cat }) { index, cat ->
                    val isEditing = editingId == cat
                    val isDragging = draggingCategoryIndex == index
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !isEditing && !isDragging) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onSelect(cat)
                                onClose()
                            }
                            .alpha(if (isDragging) 0f else 1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (currentCategory == cat) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .border(
                                1.dp,
                                if (currentCategory == cat) MaterialTheme.colorScheme.primary.copy(alpha = 0.8f) else MaterialTheme.colorScheme.outline,
                                RoundedCornerShape(12.dp)
                            )
                            .padding(start = 4.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Drag handle - wider touch target
                        Box(
                            modifier = Modifier
                                .size(width = 36.dp, height = 48.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                AppIcons.GripVertical,
                                contentDescription = stringResource(R.string.a11y_drag_reorder),
                                tint = if (cat == "General") MaterialTheme.colorScheme.outlineVariant else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        if (isEditing) {
                            BasicTextField(
                                value = editName,
                                onValueChange = { editName = it },
                                singleLine = true,
                                textStyle = TextStyle(color = Color.White, fontSize = 14.sp),
                                modifier = Modifier
                                    .weight(1f)
                                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                                    .border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
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
                                text = if (cat == "General") stringResource(R.string.label_general) else cat,
                                style = TextStyle(
                                    color = if (currentCategory == cat) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
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
                                        .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                ) {
                                    Icon(AppIcons.Edit, contentDescription = stringResource(R.string.a11y_edit), tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
                                }
                                IconButton(
                                    onClick = { categoryToDelete = cat },
                                    modifier = Modifier
                                        .size(32.dp)
                                        .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                ) {
                                    Icon(AppIcons.Delete, contentDescription = stringResource(R.string.a11y_delete), tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
                                }
                            }
                        } else if (cat == "General") {
                            Box(
                                modifier = Modifier
                                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(stringResource(R.string.label_default), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
                            }
                        } else if (isEditing) {
                            IconButton(
                                onClick = {
                                    if (editName.isNotBlank() && editName != cat) {
                                        onRename(cat, editName.trim())
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    }
                                    editingId = null
                                },
                                modifier = Modifier
                                    .size(32.dp)
                                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
                            ) {
                                Icon(AppIcons.Check, contentDescription = stringResource(R.string.a11y_save), tint = Color.White, modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                }
            }

            // Drag overlay
            if (draggingCategory != null) {
                val cat = draggingCategory!!
                Row(
                    modifier = Modifier
                        .offset { IntOffset(0, overlayOffsetY.roundToInt()) }
                        .padding(horizontal = 20.dp)
                        .shadow(8.dp, RoundedCornerShape(12.dp))
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                        .border(1.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp))
                        .padding(start = 4.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier.size(width = 36.dp, height = 48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            AppIcons.GripVertical,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Text(
                        text = if (cat == "General") stringResource(R.string.label_general) else cat,
                        style = TextStyle(color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

