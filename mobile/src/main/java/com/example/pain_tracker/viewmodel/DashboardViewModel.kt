package com.example.pain_tracker.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pain_tracker.model.*
import com.example.pain_tracker.repository.FirestoreRepository
import com.example.pain_tracker.repository.WatchSessionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Calendar

class DashboardViewModel : ViewModel() {

    // ─── state ────────────────────────────────────────────────────────────────

    private val _editingSession = MutableStateFlow<PainSession?>(null)
    val editingSession: StateFlow<PainSession?> = _editingSession.asStateFlow()

    private var allCachedSessions: List<PainSession> = emptyList()

    // NEW: Keyed by "YYYY-MM-DD" so months never overlap in the week view
    private val _scoresMap = MutableStateFlow<Map<String, DayScore>>(emptyMap())
    val scoresMap: StateFlow<Map<String, DayScore>> = _scoresMap.asStateFlow()

    private val _selectedYear = MutableStateFlow(Calendar.getInstance().get(Calendar.YEAR))
    val selectedYear: StateFlow<Int> = _selectedYear.asStateFlow()

    private val _selectedMonth = MutableStateFlow(Calendar.getInstance().get(Calendar.MONTH))
    val selectedMonth: StateFlow<Int> = _selectedMonth.asStateFlow()

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

    private val _showPeriodSheet = MutableStateFlow(false)
    val showPeriodSheet: StateFlow<Boolean> = _showPeriodSheet.asStateFlow()

    private val _periodDays = MutableStateFlow<Set<String>>(emptySet())
    val periodDays: StateFlow<Set<String>> = _periodDays.asStateFlow()

    fun openPeriodSheet() { _showPeriodSheet.value = true }
    fun closePeriodSheet() { _showPeriodSheet.value = false }

    fun logPeriod(year: Int, month: Int, day: Int, flow: Int, symptoms: Set<String>, notes: String) {
        val key = "%04d-%02d-%02d".format(year, month + 1, day)
        _periodDays.update { it + key }
        closePeriodSheet()
    }

    private val _expandedId = MutableStateFlow<Long?>(null)
    val expandedId: StateFlow<Long?> = _expandedId.asStateFlow()

    init {
        viewModelScope.launch {
            FirestoreRepository.signInAnonymously()
            observeRealData()
        }
        observeWatchSessions()
    }

    // ─── real-time data ingestion ─────────────────────────────────────────────

    private fun observeRealData() {
        viewModelScope.launch {
            FirestoreRepository.sessionStream().collect { sessions ->
                allCachedSessions = sessions
                rebuildCalendarData()
            }
        }
    }

    private fun rebuildCalendarData() {
        val cal = Calendar.getInstance()

        // Group all sessions globally by exact date
        val sessionsByDate = allCachedSessions.groupBy {
            cal.timeInMillis = it.startTime
            "${cal.get(Calendar.YEAR)}-${cal.get(Calendar.MONTH)}-${cal.get(Calendar.DAY_OF_MONTH)}"
        }

        // Create DayScores only for days that have data
        val newScores = sessionsByDate.mapValues { (_, daySessions) ->
            val score = refreshScore(daySessions)
            DayScore(
                date = 0L,
                score = score,
                label = scoreLabel(score),
                sessions = daySessions
            )
        }

        _scoresMap.value = newScores

        // Update the bottom list of sessions for the exact selected day
        val selectedKey = "${_selectedYear.value}-${_selectedMonth.value}-${_selectedDay.value}"
        val todayScore = newScores[selectedKey]
        _displayedScore.value = todayScore
        _displayedSessions.value = todayScore?.sessions ?: emptyList()
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
            id = System.currentTimeMillis(),
            startTime = start, endTime = end, source = SessionSource.MANUAL,
            peakLevel = peak, zones = buildZones(peak, totalMins), symptoms = sx, notes = notes
        )

