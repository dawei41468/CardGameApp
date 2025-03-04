package com.example.cardgameapp.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cardgameapp.ErrorType
import kotlinx.coroutines.delay

@Composable
fun ErrorMessage(
    message: String,
    errorType: ErrorType,
    onDismiss: () -> Unit
) {
    if (message.isNotEmpty()) {
        AnimatedVisibility(
            visible = true,
            enter = fadeIn(),
            exit = fadeOut(animationSpec = tween(durationMillis = 500)) // Faster exit
        ) {
            AlertDialog(
                onDismissRequest = { if (errorType != ErrorType.CRITICAL) onDismiss() },
                modifier = Modifier
                    .background(Color(0xFFFFEBEE), shape = RoundedCornerShape(12.dp)) // Light red
                    .widthIn(max = 280.dp), // Slimmer width
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Error",
                            tint = Color(0xFFD32F2F), // Dark red
                            modifier = Modifier.size(20.dp) // Smaller icon
                        )
                        Spacer(Modifier.width(6.dp)) // Tighter spacing
                        Text(
                            text = "Error",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp, // Reduced from 20sp
                            color = Color(0xFFD32F2F),
                            textAlign = TextAlign.Center
                        )
                    }
                },
                text = {
                    Text(
                        text = message,
                        color = Color.Black,
                        textAlign = TextAlign.Center,
                        fontSize = 14.sp // Reduced from 16sp
                    )
                },
                confirmButton = {
                    if (errorType == ErrorType.CRITICAL) {
                        TextButton(onClick = onDismiss) {
                            Text("OK", color = Color(0xFFD32F2F), fontSize = 14.sp)
                        }
                    }
                },
                dismissButton = null,
                containerColor = Color(0xFFFFEBEE),
                shape = RoundedCornerShape(12.dp),
                tonalElevation = 0.dp // Flat look
            )
        }
        if (errorType == ErrorType.TRANSIENT) {
            LaunchedEffect(message) {
                delay(3000) // Keep 3s for transient
                onDismiss()
            }
        }
    }
}