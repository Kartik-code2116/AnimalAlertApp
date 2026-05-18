package com.example.animalalert.ui.fragments

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.animalalert.R
import com.example.animalalert.Service.AlertService
import com.example.animalalert.databinding.FragmentAlertSystemBinding
import com.example.animalalert.model.AlertResponse
import com.example.animalalert.model.DetectionHistory
import com.example.animalalert.network.RetrofitClient
import java.util.Locale

import com.example.animalalert.ui.MainActivity
import com.example.animalalert.ui.adapters.DetectionAdapter
import com.example.animalalert.utils.PreferenceManager
import kotlinx.coroutines.*

class AlertSystemFragment : Fragment() {

    private var _binding: FragmentAlertSystemBinding? = null
    private val binding get() = _binding!!
    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.Main + job)
    private lateinit var preferenceManager: PreferenceManager
    private lateinit var detectionAdapter: DetectionAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAlertSystemBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        preferenceManager = PreferenceManager(requireContext())
        
        setupViews()
        
        val running = isServiceRunning(AlertService::class.java)
        binding.switchServiceStatus.isChecked = running
        binding.tvStatus.text = if (running) "Service Running" else "Service Stopped"
        
        startMonitoring()
    }

    private fun setupViews() {
        binding.btnStopSiren.setOnClickListener {
            stopSiren()
        }
        
        binding.btnStartService.setOnClickListener {
            startAlertService()
        }
        
        binding.btnOpenCamera.setOnClickListener {
            openCameraDetection()
        }

        binding.switchServiceStatus.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                startAlertService()
                binding.tvStatus.text = "Service Running"
            } else {
                stopAlertService()
                binding.tvStatus.text = "Service Stopped"
            }
        }
        
        setupRecyclerView()
        loadRecentDetections()
    }
    
    private fun openCameraDetection() {
        Toast.makeText(requireContext(), "External camera server in use", Toast.LENGTH_SHORT).show()
    }
    
    private fun setupRecyclerView() {
        detectionAdapter = DetectionAdapter(
            detections = emptyList(),
            onItemClick = { detection ->
                // Navigate to MapFragment with detection location
                navigateToMapWithDetection(detection)
            },
            onDeleteClick = { detection ->
                preferenceManager.removeDetectionHistory(detection.id)
                loadRecentDetections()
                Toast.makeText(requireContext(), "Detection deleted", Toast.LENGTH_SHORT).show()
            }
        )
        
        binding.recyclerViewDetections.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerViewDetections.adapter = detectionAdapter
    }
    
    private fun navigateToMapWithDetection(detection: DetectionHistory) {
        // Navigate to MainActivity and switch to MapFragment
        val intent = Intent(requireContext(), MainActivity::class.java).apply {
            putExtra("show_detection", true)
            putExtra("detection_lat", detection.latitude)
            putExtra("detection_lng", detection.longitude)
            putExtra("detection_location", detection.location)
            putExtra("detection_animal_type", detection.animalType)
            putExtra("detection_confidence", detection.confidence)
            putExtra("detection_danger", detection.dangerLevel)
        }
        startActivity(intent)
    }
    
    private fun loadRecentDetections() {
        val b = _binding ?: return
        val detections = preferenceManager.getDetectionHistory()
        if (detections.isEmpty()) {
            b.recyclerViewDetections.visibility = View.GONE
            b.tvNoDetections.visibility = View.VISIBLE
        } else {
            b.recyclerViewDetections.visibility = View.VISIBLE
            b.tvNoDetections.visibility = View.GONE
            if (!::detectionAdapter.isInitialized) {
                setupRecyclerView()
            }
            detectionAdapter.updateData(detections)
        }
    }

    private fun startAlertService() {
        val intent = Intent(requireContext(), AlertService::class.java)
        requireContext().startService(intent)
        Toast.makeText(context, "Alert service started", Toast.LENGTH_SHORT).show()
    }

    private fun stopSiren() {
        val stopIntent = Intent(requireContext(), AlertService::class.java)
        stopIntent.action = "STOP_SIREN"
        requireContext().startService(stopIntent)
        Toast.makeText(context, "Siren stopped", Toast.LENGTH_SHORT).show()
    }

    private fun startMonitoring() {
        scope.launch {
            while (true) {
                try {
                    val response = withContext(Dispatchers.IO) {
                        RetrofitClient.api.getLatestAlert().execute()
                    }
                    if (response.isSuccessful) {
                        response.body()?.let { alert ->
                            updateUI(alert)
                            // Stats are already incremented by AlertService;
                            // do NOT increment here to avoid double-counting.
                        }
                    }
                } catch (e: Exception) {
                    val b = _binding ?: break
                    b.tvStatus.text = "Error: ${e.message}"
                }
                delay(3000)
            }
        }
    }

    private fun updateUI(alert: AlertResponse) {
        val b = _binding ?: return
        if (alert.animal_detected) {
            val dangerLevel = DetectionHistory.calculateDangerLevel(alert.animal_type, alert.confidence)
            b.tvStatus.text = "⚠ LIVE THREAT"
            b.tvAnimalType.text = "${iconForAnimal(alert.animal_type)} ${alert.animal_type ?: "Unknown"}"
            b.tvConfidence.text = "Confidence: ${alert.confidence}% · LV $dangerLevel"
            
            // Geocode location if possible
            var displayLocation = alert.location ?: "N/A"
            if (alert.location != null) {
                val parts = alert.location.split(",")
                if (parts.size == 2) {
                    try {
                        val lat = parts[0].trim().toDouble()
                        val lng = parts[1].trim().toDouble()
                        val geocoder = android.location.Geocoder(requireContext(), java.util.Locale.getDefault())
                        val addresses = geocoder.getFromLocation(lat, lng, 1)
                        if (addresses != null && addresses.isNotEmpty()) {
                            val address = addresses[0]
                            displayLocation = "${address.locality ?: ""}, ${address.adminArea ?: ""}".trim { it <= ' ' || it == ',' }
                        }
                    } catch (e: Exception) { }
                }
            }
            
            val relative = relativeTimeAgo(alert.timestamp)
            b.tvLocation.text = "📍 $displayLocation · $relative"
            b.cardAlert.visibility = View.VISIBLE
            b.ivStatus.setImageResource(R.drawable.ic_alert_active)
            // History is written by AlertService. Here we only refresh UI list.
            loadRecentDetections()
            
            // Allow user to click the active alert to see it on map
            b.cardAlert.setOnClickListener {
                if (alert.location != null) {
                    val parts = alert.location.split(",")
                    if (parts.size == 2) {
                        try {
                            val lat = parts[0].trim().toDouble()
                            val lng = parts[1].trim().toDouble()
                            val dangerLevel = DetectionHistory.calculateDangerLevel(alert.animal_type, alert.confidence)
                            
                            val intent = Intent(requireContext(), MainActivity::class.java).apply {
                                putExtra("show_detection", true)
                                putExtra("detection_lat", lat)
                                putExtra("detection_lng", lng)
                                putExtra("detection_location", alert.location)
                                putExtra("detection_animal_type", alert.animal_type)
                                putExtra("detection_confidence", alert.confidence)
                                putExtra("detection_danger", dangerLevel)
                            }
                            startActivity(intent)
                        } catch (e: Exception) {
                             Toast.makeText(requireContext(), "Location not parseable", Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    Toast.makeText(requireContext(), "Location unavailable for this alert", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            b.tvStatus.text = "✅ SYSTEM ACTIVE"
            b.cardAlert.visibility = View.GONE
            b.ivStatus.setImageResource(R.drawable.ic_alert_inactive)
            b.cardAlert.setOnClickListener(null)
        }
    }

    private fun relativeTimeAgo(timestamp: Long): String {
        val normalizedTimestamp = if (timestamp < 1000000000000L) timestamp * 1000 else timestamp
        val diffMs = System.currentTimeMillis() - normalizedTimestamp
        if (diffMs < 0) return "now"

        val minutes = diffMs / 60000L
        if (minutes < 1) return "just now"
        if (minutes < 60) return "${minutes} min ago"

        val hours = minutes / 60
        if (hours < 24) return "${hours} hr ago"

        val days = hours / 24
        return "${days} d ago"
    }

    private fun iconForAnimal(animalType: String?): String {
        val type = animalType?.lowercase(Locale.getDefault()) ?: ""
        return when {
            type.contains("bear") -> "🐻"
            type.contains("wolf") -> "🐺"
            type.contains("lion") -> "🦁"
            type.contains("tiger") -> "🐯"
            type.contains("snake") || type.contains("cobra") -> "🐍"
            type.contains("fox") -> "🦊"
            type.contains("rabbit") -> "🐰"
            type.contains("deer") -> "🦌"
            else -> "🐾"
        }
    }

    private fun stopAlertService() {
        val intent = Intent(requireContext(), AlertService::class.java)
        requireContext().stopService(intent)
        Toast.makeText(context, "Alert service stopped", Toast.LENGTH_SHORT).show()
    }

    private fun isServiceRunning(serviceClass: Class<*>): Boolean {
        val manager = requireContext().getSystemService(android.content.Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        @Suppress("DEPRECATION")
        for (service in manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.name == service.service.className) {
                return true
            }
        }
        return false
    }

    override fun onDestroyView() {
        super.onDestroyView()
        job.cancel()
        _binding = null
    }
}

