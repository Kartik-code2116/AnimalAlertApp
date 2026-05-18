package com.example.animalalert.model

data class AlertResponse(
    val animal_detected: Boolean,
    val animal_type: String?,
    val confidence: Float,
    val location: String?,
    val timestamp: Long,
    val image: String? = null
)



