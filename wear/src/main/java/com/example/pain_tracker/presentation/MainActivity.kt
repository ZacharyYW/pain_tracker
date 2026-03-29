package com.example.pain_tracker.presentation

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.Text

// color palette
private val BgColor       = Color(0xFFFCF4EC)
private val Surface1      = Color(0xFF7A9B6A)
private val Surface2      = Color(0xFF725241)
private val Border        = Color(0xFF6B3820)
private val TextPrimary   = Color(0xFF6B3820)
private val TextOnSurface = Color(0xFFFFFFFF)
private val TextMuted     = Color(0xFFDAD8D8)
private val PinkAccent    = Color(0xFFCB5A6C)
private val AmberAccent   = Color(0xFFFFB13D)
private val GreenAccent   = Color(0xFF96F32F)

// button color per pain level: 0=green, 1=amber, 2=amber, 3=pink
private val painColors = listOf(GreenAccent, AmberAccent, AmberAccent, PinkAccent)

class MainActivity : ComponentActivity() {
    private var lastLogTime by mutableStateOf(0L)
    private val LOG_COOLDOWN_MS = 30_000L
    private var statusMessage by mutableStateOf("waiting for permission...")
    private var ecgInProgress by mutableStateOf(false)
    private lateinit var healthTrackingManager: HealthTrackingManager
    private lateinit var syncManager: WatchDataSyncManager
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // keep screen on while app is open so user can interact during pain logging
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // cpu wake lock so tracking and sync don't die when screen would normally sleep
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "PainTracker::TrackingLock"
        )

        // create the manager, passing a callback to update our status text
        healthTrackingManager = HealthTrackingManager(this) { status ->
            statusMessage = status
        }

        syncManager = WatchDataSyncManager(this)

        /*com.google.android.gms.wearable.Wearable.getDataClient(this)
            .getDataItems(
                android.net.Uri.parse("wear://*/pain_entry/"),
                com.google.android.gms.wearable.DataClient.FILTER_PREFIX
            )
            .addOnSuccessListener { items ->
                Log.d("PainTracker", "clearing ${items.count} old items from data layer")
                items.forEach { item ->
                    com.google.android.gms.wearable.Wearable.getDataClient(this)
                        .deleteDataItems(item.uri)
                }
                items.release()
            }*/

         */

        // when ecg finishes (success or fail), re-enable buttons and try to sync
        healthTrackingManager.onEcgComplete = { success ->
            ecgInProgress = false
            if (success) {
                syncManager.syncPendingEntries()
                statusMessage = "logged & synced"
            } else {
                val entries = DataRepository.getPainLogEntries()
                val lastId = entries.lastOrNull()?.id
                if (lastId != null) {
                    DataRepository.removeSyncedEntries(listOf(lastId))
                }
                statusMessage = "ecg failed - try again"
            }
        }

        val permissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            permissions.forEach { (perm, granted) ->
                Log.d("PainTracker", "permission: $perm = $granted")
            }

            val activityRecognition = permissions[Manifest.permission.ACTIVITY_RECOGNITION] == true
            val heartRate = permissions["android.permission.health.READ_HEART_RATE"] == true
            val additionalHealth = permissions["com.samsung.android.hardware.sensormanager.permission.READ_ADDITIONAL_HEALTH_DATA"] == true

            if (activityRecognition && heartRate && additionalHealth) {
                healthTrackingManager.connect()
            } else {
                statusMessage = "missing permissions, app cannot start"
                Log.d("PainTracker", "missing critical permissions")
            }
        }

        setContent {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(BgColor)
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = statusMessage,
                    color = TextPrimary,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Text(
                    text = "Log Pain",
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 6.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    (0..3).forEach { level ->
                        Button(
                            onClick = {
                                val now = System.currentTimeMillis()
                                if (now - lastLogTime >= LOG_COOLDOWN_MS) {
                                    DataRepository.logPain(level)
                                    lastLogTime = now
                                    ecgInProgress = true
                                    statusMessage = "level $level logged - place finger on crown..."

                                    // 3 second delay so user has time to move finger to crown
                                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                                        healthTrackingManager.triggerEcg()
                                        statusMessage = "recording ecg... hold crown"
                                    }, 3000)
                                } else {
                                    val secondsLeft = (LOG_COOLDOWN_MS - (now - lastLogTime)) / 1000
                                    statusMessage = "wait ${secondsLeft}s"
                                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                                        statusMessage = "logged & synced"
                                    }, 1000)
                                }
                            },
                            enabled = !ecgInProgress,
                            modifier = Modifier
                                .size(40.dp)
                                .border(1.dp, Border, CircleShape),
                            shape = CircleShape,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = painColors[level],
                                disabledContainerColor = painColors[level].copy(alpha = 0.4f)
                            )
                        ) {
                            Text(
                                text = "$level",
                                color = TextPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // if using android 15 or earlier, use BODY_SENSORS instead
        val hrPermission = if (Build.VERSION.SDK_INT >= 36) {
            "android.permission.health.READ_HEART_RATE"
        } else {
            Manifest.permission.BODY_SENSORS
        }

        permissionLauncher.launch(arrayOf(
            Manifest.permission.ACTIVITY_RECOGNITION,
            hrPermission,
            "com.samsung.android.hardware.sensormanager.permission.READ_ADDITIONAL_HEALTH_DATA"
        ))
    }

    override fun onResume() {
        super.onResume()
        wakeLock?.acquire(10 * 60 * 1000L)
    }

    override fun onPause() {
        super.onPause()
        wakeLock?.let {
            if (it.isHeld) it.release()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        syncManager.syncPendingEntries()
        healthTrackingManager.disconnect()
    }
}