package com.example.pain_tracker.presentation
import android.util.Log
// one second of sensor data from the watch
data class SensorReading(
    val timestamp: Long,
    val heartRate: Int,
    val ibiList: List<Int>      // multiple beats can happen per second, so this is a list
)

// a pain log event - stores the window of readings leading up to the button press
data class PainLogEntry(
    val timestamp: Long,
    val painLevel: Int,
    val windowOfReadings: List<SensorReading>,
    val ecgReadings: MutableList<Float> = mutableListOf()
)

object DataRepository {

    // how many seconds of data to keep before a pain log press
    // roughly 1 reading/sec so 30 = 30 seconds, tweak as needed
    private const val WINDOW_SIZE = 30
    private val sessionTimestamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US).format(java.util.Date())
    // ^ for session timestamp for export csv

    // the rolling window - at any point this holds the last WINDOW_SIZE readings
    private val slidingWindow = ArrayDeque<SensorReading>()

    // full history kept separately for CSV export
    private val allReadings = mutableListOf<SensorReading>()
    private val painLogEntries = mutableListOf<PainLogEntry>()

    // called every ~1 second by HealthTrackingManager
    fun addSensorReading(reading: SensorReading) {
        allReadings.add(reading)

        slidingWindow.addLast(reading)
        if (slidingWindow.size > WINDOW_SIZE) {
            slidingWindow.removeFirst()
        }
    }

    // called when user taps a pain level button
    // snapshots whatever is in the window right now
    fun logPain(painLevel: Int) {
        Log.d("PainTracker", "pain logged: level $painLevel, window size: ${slidingWindow.size}")
        Log.d("PainTracker", "window first: HR=${slidingWindow.first().heartRate} IBI=${slidingWindow.first().ibiList}")
        Log.d("PainTracker", "window last: HR=${slidingWindow.last().heartRate} IBI=${slidingWindow.last().ibiList}")
        painLogEntries.add(
            PainLogEntry(
                timestamp = System.currentTimeMillis(),
                painLevel = painLevel,
                windowOfReadings = slidingWindow.toList()
            )
        )
    }
    fun addEcgReading(ecgValues: List<Float>, context: android.content.Context) {
        val latest = painLogEntries.lastOrNull() ?: return // most recent pain log entry (30 second delay)
        latest.ecgReadings.addAll(ecgValues)
        Log.d("PainTracker", "ecg added to latest entry: ${ecgValues.size} values")
        Log.d("PainTracker", "ecg values: $ecgValues")
        writeToFile(context);
    }

    fun getAllReadings(): List<SensorReading> = allReadings.toList()
    fun getPainLogEntries(): List<PainLogEntry> = painLogEntries.toList()

    // CSV export for Zach - each pain log entry expands into one row per reading in its window
    // format: timestamp, hr, ibi (pipe-separated), pain_level
    fun exportAsCsv(): String {
        val sb = StringBuilder()
        sb.appendLine("timestamp,hr,ibi,pain_level,ecg")

        // unlabeled readings - pain_level = -1, no ecg since ecg is only captured on pain log
        for (reading in allReadings) {
            sb.appendLine("${reading.timestamp},${reading.heartRate},${reading.ibiList.joinToString("|")},-1,")
        }

        // each reading in the window gets the pain label and the ecg snapshot from that event
        for (entry in painLogEntries) {
            val ecgString = entry.ecgReadings.joinToString("|")
            for (reading in entry.windowOfReadings) {
                sb.appendLine("${reading.timestamp},${reading.heartRate},${reading.ibiList.joinToString("|")},${entry.painLevel},$ecgString")
            }
        }

        return sb.toString()
    }
    fun writeToFile(context: android.content.Context) {
        val filename = "pain_tracker_$sessionTimestamp.csv"
        val file = java.io.File(context.getExternalFilesDir(null), filename)
        file.writeText(exportAsCsv())
        Log.d("PainTracker", "csv written to ${file.absolutePath}")
    }
}