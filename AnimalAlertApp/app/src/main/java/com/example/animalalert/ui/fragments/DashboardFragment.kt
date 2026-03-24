package com.example.animalalert.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.animalalert.R
import com.example.animalalert.databinding.FragmentDashboardBinding

class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!
    private lateinit var detectionAdapter: com.example.animalalert.ui.adapters.DetectionAdapter
    private lateinit var preferenceManager: com.example.animalalert.utils.PreferenceManager

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        preferenceManager = com.example.animalalert.utils.PreferenceManager(requireContext())
        setupViews()
        setupClickListeners()
        updateStats()
        loadRecentDetections()
    }

    private fun setupViews() {
        detectionAdapter = com.example.animalalert.ui.adapters.DetectionAdapter(
            detections = emptyList(),
            onItemClick = { detection ->
                val intent = android.content.Intent(requireContext(), com.example.animalalert.ui.MainActivity::class.java).apply {
                    putExtra("show_detection", true)
                    putExtra("detection_lat", detection.latitude)
                    putExtra("detection_lng", detection.longitude)
                    putExtra("detection_location", detection.location)
                    putExtra("detection_animal_type", detection.animalType)
                    putExtra("detection_confidence", detection.confidence)
                    putExtra("detection_danger", detection.dangerLevel)
                }
                startActivity(intent)
            },
            onDeleteClick = { detection ->
                preferenceManager.removeDetectionHistory(detection.id)
                loadRecentDetections()
                updateStats()
                Toast.makeText(requireContext(), "Detection deleted", Toast.LENGTH_SHORT).show()
            }
        )
        binding.recyclerViewDashboardDetections.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(requireContext())
        binding.recyclerViewDashboardDetections.adapter = detectionAdapter
    }

    private fun setupClickListeners() {
        // Quick action buttons
        binding.cardStartMonitoring.setOnClickListener {
            handleQuickActionClick(it)
        }

        binding.cardViewMap.setOnClickListener {
            handleQuickActionClick(it)
        }

        binding.cardViewAlerts.setOnClickListener {
            handleQuickActionClick(it)
        }

        binding.cardSettings.setOnClickListener {
            handleQuickActionClick(it)
        }
    }

    private fun updateStats() {
        val today = preferenceManager.getTodayDetections()
        val total = preferenceManager.getTotalDetections()
        animateCounter(binding.tvDetectionsToday, today, 1000)
        animateCounter(binding.tvActiveAlerts, total, 800) // Using total for now or whatever fits
        binding.tvAccuracy.text = "98.5%"
    }

    private fun loadRecentDetections() {
        val detections = preferenceManager.getDetectionHistory().take(3)
        if (detections.isEmpty()) {
            binding.recyclerViewDashboardDetections.visibility = View.GONE
            binding.tvEmptyRecent.visibility = View.VISIBLE
        } else {
            binding.recyclerViewDashboardDetections.visibility = View.VISIBLE
            binding.tvEmptyRecent.visibility = View.GONE
            detectionAdapter.updateData(detections)
        }
    }

    private fun animateCounter(textView: android.widget.TextView, target: Int, duration: Long) {
        android.animation.ValueAnimator().apply {
            setObjectValues(0, target)
            setDuration(duration)
            addUpdateListener { animator ->
                textView.text = animator.animatedValue.toString()
            }
            start()
        }
    }

    private fun handleQuickActionClick(view: View) {
        when (view.id) {
            R.id.card_start_monitoring -> {
                Toast.makeText(requireContext(), "Starting monitoring...", Toast.LENGTH_SHORT).show()
                // Add your monitoring start logic here
            }
            R.id.card_view_map -> {
                navigateToTab(R.id.nav_map)
            }
            R.id.card_view_alerts -> {
                navigateToTab(R.id.nav_alert)
            }
            R.id.card_settings -> {
                navigateToTab(R.id.nav_profile)
            }
        }
    }

    private fun navigateToTab(tabId: Int) {
        try {
            val activity = requireActivity() as com.example.animalalert.ui.MainActivity
            activity.binding.bottomNavigation.selectedItemId = tabId
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Navigation failed", Toast.LENGTH_SHORT).show()
        }
    }

    fun refreshData() {
        // Called when fragment is resumed or manually refreshed
        updateStats()
        loadRecentDetections()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}