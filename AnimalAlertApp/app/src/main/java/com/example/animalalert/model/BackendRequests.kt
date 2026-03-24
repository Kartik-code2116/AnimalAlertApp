package com.example.animalalert.model

data class CameraRegisterRequest(
    val camera_id: String,
    val location: String
)

data class CameraDetectRequest(
    val camera_id: String,
    val image: String
)

