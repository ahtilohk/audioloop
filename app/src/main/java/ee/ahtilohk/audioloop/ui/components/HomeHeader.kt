package ee.ahtilohk.audioloop.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ee.ahtilohk.audioloop.AudioLoopUiState
import ee.ahtilohk.audioloop.AppIcons
import ee.ahtilohk.audioloop.R

@Composable
fun HomeHeader(
    uiState: AudioLoopUiState,
    onSearchClick: () -> Unit,
    onPlaylistClick: () -> Unit
) {
    val themeColors = uiState.currentTheme.palette

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f, fill = false)
        ) {
            Icon(
                imageVector = AppIcons.GraphicEq,
                contentDescription = stringResource(R.string.a11y_logo),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Text(
                text = stringResource(R.string.header_title),
                style = MaterialTheme.typography.titleLarge.copy(
                    brush = Brush.horizontalGradient(
                        colors = listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.primary)
                    ),
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.5).sp
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            if (uiState.isProUser) {
                Surface(
                    color = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.padding(start = 6.dp)
                ) {
                    Text(
                        text = stringResource(R.string.label_pro),
                        color = Color.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.wrapContentWidth()
        ) {
            IconButton(
                onClick = onSearchClick,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = AppIcons.Search,
                    contentDescription = stringResource(R.string.a11y_search),
                    tint = if (uiState.searchQuery.isNotEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp)
                )
            }

            Surface(
                onClick = onPlaylistClick,
                modifier = Modifier.widthIn(min = 125.dp),
                shape = RoundedCornerShape(16.dp),
                color = themeColors.primaryContainer.copy(alpha = 0.25f),
                border = BorderStroke(1.2.dp, themeColors.primary.copy(alpha = 0.6f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = AppIcons.QueueMusic,
                        contentDescription = stringResource(R.string.a11y_playlists),
                        tint = themeColors.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = stringResource(R.string.label_playlists),
                        style = MaterialTheme.typography.labelLarge.copy(
                            color = themeColors.primary,
                            fontWeight = FontWeight.ExtraBold
                        ),
                        maxLines = 1
                    )
                }
            }
        }
    }
}
