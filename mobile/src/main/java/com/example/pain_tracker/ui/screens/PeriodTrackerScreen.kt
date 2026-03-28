package com.example.pain_tracker.ui.screens

import androidx.compose.runtime.*
import androidx.compose.material3.*
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.pain_tracker.CycleSettings
import java.time.LocalDate
import com.example.pain_tracker.PeriodCalculator

@Composable
fun PeriodTrackerScreen() {
    // 'remember' keeps the data safe when the screen rotates or updates
    // 'mutableStateOf' tells the UI "hey, if this changes, redraw yourself!"
    var lastPeriodDate by remember { mutableStateOf(LocalDate.now().minusDays(10)) }

    val calculator = PeriodCalculator()
    val settings = CycleSettings(lastPeriodDate = lastPeriodDate)
    val nextDate = calculator.predictNextPeriod(settings)
    val currentPhase = calculator.getCurrentPhase(settings)

    Column(modifier = Modifier.padding(16.dp)) {
        Text(text = "Period Tracker", style = MaterialTheme.typography.headlineMedium)

        Spacer(modifier = Modifier.height(16.dp))

        // A nice card to show the status
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(text = "Current Phase: $currentPhase", style = MaterialTheme.typography.titleLarge)
                Text(text = "Next Period: $nextDate")
            }
        }
    }
}