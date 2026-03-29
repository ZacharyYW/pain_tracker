package com.example.pain_tracker.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.pain_tracker.R
import kotlinx.coroutines.delay

// Re-using your palette colors
private val BgColor = Color(0xFFFCF4EC)
private val TextPrimary = Color(0xFF6B3820)

@Composable
fun FallingLeavesLoadingScreen(onTimeout: () -> Unit) { // Added the missing parameter here

    // 1. The Timer Logic
    LaunchedEffect(Unit) {
        delay(3000) // Wait for 3 seconds
        onTimeout()  // Call the function to switch screens
    }

    // 2. The UI
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgColor),
        contentAlignment = Alignment.Center
    ) {
        // Falling Leaves Animation
        repeat(45) { index ->
            val spawnOnScreen = index < 20
            FallingLeaf(index = index, startsOffscreen = !spawnOnScreen)
        }

        // Loading Text
        Text(
            text = "gathering your data...",
            style = MaterialTheme.typography.bodyLarge,
            color = TextPrimary.copy(alpha = 0.8f)
        )
    }
}@Composable
fun FallingLeaf(index: Int, startsOffscreen: Boolean) {
    val infiniteTransition = rememberInfiniteTransition(label = "leaf")

    val leafDrawables = listOf(
        R.drawable.fern_bg,
        R.drawable.heart_leaf_pink,
        R.drawable.maple_leaf_brown,
        R.drawable.wavy_leaf_green
    )

    // 1. RANDOM STATS
    val leafResource: Int = remember(index) { leafDrawables.random() }

    // VARIATION IN SPEED:
    // Fast leaves = 2.5 seconds (2500ms)
    // Slow leaves = 9.0 seconds (9000ms)
    val duration: Int = remember(index) { (6000..9000).random() }

    val delay = 0
    val startX: Float = remember(index) { (-95..95).random() / 100f }

    // Variation in Depth (Forward/Backward)
    val leafScale: Float = remember(index) { (10..45).random() / 100f }
    val leafAlpha: Float = remember(index) { (20..60).random() / 100f }

    // 2. STARTING POSITION
    val initialY = if (startsOffscreen) ((-300..-180).random() / 100f) else remember(index) { (-150..250).random() / 100f }

    // Vertical fall - Speed is determined by the randomized 'duration'
    val yPos by infiniteTransition.animateFloat(
        initialValue = initialY,
        targetValue = 1.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(duration, delayMillis = delay, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ), label = "y"
    )

    /// 1. Randomize whether this leaf starts swaying left (-70) or right (+70)
    val swayDirection = remember(index) { if ((0..1).random() == 0) -1f else 1f }

// 2. Randomize the initial value so they aren't all synced
    val sway by infiniteTransition.animateFloat(
        initialValue = -70f * swayDirection, // Some start at -70, some at 70
        targetValue = 70f * swayDirection,
        animationSpec = infiniteRepeatable(
            // Use a random duration for the sway too so they don't move in a "pack"
            animation = tween((duration * 0.6f).toInt(), easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ), label = "sway"
    )

    Image(
        painter = painterResource(id = leafResource),
        contentDescription = null,
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                val xShift = if (startsOffscreen) -300.dp.toPx() else 0f

                translationX = (startX * size.width) + sway.dp.toPx() + xShift
                translationY = yPos * size.height

                // Rotation also syncs with fall progress
                rotationZ = (yPos * 140f)
                alpha = leafAlpha
                scaleX = leafScale
                scaleY = leafScale
            },
        contentScale = ContentScale.Fit
    )
}