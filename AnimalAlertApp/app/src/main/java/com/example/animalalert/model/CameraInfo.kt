package com.example.animalalert.model

data class CameraInfo(
    val id: String,
    val camera_number: Int? = null,
    val city: String? = null,
    val name: String? = null,
    val location: String,   // "lat,lng" format
    val place: String? = null,
    val status: String? = null,
    val type: String? = null,
    val rtspUrl: String? = null,
    val streamUrl: String? = null,
    val notes: String? = null,
    val is_primary: Boolean? = null,
    val addedAt: Long? = null
) {
    fun displayNumber(fallbackIndex: Int): Int = camera_number ?: fallbackIndex

    fun displayTitle(fallbackIndex: Int): String {
        val num = displayNumber(fallbackIndex)
        val label = name?.takeIf { it.isNotBlank() } ?: id
        return "#$num $label"
    }

    fun displaySnippet(fallbackIndex: Int): String {
        val num = displayNumber(fallbackIndex)
        val cityLabel = city?.takeIf { it.isNotBlank() } ?: place ?: "—"
        val statusLabel = status?.takeIf { it.isNotBlank() } ?: "active"
        val primary = if (is_primary == true) " · PRIMARY" else ""
        return "#$num · ${statusLabel.uppercase()} · $cityLabel$primary"
    }

    fun deploymentCity(): String? = city?.takeIf { it.isNotBlank() }
}
