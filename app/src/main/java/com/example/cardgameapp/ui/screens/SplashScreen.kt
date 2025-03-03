package com.example.cardgameapp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cardgameapp.ui.components.ActionButton

@Composable
fun SplashScreen(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(androidx.compose.ui.graphics.Color(0xFF3F51B5), androidx.compose.ui.graphics.Color(0xFF9FA8DA), androidx.compose.ui.graphics.Color(0xFF3F51B5))))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "Cards Now!",
            style = MaterialTheme.typography.headlineLarge.copy(fontSize = 48.sp),
            color = androidx.compose.ui.graphics.Color.White,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 24.dp)
        )
        ActionButton(
            onClick = onClick,
            text = "Tap to Start",
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 48.dp)
        )
    }
}