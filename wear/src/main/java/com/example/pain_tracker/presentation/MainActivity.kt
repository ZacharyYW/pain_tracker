package com.example.pain_tracker.presentation

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.Text
import com.example.pain_tracker.presentation.theme.Pain_trackerTheme

// MainActivity is just UI and lifecycle.
// All Samsung SDK logic lives in HealthTrackingManager.
// All data storage lives in DataRepository.

class MainActivity : ComponentActivity() {
    private var lastLogTime by mutableStateOf(0L)
    private val LOG_COOLDOWN_MS = 30_000L
    private var statusMessage by mutableStateOf("waiting for permission...")
    private lateinit var healthTrackingManager: HealthTrackingManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // create the manager, passing a callback to update our status text
        healthTrackingManager = HealthTrackingManager(this) { status ->
            statusMessage = status // onStatusChange does statusMEssage = "..."
        }

        val permissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            permissions.forEach { (perm, granted) ->
                Log.d("PainTracker", "permission: $perm = $granted")
            }
            // connect regardless - BODY_SENSORS returns false on Android 16
            // but the SDK works fine with health.READ_HEART_RATE
            healthTrackingManager.connect()
        }

        setContent {
            Pain_trackerTheme {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(statusMessage)

                    // pain logging buttons - 0 through 4
                    // when tapped, saves current HR+IBI alongside the pain level
                    Text("Log Pain Level:")
                    Row(modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly) {
                        (0..4).forEach { level ->
                            Button(onClick = {
                                val now = System.currentTimeMillis()
                                if (now - lastLogTime >= LOG_COOLDOWN_MS) {
                                    DataRepository.logPain(level)
                                    healthTrackingManager.triggerEcg()
                                    lastLogTime = now
                                    val time = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).format(now)
                                    statusMessage = "level $level logged at $time - hold crown for ECG"
                                } else {
                                    val secondsLeft = (LOG_COOLDOWN_MS - (now - lastLogTime)) / 1000
                                    statusMessage = "wait ${secondsLeft}s"
                                }
                            }) {
                                Text("$level")
                            }
                        }
                    }
                }
            }
        }

        val hrPermission = if (Build.VERSION.SDK_INT >= 36) {
            "android.permission.health.READ_HEART_RATE"
        } else {
            Manifest.permission.BODY_SENSORS
        }

        permissionLauncher.launch(arrayOf(
            Manifest.permission.ACTIVITY_RECOGNITION,
            Manifest.permission.BODY_SENSORS,
            hrPermission,
            "com.samsung.android.hardware.sensormanager.permission.READ_ADDITIONAL_HEALTH_DATA"
        ))
    }

    override fun onDestroy() {
        super.onDestroy()
        healthTrackingManager.disconnect()
    }
}