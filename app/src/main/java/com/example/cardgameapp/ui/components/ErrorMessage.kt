package com.example.cardgameapp.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.text.style.TextAlign
import com.example.cardgameapp.ErrorType
import kotlinx.coroutines.delay

@Composable
fun ErrorMessage(
    message: String,
    errorType: ErrorType,
    onDismiss: () -> Unit
) {
    if (message.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { if (errorType != ErrorType.CRITICAL) onDismiss() },
            title = { Text("Error", textAlign = TextAlign.Center) },
            text = { Text(message, textAlign = TextAlign.Center) },
            confirmButton = {
                if (errorType == ErrorType.CRITICAL) {
                    TextButton(onClick = onDismiss) {
                        Text("OK")
                    }
                }
            },
            dismissButton = null
        )
        if (errorType == ErrorType.TRANSIENT) {
            LaunchedEffect(message) {
                delay(3000) // Auto-dismiss after 3 seconds
                onDismiss()
            }
        }
    }
}