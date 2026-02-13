package com.example.audioloop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.*
import androidx.compose.foundation.layout.*
import kotlinx.coroutines.launch
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audioloop.AppIcons
import com.example.audioloop.ui.theme.*
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
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
    themeColors: AppColorPalette = AppTheme.CYAN.palette
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    
    // Local state
    var newCategoryName by remember { mutableStateOf("") }
    var editingId by remember { mutableStateOf<String?>(null) }
    var editName by remember { mutableStateOf("") }
    val uiCategories = remember { mutableStateListOf<String>() }

    LaunchedEffect(categories) {
        uiCategories.clear()
        uiCategories.addAll(categories)
    }

    val scope = rememberCoroutineScope()
    
    fun animateClose() {
        scope.launch { sheetState.hide() }.invokeOnCompletion {
            if (!sheetState.isVisible) {
                onClose()
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = sheetState,
        containerColor = Zinc900,
        contentColor = Zinc200,
        dragHandle = { BottomSheetDefaults.DragHandle(color = Zinc600) },
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 20.dp) // Extra padding for safety
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Categories",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, color = Zinc100)
                )
                // Minimal close button if needed, but swipe down works. keeping it for accessibility/clarity
                IconButton(
                    onClick = { animateClose() },
                    modifier = Modifier.size(32.dp).background(Zinc800, CircleShape)
                ) {
                    Icon(AppIcons.Close, contentDescription = "Close", tint = Zinc400, modifier = Modifier.size(18.dp))
                }
            }

            // Add new Section
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                BasicTextField(
                    value = newCategoryName,
                    onValueChange = { newCategoryName = it },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = Zinc100),
                    cursorBrush = SolidColor(themeColors.primary),
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                        .background(Zinc800, RoundedCornerShape(12.dp))
                        .border(1.dp, Zinc700, RoundedCornerShape(12.dp))
                        .padding(horizontal = 16.dp),
                    decorationBox = { innerTextField ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (newCategoryName.isEmpty()) Text("New category...", color = Zinc500, style = MaterialTheme.typography.bodyMedium)
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
                        }
                    },
                    modifier = Modifier.height(50.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = themeColors.primary),
                    shape = RoundedCornerShape(12.dp),
                    enabled = newCategoryName.isNotBlank()
                ) {
                    Icon(AppIcons.Add, null, modifier = Modifier.size(20.dp))
                }
            }

            HorizontalDivider(color = Zinc800, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))

            // Scrollable List Area with Drag & Drop Logic
            Box(
                modifier = Modifier
                    .weight(1f, fill = false) // Allow it to take available space but not force infinite
                    .fillMaxWidth()
                    .heightIn(max = 400.dp) // Limit height so it doesn't take over screen if list is huge
            ) {
                val scrollState = rememberLazyListState()
                
                // --- Drag Logic Reuse ---
                var draggingCategoryIndex by remember { mutableIntStateOf(-1) }
                var draggingCategory by remember { mutableStateOf<String?>(null) }
                var overlayOffsetY by remember { mutableFloatStateOf(0f) }
                var grabOffsetY by remember { mutableFloatStateOf(0f) }
                var overscrollSpeed by remember { mutableFloatStateOf(0f) }

                fun checkForSwap() {
                    if (uiCategories.size <= 1) return
                    val currentVisibleItems = scrollState.layoutInfo.visibleItemsInfo
                    if (currentVisibleItems.isEmpty()) return

                    val hitItem = currentVisibleItems.find { item ->
                        val itemCenter = item.offset + (item.size / 2)
                        val overlayCenter = overlayOffsetY + (item.size / 2) // Approximate
                        // Simple collision: check if overlay center is within item bounds
                         overlayCenter >= item.offset && overlayCenter <= (item.offset + item.size)
                    }

                    if (hitItem != null) {
                       val targetIndex = hitItem.index
                       if (targetIndex != -1 && targetIndex != draggingCategoryIndex) {
                            if (draggingCategoryIndex in uiCategories.indices && targetIndex in uiCategories.indices) {
                                val itemToMove = uiCategories.removeAt(draggingCategoryIndex)
                                uiCategories.add(targetIndex, itemToMove)
                                draggingCategoryIndex = targetIndex
                            }
                        }
                    }
                }

                // Auto-scroll
                LaunchedEffect(overscrollSpeed) {
                    if (overscrollSpeed != 0f) {
                        while (true) {
                            scrollState.scrollBy(overscrollSpeed)
                            checkForSwap()
                            delay(16)
                        }
                    }
                }

                // List Container with Gesture Handler
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            val gripWidth = 60.dp.toPx()
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                val x = down.position.x
                                val y = down.position.y
                                
                                // Find what we touched
                                val hitItem = scrollState.layoutInfo.visibleItemsInfo.find {
                                    y >= it.offset && y <= it.offset + it.size
                                }

                                if (hitItem != null && x <= gripWidth) {
                                    val index = hitItem.index
                                    // Don't drag "General" or if invalid index
                                    if (index in uiCategories.indices && uiCategories[index] != "General") {
                                        down.consume()
                                        draggingCategoryIndex = index
                                        draggingCategory = uiCategories[index]
                                        
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
                                                
                                                // Overscroll logic
                                                val viewportHeight = size.height.toFloat()
                                                val threshold = 50.dp.toPx()
                                                val maxSpeed = 15.dp.toPx()
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
                                        // End drag
                                        overscrollSpeed = 0f
                                        onReorder(uiCategories.toList())
                                        draggingCategoryIndex = -1
                                        draggingCategory = null
                                    }
                                }
                            }
                        }
                ) {
                    LazyColumn(
                        state = scrollState,
                        contentPadding = PaddingValues(bottom = 60.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                         itemsIndexed(uiCategories, key = { _, cat -> cat }) { index, cat ->
                             val isDragging = index == draggingCategoryIndex
                             val isEditing = editingId == cat
                             val isSelected = currentCategory == cat
                             
                             // List Item
                             Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 24.dp, vertical = 6.dp)
                                    .alpha(if (isDragging) 0f else 1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (isSelected) themeColors.primaryContainer.copy(alpha = 0.2f) else Zinc800.copy(alpha=0.4f))
                                    .border(1.dp, if (isSelected) themeColors.primary.copy(alpha=0.3f) else Color.Transparent, RoundedCornerShape(12.dp))
                                    .clickable(enabled = !isEditing) {
                                        onSelect(cat)
                                        animateClose()
                                    }
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                             ) {
                                 // Grip
                                 Icon(AppIcons.GripVertical, null, tint = if (cat == "General") Zinc700 else Zinc500, modifier = Modifier.size(20.dp))
                                 
                                 // Content
                                 Box(modifier = Modifier.weight(1f)) {
                                     if (isEditing) {
                                         BasicTextField(
                                             value = editName,
                                             onValueChange = { editName = it },
                                             singleLine = true,
                                             textStyle = TextStyle(color = Zinc100, fontSize = 16.sp),
                                             cursorBrush = SolidColor(themeColors.primary),
                                             modifier = Modifier
                                                 .fillMaxWidth()
                                                 .background(Zinc900, RoundedCornerShape(8.dp))
                                                 .padding(horizontal = 8.dp, vertical = 4.dp),
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
                                             style = MaterialTheme.typography.bodyLarge.copy(
                                                 color = if (isSelected) themeColors.primary else Zinc200,
                                                 fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                                             )
                                         )
                                     }
                                 }
                                 
                                 // Actions
                                 if (cat != "General" && !isEditing) {
                                     Row {
                                         IconButton(onClick = { editingId = cat; editName = cat }) {
                                             Icon(AppIcons.Edit, "Edit", tint = Zinc500, modifier = Modifier.size(18.dp))
                                         }
                                         IconButton(onClick = { onDelete(cat) }) {
                                             Icon(AppIcons.Delete, "Delete", tint = Zinc500, modifier = Modifier.size(18.dp))
                                         }
                                     }
                                 } else if (isEditing) {
                                     IconButton(
                                         onClick = {
                                            if (editName.isNotBlank() && editName != cat) {
                                                onRename(cat, editName.trim())
                                            }
                                            editingId = null
                                         },
                                         modifier = Modifier.background(themeColors.primary, CircleShape).size(32.dp)
                                     ) {
                                         Icon(AppIcons.Check, "Save", tint = Color.White, modifier = Modifier.size(16.dp))
                                     }
                                 }
                             }
                         }
                    }
                }
                
                // Drag Overlay
                if (draggingCategory != null) {
                    Row(
                        modifier = Modifier
                            .offset { IntOffset(0, overlayOffsetY.roundToInt()) }
                            .padding(horizontal = 24.dp)
                            .fillMaxWidth()
                            .shadow(8.dp, RoundedCornerShape(12.dp))
                            .clip(RoundedCornerShape(12.dp))
                            .background(Zinc800)
                            .border(1.dp, themeColors.primary, RoundedCornerShape(12.dp))
                            .padding(16.dp), // slightly larger padding for visual pop
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(AppIcons.GripVertical, null, tint = themeColors.primary, modifier = Modifier.size(24.dp))
                        Text(
                            text = draggingCategory!!,
                            style = MaterialTheme.typography.titleMedium.copy(color = Zinc100),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}
