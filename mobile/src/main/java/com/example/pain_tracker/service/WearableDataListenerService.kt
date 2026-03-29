package com.example.pain_tracker.service

import android.util.Log
import com.example.pain_tracker.model.PainPredictionModel
import com.example.pain_tracker.model.PredictionPipeline
import com.example.pain_tracker.model.SensorPreprocessor
import com.example.pain_tracker.model.WatchSessionParser
import com.example.pain_tracker.repository.WatchSessionRepository
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class WearableDataListenerService : WearableListenerService() {

    companion object {
        private const val TAG = "WearableListener"
        const val MESSAGE_PATH = "/pain_tracker/session"
    }

    private val model by lazy { PainPredictionModel(applicationContext) }
    private val pipeline by lazy { PredictionPipeline(model) }

    // Service has no lifecycle tied to a ViewModel, so we manage our own scope.
    // SupervisorJob means one failed emit doesn't cancel all others.
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onMessageReceived(event: MessageEvent) {
        if (event.path != MESSAGE_PATH) return

        serviceScope.launch {
            val parsed  = WatchSessionParser.parse(event.data)
            val cleaned = SensorPreprocessor.process(parsed)
            val result  = pipeline.run(cleaned)

            if (result.windows.isEmpty()) {
                Log.w(TAG, "Session too short to evaluate")
                return@launch
            }

            Log.d(TAG, "Dominant prediction: L${result.dominantPrediction}, windows: ${result.windows.size}")
            WatchSessionRepository.emit(result)
        }
    }
}