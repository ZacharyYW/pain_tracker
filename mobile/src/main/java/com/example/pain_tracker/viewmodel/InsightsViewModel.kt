package com.example.pain_tracker.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pain_tracker.repository.FirestoreRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Calendar

// ── Data ──────────────────────────────────────────────────────────────────────

enum class InsightsViewMode(val label: String, val days: Int) {
    WEEK("week", 7),
    MONTH("month", 30),
    THREE_MONTH("3 months", 90),
    YEAR("year", 365)
}

data class ScorePoint(
    val dateLabel: String,
    val score: Float,       // 0–100, or -1f if no data
    val epochDay: Long
)

data class InsightsSummary(
    val avgScore: Float,
    val bestScore: Float,
    val worstScore: Float,
    val totalSessions: Int,
    val daysTracked: Int
)

/**
 * One bar in the hourly pain chart.
 * [hour] is 0–23. [avgPeakLevel] is the mean peakLevel across all sessions
 * that started in this hour within the selected window, or 0f if none.
 * [sessionCount] is how many sessions contributed.
 */
data class HourlyPainBar(
    val hour: Int,           // 0–23
    val avgPeakLevel: Float, // 0–10
    val sessionCount: Int
)

// Cached raw values — reused when switching view modes
private data class RawEntry(val startTime: Long, val endTime: Long, val peakLevel: Float) {
    val durationMinutes: Int get() = ((endTime - startTime) / 60_000L).toInt().coerceAtLeast(1)
    val startHour: Int get() = Calendar.getInstance().also { it.timeInMillis = startTime }.get(Calendar.HOUR_OF_DAY)
}

// ── ViewModel ─────────────────────────────────────────────────────────────────

class InsightsViewModel : ViewModel() {

    private val _viewMode = MutableStateFlow(InsightsViewMode.WEEK)
    val viewMode: StateFlow<InsightsViewMode> = _viewMode.asStateFlow()

    private val _chartPoints = MutableStateFlow<List<ScorePoint>>(emptyList())
    val chartPoints: StateFlow<List<ScorePoint>> = _chartPoints.asStateFlow()

    private val _summary = MutableStateFlow<InsightsSummary?>(null)
    val summary: StateFlow<InsightsSummary?> = _summary.asStateFlow()

    private val _hourlyPainBars = MutableStateFlow<List<HourlyPainBar>>(emptyList())
    val hourlyPainBars: StateFlow<List<HourlyPainBar>> = _hourlyPainBars.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private var cachedEntries: List<RawEntry> = emptyList()

    init {
        loadData()
    }

    fun setViewMode(mode: InsightsViewMode) {
        _viewMode.value = mode
        val filtered = filterByMode(cachedEntries, mode)
        buildChartPoints(filtered, mode)
        buildHourlyBars(filtered)
    }

