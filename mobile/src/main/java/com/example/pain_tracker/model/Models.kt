package com.example.pain_tracker.model

data class PainRecord(
    val timestamp: Long,
    val painLevel: Int, // 0: None, 1: Mild, 2: Moderate, 3: Severe
    val confidence: Float
)

enum class Symptom(val label: String) {
    NAUSEA("nausea"),
    CRAMPING("cramping"),
    FATIGUE("fatigue"),
    HEADACHE("headache"),
    BLOATING("bloating"),
    BACK_PAIN("back pain"),
    DIZZINESS("dizziness"),
    CHILLS("chills"),
    MOOD_CHANGES("mood changes")
}

data class PainZone(
    val level: ZoneLevel,
    val durationMinutes: Int
)

enum class ZoneLevel { SEVERE, MODERATE, MILD }

enum class SessionSource { SMARTWATCH, MANUAL }

data class PainSession(
    val id: Long = System.currentTimeMillis(),
    val startTime: Long,
    val endTime: Long,
    val source: SessionSource,
    val peakLevel: Float,         // 1–10 scale
    val zones: List<PainZone>,
    val symptoms: Set<Symptom> = emptySet(),
    val notes: String = ""
) {
    val durationMinutes: Int get() = ((endTime - startTime) / 60_000).toInt()
}

data class DayScore(
    val date: Long,           // start-of-day timestamp
    val score: Int,           // 0–100
    val label: String,        // "excellent", "good", "fair", "poor"
    val sessions: List<PainSession>
) {
    val peakSeverity: Float get() = sessions.maxOfOrNull { it.peakLevel } ?: 0f
    val totalMinutes: Int   get() = sessions.sumOf { it.durationMinutes }
}

fun scoreEmoji(score: Int): String = when {
    score >= 85 -> "😊"
    score >= 70 -> "🙂"
    score >= 55 -> "😐"
    score >= 40 -> "😟"
    score >= 25 -> "😣"
    else        -> "😭"
}

fun scoreLabel(score: Int): String = when {
    score >= 85 -> "excellent"
    score >= 70 -> "good"
    score >= 55 -> "fair"
    score >= 35 -> "poor"
    else        -> "very poor"
}