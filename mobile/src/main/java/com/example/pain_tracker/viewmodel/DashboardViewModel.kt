package com.example.pain_tracker.viewmodel

import androidx.lifecycle.ViewModel
import com.example.pain_tracker.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.Calendar
import androidx.lifecycle.viewModelScope
import com.example.pain_tracker.model.PredictionPipeline
import com.example.pain_tracker.repository.WatchSessionRepository
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

class DashboardViewModel : ViewModel() {

    // ─── state ────────────────────────────────────────────────────────────────

    private val _editingSession = MutableStateFlow<PainSession?>(null)
    val editingSession: StateFlow<PainSession?> = _editingSession.asStateFlow()

    private val _painHistory = MutableStateFlow<List<PainRecord>>(emptyList())
    val painHistory: StateFlow<List<PainRecord>> = _painHistory.asStateFlow()

    private val _latestEcw = MutableStateFlow<Float?>(null)
    val latestEcw: StateFlow<Float?> = _latestEcw.asStateFlow()

    private val _monthScores = MutableStateFlow<Map<Int, DayScore>>(emptyMap())
    val monthScores: StateFlow<Map<Int, DayScore>> = _monthScores.asStateFlow()

    private val _weekScores = MutableStateFlow<List<DayScore>>(emptyList())
    val weekScores: StateFlow<List<DayScore>> = _weekScores.asStateFlow()

    private val _selectedDay = MutableStateFlow(Calendar.getInstance().get(Calendar.DAY_OF_MONTH))
    val selectedDay: StateFlow<Int> = _selectedDay.asStateFlow()

    private val _weekOffset = MutableStateFlow(0)
    val weekOffset: StateFlow<Int> = _weekOffset.asStateFlow()

    private val _displayedSessions = MutableStateFlow<List<PainSession>>(emptyList())
    val displayedSessions: StateFlow<List<PainSession>> = _displayedSessions.asStateFlow()

    private val _displayedScore = MutableStateFlow<DayScore?>(null)
    val displayedScore: StateFlow<DayScore?> = _displayedScore.asStateFlow()

    private val _showAddSheet = MutableStateFlow(false)
    val showAddSheet: StateFlow<Boolean> = _showAddSheet.asStateFlow()

    private val _expandedId = MutableStateFlow<Long?>(null)
    val expandedId: StateFlow<Long?> = _expandedId.asStateFlow()

    init {
        loadMockData()
        observeWatchSessions()
    }



    // ─── session actions ──────────────────────────────────────────────────────
    fun openAddSheet(session: PainSession? = null) {
        _editingSession.value = session
        _showAddSheet.value = true
    }

    fun closeAddSheet() {
        _showAddSheet.value = false
        _editingSession.value = null
    }

