package com.example.pain_tracker.presentation.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import com.example.pain_tracker.R // Ensure this matches your package name!

// 1. Define your custom font family
val TypewriterFont = FontFamily(
    Font(R.font.courier_prime) // <-- Replace with your exact font file name!
)

// 2. Grab the default Material 3 typography baseline
private val defaultTypography = Typography()

// 3. Apply your custom font family globally
val AppTypography = Typography(
    displayLarge = defaultTypography.displayLarge.copy(fontFamily = TypewriterFont),
    displayMedium = defaultTypography.displayMedium.copy(fontFamily = TypewriterFont),
    displaySmall = defaultTypography.displaySmall.copy(fontFamily = TypewriterFont),
    headlineLarge = defaultTypography.headlineLarge.copy(fontFamily = TypewriterFont),
    headlineMedium = defaultTypography.headlineMedium.copy(fontFamily = TypewriterFont),
    headlineSmall = defaultTypography.headlineSmall.copy(fontFamily = TypewriterFont),
    titleLarge = defaultTypography.titleLarge.copy(fontFamily = TypewriterFont),
    titleMedium = defaultTypography.titleMedium.copy(fontFamily = TypewriterFont),
    titleSmall = defaultTypography.titleSmall.copy(fontFamily = TypewriterFont),
    bodyLarge = defaultTypography.bodyLarge.copy(fontFamily = TypewriterFont),
    bodyMedium = defaultTypography.bodyMedium.copy(fontFamily = TypewriterFont),
    bodySmall = defaultTypography.bodySmall.copy(fontFamily = TypewriterFont),
    labelLarge = defaultTypography.labelLarge.copy(fontFamily = TypewriterFont),
    labelMedium = defaultTypography.labelMedium.copy(fontFamily = TypewriterFont),
    labelSmall = defaultTypography.labelSmall.copy(fontFamily = TypewriterFont)
)