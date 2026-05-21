package com.example.animalalert.model

data class SystemStatus(
    val monitoring_enabled: Boolean = true,
    val deployment_city: String? = null,
    val active_detection_camera: String? = null,
    val active_camera_name: String? = null,
    val cameras_total: Int = 0,
    val cameras_active: Int = 0,
    val db_connected: Boolean = false,
    val webcam_running: Boolean = false,
    val latest_alert: AlertResponse? = null
)
