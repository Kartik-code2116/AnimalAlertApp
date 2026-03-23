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
        setupViews()
        setupClickListeners()
        updateStats()
    }

    private fun setupViews() {
        // Title is already set in XML layout
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

        // Activity items
        binding.cardActivity1.setOnClickListener {
            handleActivityItemClick(it)
        }

        binding.cardActivity2.setOnClickListener {
            handleActivityItemClick(it)
        }
    }

    private fun updateStats() {
        // Simulate data updates - replace with real data from ViewModel
        animateCounter(binding.tvDetectionsToday, 12, 1000)
        animateCounter(binding.tvActiveAlerts, 3, 800)
        binding.tvAccuracy.text = "98.5%"
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

    private fun handleActivityItemClick(view: View) {
        Toast.makeText(requireContext(), "Viewing detection details...", Toast.LENGTH_SHORT).show()
        // You can navigate to detailed view or show dialog
        navigateToTab(R.id.nav_map)
    }

    fun refreshData() {
        // Called when fragment is resumed or manually refreshed
        updateStats()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}