package com.example.pain_tracker.viewmodel

import androidx.lifecycle.ViewModel
import com.example.pain_tracker.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.Calendar

class DashboardViewModel : ViewModel() {

    // legacy (watch data feed)
    private val _painHistory = MutableStateFlow<List<PainRecord>>(emptyList())
    val painHistory: StateFlow<List<PainRecord>> = _painHistory.asStateFlow()

    private val _latestEcw = MutableStateFlow<Float?>(null)
    val latestEcw: StateFlow<Float?> = _latestEcw.asStateFlow()

    // new dashboard state
    private val _monthScores   = MutableStateFlow<Map<Int, DayScore>>(emptyMap())
    val monthScores: StateFlow<Map<Int, DayScore>> = _monthScores.asStateFlow()

    private val _weekScores    = MutableStateFlow<List<DayScore>>(emptyList())
    val weekScores: StateFlow<List<DayScore>> = _weekScores.asStateFlow()

    private val _selectedDay = MutableStateFlow(Calendar.getInstance().get(Calendar.DAY_OF_MONTH))
    val selectedDay: StateFlow<Int> = _selectedDay.asStateFlow()

    private val _weekOffset = MutableStateFlow(0)
    val weekOffset: StateFlow<Int> = _weekOffset.asStateFlow()

    fun shiftWeek(delta: Int) {
        _weekOffset.update { it + delta }
    }

    // NEW: Directly set offset from Pager swipes
    fun setWeekOffset(offset: Int) {
        _weekOffset.value = offset
    }

