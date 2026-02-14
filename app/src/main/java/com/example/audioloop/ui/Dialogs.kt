package com.example.audioloop.ui

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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.audioloop.ui.theme.*

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