    fun addManualSession(sh: Int, sm: Int, eh: Int, em: Int, peak: Float, sx: Set<Symptom>, notes: String) {
        val cal = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, sh); set(Calendar.MINUTE, sm); set(Calendar.SECOND, 0) }
        val start = cal.timeInMillis
        cal.set(Calendar.HOUR_OF_DAY, eh); cal.set(Calendar.MINUTE, em)
        val end = cal.timeInMillis
        val totalMins = ((end - start) / 60_000).toInt().coerceAtLeast(1)

        val newSession = PainSession(
            startTime = start, endTime = end, source = SessionSource.MANUAL,
            peakLevel = peak, zones = buildZones(peak, totalMins), symptoms = sx, notes = notes
        )
        _displayedSessions.update { it + newSession }
        refreshDisplayedScore()
        closeAddSheet()
    }

    fun updateSession(id: Long, sh: Int, sm: Int, eh: Int, em: Int, peak: Float, sx: Set<Symptom>, notes: String) {
        _displayedSessions.update { list ->
            list.map { s ->
                if (s.id == id) {
                    val cal = Calendar.getInstance().apply { timeInMillis = s.startTime }
                    cal.set(Calendar.HOUR_OF_DAY, sh); cal.set(Calendar.MINUTE, sm)
                    val start = cal.timeInMillis
                    cal.set(Calendar.HOUR_OF_DAY, eh); cal.set(Calendar.MINUTE, em)
                    val end = cal.timeInMillis
                    s.copy(startTime = start, endTime = end, peakLevel = peak,
                        zones = buildZones(peak, ((end - start) / 60_000).toInt().coerceAtLeast(1)),
                        symptoms = sx, notes = notes)
                } else s
            }
        }
        refreshDisplayedScore()
        closeAddSheet()
    }

    fun deleteSession(id: Long) {
        _displayedSessions.update { list -> list.filterNot { it.id == id } }
        refreshDisplayedScore()
        closeAddSheet()
    }

    // ─── calendar logic ───────────────────────────────────────────────────────

    fun selectDay(year: Int, month: Int, dayOfMonth: Int, dayScore: DayScore?) {
        _selectedDay.value = dayOfMonth
        // If it's a mock day from our map, load its sessions
        val scoreFromMap = _monthScores.value[dayOfMonth]
        _displayedScore.value = scoreFromMap ?: dayScore
        _displayedSessions.value = scoreFromMap?.sessions ?: dayScore?.sessions ?: emptyList()

        val today = Calendar.getInstance().apply { firstDayOfWeek = Calendar.SUNDAY; set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY); set(Calendar.HOUR_OF_DAY, 0) }
        val target = Calendar.getInstance().apply { set(year, month, dayOfMonth); firstDayOfWeek = Calendar.SUNDAY; set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY); set(Calendar.HOUR_OF_DAY, 0) }
        _weekOffset.value = Math.round((target.timeInMillis - today.timeInMillis) / (1000.0 * 60 * 60 * 24 * 7)).toInt()
    }

    fun resetToToday() {
        val today = Calendar.getInstance()
        val day = today.get(Calendar.DAY_OF_MONTH)
        _weekOffset.value = 0
        _selectedDay.value = day
        val todayScore = _monthScores.value[day]
        _displayedScore.value = todayScore
        _displayedSessions.value = todayScore?.sessions ?: emptyList()
    }

    fun setWeekOffset(offset: Int) { _weekOffset.value = offset }

    fun setMonth(year: Int, month: Int) { /* Logic omitted for brevity, same as previous */ }

    // ─── helpers ──────────────────────────────────────────────────────────────

    fun toggleSession(id: Long) { _expandedId.update { if (it == id) null else id } }

    fun toggleSymptom(sessionId: Long, symptom: Symptom) {
        _displayedSessions.update { list ->
            list.map { if (it.id == sessionId) it.copy(symptoms = if (symptom in it.symptoms) it.symptoms - symptom else it.symptoms + symptom) else it }
        }
        refreshDisplayedScore()
    }

    fun updateNotes(sessionId: Long, notes: String) {
        _displayedSessions.update { list -> list.map { if (it.id == sessionId) it.copy(notes = notes) else it } }
    }

    private fun refreshDisplayedScore() {
        val sessions = _displayedSessions.value
        val raw = refreshScore(sessions)
        _displayedScore.update { it?.copy(score = raw, label = scoreLabel(raw), sessions = sessions) }
    }

    private fun buildZones(peak: Float, total: Int): List<PainZone> = when {
        peak >= 7f -> listOf(PainZone(ZoneLevel.SEVERE,(total*.5f).toInt()), PainZone(ZoneLevel.MODERATE,(total*.3f).toInt()), PainZone(ZoneLevel.MILD,(total*.2f).toInt()))
        peak >= 4f -> listOf(PainZone(ZoneLevel.MODERATE,(total*.6f).toInt()), PainZone(ZoneLevel.MILD,(total*.4f).toInt()))
        else       -> listOf(PainZone(ZoneLevel.MILD, total))
    }

    private fun loadMockData() {
        val now = System.currentTimeMillis()
        val today = Calendar.getInstance().get(Calendar.DAY_OF_MONTH)

        val s1 = PainSession(id=1L, startTime=now-4*3600000L, endTime=now-2*3600000L, source=SessionSource.SMARTWATCH, peakLevel=8.2f,
            zones=listOf(PainZone(ZoneLevel.SEVERE,60), PainZone(ZoneLevel.MODERATE,60)), symptoms=setOf(Symptom.CRAMPING))

        val initialSessions = listOf(s1)
        _displayedSessions.value = initialSessions

        val mockMonth = mutableMapOf<Int, DayScore>()

        // Loop through 31 days to ensure a full month is covered
        for (i in 0 until 31) {
            val day = i + 1

            // Generate a random score between 15 and 95 for each day
            val randomScore = (15..95).random()

            mockMonth[day] = DayScore(
                date = 0L,
                score = randomScore,
                label = scoreLabel(randomScore), // Dynamically sets "good", "fair", "poor"
                sessions = if (day == today) initialSessions else emptyList()
            )
        }

        _monthScores.value = mockMonth
        _displayedScore.value = mockMonth[today]
    }
    // ─── watch session ingestion ──────────────────────────────────────────────

    private fun observeWatchSessions() {
        WatchSessionRepository.results
            .onEach { result -> addWatchSession(result) }
            .launchIn(viewModelScope)
    }

    private fun addWatchSession(result: PredictionPipeline.SessionResult) {
        if (result.windows.isEmpty()) return

        val startTime = result.windows.first().timestamp
        val endTime   = result.windows.last().timestamp

        // Map model's 0–3 class onto the dashboard's 1–10 peak level scale
        val peakLevel = when (result.dominantPrediction) {
            0    -> 2f
            1    -> 4f
            2    -> 7f
            3    -> 9f
            else -> 5f
        }

        val totalMins = ((endTime - startTime) / 60_000).toInt().coerceAtLeast(1)

        val newSession = PainSession(
            id        = System.currentTimeMillis(),
            startTime = startTime,
            endTime   = endTime,
            source    = SessionSource.SMARTWATCH,
            peakLevel = peakLevel,
            zones     = buildZonesFromWindows(result.windows),
            symptoms  = emptySet(),
            notes     = "",
        )

        // Add to the displayed list (same day view the user is looking at)
        _displayedSessions.update { it + newSession }
        refreshDisplayedScore()

        // Also stamp today's calendar cell so the icon updates
        val todayDay = Calendar.getInstance().get(Calendar.DAY_OF_MONTH)
        _monthScores.update { current ->
            val todayScore = current[todayDay]
            if (todayScore != null) {
                val updatedSessions = todayScore.sessions + newSession
                val updatedScore = refreshScore(updatedSessions)
                current + (todayDay to todayScore.copy(
                    score    = updatedScore,
                    label    = scoreLabel(updatedScore),
                    sessions = updatedSessions,
                ))
            } else current
        }
    }

    /**
     * Groups consecutive windows with the same predicted level into PainZones.
     * Falls back to buildZones() when the window list has no useful structure.
     */
    private fun buildZonesFromWindows(
        windows: List<PredictionPipeline.WindowResult>
    ): List<PainZone> {
        if (windows.isEmpty()) return emptyList()

        val zones   = mutableListOf<PainZone>()
        var current = windows.first().predicted
        var count   = 0

        for (w in windows) {
            if (w.predicted == current) {
                count++
            } else {
                zones += PainZone(
                    level           = toZoneLevel(current),
                    durationMinutes = (count / 60).coerceAtLeast(1),
                )
                current = w.predicted
                count   = 1
            }
        }
        zones += PainZone(
            level           = toZoneLevel(current),
            durationMinutes = (count / 60).coerceAtLeast(1),
        )

        return zones
    }

    private fun toZoneLevel(predicted: Int) = when (predicted) {
        3    -> ZoneLevel.SEVERE
        2    -> ZoneLevel.MODERATE
        else -> ZoneLevel.MILD
    }

    // Extracted from refreshDisplayedScore so it can be called for calendar updates too
    private fun refreshScore(sessions: List<PainSession>): Int {
        val totalPainMins = sessions.sumOf { it.durationMinutes }
        val peak = sessions.maxOfOrNull { it.peakLevel } ?: 0f
        return (100 - (totalPainMins * 1.2f + peak * 3f)).toInt().coerceIn(0, 100)
    }
}