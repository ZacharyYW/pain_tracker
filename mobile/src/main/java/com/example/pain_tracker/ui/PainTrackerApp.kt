package com.example.pain_tracker.ui

import androidx.compose.runtime.*
import com.example.pain_tracker.ui.screens.DashboardScreen
import com.example.pain_tracker.ui.screens.DevicePairingScreen
import com.example.pain_tracker.ui.screens.LoginScreen

@Composable
fun PainTrackerApp() {
    var isLoggedIn by remember { mutableStateOf(false) }
    var isPaired by remember { mutableStateOf(false) }

    when {
        !isLoggedIn -> LoginScreen(onLoginSuccess = { isLoggedIn = true })
        !isPaired -> DevicePairingScreen(onPaired = { isPaired = true })
        else -> DashboardScreen()
    }
}