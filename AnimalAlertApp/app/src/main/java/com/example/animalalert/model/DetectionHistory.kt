package com.example.animalalert.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class DetectionHistory(
    val id: String,
    val animalType: String?,
    val confidence: Float,
    val location: String?,
    val latitude: Double?,
    val longitude: Double?,
    val timestamp: Long,
    val dangerLevel: Int // 1-5 (1=Low, 5=Very High)
) : Parcelable {
    
    fun getDangerLevelText(): String {
        return when (dangerLevel) {
            1 -> "Low"
            2 -> "Moderate"
            3 -> "Medium"
            4 -> "High"
            5 -> "Very High"
            else -> "Unknown"
        }
    }
    
    fun getDangerColor(): Int {
        return when (dangerLevel) {
            1 -> 0xFF4CAF50.toInt() // Green
            2 -> 0xFF8BC34A.toInt() // Light Green
            3 -> 0xFFFFC107.toInt() // Amber
            4 -> 0xFFFF9800.toInt() // Orange
            5 -> 0xFFF44336.toInt() // Red
            else -> 0xFF757575.toInt() // Grey
        }
    }
    
    companion object {
        fun calculateDangerLevel(animalType: String?, confidence: Float): Int {
            val type = animalType?.lowercase() ?: ""
            
            // Very High Danger (5)
            if (type.contains("bear") || type.contains("wolf") || type.contains("lion") || 
                type.contains("tiger") || type.contains("leopard") || type.contains("crocodile") ||
                type.contains("alligator") || (type.contains("snake") && type.contains("venom"))) {
                return 5
            }
            
            // High Danger (4)
            if (type.contains("wild boar") || type.contains("boar") || type.contains("snake") || 
                (type.contains("deer") && confidence > 80) || type.contains("moose") || 
                type.contains("elk") || type.contains("bison")) {
                return 4
            }
            
            // Medium Danger (3)
            if (type.contains("deer") || type.contains("fox") || type.contains("coyote") ||
                type.contains("raccoon") || type.contains("skunk")) {
                return 3
            }
            
            // Moderate Danger (2)
            if (type.contains("rabbit") || type.contains("squirrel") || type.contains("bird") ||
                type.contains("cat") || type.contains("dog")) {
                return 2
            }
            
            // Low Danger (1) - default
            return 1
        }
    }
}


