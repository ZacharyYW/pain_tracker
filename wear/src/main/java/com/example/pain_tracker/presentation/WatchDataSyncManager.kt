package com.example.pain_tracker.presentation

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable

class WatchDataSyncManager(private val context: Context) {

    private val dataClient: DataClient = Wearable.getDataClient(context)

    // push all ready entries to phone via data layer
    // each entry goes as its own data item keyed by uuid
    fun syncPendingEntries() {
        val entries = DataRepository.getReadyToSync()
        if (entries.isEmpty()) {
            Log.d("PainTracker", "sync: nothing to send")
            return
        }

        Log.d("PainTracker", "sync: ${entries.size} entries to send")

        for (entry in entries) {
            val request = PutDataMapRequest.create("/pain_entry/${entry.id}").apply {
                dataMap.putString("id", entry.id)
                dataMap.putLong("timestamp", entry.timestamp)
                dataMap.putInt("pain_level", entry.painLevel)

                // flatten the window into parallel arrays for the data map
                val timestamps = entry.windowOfReadings.map { it.timestamp }.toLongArray()
                val heartRates = ArrayList(entry.windowOfReadings.map { it.heartRate })

                // ibi is a list of lists, pipe-join each one to match training format
                val ibiStrings = entry.windowOfReadings.map {
                    it.ibiList.joinToString("|")
                }.toTypedArray()

                dataMap.putLongArray("window_timestamps", timestamps)
                dataMap.putIntegerArrayList("window_heart_rates", heartRates)
                dataMap.putStringArray("window_ibi_strings", ibiStrings)

                // ecg as float array
                dataMap.putFloatArray("ecg_values", entry.ecgReadings.toFloatArray())

            }

            val putRequest = request.asPutDataRequest().setUrgent()
            val entryId = entry.id

            dataClient.putDataItem(putRequest)
                .addOnSuccessListener {
                    Log.d("PainTracker", "sync: sent entry $entryId")
                    DataRepository.removeSyncedEntries(listOf(entryId))
                }
                .addOnFailureListener { e ->
                    // entry stays in DataRepository, will retry next sync call
                    Log.e("PainTracker", "sync: failed to send $entryId: ${e.message}")
                }
        }
    }
}