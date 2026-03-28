package com.example.pain_tracker.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.pain_tracker.model.PainRecord
import com.example.pain_tracker.viewmodel.DashboardViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(viewModel: DashboardViewModel = viewModel()) {
    val painHistory by viewModel.painHistory.collectAsState()
    val latestEcw by viewModel.latestEcw.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Pain Tracker Dashboard") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            EcwCard(ecwValue = latestEcw)

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Recent Pain Classifications",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(painHistory) { record ->
                    PainRecordCard(record)
                }
            }
        }
    }
}

@Composable
fun EcwCard(ecwValue: Float?) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = "BIA Sensor Data", style = MaterialTheme.typography.labelLarge)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Extracellular Water (ECW) Ratio",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            if (ecwValue != null) {
                Text(
                    text = "${(ecwValue * 100).toInt()}%",
                    style = MaterialTheme.typography.displayMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Text(text = "Indicator of fluid retention / inflammation.", style = MaterialTheme.typography.bodySmall)
            } else {
                Text(text = "No scan data available. Trigger scan from watch.")
            }
        }
    }
}

@Composable
fun PainRecordCard(record: PainRecord) {
    val sdf = remember { SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()) }
    val timeString = sdf.format(Date(record.timestamp))

    val (painText, painColor) = when (record.painLevel) {
        0 -> "None" to Color(0xFF4CAF50)
        1 -> "Mild" to Color(0xFFFFC107)
        2 -> "Moderate" to Color(0xFFFF9800)
        3 -> "Severe" to Color(0xFFF44336)
        else -> "Unknown" to Color.Gray
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(text = timeString, style = MaterialTheme.typography.labelMedium)
                Text(
                    text = "Intensity: $painText",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Model Confidence: ${(record.confidence * 100).toInt()}%",
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(painColor, shape = RoundedCornerShape(24.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (record.painLevel >= 2) {
                    Icon(Icons.Default.Warning, contentDescription = "High Pain Alert", tint = Color.White)
                }
            }
        }
    }
}