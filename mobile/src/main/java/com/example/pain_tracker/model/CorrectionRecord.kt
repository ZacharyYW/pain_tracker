package com.example.pain_tracker.model

/**
 * Stored whenever a user edits a watch session's pain level.
 * The original predicted class (0–3) and the user's corrected class
 * are both stored so the retraining script can use them as labeled examples.
 */
data class CorrectionRecord(
    val sessionId: Long,
    val timestamp: Long,
    val originalPredicted: Int,   // model's dominant prediction (0–3)
    val correctedPainLevel: Float, // user's new peakLevel (1–10 scale)
    val correctedClass: Int,       // converted back to 0–3 for the model
    val windowTimestamps: List<Long>, // timestamps of windows in this session
)

/** Maps the 1–10 UI peak level back to the 0–3 model class. */
fun peakLevelToClass(peak: Float) = when {
    peak >= 8f -> 3
    peak >= 5f -> 2
    peak >= 3f -> 1
    else       -> 0
}