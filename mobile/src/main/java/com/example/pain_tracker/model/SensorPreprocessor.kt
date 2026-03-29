package com.example.pain_tracker.model

import com.example.pain_tracker.model.WatchSessionParser.LabeledSensorRow

/**
 * Cleans a session of raw sensor rows before windowing.
 * Mirrors preprocess.py Steps 3–4:
 *   - Rows with hr == 0 are already dropped by WatchSessionParser.
 *   - Rows with empty IBI lists are filled using the nearest non-empty neighbor
 *     (forward-fill first, then back-fill), exactly as pandas ffill/bfill does.
 */
object SensorPreprocessor {

    fun process(rows: List<LabeledSensorRow>): List<LabeledSensorRow> {
        if (rows.isEmpty()) return emptyList()
        return forwardBackFillIbi(rows)
    }

    private fun forwardBackFillIbi(rows: List<LabeledSensorRow>): List<LabeledSensorRow> {
        val filled = rows.toMutableList()

        // Forward fill
        var last: List<Int>? = null
        for (i in filled.indices) {
            val ibi = filled[i].row.ibi
            if (ibi.isNotEmpty()) {
                last = ibi
            } else if (last != null) {
                filled[i] = filled[i].withIbi(last)
            }
        }

        // Back fill (handles leading empty rows that forward-fill couldn't reach)
        last = null
        for (i in filled.indices.reversed()) {
            val ibi = filled[i].row.ibi
            if (ibi.isNotEmpty()) {
                last = ibi
            } else if (last != null) {
                filled[i] = filled[i].withIbi(last)
            }
        }

        return filled
    }

    private fun LabeledSensorRow.withIbi(ibi: List<Int>) =
        copy(row = row.copy(ibi = ibi))
}