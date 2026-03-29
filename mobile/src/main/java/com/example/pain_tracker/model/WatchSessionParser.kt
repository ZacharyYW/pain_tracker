package com.example.pain_tracker.model

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName

/**
 * Parses the JSON payload sent from the watch over the Wearable MessageClient.
 *
 * Expected JSON shape:
 * {
 *   "rows": [
 *     { "timestamp": 1774720000000, "hr": 68, "ibi": [756, 658], "ecg": [179.7, 173.3, ...], "pain_level": 0 },
 *     { "timestamp": 1774720001000, "hr": 67, "ibi": [832],      "ecg": [],                  "pain_level": 0 },
 *     ...
 *   ]
 * }
 *
 * pain_level is optional from the watch — the watch only knows it when the user
 * has actively logged a pain event. Pass -1 if unknown (inference-only mode).
 */
object WatchSessionParser {

    private val gson = Gson()

    data class WatchSession(
        val rows: List<WatchRow>,
    )

    data class WatchRow(
        val timestamp: Long,
        val hr: Float,
        val ibi: List<Int>,
        val ecg: List<Float>,
        @SerializedName("pain_level") val painLevel: Int = -1,
    )

    /**
     * Converts raw JSON bytes (from MessageClient) into a list of RawSensorRows
     * paired with their pain labels. Rows with hr == 0 are dropped here
     * (ECG interference artifact), matching preprocess.py Step 2.
     */
    fun parse(jsonBytes: ByteArray): List<LabeledSensorRow> {
        val session = gson.fromJson(jsonBytes.toString(Charsets.UTF_8), WatchSession::class.java)

        return session.rows
            .filter { it.hr != 0f }
            .map { row ->
                LabeledSensorRow(
                    row = RawSensorRow(hr = row.hr, ibi = row.ibi, ecg = row.ecg),
                    painLevel = row.painLevel,
                    timestamp = row.timestamp,
                )
            }
    }

    data class LabeledSensorRow(
        val row: RawSensorRow,
        val painLevel: Int,   // -1 = unlabeled (inference-only)
        val timestamp: Long,
    )
}