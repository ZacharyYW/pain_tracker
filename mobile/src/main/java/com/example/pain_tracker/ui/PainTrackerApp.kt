package com.example.pain_tracker.ui

import androidx.compose.animation.Crossfade
import androidx.compose.runtime.*
import com.example.pain_tracker.ui.screens.DashboardScreen
import com.example.pain_tracker.ui.screens.DevicePairingScreen
import com.example.pain_tracker.ui.screens.LoginScreen
import com.example.pain_tracker.ui.screens.FallingLeavesLoadingScreen // Ensure you create this file

@Composable
fun PainTrackerApp() {
    var isLoggedIn by remember { mutableStateOf(false) }
    var isPaired by remember { mutableStateOf(false) }
    var isLoadingComplete by remember { mutableStateOf(false) }

    // Using Crossfade makes the transition between screens look much smoother
    Crossfade(targetState = Triple(isLoggedIn, isPaired, isLoadingComplete), label = "app_flow") { (logged, paired, loaded) ->
        when {
            // 1. First, user must log in
            !logged -> {
                LoginScreen(onLoginSuccess = { isLoggedIn = true })
            }

            // 2. Second, user pairs their device
            !paired -> {
                DevicePairingScreen(onPaired = { isPaired = true })
            }

            // 3. Third, show the falling leaves for 3 seconds
            !loaded -> {
                FallingLeavesLoadingScreen(onTimeout = {
                    isLoadingComplete = true
                })
            }

            // 4. Finally, show the dashboard
            else -> {
                DashboardScreen()
            }
        }
    }
}