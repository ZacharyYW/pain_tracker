package com.example.pain_tracker.presentation

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.Wearable
import com.google.gson.Gson

class WatchDataSyncManager(private val context: Context) {

    private val messageClient = Wearable.getMessageClient(context)
    private val nodeClient = Wearable.getNodeClient(context)
    private val gson = Gson()

    companion object {
        // must match WearableDataListenerService.MESSAGE_PATH on the phone
        private const val MESSAGE_PATH = "/pain_tracker/session"
    }

    // json shape that WatchSessionParser expects
    private data class WatchRow(
        val timestamp: Long,
        val hr: Float,
        val ibi: List<Int>,
        val ecg: List<Float>,
        val pain_level: Int
    )

    private data class WatchSession(
        val rows: List<WatchRow>
    )

    fun syncPendingEntries() {
        val entries = DataRepository.getReadyToSync()
        if (entries.isEmpty()) {
            Log.d("PainTracker", "sync: nothing to send")
            return
        }

        Log.d("PainTracker", "sync: ${entries.size} entries to send")

        // find the connected phone node first
        nodeClient.connectedNodes.addOnSuccessListener { nodes ->
            val phoneNode = nodes.firstOrNull()
            if (phoneNode == null) {
                Log.e("PainTracker", "sync: no connected phone found")
                return@addOnSuccessListener
            }

            for (entry in entries) {
                // convert each reading in the window to a json row
                // ecg and pain_level are the same for every row, matching csv training format
                val rows = entry.windowOfReadings.map { reading ->
                    WatchRow(
                        timestamp = reading.timestamp,
                        hr = reading.heartRate.toFloat(),
                        ibi = reading.ibiList,
                        ecg = entry.ecgReadings,
                        pain_level = entry.painLevel
                    )
                }

                val json = gson.toJson(WatchSession(rows))
                val entryId = entry.id
                Log.d("PainTracker", "sync payload preview: ${json.take(500)}")
                Log.d("PainTracker", "sync: rows=${rows.size}, pain=${entry.painLevel}, ecg_count=${entry.ecgReadings.size}")
                messageClient.sendMessage(phoneNode.id, MESSAGE_PATH, json.toByteArray())
                    .addOnSuccessListener {
                        Log.d("PainTracker", "sync: sent entry $entryId (${json.length} bytes)")
                        DataRepository.removeSyncedEntries(listOf(entryId))
                    }
                    .addOnFailureListener { e ->
                        Log.e("PainTracker", "sync: failed to send $entryId: ${e.message}")
                    }
            }
        }.addOnFailureListener { e ->
            Log.e("PainTracker", "sync: failed to find nodes: ${e.message}")
        }
    }
}