    // NEW: Calculate how many weeks away the selected month is and sync
    fun setMonth(year: Int, month: Int) {
        val today = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month)
            set(Calendar.DAY_OF_MONTH, 1)
        }

        // Standardize both to Monday to compare exact week differences
        today.firstDayOfWeek = Calendar.MONDAY
        today.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        today.set(Calendar.HOUR_OF_DAY, 0)

        target.firstDayOfWeek = Calendar.MONDAY
        target.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        target.set(Calendar.HOUR_OF_DAY, 0)

        // Calculate offset
        val diffMs = target.timeInMillis - today.timeInMillis
        val diffWeeks = Math.round(diffMs / (1000.0 * 60 * 60 * 24 * 7)).toInt()

        _weekOffset.value = diffWeeks
    }

    // RENAMED: from todaySessions -> displayedSessions
    private val _displayedSessions = MutableStateFlow<List<PainSession>>(emptyList())
    val displayedSessions: StateFlow<List<PainSession>> = _displayedSessions.asStateFlow()

    // RENAMED: from todayScore -> displayedScore
    private val _displayedScore    = MutableStateFlow<DayScore?>(null)
    val displayedScore: StateFlow<DayScore?> = _displayedScore.asStateFlow()

    private val _showAddSheet  = MutableStateFlow(false)
    val showAddSheet: StateFlow<Boolean> = _showAddSheet.asStateFlow()

    private val _expandedId    = MutableStateFlow<Long?>(null)
    val expandedId: StateFlow<Long?> = _expandedId.asStateFlow()

    init { loadMockData() }


    // NEW: Function to handle calendar clicks
    fun selectDay(dayOfMonth: Int, dayScore: DayScore?) {
        _selectedDay.value = dayOfMonth
        _displayedScore.value = dayScore
        _displayedSessions.value = dayScore?.sessions ?: emptyList()
    }

    fun toggleSession(id: Long) {
        _expandedId.update { if (it == id) null else id }
    }

    fun toggleSymptom(sessionId: Long, symptom: Symptom) {
        _displayedSessions.update { list ->
            list.map { s ->
                if (s.id == sessionId) {
                    val updated = if (symptom in s.symptoms) s.symptoms - symptom else s.symptoms + symptom
                    s.copy(symptoms = updated)
                } else s
            }
        }
        refreshDisplayedScore()
    }

    fun updateNotes(sessionId: Long, notes: String) {
        _displayedSessions.update { list ->
            list.map { s -> if (s.id == sessionId) s.copy(notes = notes) else s }
        }
    }

    fun openAddSheet()  { _showAddSheet.value = true }
    fun closeAddSheet() { _showAddSheet.value = false }

    fun addManualSession(
        startHour: Int, startMinute: Int,
        endHour: Int,   endMinute: Int,
        peakLevel: Float,
        symptoms: Set<Symptom>,
        notes: String
    ) {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, startHour); set(Calendar.MINUTE, startMinute); set(Calendar.SECOND, 0)
        }
        val start = cal.timeInMillis
        cal.set(Calendar.HOUR_OF_DAY, endHour); cal.set(Calendar.MINUTE, endMinute)
        val end = cal.timeInMillis
        val totalMins = ((end - start) / 60_000).toInt().coerceAtLeast(1)
        val zones = buildZones(peakLevel, totalMins)

        _displayedSessions.update { it + PainSession(
            startTime = start, endTime = end,
            source = SessionSource.MANUAL, peakLevel = peakLevel,
            zones = zones, symptoms = symptoms, notes = notes
        )}
        refreshDisplayedScore()
        closeAddSheet()
    }

    fun onNewDataReceivedFromWatch(record: PainRecord) {
        _painHistory.update { listOf(record) + it }
    }

    private fun refreshDisplayedScore() {
        val sessions = _displayedSessions.value
        val totalPainMins = sessions.sumOf { it.durationMinutes }
        val raw = (100 - (totalPainMins * 1.2f + (sessions.maxOfOrNull { it.peakLevel } ?: 0f) * 3f)).toInt().coerceIn(0, 100)
        _displayedScore.update { it?.copy(score = raw, label = scoreLabel(raw), sessions = sessions) }
    }

    private fun buildZones(peak: Float, total: Int): List<PainZone> = when {
        peak >= 7f -> listOf(PainZone(ZoneLevel.SEVERE,(total*.5f).toInt()), PainZone(ZoneLevel.MODERATE,(total*.3f).toInt()), PainZone(ZoneLevel.MILD,(total*.2f).toInt()))
        peak >= 4f -> listOf(PainZone(ZoneLevel.MODERATE,(total*.6f).toInt()), PainZone(ZoneLevel.MILD,(total*.4f).toInt()))
        else       -> listOf(PainZone(ZoneLevel.MILD, total))
    }

    private fun loadMockData() {
        _painHistory.value = listOf(
            PainRecord(System.currentTimeMillis() - 3_600_000L, 2, 0.85f),
            PainRecord(System.currentTimeMillis() - 7_200_000L, 1, 0.92f),
            PainRecord(System.currentTimeMillis() - 10_800_000L, 3, 0.78f)
        )
        _latestEcw.value = 0.34f
        val now = System.currentTimeMillis()
        val dayMs = 86_400_000L
        val s1 = PainSession(id=1L, startTime=now-8*3600000L, endTime=now-6*3600000L+12*60000L,
            source=SessionSource.SMARTWATCH, peakLevel=8.2f,
            zones=listOf(PainZone(ZoneLevel.SEVERE,54),PainZone(ZoneLevel.MODERATE,36),PainZone(ZoneLevel.MILD,18)),
            symptoms=setOf(Symptom.NAUSEA,Symptom.CRAMPING))
        val s2 = PainSession(id=2L, startTime=now-3*3600000L, endTime=now-3*3600000L+45*60000L,
            source=SessionSource.MANUAL, peakLevel=5.5f,
            zones=listOf(PainZone(ZoneLevel.MODERATE,30),PainZone(ZoneLevel.MILD,15)),
            symptoms=setOf(Symptom.FATIGUE))

        val initialSessions = listOf(s1, s2)
        _displayedSessions.value = initialSessions
        _displayedScore.value = DayScore(date=now-(now%dayMs), score=62, label="fair", sessions=initialSessions)

        val weekVals = listOf(74,45,30,19,62,82,64)
        _weekScores.value = weekVals.mapIndexed { i, sc ->
            DayScore(date=now-(4-i)*dayMs, score=sc, label=scoreLabel(sc), sessions=if(i==4)initialSessions else emptyList())
        }
        val monthVals = listOf(80,72,35,28,18,12,42,55,40,65,70,78,82,30,45,74,38,52,26,19,73,68,80,44,56,76,31,64,70,47,38)
        _monthScores.value = monthVals.mapIndexed { idx, sc ->
            (idx+1) to DayScore(date=0L, score=sc, label=scoreLabel(sc), sessions=if(idx+1==28)initialSessions else emptyList())
        }.toMap()
    }

    fun resetToToday() {
        val today = Calendar.getInstance()
        _weekOffset.value = 0
        _selectedDay.value = today.get(Calendar.DAY_OF_MONTH)

        // Also fetch today's score data if available
        val todayScore = _monthScores.value[_selectedDay.value]
        _displayedScore.value = todayScore
        _displayedSessions.value = todayScore?.sessions ?: emptyList()
    }
}