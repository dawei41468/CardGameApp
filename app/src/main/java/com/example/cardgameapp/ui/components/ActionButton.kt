package com.example.cardgameapp.ui.components

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ActionButton(
    onClick: () -> Unit,
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    ElevatedButton(
        onClick = onClick,
        modifier = modifier, // No shadow modifier, relying on elevation
        colors = ButtonDefaults.elevatedButtonColors(
            containerColor = if (enabled) Color(0xFFFF5722) else Color(0xFFFFCCBC),
            contentColor = Color.White
        ),
        enabled = enabled,
        elevation = ButtonDefaults.elevatedButtonElevation(
            defaultElevation = 2.dp, // Slight lift when idle
            pressedElevation = 2.dp, // Same lift when pressed (no extra shadow)
            disabledElevation = 0.dp // Same lift when disabled
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Text(text, fontSize = 16.sp)
    }
}