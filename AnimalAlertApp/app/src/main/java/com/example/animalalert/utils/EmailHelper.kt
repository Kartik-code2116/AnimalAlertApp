package com.example.animalalert.utils

import android.content.Intent
import android.net.Uri
import android.util.Log
import com.example.animalalert.model.AlertResponse
import java.text.SimpleDateFormat
import java.util.*

class EmailHelper(private val context: android.content.Context) {

    fun sendEmailNotification(email: String, alert: AlertResponse) {
        try {
            val subject = "🚨 Animal Detection Alert - ${alert.animal_type ?: "Unknown"}"
            val body = buildEmailBody(alert)

            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:")
                putExtra(Intent.EXTRA_EMAIL, arrayOf(email))
                putExtra(Intent.EXTRA_SUBJECT, subject)
                putExtra(Intent.EXTRA_TEXT, body)
            }

            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(Intent.createChooser(intent, "Send email via..."))
            } else {
                Log.e("EmailHelper", "No email app found")
            }
        } catch (e: Exception) {
            Log.e("EmailHelper", "Error sending email: ${e.message}")
        }
    }

    private fun buildEmailBody(alert: AlertResponse): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        return """
            Animal Detection Alert
            
            Animal Type: ${alert.animal_type ?: "Unknown"}
            Confidence: ${alert.confidence}%
            Location: ${alert.location ?: "Not available"}
            Detection Time: ${dateFormat.format(Date(if (alert.timestamp < 1000000000000L) alert.timestamp * 1000 else alert.timestamp))}
            
            This is an automated alert from Animal Alert System.
            
            Please check the app for more details.
        """.trimIndent()
    }

    // Alternative: Send email using backend API (if you have email service)
    fun sendEmailViaAPI(email: String, alert: AlertResponse) {
        // This would call your backend API to send email
        // Example implementation:
        /*
        RetrofitClient.api.sendEmailNotification(
            EmailRequest(
                to = email,
                subject = "Animal Detection Alert",
                body = buildEmailBody(alert)
            )
        ).enqueue(...)
        */
    }
}


