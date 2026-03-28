package com.example.pain_tracker.viewmodel

import androidx.lifecycle.ViewModel
import com.example.pain_tracker.model.PainRecord
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class DashboardViewModel : ViewModel() {
    private val _painHistory = MutableStateFlow<List<PainRecord>>(emptyList())
    val painHistory: StateFlow<List<PainRecord>> = _painHistory.asStateFlow()

    private val _latestEcw = MutableStateFlow<Float?>(null)
    val latestEcw: StateFlow<Float?> = _latestEcw.asStateFlow()

    init {
        loadMockData()
    }

    private fun loadMockData() {
        val mockData = listOf(
            PainRecord(System.currentTimeMillis() - 3600000, 2, 0.85f),
            PainRecord(System.currentTimeMillis() - 7200000, 1, 0.92f),
            PainRecord(System.currentTimeMillis() - 10800000, 3, 0.78f)
        )
        _painHistory.value = mockData
        _latestEcw.value = 0.34f
    }

    fun onNewDataReceivedFromWatch(record: PainRecord) {
        val currentList = _painHistory.value.toMutableList()
        currentList.add(0, record)
        _painHistory.value = currentList
    }
}