package com.example.pain_tracker

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * EXPLANATION: 
 * 'data class' is a special Kotlin class used only to hold data.
 * 'LocalDate' is the standard way to handle dates (Year-Month-Day).
 */
data class CycleSettings(
    val lastPeriodDate: LocalDate,
    val averageCycleLength: Int = 28,
    val averagePeriodLength: Int = 5
)

class PeriodCalculator {

    // This function calculates when the next period will start
    fun predictNextPeriod(settings: CycleSettings): LocalDate {
        // 'plusDays' is a built-in method to add days to a date
        return settings.lastPeriodDate.plusDays(settings.averageCycleLength.toLong())
    }

    // This function tells us which "Phase" the user is in today
    fun getCurrentPhase(settings: CycleSettings): String {
        val today = LocalDate.now()

        // ChronoUnit.DAYS.between calculates how many days have passed since the start
        val daysSinceStart = ChronoUnit.DAYS.between(settings.lastPeriodDate, today).toInt()

        // We use 'when' in Kotlin instead of 'if/else' (it's much cleaner!)
        return when {
            daysSinceStart < 0 -> "Upcoming"
            daysSinceStart < settings.averagePeriodLength -> "Menstrual Phase (Period)"
            daysSinceStart < 14 -> "Follicular Phase"
            daysSinceStart == 14 -> "Ovulation"
            else -> "Luteal Phase"
        }
    }
}