        viewModelScope.launch {
            FirestoreRepository.saveManualSession(newSession)
        }
        closeAddSheet()
    }

    fun updateSession(id: Long, sh: Int, sm: Int, eh: Int, em: Int, peak: Float, sx: Set<Symptom>, notes: String) {
        val original = allCachedSessions.find { it.id == id } ?: return

        val cal = Calendar.getInstance().apply { timeInMillis = original.startTime }
        cal.set(Calendar.HOUR_OF_DAY, sh); cal.set(Calendar.MINUTE, sm)
        val start = cal.timeInMillis
        cal.set(Calendar.HOUR_OF_DAY, eh); cal.set(Calendar.MINUTE, em)
        val end = cal.timeInMillis
        val totalMins = ((end - start) / 60_000).toInt().coerceAtLeast(1)

        val updatedZones = if (original.source == SessionSource.SMARTWATCH && original.peakLevel == peak) {
            original.zones
        } else {
            buildZones(peak, totalMins)
        }

        val updated = original.copy(
            startTime = start, endTime = end, peakLevel = peak,
            zones = updatedZones, symptoms = sx, notes = notes
        )

        viewModelScope.launch {
            FirestoreRepository.updateSessionFields(updated)

            if (original.source == SessionSource.SMARTWATCH && original.peakLevel != peak) {
                val dominantPredicted = peakLevelToClass(original.peakLevel)
                val windowTimestamps  = original.zones.map { it.hashCode().toLong() }

                FirestoreRepository.saveCorrection(
                    CorrectionRecord(
                        sessionId = id,
                        timestamp = System.currentTimeMillis(),
                        originalPredicted = dominantPredicted,
                        correctedPainLevel = peak,
                        correctedClass = peakLevelToClass(peak),
                        windowTimestamps = windowTimestamps,
                    )
                )
            }
        }
        closeAddSheet()
    }

    fun deleteSession(id: Long) {
        viewModelScope.launch {
            FirestoreRepository.deleteSession(id)
        }
        closeAddSheet()
    }

    // ─── calendar logic ───────────────────────────────────────────────────────

    fun selectDay(year: Int, month: Int, dayOfMonth: Int, dayScore: DayScore? = null) {
        _selectedYear.value = year
        _selectedMonth.value = month
        _selectedDay.value = dayOfMonth

        val today = Calendar.getInstance().apply {
            firstDayOfWeek = Calendar.SUNDAY
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            while (get(Calendar.DAY_OF_WEEK) != Calendar.SUNDAY) { add(Calendar.DAY_OF_MONTH, -1) }
        }

        val target = Calendar.getInstance().apply {
            set(Calendar.YEAR, year); set(Calendar.MONTH, month); set(Calendar.DAY_OF_MONTH, dayOfMonth)
            firstDayOfWeek = Calendar.SUNDAY
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            while (get(Calendar.DAY_OF_WEEK) != Calendar.SUNDAY) { add(Calendar.DAY_OF_MONTH, -1) }
        }

        val diffMs = target.timeInMillis - today.timeInMillis
        _weekOffset.value = Math.round(diffMs / (1000.0 * 60 * 60 * 24 * 7)).toInt()

        rebuildCalendarData()
    }

    fun resetToToday() {
        val today = Calendar.getInstance()
        _selectedYear.value = today.get(Calendar.YEAR)
        _selectedMonth.value = today.get(Calendar.MONTH)
        _selectedDay.value = today.get(Calendar.DAY_OF_MONTH)
        _weekOffset.value = 0

        rebuildCalendarData()
    }

    fun setWeekOffset(offset: Int) { _weekOffset.value = offset }

    fun setMonth(year: Int, month: Int) {
        _selectedYear.value = year
        _selectedMonth.value = month
        rebuildCalendarData()
    }

    fun toggleSession(id: Long) { _expandedId.update { if (it == id) null else id } }

    fun toggleSymptom(sessionId: Long, symptom: Symptom) {
        val session = allCachedSessions.find { it.id == sessionId } ?: return
        val newSymptoms = if (symptom in session.symptoms) session.symptoms - symptom else session.symptoms + symptom
        viewModelScope.launch {
            FirestoreRepository.updateSessionFields(session.copy(symptoms = newSymptoms))
        }
    }

    fun updateNotes(sessionId: Long, notes: String) {
        val session = allCachedSessions.find { it.id == sessionId } ?: return
        viewModelScope.launch {
            FirestoreRepository.updateSessionFields(session.copy(notes = notes))
        }
    }

    private fun buildZones(peak: Float, total: Int): List<PainZone> = when {
        peak >= 7f -> listOf(PainZone(ZoneLevel.SEVERE,(total*.5f).toInt()), PainZone(ZoneLevel.MODERATE,(total*.3f).toInt()), PainZone(ZoneLevel.MILD,(total*.2f).toInt()))
        peak >= 4f -> listOf(PainZone(ZoneLevel.MODERATE,(total*.6f).toInt()), PainZone(ZoneLevel.MILD,(total*.4f).toInt()))
        else       -> listOf(PainZone(ZoneLevel.MILD, total))
    }

    private fun refreshScore(sessions: List<PainSession>): Int {
        if (sessions.isEmpty()) return 100
        val totalPainMins = sessions.sumOf { it.durationMinutes }
        val peak = sessions.maxOfOrNull { it.peakLevel } ?: 0f
        return (100 - (totalPainMins * 1.2f + peak * 3f)).toInt().coerceIn(0, 100)
    }

    private fun scoreLabel(score: Int): String = when {
        score >= 70 -> "good"
        score >= 45 -> "fair"
        else -> "rough"
    }

    private fun observeWatchSessions() {
        WatchSessionRepository.results
            .onEach { result -> addWatchSession(result) }
            .launchIn(viewModelScope)
    }

    private fun addWatchSession(result: PredictionPipeline.SessionResult) {
        if (result.windows.isEmpty()) return

        val startTime = result.windows.first().timestamp
        val endTime   = result.windows.last().timestamp

        val peakLevel = when (result.dominantPrediction) {
            0    -> 2f
            1    -> 4f
            2    -> 7f
            3    -> 9f
            else -> 5f
        }

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

        viewModelScope.launch {
            FirestoreRepository.saveWatchSession(newSession, result)
        }

        // ADDED: Save the watch session to Firestore
        viewModelScope.launch {
            runCatching {
                FirestoreRepository.saveWatchSession(newSession, result)
            }.onFailure { e ->
                Log.e("DashboardViewModel", "Failed to save watch session to Firestore: ${e.message}")
            }
        }
    }

    private fun buildZonesFromWindows(windows: List<PredictionPipeline.WindowResult>): List<PainZone> {
        if (windows.isEmpty()) return emptyList()

        val zones   = mutableListOf<PainZone>()
        var current = windows.first().predicted
        var count   = 0

        for (w in windows) {
            if (w.predicted == current) {
                count++
            } else {
                zones += PainZone(level = toZoneLevel(current), durationMinutes = (count / 60).coerceAtLeast(1))
                current = w.predicted
                count   = 1
            }
        }
        zones += PainZone(level = toZoneLevel(current), durationMinutes = (count / 60).coerceAtLeast(1))

        return zones
    }

    private fun toZoneLevel(predicted: Int) = when (predicted) {
        3    -> ZoneLevel.SEVERE
        2    -> ZoneLevel.MODERATE
        else -> ZoneLevel.MILD
    }
}