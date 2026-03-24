package com.example.animalalert.utils

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.telephony.SmsManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.animalalert.model.AlertResponse
import java.text.SimpleDateFormat
import java.util.*

class SMSHelper(private val context: Context) {

    fun sendSMSNotification(phoneNumber: String, alert: AlertResponse) {
        if (!hasSMSPermission()) {
            Log.e("SMSHelper", "SMS permission not granted")
            return
        }

        try {
            val message = buildSMSMessage(alert)
            val smsManager = SmsManager.getDefault()
            
            // Split message if it's too long (SMS limit is 160 chars)
            if (message.length > 160) {
                val parts = smsManager.divideMessage(message)
                smsManager.sendMultipartTextMessage(phoneNumber, null, parts, null, null)
            } else {
                smsManager.sendTextMessage(phoneNumber, null, message, null, null)
            }
            
            Log.d("SMSHelper", "SMS sent successfully to $phoneNumber")
        } catch (e: Exception) {
            Log.e("SMSHelper", "Error sending SMS: ${e.message}")
        }
    }

    private fun buildSMSMessage(alert: AlertResponse): String {
        val dateFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        return "🚨 Animal Alert: ${alert.animal_type ?: "Unknown"} detected (${alert.confidence}%) at ${dateFormat.format(Date(alert.timestamp))}. Location: ${alert.location ?: "N/A"}"
    }

    private fun hasSMSPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.SEND_SMS
        ) == PackageManager.PERMISSION_GRANTED
    }

    // Alternative: Send SMS using backend API (if you have SMS service like Twilio)
    fun sendSMSViaAPI(phoneNumber: String, alert: AlertResponse) {
        // This would call your backend API to send SMS
        // Example implementation:
        /*
        RetrofitClient.api.sendSMSNotification(
            SMSRequest(
                to = phoneNumber,
                message = buildSMSMessage(alert)
            )
        ).enqueue(...)
        */
    }
}


