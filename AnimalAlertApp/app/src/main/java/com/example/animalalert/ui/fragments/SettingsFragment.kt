package com.example.animalalert.ui.fragments

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDelegate
import androidx.fragment.app.Fragment
import com.example.animalalert.databinding.FragmentSettingsBinding
import com.example.animalalert.ui.LoginActivity
import com.example.animalalert.utils.PreferenceManager

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    private lateinit var prefs: PreferenceManager

    // Flag to suppress recursive switch callbacks while loading
    private var isLoading = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        prefs = PreferenceManager(requireContext())
        loadCurrentValues()
        setupListeners()
    }

    // ── Load saved preferences into UI ──────────────────────────────────────

    private fun loadCurrentValues() {
        isLoading = true

        // Appearance
        binding.switchDarkMode.isChecked = prefs.isDarkMode()

        // Notifications
        binding.switchPushNotifications.isChecked = prefs.isNotificationEnabled()
        binding.switchEmailNotifications.isChecked = prefs.isEmailNotificationEnabled()
        binding.switchSmsNotifications.isChecked = prefs.isSmsNotificationEnabled()
        binding.switchInAppSound.isChecked = prefs.isInAppSoundEnabled()

        // Alert System
        binding.switchAutoStart.isChecked = prefs.isAutoStartService()
        binding.switchDangerOnly.isChecked = prefs.isDangerOnly()

        val confidence = prefs.getConfidenceThreshold()
        binding.seekConfidence.progress = confidence
        binding.tvConfidenceValue.text = "$confidence%"

        val poll = prefs.getPollIntervalSec()
        binding.seekPollInterval.progress = poll - 1 // 0-index for 1-10
        binding.tvPollIntervalValue.text = "${poll}s"

        // Map
        binding.switchShowHistory.isChecked = prefs.isShowHistoryOnMap()
        binding.switchAutoCenter.isChecked = prefs.isAutoCenterMap()

        // Server URL
        binding.etServerUrl.setText(prefs.getServerUrl())

        // App version
        try {
            val pInfo = requireContext().packageManager.getPackageInfo(requireContext().packageName, 0)
            binding.tvAppVersion.text = pInfo.versionName
        } catch (e: Exception) {
            binding.tvAppVersion.text = "1.0.0"
        }

        isLoading = false
    }

    // ── Wire UI listeners ────────────────────────────────────────────────────

    private fun setupListeners() {

        // ── Appearance ───────────────────────────────────────────────────────

        binding.switchDarkMode.setOnCheckedChangeListener { _, checked ->
            if (isLoading) return@setOnCheckedChangeListener
            prefs.setDarkMode(checked)
            prefs.setFollowSystemTheme(false)
            applyTheme()
        }

        // ── Notifications ────────────────────────────────────────────────────

        binding.switchPushNotifications.setOnCheckedChangeListener { _, checked ->
            if (isLoading) return@setOnCheckedChangeListener
            prefs.setNotificationEnabled(checked)
            showQuickToast(if (checked) "Push notifications enabled" else "Push notifications disabled")
        }

        binding.switchEmailNotifications.setOnCheckedChangeListener { _, checked ->
            if (isLoading) return@setOnCheckedChangeListener
            prefs.setEmailNotificationEnabled(checked)
            showQuickToast(if (checked) "Email alerts enabled" else "Email alerts disabled")
        }

        binding.switchSmsNotifications.setOnCheckedChangeListener { _, checked ->
            if (isLoading) return@setOnCheckedChangeListener
            prefs.setSmsNotificationEnabled(checked)
            showQuickToast(if (checked) "SMS alerts enabled" else "SMS alerts disabled")
        }

        binding.switchInAppSound.setOnCheckedChangeListener { _, checked ->
            if (isLoading) return@setOnCheckedChangeListener
            prefs.setInAppSound(checked)
            showQuickToast(if (checked) "Siren sound enabled" else "Siren sound disabled")
        }

        // ── Alert System ─────────────────────────────────────────────────────

        binding.switchAutoStart.setOnCheckedChangeListener { _, checked ->
            if (isLoading) return@setOnCheckedChangeListener
            prefs.setAutoStartService(checked)
            showQuickToast(if (checked) "Auto-start monitoring enabled" else "Auto-start monitoring disabled")
        }

        binding.switchDangerOnly.setOnCheckedChangeListener { _, checked ->
            if (isLoading) return@setOnCheckedChangeListener
            prefs.setDangerOnly(checked)
            showQuickToast(if (checked) "Alerts: dangerous animals only" else "Alerts: all animals")
        }

        binding.seekConfidence.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                binding.tvConfidenceValue.text = "$progress%"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                val value = seekBar?.progress ?: 60
                prefs.setConfidenceThreshold(value)
                showQuickToast("Confidence threshold set to $value%")
            }
        })

        binding.seekPollInterval.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val sec = progress + 1 // Convert 0-9 → 1-10
                binding.tvPollIntervalValue.text = "${sec}s"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                val sec = (seekBar?.progress ?: 2) + 1
                prefs.setPollIntervalSec(sec)
                showQuickToast("Polling every ${sec}s")
            }
        })

        // ── Map ──────────────────────────────────────────────────────────────

        binding.switchShowHistory.setOnCheckedChangeListener { _, checked ->
            if (isLoading) return@setOnCheckedChangeListener
            prefs.setShowHistoryOnMap(checked)
            showQuickToast(if (checked) "Showing history on map" else "History hidden on map")
        }

        binding.switchAutoCenter.setOnCheckedChangeListener { _, checked ->
            if (isLoading) return@setOnCheckedChangeListener
            prefs.setAutoCenterMap(checked)
            showQuickToast(if (checked) "Auto-center enabled" else "Auto-center disabled")
        }

        // ── Data & Privacy ────────────────────────────────────────────────────

        binding.btnSaveServerUrl.setOnClickListener {
            val url = binding.etServerUrl.text?.toString()?.trim() ?: ""
            if (url.isEmpty()) {
                showQuickToast("URL cannot be empty")
            } else {
                prefs.setServerUrl(url)
                showQuickToast("Server URL saved")
            }
        }

        binding.rowClearHistory.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle("Clear Detection History")
                .setMessage("This will permanently delete all local detection records. This cannot be undone.")
                .setPositiveButton("Clear") { _, _ ->
                    prefs.clearDetectionHistory()
                    showQuickToast("Detection history cleared")
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        // ── About ─────────────────────────────────────────────────────────────

        binding.rowHelp.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle("Help & Support")
                .setMessage(
                    "Animal Alert App helps you:\n\n" +
                    "• Detect wild animals using AI\n" +
                    "• Get real-time push / SMS / email alerts\n" +
                    "• View animals on an interactive map\n" +
                    "• Review detection history\n\n" +
                    "For support, contact: support@animalalert.com"
                )
                .setPositiveButton("OK", null)
                .show()
        }

        binding.rowEmergencyContacts.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle("Emergency Contacts")
                .setMessage(
                    "• Forest Department: 1926\n" +
                    "• Wildlife SOS: +91-9876543210\n" +
                    "• Local Police: 100"
                )
                .setPositiveButton("Call Forest Dept") { _, _ ->
                    val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:1926"))
                    startActivity(intent)
                }
                .setNegativeButton("Close", null)
                .show()
        }

        binding.rowLogout.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle("Logout")
                .setMessage("Are you sure you want to logout?")
                .setPositiveButton("Logout") { _, _ ->
                    prefs.clearAll()
                    val intent = Intent(requireContext(), LoginActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                    requireActivity().finish()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    // ── Theme application ────────────────────────────────────────────────────

    private fun applyTheme() {
        val mode = when {
            prefs.isFollowSystemTheme() -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            prefs.isDarkMode()          -> AppCompatDelegate.MODE_NIGHT_YES
            else                        -> AppCompatDelegate.MODE_NIGHT_NO
        }
        AppCompatDelegate.setDefaultNightMode(mode)
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun showQuickToast(msg: String) {
        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
