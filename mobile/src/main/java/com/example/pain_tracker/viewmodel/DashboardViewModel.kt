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

    private val _todaySessions = MutableStateFlow<List<PainSession>>(emptyList())
    val todaySessions: StateFlow<List<PainSession>> = _todaySessions.asStateFlow()

    private val _todayScore    = MutableStateFlow<DayScore?>(null)
    val todayScore: StateFlow<DayScore?> = _todayScore.asStateFlow()

    private val _showAddSheet  = MutableStateFlow(false)
    val showAddSheet: StateFlow<Boolean> = _showAddSheet.asStateFlow()

    private val _expandedId    = MutableStateFlow<Long?>(null)
    val expandedId: StateFlow<Long?> = _expandedId.asStateFlow()

    init { loadMockData() }

    fun toggleSession(id: Long) {
        _expandedId.update { if (it == id) null else id }
    }

    fun toggleSymptom(sessionId: Long, symptom: Symptom) {
        _todaySessions.update { list ->
            list.map { s ->
                if (s.id == sessionId) {
                    val updated = if (symptom in s.symptoms) s.symptoms - symptom else s.symptoms + symptom
                    s.copy(symptoms = updated)
                } else s
            }
        }
        refreshTodayScore()
    }

    fun updateNotes(sessionId: Long, notes: String) {
        _todaySessions.update { list ->
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
        _todaySessions.update { it + PainSession(
            startTime = start, endTime = end,
            source = SessionSource.MANUAL, peakLevel = peakLevel,
            zones = zones, symptoms = symptoms, notes = notes
        )}
        refreshTodayScore()
        closeAddSheet()
    }

    fun onNewDataReceivedFromWatch(record: PainRecord) {
        _painHistory.update { listOf(record) + it }
    }

    private fun refreshTodayScore() {
        val sessions = _todaySessions.value
        val totalPainMins = sessions.sumOf { it.durationMinutes }
        val raw = (100 - (totalPainMins * 1.2f + (sessions.maxOfOrNull { it.peakLevel } ?: 0f) * 3f)).toInt().coerceIn(0, 100)
        _todayScore.update { it?.copy(score = raw, label = scoreLabel(raw), sessions = sessions) }
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
        val todaySessions = listOf(s1, s2)
        _todaySessions.value = todaySessions
        _todayScore.value = DayScore(date=now-(now%dayMs), score=62, label="fair", sessions=todaySessions)
        val weekVals = listOf(74,45,30,19,62,82,64)
        _weekScores.value = weekVals.mapIndexed { i, sc ->
            DayScore(date=now-(4-i)*dayMs, score=sc, label=scoreLabel(sc), sessions=if(i==4)todaySessions else emptyList())
        }
        val monthVals = listOf(80,72,35,28,18,12,42,55,40,65,70,78,82,30,45,74,38,52,26,19,73,68,80,44,56,76,31,64,70,47,38)
        _monthScores.value = monthVals.mapIndexed { idx, sc ->
            (idx+1) to DayScore(date=0L, score=sc, label=scoreLabel(sc), sessions=if(idx+1==28)todaySessions else emptyList())
        }.toMap()
    }
}