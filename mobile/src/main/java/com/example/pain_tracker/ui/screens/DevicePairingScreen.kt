package com.example.pain_tracker.ui.screens

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.Wearable

// ── colour palette ────────────────────────────────────────────────────────────
private val BgColor      = Color(0xFFFCF4EC) //cream 0xFFFCF4EC
private val Surface1    = Color(0xFF7A9B6A)
private val Surface2    = Color(0xFF725241)
private val Border      = Color(0xFF6B3820)
private val TextPrimary = Color(0xFF6B3820)

private val TextOnSurface = Color(0xFFFFFFFF)

private val TextMuted   = Color(0xFF887D7D)
@Composable
fun DevicePairingScreen(onPaired: () -> Unit) {
    val context = LocalContext.current
    var connectedNodes by remember { mutableStateOf<List<Node>>(emptyList()) }
    var isScanning by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.background(BgColor).fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "Paired Devices", style = MaterialTheme.typography.headlineMedium, color = TextPrimary)
        Spacer(modifier = Modifier.height(16.dp))

        if (isScanning) {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(16.dp))
            Text("Scanning for Galaxy Watch...")
        } else if (connectedNodes.isEmpty()) {
            Text("No Wear OS devices found.", color = MaterialTheme.colorScheme.error)
            Spacer(modifier = Modifier.height(16.dp))
        }

        Button(
            onClick = {
                isScanning = true
                val nodeClient = Wearable.getNodeClient(context)
                nodeClient.connectedNodes.addOnSuccessListener { nodes ->
                    connectedNodes = nodes
                    isScanning = false
                    Log.d("PainTracker", "Found ${nodes.size} connected nodes.")
                    if (nodes.isNotEmpty()) onPaired()
                }.addOnFailureListener {
                    isScanning = false
                    Log.e("PainTracker", "Failed to scan for nodes.", it)
                }
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = Surface1,
                contentColor = TextOnSurface
        )
        ) {
            Text("Scan & Connect to Watch")
        }

        Spacer(modifier = Modifier.height(16.dp))

        TextButton(
            onClick = { onPaired() },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = BgColor, contentColor = TextMuted)
        ) {
            Text("Skip for now (View Dashboard)")
        }
    }
}