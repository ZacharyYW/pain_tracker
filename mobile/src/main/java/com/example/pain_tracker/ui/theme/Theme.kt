package com.example.pain_tracker.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

// Map your custom colors to standard Material parts so text fields and buttons use them naturally
private val LightColorScheme = lightColorScheme(
    primary = Surface1,
    onPrimary = TextOnSurface,
    background = BgColor,
    surface = BgColor,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    error = PinkAccent
)

@Composable
fun PerennialTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        typography = Typography, // <-- This activates your Typewriter font!
        content = content
    )
}