package com.example.cardgameapp.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
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
import kotlinx.coroutines.delay

@Composable
fun SuccessMessage(
    message: String,
    onDismiss: () -> Unit
) {
    if (message.isNotEmpty()) {
        AnimatedVisibility(
            visible = true,
            enter = fadeIn(),
            exit = fadeOut(animationSpec = tween(durationMillis = 400)) // Faster exit
        ) {
            AlertDialog(
                onDismissRequest = { onDismiss() },
                modifier = Modifier
                    .background(Color(0xFFE8F5E9), shape = RoundedCornerShape(12.dp)) // Light green
                    .widthIn(max = 280.dp), // Slimmer width
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Success",
                            tint = Color(0xFF2E7D32), // Dark green
                            modifier = Modifier.size(20.dp) // Smaller icon
                        )
                        Spacer(Modifier.width(6.dp)) // Tighter spacing
                        Text(
                            text = "Success",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp, // Reduced from 20sp
                            color = Color(0xFF2E7D32),
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
                confirmButton = {},
                dismissButton = {},
                containerColor = Color(0xFFE8F5E9),
                shape = RoundedCornerShape(12.dp),
                tonalElevation = 0.dp // Flat look
            )
        }
        LaunchedEffect(message) {
            delay(2000) // Keep 2s for success
            onDismiss()
        }
    }
}