package com.example.pain_tracker.model

import com.example.pain_tracker.model.WatchSessionParser.LabeledSensorRow

class PredictionPipeline(private val model: PainPredictionModel) {

    companion object {
        private const val WINDOW_SIZE = PainPredictionModel.WINDOW_SIZE
    }

    data class WindowResult(
        val windowIndex: Int,
        val timestamp: Long,
        val predicted: Int,
        val actual: Int,
        val features: List<Float> // --- NEW: Store the features ---
    )

    data class SessionResult(
        val windows: List<WindowResult>,
        val accuracy: Double?,
        val dominantPrediction: Int,
    )

    fun run(rows: List<LabeledSensorRow>): SessionResult {
        if (rows.size < WINDOW_SIZE) {
            return SessionResult(windows = emptyList(), accuracy = null, dominantPrediction = -1)
        }

        val results = mutableListOf<WindowResult>()

        for (start in 0..rows.size - WINDOW_SIZE) {
            val windowRows = rows.subList(start, start + WINDOW_SIZE)

            // --- NEW: Unwrap the Pair ---
            val predictionPair = model.extractFeaturesAndPredict(windowRows.map { it.row })

            if (predictionPair != null) {
                val (predicted, featuresArray) = predictionPair

                results += WindowResult(
                    windowIndex = start,
                    timestamp   = windowRows.first().timestamp,
                    predicted   = predicted,
                    actual      = windowRows.first().painLevel,
                    features    = featuresArray.toList()
                )
            }
        }

        val labeled = results.filter { it.actual != -1 }
        val accuracy = if (labeled.isNotEmpty()) {
            labeled.count { it.predicted == it.actual }.toDouble() / labeled.size
        } else null

        val dominantPrediction = results
            .groupingBy { it.predicted }
            .eachCount()
            .maxByOrNull { it.value }?.key ?: -1

        return SessionResult(
            windows             = results,
            accuracy            = accuracy,
            dominantPrediction  = dominantPrediction,
        )
    }
}