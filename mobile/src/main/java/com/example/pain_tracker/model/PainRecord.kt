package com.example.pain_tracker.model

data class PainRecord(
    val timestamp: Long,
    val painLevel: Int, // 0: None, 1: Mild, 2: Moderate, 3: Severe
    val confidence: Float
)