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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
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
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
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
import androidx.compose.ui.semantics.*
import androidx.compose.ui.semantics.Role
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import android.graphics.Paint
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.DrawScope

import androidx.compose.runtime.mutableFloatStateOf


@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
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
    onSplit: () -> Unit = {},
    onNormalize: () -> Unit = {},
    onAutoTrim: () -> Unit = {},
    onEditNote: () -> Unit = {},
    onShowInfo: () -> Unit = {},
    currentProgress: Float = 0f,
    currentTimeString: String = "00:00",
    onReorder: (Int) -> Unit = {},
    isDragging: Boolean = false,
    themeColors: ee.ahtilohk.audioloop.ui.theme.AppColorPalette = ee.ahtilohk.audioloop.ui.theme.AppTheme.SLATE.palette,
    playlistPosition: Int = 0,
    onTuneClick: () -> Unit = {}
) {
    val ctx = LocalContext.current
    var menuExpanded by remember { mutableStateOf(false) }

    // Modern MD3 card states
    val backgroundColor = when {
        isDragging -> themeColors.primary.copy(alpha = 0.15f)
        isPlaying -> themeColors.primary.copy(alpha = 0.1f)
        isSelected -> themeColors.secondary.copy(alpha = 0.12f)
        else -> MaterialTheme.colorScheme.surface
    }

    val borderColor = when {
        isDragging -> themeColors.primary
        isPlaying -> themeColors.primary.copy(alpha = 0.5f)
        isSelected -> themeColors.secondary.copy(alpha = 0.5f)
        else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    }

    val scale by animateFloatAsState(targetValue = if (isDragging) 1.03f else 1f, label = "scale")
    val spacing = LocalSpacing.current
    val elevation by animateDpAsState(targetValue = if (isDragging) spacing.small else if (isPlaying) spacing.extraSmall else 2.dp, label = "elevation")

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp, horizontal = 8.dp)
            .border(
                0.5.dp,
                if (isPlaying) themeColors.primary.copy(alpha = 0.5f) else Color.Transparent,
                RoundedCornerShape(16.dp)
            ),
        color = backgroundColor,
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .clickable {
                    if (isSelectionMode) onToggleSelect() else if (isPlaying) onStop() else onPlay()
                }
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.small)
            ) {

                Box(
                    modifier = Modifier
                        .size(width = 28.dp, height = 48.dp), // Narrow but tall touch target
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = AppIcons.GripVertical,
                        contentDescription = stringResource(R.string.a11y_drag_reorder),
                        tint = if (isDragging) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, // Visual feedback
                        modifier = Modifier.size(20.dp)
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
                            if (isSelected) themeColors.primary else MaterialTheme.colorScheme.onSurfaceVariant
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
                } else {
                    val icon = when {
                        isPlaying && !isPaused -> AppIcons.Pause
                        isPlaying && isPaused -> AppIcons.PlayArrow
                        else -> AppIcons.PlayArrow
                    }
                    val containerColor = if (isPlaying) themeColors.primary else themeColors.primaryContainer
                    val contentColor = if (isPlaying) Color.White else themeColors.onPrimaryContainer
                    
                    FilledIconButton(
                        onClick = { if (isPlaying) (if (isPaused) onResume() else onPause()) else onPlay() },
                        modifier = Modifier.size(36.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = containerColor,
                            contentColor = contentColor
                        )
                    ) {
                        Icon(icon, null, modifier = Modifier.size(18.dp))
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
                        val displayName = item.name.substringBeforeLast(".")
                            .replace(Regex("(\\d{2})_(\\d{2})_(\\d{2})"), "$1:$2:$3")
                        Text(
                            text = displayName,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = if (isPlaying) themeColors.primary else MaterialTheme.colorScheme.onSurface,
                                fontWeight = if (isPlaying) FontWeight.Bold else FontWeight.Medium
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (item.note.isNotBlank()) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = themeColors.primary.copy(alpha = 0.1f)
                            ) {
                                Text(
                                    text = "Aa",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = themeColors.primary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 9.sp
                                    ),
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }
                    }
                    
                    // Time / Status text
                    val statusText = if (isPlaying) {
                        item.durationString
                    } else item.durationString
                    
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = if (isPlaying) themeColors.primary.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = if (isPlaying) FontWeight.Bold else FontWeight.Normal
                        )
                    )
                }

                // Tune (Practice Toolbox) Icon
                // Always visible Overflow Menu (unless in selection mode)
                if (!isSelectionMode) {
                    Box {
                        IconButton(
                            onClick = { menuExpanded = true },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = AppIcons.MoreVert,
                                contentDescription = stringResource(R.string.a11y_menu),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false },
                            modifier = Modifier.background(MaterialTheme.colorScheme.surface).border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                        ) {
                            if (isPlaying) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.btn_stop), color = Red400) },
                                    leadingIcon = { Icon(AppIcons.Stop, null, tint = Red400) },
                                    onClick = { menuExpanded = false; onStop() }
                                )
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            }
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.menu_rename), color = MaterialTheme.colorScheme.onSurface) },
                                leadingIcon = { Icon(AppIcons.Edit, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                                onClick = { menuExpanded = false; onRename() }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.menu_trim), color = MaterialTheme.colorScheme.onSurface) },
                                leadingIcon = { Icon(AppIcons.ContentCut, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                                onClick = { menuExpanded = false; onTrim() }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.menu_move), color = MaterialTheme.colorScheme.onSurface) },
                                leadingIcon = { Icon(AppIcons.ArrowForward, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                                onClick = { menuExpanded = false; onMove() }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.menu_share), color = MaterialTheme.colorScheme.onSurface) },
                                leadingIcon = { Icon(AppIcons.Share, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                                onClick = { menuExpanded = false; onShare() }
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.btn_delete), color = Red400) },
                                leadingIcon = { Icon(AppIcons.Delete, null, tint = Red400) },
                                onClick = { menuExpanded = false; onDelete() }
                            )
                        }
                    }
                }
            }
            
        }
    }
}



