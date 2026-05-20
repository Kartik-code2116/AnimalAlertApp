package com.example.animalalert.Service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.media.MediaPlayer
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.animalalert.R
import com.example.animalalert.model.AlertResponse
import com.example.animalalert.model.DetectionHistory
import com.example.animalalert.network.RetrofitClient
import com.example.animalalert.utils.EmailHelper
import com.example.animalalert.utils.NotificationHelper
import com.example.animalalert.utils.PreferenceManager
import com.example.animalalert.utils.SMSHelper
import kotlinx.coroutines.*
import retrofit2.awaitResponse
import java.util.*

class AlertService : Service() {

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)

    private var mediaPlayer: MediaPlayer? = null
    private var userStopped = false
    private var lastNotificationTimestamp: Long = 0
    private val notificationCooldown = 60000L // 1 minute cooldown between notifications
    private var lastDetectionId: String? = null

    private var isMonitoring = false

    companion object {
        private const val FOREGROUND_CHANNEL_ID = "alert_service_channel"
        private const val FOREGROUND_NOTIFICATION_ID = 2001
    }

    override fun onCreate() {
        super.onCreate()
        startForegroundWithNotification()
    }

    private fun startForegroundWithNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                FOREGROUND_CHANNEL_ID,
                "Alert Monitoring Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps the animal alert monitoring service running"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, FOREGROUND_CHANNEL_ID)
            .setContentTitle("Animal Alert Active")
            .setContentText("Monitoring for animal detections...")
            .setSmallIcon(R.drawable.ic_alert)
            .setOngoing(true)
            .build()

        startForeground(FOREGROUND_NOTIFICATION_ID, notification)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

        if (intent?.action == "STOP_SIREN") {
            userStopped = true
            stopSiren()
            return START_STICKY
        }

        if (!isMonitoring) {
            isMonitoring = true
            scope.launch {
                while (true) {
                    try {
                        val response = RetrofitClient.api.getLatestAlert().awaitResponse()

                        if (response.isSuccessful) {

                            val alert = response.body()
                            if (alert != null) {
                                Log.d("AlertService", "Alert fetched: $alert")

                                if (alert.animal_detected && !userStopped && DetectionHistory.isWildAnimal(alert.animal_type)) {
                                    playSiren()
                                    // Update statistics
                                    val prefs = PreferenceManager(this@AlertService)
                                    prefs.incrementTotalDetections()
                                    prefs.incrementTodayDetections()
                                    
                                    // Save to detection history (avoid duplicates)
                                    val detectionId = "${alert.timestamp}_${alert.animal_type}"
                                    if (detectionId != lastDetectionId) {
                                        saveDetectionToHistory(alert, prefs)
                                        lastDetectionId = detectionId
                                    }
                                    
                                    // Send notifications
                                    sendNotifications(alert, prefs)
                                } else if (!alert.animal_detected || !DetectionHistory.isWildAnimal(alert.animal_type)) {
                                    stopSiren()
                                    userStopped = false
                                }
                            }

                        }

                    } catch (e: Exception) {
                        Log.e("AlertService", "Error: ${e.localizedMessage}")
                    }

                    delay(3000)
                }
            }
        }

        return START_STICKY
    }


    private fun playSiren() {
        try {
            if (mediaPlayer == null) {
                mediaPlayer = MediaPlayer()
                val afd = resources.openRawResourceFd(R.raw.siren)
                if (afd == null) return
                mediaPlayer?.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                afd.close()

                mediaPlayer?.setAudioAttributes(
                    android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_ALARM)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                mediaPlayer?.isLooping = true
                mediaPlayer?.prepare()
                
                try {
                    // Maximize alarm volume so it is strictly heard
                    val audioManager = getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager
                    val maxVolume = audioManager.getStreamMaxVolume(android.media.AudioManager.STREAM_ALARM)
                    audioManager.setStreamVolume(android.media.AudioManager.STREAM_ALARM, maxVolume, 0)
                } catch (e: Exception) {
                    Log.w("AlertService", "Could not max system volume: ${e.message}")
                }
            }

            if (mediaPlayer?.isPlaying == false) {
                mediaPlayer?.start()
                Log.d("AlertService", "SIREN PLAYING LOUDLY")
            }
        } catch (e: Exception) {
            Log.e("AlertService", "Error playing siren: ${e.message}")
        }
    }

    private fun stopSiren() {
        try {
            if (mediaPlayer?.isPlaying == true) {
                mediaPlayer?.stop()
            }
            mediaPlayer?.reset()
            mediaPlayer?.release()
        } catch (e: Exception) {
            Log.e("AlertService", "Error stopping siren: ${e.message}")
        } finally {
            mediaPlayer = null
            Log.d("AlertService", "SIREN STOPPED")
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun saveDetectionToHistory(alert: AlertResponse, prefs: PreferenceManager) {
        // Parse location if available
        var latitude: Double? = null
        var longitude: Double? = null
        
        alert.location?.let { location ->
            val parts = location.split(",")
            if (parts.size == 2) {
                try {
                    latitude = parts[0].trim().toDouble()
                    longitude = parts[1].trim().toDouble()
                } catch (e: Exception) {
                    // Location format not valid
                }
            }
        }
        
        // Normalize timestamp to milliseconds (Python sends seconds)
        val normalizedTimestamp = if (alert.timestamp < 1000000000000L) alert.timestamp * 1000 else alert.timestamp
        
        val dangerLevel = DetectionHistory.calculateDangerLevel(alert.animal_type, alert.confidence)
        
        val detection = DetectionHistory(
            id = UUID.randomUUID().toString(),
            animalType = alert.animal_type,
            confidence = alert.confidence,
            location = alert.location,
            latitude = latitude,
            longitude = longitude,
            timestamp = normalizedTimestamp,
            dangerLevel = dangerLevel,
            image = alert.image
        )
        
        prefs.addDetectionHistory(detection)
    }
    
    private fun sendNotifications(alert: AlertResponse, prefs: PreferenceManager) {
        val currentTime = System.currentTimeMillis()
        
        // Check cooldown to avoid spam
        if (currentTime - lastNotificationTimestamp < notificationCooldown) {
            return
        }
        
        if (!prefs.isNotificationEnabled()) {
            return
        }
        
        lastNotificationTimestamp = currentTime
        
        // In-app notification
        if (prefs.isInAppNotificationEnabled()) {
            val notificationHelper = NotificationHelper(this)
            notificationHelper.showAnimalDetectionNotification(alert)
        }
        
        // Email notification — skipped from background service.
        // EmailHelper uses startActivity() which cannot launch from a background service
        // on Android 10+. Email alerts are only sent when triggered from a foreground Activity.
        // To send emails from background, integrate a backend email API instead.
        if (prefs.isEmailNotificationEnabled()) {
            val email = prefs.getUserEmail()
            if (email.isNotEmpty()) {
                Log.d("AlertService", "Email notification skipped (cannot launch email app from background service). Email: $email")
            }
        }
        
        // SMS notification
        if (prefs.isSmsNotificationEnabled()) {
            val phone = prefs.getUserPhone()
            if (phone.isNotEmpty()) {
                val smsHelper = SMSHelper(this)
                smsHelper.sendSMSNotification(phone, alert)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopSiren()
        job.cancel()
    }
}
