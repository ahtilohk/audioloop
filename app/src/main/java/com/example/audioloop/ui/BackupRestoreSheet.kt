package com.example.audioloop.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audioloop.AppIcons
import com.example.audioloop.BackupInfo
import com.example.audioloop.ui.theme.*

@Composable
fun BackupRestoreSheet(
    isBackupSignedIn: Boolean,
    backupEmail: String,
    backupProgress: String,
    isBackupRunning: Boolean,
    onBackupSignIn: () -> Unit,
    onBackupSignOut: () -> Unit,
    onBackupCreate: () -> Unit,
    onBackupList: () -> Unit,
    backupList: List<BackupInfo>,
    onRestoreFromBackup: (String) -> Unit,
    onDeleteBackup: (String) -> Unit,
    onClose: () -> Unit,
    themeColors: AppColorPalette
) {
    var restoreConfirmBackup by remember { mutableStateOf<BackupInfo?>(null) }

    // Restore confirmation dialog
    restoreConfirmBackup?.let { backup ->
        AlertDialog(
            onDismissRequest = { restoreConfirmBackup = null },
            containerColor = Zinc900,
            titleContentColor = Color.White,
            textContentColor = Zinc300,
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = AppIcons.CloudDownload,
                        contentDescription = null,
                        tint = themeColors.primary400,
                        modifier = Modifier.size(22.dp)
                    )
                    Text("Restore Backup?")
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        backup.date,
                        style = TextStyle(color = Color.White, fontWeight = FontWeight.Medium)
                    )
                    Text(
                        backup.name,
                        style = TextStyle(color = Zinc400, fontSize = 12.sp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (backup.sizeBytes > 0) {
                        val mb = backup.sizeBytes / (1024.0 * 1024.0)
                        Text(
                            String.format("%.1f MB", mb),
                            style = TextStyle(color = Zinc500, fontSize = 12.sp)
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "This will replace your current files, categories, and settings with the backup contents.",
                        style = TextStyle(color = Sunset400, fontSize = 12.sp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onRestoreFromBackup(backup.id)
                        restoreConfirmBackup = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = themeColors.primary600)
                ) {
                    Icon(
                        imageVector = AppIcons.CloudDownload,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Restore")
                }
            },
            dismissButton = {
                TextButton(onClick = { restoreConfirmBackup = null }) {
                    Text("Cancel", color = Zinc400)
                }
            }
        )
    }

    BackHandler { onClose() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Zinc950)
            .statusBarsPadding()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // ── Gradient Header ──
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                themeColors.primary900.copy(alpha = 0.4f),
                                Zinc950
                            )
                        )
                    )
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Back button
                        IconButton(
                            onClick = onClose,
                            modifier = Modifier
                                .size(40.dp)
                                .background(Zinc900, CircleShape)
                                .border(1.dp, Zinc700, CircleShape)
                        ) {
                            Icon(AppIcons.ArrowBack, contentDescription = "Back", tint = Zinc300)
                        }

                        Column {
                            Text(
                                "Backup & Restore",
                                style = TextStyle(
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 22.sp
                                )
                            )
                            val statusText = when {
                                isBackupSignedIn -> backupEmail
                                else -> "Google Drive"
                            }
                            Text(
                                statusText,
                                style = TextStyle(color = themeColors.primary400, fontSize = 13.sp),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            // ── Content ──
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Progress/error text
                if (backupProgress.isNotEmpty()) {
                    val progressColor = when {
                        backupProgress.startsWith("✅") -> Forest400
                        backupProgress.startsWith("❌") -> Red400
                        else -> themeColors.primary300
                    }
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Zinc900.copy(alpha = 0.6f),
                        border = BorderStroke(1.dp, progressColor.copy(alpha = 0.3f))
                    ) {
                        Text(
                            text = backupProgress,
                            style = TextStyle(color = progressColor, fontSize = 13.sp),
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                        )
                    }
                }

                if (!isBackupSignedIn) {
                    // ── Not signed in ──
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = Zinc900.copy(alpha = 0.5f),
                        border = BorderStroke(1.dp, Zinc700)
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Icon(
                                imageVector = AppIcons.CloudSync,
                                contentDescription = null,
                                tint = Zinc500,
                                modifier = Modifier.size(48.dp)
                            )
                            Text(
                                "Sign in to back up your recordings and settings to Google Drive",
                                style = TextStyle(
                                    color = Zinc400,
                                    fontSize = 14.sp,
                                    lineHeight = 20.sp
                                ),
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp),
                                color = if (isBackupRunning) Zinc700 else themeColors.primary600,
                                onClick = { if (!isBackupRunning) onBackupSignIn() }
                            ) {
                                Box(
                                    modifier = Modifier.padding(vertical = 14.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        if (isBackupRunning) "Signing in..." else "Sign in with Google",
                                        style = TextStyle(
                                            color = Color.White,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    )
                                }
                            }
                        }
                    }
                } else {
                    // ── Signed in ──
                    // Account info card
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Zinc900.copy(alpha = 0.5f),
                        border = BorderStroke(1.dp, Zinc700)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(Forest400)
                                )
                                Text(
                                    backupEmail,
                                    style = TextStyle(color = Zinc200, fontSize = 14.sp),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                            }
                            Text(
                                "Sign out",
                                style = TextStyle(
                                    color = Zinc500,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                ),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { onBackupSignOut() }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }

                    // Backup & Restore action buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Surface(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp),
                            color = if (isBackupRunning) Zinc700 else themeColors.primary600,
                            onClick = { if (!isBackupRunning) onBackupCreate() }
                        ) {
                            Row(
                                modifier = Modifier.padding(vertical = 14.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = AppIcons.CloudUpload,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "Backup",
                                    style = TextStyle(
                                        color = Color.White,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                )
                            }
                        }
                        Surface(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp),
                            color = if (isBackupRunning) Zinc700 else Zinc800,
                            border = BorderStroke(1.dp, themeColors.primary600.copy(alpha = 0.6f)),
                            onClick = { if (!isBackupRunning) onBackupList() }
                        ) {
                            Row(
                                modifier = Modifier.padding(vertical = 14.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = AppIcons.CloudDownload,
                                    contentDescription = null,
                                    tint = themeColors.primary300,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "Restore",
                                    style = TextStyle(
                                        color = themeColors.primary300,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                )
                            }
                        }
                    }

                    // Backup list (for restore)
                    if (backupList.isNotEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = Zinc900.copy(alpha = 0.5f),
                            border = BorderStroke(1.dp, Zinc700)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    "Available backups",
                                    style = TextStyle(
                                        color = Zinc400,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                )
                                backupList.forEach { backup ->
                                    Surface(
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(12.dp),
                                        color = Zinc800,
                                        onClick = { restoreConfirmBackup = backup }
                                    ) {
                                        Row(
                                            Modifier
                                                .fillMaxWidth()
                                                .padding(14.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    backup.date,
                                                    style = TextStyle(
                                                        color = Zinc200,
                                                        fontSize = 14.sp,
                                                        fontWeight = FontWeight.Medium
                                                    )
                                                )
                                                Text(
                                                    backup.name,
                                                    style = TextStyle(color = Zinc500, fontSize = 11.sp),
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                if (backup.sizeBytes > 0) {
                                                    val mb = backup.sizeBytes / (1024.0 * 1024.0)
                                                    Text(
                                                        String.format("%.1f MB", mb),
                                                        style = TextStyle(color = Zinc500, fontSize = 11.sp)
                                                    )
                                                }
                                                Icon(
                                                    imageVector = AppIcons.CloudDownload,
                                                    contentDescription = "Restore",
                                                    tint = themeColors.primary400,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
