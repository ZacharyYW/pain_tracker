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
    val score: Float,
    val epochDay: Long
)

data class InsightsSummary(
    val avgScore: Float,
    val bestScore: Float,
    val worstScore: Float,
    val totalSessions: Int,
    val daysTracked: Int
)

data class HourlyPainBar(
    val hour: Int,
    val avgPeakLevel: Float,
    val sessionCount: Int
)

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

    private fun loadData() {
        viewModelScope.launch {
            _isLoading.value = true
            FirestoreRepository.sessionStream().collect { sessions ->
                cachedEntries = sessions.map { s ->
                    RawEntry(s.startTime, s.endTime, s.peakLevel)
                }

                val filtered = filterByMode(cachedEntries, _viewMode.value)
                buildChartPoints(filtered, _viewMode.value)
                buildHourlyBars(filtered)

                _isLoading.value = false
            }
        }
    }

    // ── filtering ─────────────────────────────────────────────────────────────

    private fun filterByMode(entries: List<RawEntry>, mode: InsightsViewMode): List<RawEntry> {
        val cutoff = Calendar.getInstance().apply {
            // FIX: Zero out time so microsecond differences don't break the filter
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.DAY_OF_YEAR, -(mode.days - 1))
        }
        return entries.filter { it.startTime >= cutoff.timeInMillis }
    }

    // ── hourly heatmap ────────────────────────────────────────────────────────

    private fun buildHourlyBars(entries: List<RawEntry>) {
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
        // FIX: Ensure 'now' is perfectly aligned to midnight
        val now = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val cutoff = (now.clone() as Calendar).apply {
            add(Calendar.DAY_OF_YEAR, -(mode.days - 1))
        }

        // FIX: Use YYYY-MM-DD keys instead of epoch math to avoid timezone shift bugs
        val byDay = mutableMapOf<String, MutableList<RawEntry>>()
        val cal = Calendar.getInstance()
        for (e in entries) {
            cal.timeInMillis = e.startTime
            val key = "${cal.get(Calendar.YEAR)}-${cal.get(Calendar.MONTH)}-${cal.get(Calendar.DAY_OF_MONTH)}"
            byDay.getOrPut(key) { mutableListOf() }.add(e)
        }

        val points = mutableListOf<ScorePoint>()

        when (mode) {
            InsightsViewMode.WEEK, InsightsViewMode.MONTH -> {
                val cursor = cutoff.clone() as Calendar
                while (!cursor.after(now)) { // This now perfectly reaches 'today'
                    val key = "${cursor.get(Calendar.YEAR)}-${cursor.get(Calendar.MONTH)}-${cursor.get(Calendar.DAY_OF_MONTH)}"
                    val dayEntries = byDay[key]
                    val score      = if (dayEntries != null) computeScore(dayEntries) else -1f
                    val label      = when (mode) {
                        InsightsViewMode.WEEK -> dayOfWeekLabel(cursor)
                        else                 -> "${cursor.get(Calendar.DAY_OF_MONTH)}"
                    }
                    points.add(ScorePoint(label, score, cursor.timeInMillis))
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
                    val weekEndRef = weekEnd.timeInMillis + 86399999L // End of the day

                    val weekEntries = entries.filter { it.startTime in cursor.timeInMillis..weekEndRef }
                    val score = if (weekEntries.isNotEmpty()) computeScore(weekEntries) else -1f
                    points.add(ScorePoint("${monthAbbr(cursor)} ${cursor.get(Calendar.DAY_OF_MONTH)}", score, cursor.timeInMillis))
                    cursor.add(Calendar.WEEK_OF_YEAR, 1)
                }
            }

            InsightsViewMode.YEAR -> {
                val cursor = cutoff.clone() as Calendar
                cursor.set(Calendar.DAY_OF_MONTH, 1)
                while (!cursor.after(now)) {
                    val monthYear = "${cursor.get(Calendar.YEAR)}-${cursor.get(Calendar.MONTH)}"
                    val monthEntries = entries.filter {
                        cal.timeInMillis = it.startTime
                        "${cal.get(Calendar.YEAR)}-${cal.get(Calendar.MONTH)}" == monthYear
                    }
                    val score = if (monthEntries.isNotEmpty()) computeScore(monthEntries) else -1f
                    points.add(ScorePoint(monthAbbr(cursor), score, cursor.timeInMillis))
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
            totalSessions = entries.size,
            daysTracked   = scored.size
        ) else null
    }

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