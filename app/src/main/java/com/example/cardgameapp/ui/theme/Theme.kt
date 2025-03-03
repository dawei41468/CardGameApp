package com.example.cardgameapp.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Custom color palette
private val CardGameColorScheme = lightColorScheme(
    primary = Color(0xFF00695C), // Teal for buttons and accents
    onPrimary = Color.White,      // White text on primary
    secondary = Color(0xFF455A64), // Darker gray for secondary elements
    onSecondary = Color.White,
    surface = Color(0xFFF5F5F5),  // Light gray for backgrounds
    onSurface = Color.Black,
    background = Color(0xFFE0E0E0) // Slightly darker gray for app background
)

@Composable
fun CardGameTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = CardGameColorScheme,
        typography = MaterialTheme.typography, // Use Material 3 defaults
        content = content
    )
}