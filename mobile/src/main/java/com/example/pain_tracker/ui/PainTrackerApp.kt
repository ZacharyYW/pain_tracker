package com.example.pain_tracker.ui

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pain_tracker.ui.screens.DashboardScreen
import com.example.pain_tracker.ui.screens.DevicePairingScreen
import com.example.pain_tracker.ui.screens.LoginScreen
import com.example.pain_tracker.ui.screens.FallingLeavesLoadingScreen // Ensure you create this file

// ── colours matching DashboardScreen palette ──────────────────────────────────
private val BgColor     = Color(0xFFFCF4EC)
private val Brown       = Color(0xFF6B3820)
private val Green       = Color(0xFF7A9B6A)
private val BrownMuted  = Color(0xFFBFA08A)

// ── nav destinations ──────────────────────────────────────────────────────────
private enum class NavTab(val label: String, val icon: ImageVector) {
    CALENDAR("calendar", Icons.Default.DateRange),
    INSIGHTS("insights", Icons.Default.ShowChart),
    PROFILE("profile",  Icons.Default.Person)
}


// ── placeholder screens ───────────────────────────────────────────────────────
@Composable
fun InsightsPlaceholder() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("insights coming soon", color = Brown, fontSize = 16.sp)
    }
}

@Composable
fun ProfilePlaceholder() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("profile coming soon", color = Brown, fontSize = 16.sp)
    }
}

@Composable
fun MainShell() {
    var selectedTab by remember { mutableStateOf(NavTab.CALENDAR) }

    Scaffold(
        containerColor = BgColor,
        bottomBar = {
            NavigationBar(
                containerColor = Brown,
                contentColor = Color.White,
                tonalElevation = 0.dp
            ) {
                NavTab.entries.forEach { tab ->
                    val selected = selectedTab == tab
                    NavigationBarItem(
                        selected = selected,
                        onClick = { selectedTab = tab },
                        icon = {
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = tab.label
                            )
                        },
                        label = {
                            Text(tab.label, fontSize = 10.sp)
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Brown,
                            selectedTextColor = Color.White,
                            unselectedIconColor = BrownMuted,
                            unselectedTextColor = BrownMuted,
                            indicatorColor = BgColor
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            when (selectedTab) {
                NavTab.CALENDAR -> DashboardScreen()
                NavTab.INSIGHTS -> InsightsPlaceholder()
                NavTab.PROFILE -> ProfilePlaceholder()
            }
        }
    }
}

@Composable
fun PainTrackerApp() {
    var isLoggedIn by remember { mutableStateOf(false) }
    var isPaired by remember { mutableStateOf(false) }
    var isLoadingComplete by remember { mutableStateOf(false) }

    // Using Crossfade makes the transition between screens look much smoother
    Crossfade(
        targetState = Triple(isLoggedIn, isPaired, isLoadingComplete),
        label = "app_flow"
    ) { (logged, paired, loaded) ->
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
                MainShell()
            }
        }

    }
}
