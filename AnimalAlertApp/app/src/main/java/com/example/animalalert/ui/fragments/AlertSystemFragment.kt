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
        val detections = preferenceManager.getDetectionHistory()
        if (detections.isEmpty()) {
            binding.recyclerViewDetections.visibility = View.GONE
            binding.tvNoDetections.visibility = View.VISIBLE
        } else {
            binding.recyclerViewDetections.visibility = View.VISIBLE
            binding.tvNoDetections.visibility = View.GONE
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
                    binding.tvStatus.text = "Error: ${e.message}"
                }
                delay(3000)
            }
        }
    }

    private fun updateUI(alert: AlertResponse) {
        if (alert.animal_detected) {
            binding.tvStatus.text = "⚠️ ALERT: Animal Detected!"
            binding.tvAnimalType.text = "Type: ${alert.animal_type ?: "Unknown"}"
            binding.tvConfidence.text = "Confidence: ${alert.confidence}%"
            
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
            
            binding.tvLocation.text = "Location: $displayLocation"
            binding.cardAlert.visibility = View.VISIBLE
            binding.ivStatus.setImageResource(R.drawable.ic_alert_active)
            // History is written by AlertService. Here we only refresh UI list.
            loadRecentDetections()
            
            // Allow user to click the active alert to see it on map
            binding.cardAlert.setOnClickListener {
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
            binding.tvStatus.text = "✅ System Active - No Alerts"
            binding.cardAlert.visibility = View.GONE
            binding.ivStatus.setImageResource(R.drawable.ic_alert_inactive)
            binding.cardAlert.setOnClickListener(null)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        job.cancel()
        _binding = null
    }
}