    fun loadData() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val sessions = FirestoreRepository.fetchSessions()
                cachedEntries = sessions.map { s ->
                    RawEntry(s.startTime, s.endTime, s.peakLevel)
                }
                val filtered = filterByMode(cachedEntries, _viewMode.value)
                buildChartPoints(filtered, _viewMode.value)
                buildHourlyBars(filtered)
            } catch (e: Exception) {
                // Leave charts empty on failure
            } finally {
                _isLoading.value = false
            }
        }
    }

    // ── filtering ─────────────────────────────────────────────────────────────

    private fun filterByMode(entries: List<RawEntry>, mode: InsightsViewMode): List<RawEntry> {
        val cutoff = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -(mode.days - 1)) }
        return entries.filter { it.startTime >= cutoff.timeInMillis }
    }

    // ── hourly heatmap ────────────────────────────────────────────────────────

    private fun buildHourlyBars(entries: List<RawEntry>) {
        // For each of the 24 hours, collect all sessions whose startTime falls in that hour
        val byHour = Array(24) { mutableListOf<Float>() }
        for (e in entries) {
            byHour[e.startHour].add(e.peakLevel)
        }
        _hourlyPainBars.value = (0 until 24).map { h ->
            val levels = byHour[h]
            HourlyPainBar(
                hour          = h,
                avgPeakLevel  = if (levels.isNotEmpty()) levels.average().toFloat() else 0f,
                sessionCount  = levels.size
            )
        }
    }

    // ── score trend aggregation ───────────────────────────────────────────────

    private fun buildChartPoints(entries: List<RawEntry>, mode: InsightsViewMode) {
        val now    = Calendar.getInstance()
        val cutoff = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -(mode.days - 1)) }

        val byDay = mutableMapOf<Long, MutableList<RawEntry>>()
        for (e in entries) {
            val day = e.startTime / 86_400_000L
            byDay.getOrPut(day) { mutableListOf() }.add(e)
        }

        val points = mutableListOf<ScorePoint>()

        when (mode) {

            InsightsViewMode.WEEK, InsightsViewMode.MONTH -> {
                val cursor = cutoff.clone() as Calendar
                while (!cursor.after(now)) {
                    val epochDay   = cursor.timeInMillis / 86_400_000L
                    val dayEntries = byDay[epochDay]
                    val score      = if (dayEntries != null) computeScore(dayEntries) else -1f
                    val label      = when (mode) {
                        InsightsViewMode.WEEK -> dayOfWeekLabel(cursor)
                        else                 -> "${cursor.get(Calendar.DAY_OF_MONTH)}"
                    }
                    points.add(ScorePoint(label, score, epochDay))
                    cursor.add(Calendar.DAY_OF_MONTH, 1)
                }
            }

            InsightsViewMode.THREE_MONTH -> {
                val cursor = cutoff.clone() as Calendar
                while (cursor.get(Calendar.DAY_OF_WEEK) != Calendar.SUNDAY) {
                    cursor.add(Calendar.DAY_OF_MONTH, -1)
                }
                while (!cursor.after(now)) {
                    val weekEnd = cursor.clone() as Calendar
                    weekEnd.add(Calendar.DAY_OF_MONTH, 6)
                    val weekEntries = byDay.entries
                        .filter { it.key in (cursor.timeInMillis / 86_400_000L)..(weekEnd.timeInMillis / 86_400_000L) }
                        .flatMap { it.value }
                    val score = if (weekEntries.isNotEmpty()) computeScore(weekEntries) else -1f
                    points.add(ScorePoint("${monthAbbr(cursor)} ${cursor.get(Calendar.DAY_OF_MONTH)}", score, cursor.timeInMillis / 86_400_000L))
                    cursor.add(Calendar.WEEK_OF_YEAR, 1)
                }
            }

            InsightsViewMode.YEAR -> {
                val cursor = cutoff.clone() as Calendar
                cursor.set(Calendar.DAY_OF_MONTH, 1)
                while (!cursor.after(now)) {
                    val monthEnd = cursor.clone() as Calendar
                    monthEnd.set(Calendar.DAY_OF_MONTH, cursor.getActualMaximum(Calendar.DAY_OF_MONTH))
                    val monthEntries = byDay.entries
                        .filter { it.key in (cursor.timeInMillis / 86_400_000L)..(monthEnd.timeInMillis / 86_400_000L) }
                        .flatMap { it.value }
                    val score = if (monthEntries.isNotEmpty()) computeScore(monthEntries) else -1f
                    points.add(ScorePoint(monthAbbr(cursor), score, cursor.timeInMillis / 86_400_000L))
                    cursor.add(Calendar.MONTH, 1)
                }
            }
        }

        _chartPoints.value = points

        val scored = points.filter { it.score >= 0f }
        _summary.value = if (scored.isNotEmpty()) InsightsSummary(
            avgScore      = scored.map { it.score }.average().toFloat(),
            bestScore     = scored.maxOf { it.score },
            worstScore    = scored.minOf { it.score },
            totalSessions = byDay.values.sumOf { it.size },
            daysTracked   = scored.size
        ) else null
    }

    // Same formula as DashboardViewModel.refreshScore
    private fun computeScore(entries: List<RawEntry>): Float {
        val totalPainMins = entries.sumOf { it.durationMinutes }
        val peak          = entries.maxOfOrNull { it.peakLevel } ?: 0f
        return (100f - (totalPainMins * 1.2f + peak * 3f)).coerceIn(0f, 100f)
    }

    private fun dayOfWeekLabel(cal: Calendar): String =
        listOf("sun","mon","tue","wed","thu","fri","sat")[cal.get(Calendar.DAY_OF_WEEK) - 1]

    private fun monthAbbr(cal: Calendar): String =
        listOf("jan","feb","mar","apr","may","jun","jul","aug","sep","oct","nov","dec")[cal.get(Calendar.MONTH)]
}