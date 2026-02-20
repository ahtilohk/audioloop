package com.example.audioloop.ui

import android.media.MediaExtractor
import android.media.MediaFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.audioloop.RecordingItem
import com.example.audioloop.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

@Composable
fun RenameDialog(currentName: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit, themeColors: AppColorPalette = AppTheme.SLATE.palette) {
    var textState by remember { mutableStateOf(TextFieldValue(text = currentName, selection = TextRange(currentName.length))) }
    val focusRequester = remember { FocusRequester() }
    
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
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                    TextButton(onClick = onDismiss) { Text("Cancel", color = Zinc400) }
                }
            }
        }
    }
}

@Composable
fun NoteEditDialog(
    currentNote: String,
    fileName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    themeColors: AppColorPalette = AppTheme.SLATE.palette
) {
    var textState by remember { mutableStateOf(TextFieldValue(text = currentNote, selection = TextRange(currentNote.length))) }
    val focusRequester = remember { FocusRequester() }
    val hasNote = currentNote.isNotBlank()

    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Zinc900),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp).widthIn(max = 400.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    if (hasNote) "Edit Note" else "Add Note",
                    style = TextStyle(color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    fileName,
                    style = TextStyle(color = Zinc500, fontSize = 12.sp),
                    maxLines = 1
                )
                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = textState,
                    onValueChange = { textState = it },
                    label = { Text("Lyrics, verse, translation...") },
                    minLines = 4,
                    maxLines = 12,
                    modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
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

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    if (hasNote) {
                        TextButton(onClick = { onConfirm("") }) {
                            Text("Remove", color = Red400)
                        }
                    } else {
                        Spacer(modifier = Modifier.width(1.dp))
                    }
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

private data class AudioInfo(
    val sampleRate: Int = 0,
    val channels: Int = 0,
    val bitRate: Int = 0,
    val codec: String = ""
)

@Composable
fun FileInfoDialog(
    item: RecordingItem,
    onDismiss: () -> Unit,
    themeColors: AppColorPalette = AppTheme.SLATE.palette
) {
    val file = item.file
    val audioInfo = produceState(AudioInfo(), key1 = file) {
        value = withContext(Dispatchers.IO) {
            readAudioInfo(file)
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Zinc900),
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Zinc600)
        ) {
            Column(
                modifier = Modifier.padding(20.dp).widthIn(max = 400.dp)
            ) {
                Text(
                    "File Info",
                    style = TextStyle(color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    item.name.substringBeforeLast(".")
                        .replace(Regex("(\\d{2})_(\\d{2})_(\\d{2})"), "$1:$2:$3"),
                    style = TextStyle(color = themeColors.primary400, fontSize = 13.sp),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(16.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Zinc800.copy(alpha = 0.5f))
                        .border(1.dp, Zinc700, RoundedCornerShape(12.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    InfoRow("Format", file.extension.uppercase())
                    InfoRow("Duration", item.durationString)
                    InfoRow("Size", formatFileSize(file.length()))

                    if (audioInfo.value.sampleRate > 0) {
                        HorizontalDivider(color = Zinc700)
                        InfoRow("Sample Rate", "${audioInfo.value.sampleRate} Hz")
                        InfoRow("Channels", if (audioInfo.value.channels == 1) "Mono" else "Stereo")
                        if (audioInfo.value.bitRate > 0) {
                            InfoRow("Bit Rate", "${audioInfo.value.bitRate / 1000} kbps")
                        }
                        if (audioInfo.value.codec.isNotEmpty()) {
                            InfoRow("Codec", audioInfo.value.codec)
                        }
                    }

                    HorizontalDivider(color = Zinc700)
                    InfoRow("Location", file.parent ?: "Unknown", isPath = true)
                }

                Spacer(modifier = Modifier.height(16.dp))

                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = themeColors.primary600)
                    ) { Text("Close") }
                }
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String, isPath: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = if (isPath) Alignment.Top else Alignment.CenterVertically
    ) {
        Text(
            label,
            style = TextStyle(color = Zinc500, fontSize = 13.sp, fontWeight = FontWeight.Medium),
            modifier = Modifier.widthIn(min = 80.dp)
        )
        Text(
            value,
            style = TextStyle(
                color = Zinc200,
                fontSize = 13.sp,
                fontFamily = if (isPath) FontFamily.Monospace else FontFamily.Default
            ),
            maxLines = if (isPath) 3 else 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false).padding(start = 8.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.End
        )
    }
}

private fun formatFileSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        else -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
    }
}

private fun readAudioInfo(file: File): AudioInfo {
    return try {
        if (file.extension.equals("wav", ignoreCase = true)) {
            readWavInfo(file)
        } else {
            readCompressedInfo(file)
        }
    } catch (_: Exception) {
        AudioInfo()
    }
}

private fun readWavInfo(file: File): AudioInfo {
    val raf = RandomAccessFile(file, "r")
    raf.use {
        val headerBytes = ByteArray(36)
        raf.read(headerBytes)
        val hdr = ByteBuffer.wrap(headerBytes).order(ByteOrder.LITTLE_ENDIAN)
        hdr.position(22)
        val channels = hdr.getShort().toInt().and(0xFFFF)
        val sampleRate = hdr.getInt()
        val byteRate = hdr.getInt()
        hdr.position(34)
        val bitsPerSample = hdr.getShort().toInt().and(0xFFFF)
        return AudioInfo(
            sampleRate = sampleRate,
            channels = channels,
            bitRate = byteRate * 8,
            codec = "PCM ${bitsPerSample}-bit"
        )
    }
}

private fun readCompressedInfo(file: File): AudioInfo {
    val extractor = MediaExtractor()
    try {
        extractor.setDataSource(file.absolutePath)
        for (i in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(i)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
            if (mime.startsWith("audio/")) {
                val sampleRate = try { format.getInteger(MediaFormat.KEY_SAMPLE_RATE) } catch (_: Exception) { 0 }
                val channels = try { format.getInteger(MediaFormat.KEY_CHANNEL_COUNT) } catch (_: Exception) { 0 }
                val bitRate = try { format.getInteger(MediaFormat.KEY_BIT_RATE) } catch (_: Exception) { 0 }
                val codec = when {
                    mime.contains("aac") -> "AAC"
                    mime.contains("mp3") || mime.contains("mpeg") -> "MP3"
                    mime.contains("opus") -> "Opus"
                    mime.contains("vorbis") -> "Vorbis"
                    mime.contains("flac") -> "FLAC"
                    else -> mime.substringAfter("audio/")
                }
                return AudioInfo(sampleRate, channels, bitRate, codec)
            }
        }
        return AudioInfo()
    } catch (_: Exception) {
        return AudioInfo()
    } finally {
        try { extractor.release() } catch (_: Exception) {}
    }
}
