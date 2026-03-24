package com.example.animalalert.model

data class HealthResponse(
    val status: String? = null
)

data class GenericBackendResponse(
    val status: String? = null,
    val message: String? = null
)

data class DetectionItem(
    val class_name: String? = null,
    val confidence: Float? = null
)

data class CameraDetectResponse(
    val status: String? = null,
    val camera_id: String? = null,
    val dangerous: Boolean? = null,
    val detections: List<DetectionItem>? = null,
    val message: String? = null
)

