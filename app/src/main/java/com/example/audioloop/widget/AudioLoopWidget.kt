package com.example.audioloop.widget

import android.content.Context
import android.content.Intent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.*
import androidx.glance.text.*
import androidx.glance.unit.ColorProvider
import com.example.audioloop.MainActivity

class AudioLoopWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val prefs = context.getSharedPreferences("AudioLoopWidgetPrefs", Context.MODE_PRIVATE)
        val category = prefs.getString("current_category", "General") ?: "General"
        val lastFileName = prefs.getString("last_file_name", "") ?: ""
        val lastFileDuration = prefs.getString("last_file_duration", "") ?: ""
        val themeName = prefs.getString("theme_name", "SLATE") ?: "SLATE"

        provideContent {
            WidgetContent(
                context = context,
                category = category,
                lastFileName = lastFileName,
                lastFileDuration = lastFileDuration,
                themeName = themeName
            )
        }
    }
}

@androidx.compose.runtime.Composable
private fun WidgetContent(
    context: Context,
    category: String,
    lastFileName: String,
    lastFileDuration: String,
    themeName: String
) {
    // Theme accent color (always dark widget, so single color)
    val accentColor = ColorProvider(
        when (themeName) {
            "OCEAN" -> Color(0xFF78A8D6)
            "SUNSET" -> Color(0xFFE8A98A)
            "FOREST" -> Color(0xFF74B890)
            "VIOLET" -> Color(0xFFAD90D6)
            "ROSE" -> Color(0xFFE088A6)
            else -> Color(0xFF94A3B8) // SLATE
        }
    )

    val bgColor = ColorProvider(Color(0xFF0F172A))
    val surfaceColor = ColorProvider(Color(0xFF1E293B))
    val textPrimary = ColorProvider(Color(0xFFE4E4E7))
    val textSecondary = ColorProvider(Color(0xFFA1A1AA))
    val textMuted = ColorProvider(Color(0xFF71717A))
    val whiteColor = ColorProvider(Color.White)

    // Intent for Record action
    val recordIntent = Intent(context, MainActivity::class.java).apply {
        action = "com.example.audioloop.WIDGET_RECORD"
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }

    // Intent for Play action
    val playIntent = Intent(context, MainActivity::class.java).apply {
        action = "com.example.audioloop.WIDGET_PLAY"
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }

    // Main container
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .cornerRadius(20.dp)
            .background(bgColor)
            .padding(16.dp)
    ) {
        Column(
            modifier = GlanceModifier.fillMaxSize(),
            verticalAlignment = Alignment.Vertical.Top
        ) {
            // Header row: App name + category badge
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.Vertical.CenterVertically,
                horizontalAlignment = Alignment.Horizontal.Start
            ) {
                Text(
                    text = "🎵",
                    style = TextStyle(fontSize = 16.sp)
                )
                Spacer(modifier = GlanceModifier.width(6.dp))
                Text(
                    text = "Loop & Learn",
                    style = TextStyle(
                        color = accentColor,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
                Spacer(modifier = GlanceModifier.defaultWeight())
                // Category badge
                Box(
                    modifier = GlanceModifier
                        .cornerRadius(8.dp)
                        .background(surfaceColor)
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = category,
                        style = TextStyle(
                            color = textSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    )
                }
            }

            Spacer(modifier = GlanceModifier.height(10.dp))

            // Last file info
            if (lastFileName.isNotEmpty()) {
                Row(
                    modifier = GlanceModifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Vertical.CenterVertically
                ) {
                    Text(
                        text = "♪",
                        style = TextStyle(color = accentColor, fontSize = 12.sp)
                    )
                    Spacer(modifier = GlanceModifier.width(6.dp))
                    Text(
                        text = lastFileName,
                        style = TextStyle(
                            color = textPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Normal
                        ),
                        maxLines = 1
                    )
                    Spacer(modifier = GlanceModifier.defaultWeight())
                    Text(
                        text = lastFileDuration,
                        style = TextStyle(
                            color = textMuted,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Normal
                        )
                    )
                }
            } else {
                Text(
                    text = "No recordings yet",
                    style = TextStyle(
                        color = textMuted,
                        fontSize = 12.sp,
                        fontStyle = FontStyle.Italic
                    )
                )
            }

            Spacer(modifier = GlanceModifier.defaultWeight())

            // Action buttons row
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                horizontalAlignment = Alignment.Horizontal.CenterHorizontally,
                verticalAlignment = Alignment.Vertical.CenterVertically
            ) {
                // Record button
                Box(
                    modifier = GlanceModifier
                        .defaultWeight()
                        .cornerRadius(12.dp)
                        .background(accentColor)
                        .padding(vertical = 10.dp)
                        .clickable(actionStartActivity(recordIntent)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "🎤 Record",
                        style = TextStyle(
                            color = whiteColor,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }

                Spacer(modifier = GlanceModifier.width(8.dp))

                // Play button
                Box(
                    modifier = GlanceModifier
                        .defaultWeight()
                        .cornerRadius(12.dp)
                        .background(surfaceColor)
                        .padding(vertical = 10.dp)
                        .clickable(actionStartActivity(playIntent)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "▶ Play",
                        style = TextStyle(
                            color = accentColor,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }
        }
    }
}
