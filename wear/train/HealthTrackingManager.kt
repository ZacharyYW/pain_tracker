package com.example.pain_tracker.presentation

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.samsung.android.service.health.tracking.ConnectionListener
import com.samsung.android.service.health.tracking.HealthTracker
import com.samsung.android.service.health.tracking.HealthTrackingService
import com.samsung.android.service.health.tracking.HealthTrackerException
import com.samsung.android.service.health.tracking.data.DataPoint
import com.samsung.android.service.health.tracking.data.HealthTrackerType
import com.samsung.android.service.health.tracking.data.ValueKey

class HealthTrackingManager(
    private val context: Context,
    private val onStatusChange: (String) -> Unit
) {

    private var healthTrackingService: HealthTrackingService? = null
    private var heartRateTracker: HealthTracker? = null

    private var ecgTracker: HealthTracker? = null
    private val connectionListener = object : ConnectionListener {
        override fun onConnectionSuccess() {
            Log.d("PainTracker", "connection success")
            onStatusChange("connected, starting tracker...")
            startHeartRateTracking()
        }
        override fun onConnectionEnded() {
            Log.d("PainTracker", "connection ended")
            onStatusChange("connection ended")
        }
        override fun onConnectionFailed(error: HealthTrackerException) {
            Log.d("PainTracker", "connection failed: ${error.errorCode}")
            onStatusChange("conn failed: ${error.errorCode}")
        }
    }

    fun connect() {
        val service = HealthTrackingService(connectionListener, context)
        healthTrackingService = service
        service.connectService()
    }
    fun triggerEcg() {
        val service = healthTrackingService ?: return

        ecgTracker = service.getHealthTracker(HealthTrackerType.ECG_ON_DEMAND)

        ecgTracker?.setEventListener(object : HealthTracker.TrackerEventListener {
            override fun onDataReceived(dataPoints: List<DataPoint>) {
                dataPoints.forEach {
                    val status = it.getValue(ValueKey.EcgSet.LEAD_OFF)
                    Log.d("PainTracker", "ecg status (lead off): $status")
                }
                val ecgValues = dataPoints.map { it.getValue(ValueKey.EcgSet.ECG_MV) }
                DataRepository.addEcgReading(ecgValues, context)

                // unset immediately after receiving - on-demand should only fire once
                ecgTracker?.unsetEventListener()

                Handler(Looper.getMainLooper()).post {
                    onStatusChange("ecg captured: ${ecgValues.size} values")
                }
                Log.d("PainTracker", "ecg received: ${ecgValues.size} values")
            }
            override fun onFlushCompleted() {}
            override fun onError(error: HealthTracker.TrackerError) {
                Log.d("PainTracker", "ecg error: $error")
                Handler(Looper.getMainLooper()).post {
                    onStatusChange("ecg error: $error")
                }
            }
        })
    }
    private fun startHeartRateTracking() {
        val service = healthTrackingService ?: return

        heartRateTracker = service.getHealthTracker(HealthTrackerType.HEART_RATE_CONTINUOUS)

        heartRateTracker?.setEventListener(object : HealthTracker.TrackerEventListener {
            override fun onDataReceived(dataPoints: List<DataPoint>) { // happens when SDK detects a new heartbeat, overirde becuase it's defined in SDK, we fill in for our specifics
                val latestPoint = dataPoints.lastOrNull() ?: return

                val hr = latestPoint.getValue(ValueKey.HeartRateSet.HEART_RATE)
                val ibiList = latestPoint.getValue(ValueKey.HeartRateSet.IBI_LIST)

                DataRepository.addSensorReading(
                    SensorReading(
                        timestamp = System.currentTimeMillis(),
                        heartRate = hr,
                        ibiList = ibiList
                    )
                )

                // onDataReceived fires on a background thread, so UI updates
                // need to be posted to the main thread explicitly
                Handler(Looper.getMainLooper()).post {
                    onStatusChange("tracking | HR: $hr")
                }

                Log.d("PainTracker", "HR: $hr, IBI: $ibiList")
            }
            override fun onFlushCompleted() {}
            override fun onError(error: HealthTracker.TrackerError) {
                Log.d("PainTracker", "tracker error: $error")
                Handler(Looper.getMainLooper()).post {
                    onStatusChange("tracker error: $error")
                }
            }
        })
    }

    fun disconnect() {
        heartRateTracker?.unsetEventListener()
        ecgTracker?.unsetEventListener()
        healthTrackingService?.disconnectService()
    }
}