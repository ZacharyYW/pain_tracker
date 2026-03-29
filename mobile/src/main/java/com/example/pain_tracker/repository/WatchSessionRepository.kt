package com.example.pain_tracker.repository

import com.example.pain_tracker.model.PredictionPipeline
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Singleton bus that carries completed SessionResults from
 * WearableDataListenerService to DashboardViewModel.
 *
 * SharedFlow (replay=0) means the ViewModel only sees results that
 * arrive while it's actively subscribed — correct behaviour for
 * live watch data.
 */
object WatchSessionRepository {
    private val _results = MutableSharedFlow<PredictionPipeline.SessionResult>(replay = 0)
    val results = _results.asSharedFlow()

    suspend fun emit(result: PredictionPipeline.SessionResult) {
        _results.emit(result)
    }
}