package com.example.audioloop.ui

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.text.selection.TextSelectionColors
import com.example.audioloop.ui.theme.*

@Composable
fun RenameDialog(
    currentName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    themeColors: AppColorPalette = AppTheme.CYAN.palette
) {
    var textState by remember { mutableStateOf(TextFieldValue(text = currentName, selection = TextRange(currentName.length))) }
    val focusRequester = remember { FocusRequester() }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Zinc900),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, Zinc800),
            elevation = CardDefaults.cardElevation(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Rename File",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.SemiBold, 
                        color = Zinc100
                    )
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                OutlinedTextField(
                    value = textState,
                    onValueChange = { textState = it },
                    label = { Text("New name") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Zinc100,
                        unfocusedTextColor = Zinc300,
                        focusedBorderColor = themeColors.primary500,
                        unfocusedBorderColor = Zinc700,
                        focusedLabelColor = themeColors.primary500,
                        unfocusedLabelColor = Zinc500,
                        cursorColor = themeColors.primary500,
                        selectionColors = TextSelectionColors(
                            handleColor = themeColors.primary500,
                            backgroundColor = themeColors.primary500.copy(alpha = 0.3f)
                        )
                    ),
                    textStyle = MaterialTheme.typography.bodyLarge,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
                )
                
                LaunchedEffect(Unit) { focusRequester.requestFocus() }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) { 
                        Text("Cancel", style = MaterialTheme.typography.labelLarge.copy(color = Zinc400)) 
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { onConfirm(textState.text) },
                        colors = ButtonDefaults.buttonColors(containerColor = themeColors.primary600),
                        shape = RoundedCornerShape(8.dp)
                    ) { 
                        Text("Save", style = MaterialTheme.typography.labelLarge.copy(color = Color.White)) 
                    }
                }
            }
        }
    }
}

@Composable
fun MoveFileDialog(
    categories: List<String>, 
    onDismiss: () -> Unit, 
    onSelect: (String) -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Zinc900),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, Zinc800),
             elevation = CardDefaults.cardElevation(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(24.dp).heightIn(max = 400.dp)) {
                Text(
                    "Move to Category", 
                    style = MaterialTheme.typography.titleLarge.copy(
                        color = Zinc100, 
                        fontWeight = FontWeight.SemiBold
                    ), 
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                
                LazyColumn(
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    items(categories) { cat ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(cat) }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = cat,
                                style = MaterialTheme.typography.bodyLarge.copy(color = Zinc200),
                                modifier = Modifier.weight(1f)
                            )
                            Icon(
                                imageVector = com.example.audioloop.AppIcons.ChevronRight,
                                contentDescription = null,
                                tint = Zinc500,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        HorizontalDivider(color = Zinc800)
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                TextButton(
                    onClick = onDismiss, 
                    modifier = Modifier.align(Alignment.End)
                ) { 
                    Text("Cancel", style = MaterialTheme.typography.labelLarge.copy(color = Zinc400)) 
                }
            }
        }
    }
}

@Composable
fun DeleteConfirmDialog(
    title: String, 
    text: String, 
    onDismiss: () -> Unit, 
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Zinc900,
        titleContentColor = Zinc100,
        textContentColor = Zinc300,
        title = { 
            Text(title, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)) 
        },
        text = { 
            Text(text, style = MaterialTheme.typography.bodyMedium) 
        },
        confirmButton = { 
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = Red600),
                shape = RoundedCornerShape(8.dp)
            ) { 
                Text("Delete", style = MaterialTheme.typography.labelLarge) 
            }
        },
        dismissButton = { 
            TextButton(onClick = onDismiss) { 
                Text("Cancel", style = MaterialTheme.typography.labelLarge.copy(color = Zinc400)) 
            } 
        }
    )
}
