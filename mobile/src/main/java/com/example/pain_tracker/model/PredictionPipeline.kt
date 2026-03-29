package com.example.pain_tracker.model

import com.example.pain_tracker.model.WatchSessionParser.LabeledSensorRow

/**
 * Orchestrates the full inference path:
 *   preprocessed rows → 10-row sliding windows → PainPredictionModel → PredictionResult
 */
class PredictionPipeline(private val model: PainPredictionModel) {

    companion object {
        private const val WINDOW_SIZE = PainPredictionModel.WINDOW_SIZE
    }

    data class WindowResult(
        val windowIndex: Int,
        val timestamp: Long,       // timestamp of the first row in the window
        val predicted: Int,        // 0–3 pain level
        val actual: Int,           // -1 if unlabeled
    )

    data class SessionResult(
        val windows: List<WindowResult>,
        val accuracy: Double?,     // null if no labeled rows
        val dominantPrediction: Int,
    )

    /**
     * Run inference over an entire preprocessed session.
     * Returns one WindowResult per valid 10-row window.
     */
    fun run(rows: List<LabeledSensorRow>): SessionResult {
        if (rows.size < WINDOW_SIZE) {
            return SessionResult(windows = emptyList(), accuracy = null, dominantPrediction = -1)
        }

        val results = mutableListOf<WindowResult>()

        for (start in 0..rows.size - WINDOW_SIZE) {
            val windowRows = rows.subList(start, start + WINDOW_SIZE)
            val predicted = model.extractFeaturesAndPredict(windowRows.map { it.row })

            results += WindowResult(
                windowIndex = start,
                timestamp   = windowRows.first().timestamp,
                predicted   = predicted,
                actual      = windowRows.first().painLevel,
            )
        }

        val labeled = results.filter { it.actual != -1 }
        val accuracy = if (labeled.isNotEmpty()) {
            labeled.count { it.predicted == it.actual }.toDouble() / labeled.size
        } else null

        // Majority vote across all windows as the session-level summary
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