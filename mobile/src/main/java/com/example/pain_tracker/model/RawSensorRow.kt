package com.example.pain_tracker.model

data class RawSensorRow(
    val hr: Float,
    val ibi: List<Int>, // Parsed from the pipe-separated string
    val ecg: List<Float> // Parsed from the pipe-separated string
)