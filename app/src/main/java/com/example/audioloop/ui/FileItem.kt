package com.example.audioloop.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audioloop.AppIcons
import com.example.audioloop.RecordingItem
import com.example.audioloop.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
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
    themeColors: AppColorPalette = AppTheme.CYAN.palette,
    playlistPosition: Int = 0 // 0 means not in playlist, >0 is position in playlist
) {
    var menuExpanded by remember { mutableStateOf(false) }

    // Animation states
    val scale by animateFloatAsState(targetValue = if (isDragging) 1.05f else 1f, label = "scale")
    val elevation by animateDpAsState(targetValue = if (isDragging) 8.dp else if (isPlaying) 2.dp else 0.dp, label = "elevation")
    val borderAlpha by animateFloatAsState(targetValue = if (isSelected || isPlaying) 1f else 0.5f, label = "borderAlpha")

    // Card Container
    OutlinedCard(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp, horizontal = 16.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clickable {
                if (isSelectionMode) onToggleSelect() else if (isPlaying) onStop() else onPlay()
            },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.outlinedCardColors(
            containerColor = if (isDragging) themeColors.primaryContainer.copy(alpha = 0.9f)
                           else if (isSelected) themeColors.secondaryContainer.copy(alpha = 0.3f)
                           else Zinc900.copy(alpha = 0.5f),
            contentColor = Zinc200
        ),
        border = BorderStroke(
            1.dp, 
            if (isDragging) themeColors.primary 
            else if (isSelected) themeColors.secondary.copy(alpha = borderAlpha)
            else if (isPlaying) themeColors.primary.copy(alpha = borderAlpha)
            else Zinc800
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = elevation)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            
            // --- Top Row: Info & Controls ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Drag Handle (Always visible or only in edit mode? Always visible is better for easy reorder)
                Icon(
                    imageVector = AppIcons.GripVertical,
                    contentDescription = "Drag",
                    tint = if (isDragging) themeColors.primary else Zinc600,
                    modifier = Modifier.size(20.dp)
                )

                // Selection / Play Status Indicator
                Box(modifier = Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                    if (isSelectionMode) {
                        Surface(
                            onClick = { onToggleSelect() },
                            shape = CircleShape,
                            color = if (isSelected) themeColors.primary else Color.Transparent,
                            border = BorderStroke(2.dp, if (isSelected) themeColors.primary else Zinc600),
                            modifier = Modifier.size(24.dp)
                        ) {
                            if (isSelected && selectionOrder > 0) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = selectionOrder.toString(),
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    } else {
                        // Play/Pause Button
                        FilledIconButton(
                            onClick = { 
                                if (isPlaying) {
                                    if (isPaused) onResume() else onPause() 
                                } else {
                                    onPlay()
                                }
                            },
                            modifier = Modifier.size(40.dp),
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = if (isPlaying) themeColors.primary else Zinc800,
                                contentColor = if (isPlaying) Color.White else themeColors.primary
                            )
                        ) {
                            Icon(
                                imageVector = if (isPlaying && !isPaused) AppIcons.Pause else AppIcons.PlayArrow,
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                // File Info
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.name.substringBeforeLast("."),
                        style = MaterialTheme.typography.titleMedium,
                        color = if (isPlaying) themeColors.primary else Zinc200,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = item.durationString,
                            style = MaterialTheme.typography.labelMedium,
                            color = Zinc500
                        )
                        if (playlistPosition > 0) {
                             Surface(
                                 color = themeColors.tertiaryContainer,
                                 shape = RoundedCornerShape(4.dp),
                                 modifier = Modifier.padding(start = 4.dp)
                             ) {
                                 Text(
                                     text = "Queue #$playlistPosition",
                                     style = MaterialTheme.typography.labelSmall,
                                     color = themeColors.onTertiaryContainer,
                                     modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                 )
                             }
                        }
                    }
                }

                // Menu (More Options)
                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(AppIcons.MoreVert, "More", tint = Zinc500)
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                        modifier = Modifier.background(Zinc900).border(1.dp, Zinc800, RoundedCornerShape(8.dp))
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
                        HorizontalDivider(color = Zinc800)
                        DropdownMenuItem(
                            text = { Text("Delete", color = Red400) },
                            leadingIcon = { Icon(AppIcons.Delete, null, tint = Red400) },
                            onClick = { menuExpanded = false; onDelete() }
                        )
                    }
                }
            }
            
            // --- Bottom Section: Waveform (Animated visibility) ---
            androidx.compose.animation.AnimatedVisibility(
                visible = isPlaying,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .background(Zinc950.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                        .border(1.dp, Zinc800, RoundedCornerShape(8.dp))
                        .padding(12.dp)
                ) {
                    // Minimized Waveform Visualization
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(32.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        repeat(40) { i ->
                            val height = 0.3f + (Math.random() * 0.7f).toFloat()
                            val isActive = (i / 40f) < (currentProgress / 100f) // Approximate progress visualization
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 1.dp)
                                    .fillMaxHeight(height)
                                    .background(
                                        if (isActive) themeColors.primary400 else Zinc700.copy(alpha = 0.5f),
                                        CircleShape
                                    )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Time and Slider
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = currentTimeString,
                            style = MaterialTheme.typography.labelSmall,
                            color = themeColors.primary300,
                            modifier = Modifier.width(40.dp)
                        )
                        
                        Slider(
                            value = currentProgress,
                            onValueChange = onSeek,
                            colors = SliderDefaults.colors(
                                thumbColor = themeColors.primary,
                                activeTrackColor = themeColors.primary,
                                inactiveTrackColor = Zinc800
                            ),
                            modifier = Modifier.weight(1f)
                        )
                        
                        Text(
                            text = item.durationString,
                            style = MaterialTheme.typography.labelSmall,
                            color = Zinc500,
                            modifier = Modifier.width(40.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.End
                        )
                    }
                    
                    // Extra Controls (Stop)
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                         OutlinedButton(
                             onClick = onStop,
                             border = BorderStroke(1.dp, Red800),
                             colors = ButtonDefaults.outlinedButtonColors(contentColor = Red400),
                             modifier = Modifier.height(32.dp),
                             contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp)
                         ) {
                             Text("Stop Playback", fontSize = 12.sp)
                         }
                    }
                }
            }
        }
    }
}
