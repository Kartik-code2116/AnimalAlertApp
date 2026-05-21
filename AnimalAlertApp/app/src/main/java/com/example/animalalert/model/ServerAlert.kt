package com.example.animalalert.model

import java.util.UUID

/**
 * Mirrors the JSON structure returned by the server's /api/alerts endpoint.
 * Each object represents a historical alert stored in MongoDB.
 */
data class ServerAlert(
    val animal_detected: Boolean,
    val animal_type: String?,
    val confidence: Float,
    val location: String?,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val timestamp: Long,
    val image: String? = null
) {
    /**
     * Convert a server alert into the local DetectionHistory model
     * so it can be stored in SharedPrefs and displayed on map/lists.
     */
    fun toDetectionHistory(): DetectionHistory {
        // Try direct lat/lng first, fallback to parsing location string
        val lat = latitude ?: location?.split(",")?.getOrNull(0)?.trim()?.toDoubleOrNull()
        val lng = longitude ?: location?.split(",")?.getOrNull(1)?.trim()?.toDoubleOrNull()

        // Normalize timestamp — Python typically sends seconds, Android needs millis
        val normalizedTs = if (timestamp < 1_000_000_000_000L) timestamp * 1000 else timestamp

        val dangerLevel = DetectionHistory.calculateDangerLevel(animal_type, confidence)

        return DetectionHistory(
            id = UUID.randomUUID().toString(),
            animalType = animal_type,
            confidence = confidence,
            location = location,
            latitude = lat,
            longitude = lng,
            timestamp = normalizedTs,
            dangerLevel = dangerLevel,
            image = image
        )
    }
}
