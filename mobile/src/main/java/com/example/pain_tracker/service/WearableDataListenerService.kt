package com.example.pain_tracker.service

import android.util.Log
import com.example.pain_tracker.model.PainPredictionModel
import com.example.pain_tracker.model.PredictionPipeline
import com.example.pain_tracker.model.SensorPreprocessor
import com.example.pain_tracker.model.WatchSessionParser
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService

class WearableDataListenerService : WearableListenerService() {

    companion object {
        private const val TAG = "WearableListener"
        // The watch must send to this path — agree on it with your Wear OS app
        const val MESSAGE_PATH = "/pain_tracker/session"
    }

    private val model by lazy { PainPredictionModel(applicationContext) }
    private val pipeline by lazy { PredictionPipeline(model) }

    override fun onMessageReceived(event: MessageEvent) {
        if (event.path != MESSAGE_PATH) return

        Log.d(TAG, "Session received: ${event.data.size} bytes from ${event.sourceNodeId}")

        // 1. Parse JSON → raw rows (drops hr==0 rows)
        val parsed = WatchSessionParser.parse(event.data)
        Log.d(TAG, "Parsed ${parsed.size} valid rows")

        // 2. Preprocess (IBI forward/back-fill)
        val cleaned = SensorPreprocessor.process(parsed)

        // 3. Run windowed inference
        val result = pipeline.run(cleaned)

        if (result.windows.isEmpty()) {
            Log.w(TAG, "Not enough rows to form a 10-row window — session too short")
            return
        }

        Log.d(TAG, "Windows evaluated: ${result.windows.size}")
        Log.d(TAG, "Dominant prediction: L${result.dominantPrediction}")
        result.accuracy?.let {
            Log.d(TAG, "Accuracy on labeled rows: ${"%.1f".format(it * 100)}%")
        }

        // 4. TODO: persist result or broadcast to your UI
        //    e.g. sendBroadcast / LiveData / Room insert
    }
}