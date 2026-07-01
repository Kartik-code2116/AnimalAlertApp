package com.example.animalalert.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.animalalert.model.DetectionHistory

@Dao
interface DetectionDao {
    @Query("SELECT * FROM detection_history ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentDetections(limit: Int = 50): List<DetectionHistory>

    @Query("SELECT * FROM detection_history ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentDetectionsSync(limit: Int = 50): List<DetectionHistory>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDetection(detection: DetectionHistory)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertDetectionSync(detection: DetectionHistory)

    @Query("DELETE FROM detection_history WHERE id = :id")
    suspend fun deleteDetection(id: String)

    @Query("DELETE FROM detection_history WHERE id = :id")
    fun deleteDetectionSync(id: String)

    @Query("DELETE FROM detection_history")
    suspend fun clearHistory()

    @Query("DELETE FROM detection_history")
    fun clearHistorySync()

    @Query("SELECT COUNT(*) FROM detection_history")
    fun getTotalDetectionsSync(): Int
}
