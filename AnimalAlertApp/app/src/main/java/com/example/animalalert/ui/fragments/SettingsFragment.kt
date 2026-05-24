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
import com.example.animalalert.network.RetrofitClient
import com.example.animalalert.utils.PreferenceManager
import android.widget.ArrayAdapter
import android.widget.AdapterView
import com.example.animalalert.model.CameraInfo
import com.example.animalalert.model.SystemStatus
import kotlinx.coroutines.*

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    private lateinit var prefs: PreferenceManager

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.Main + job)
    private var isSpinnersLoading = false

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
        loadCamerasAndPopulateSpinners()
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
            var url = binding.etServerUrl.text?.toString()?.trim() ?: ""
            if (url.isEmpty()) {
                showQuickToast("URL cannot be empty")
            } else {
                // Auto-prepend http:// if scheme is missing
                if (!url.startsWith("http://") && !url.startsWith("https://")) {
                    url = "http://$url"
                    binding.etServerUrl.setText(url)
                }
                prefs.setServerUrl(url)
                RetrofitClient.setBaseUrl(url)
                showQuickToast("Server URL saved — API client updated")
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

    // ── Dropdown Spinners Logic ──────────────────────────────────────────────

    private fun loadCamerasAndPopulateSpinners() {
        isSpinnersLoading = true
        scope.launch {
            try {
                val camerasResponse = withContext(Dispatchers.IO) {
                    RetrofitClient.api.getCameras().execute()
                }
                val statusResponse = withContext(Dispatchers.IO) {
                    RetrofitClient.api.getSystemStatus().execute()
                }

                if (camerasResponse.isSuccessful) {
                    val cameras = camerasResponse.body() ?: emptyList()
                    val systemStatus = if (statusResponse.isSuccessful) statusResponse.body() else null
                    
                    if (isAdded && _binding != null) {
                        populatePersonalFocusSpinner(cameras)
                        populateGlobalPrimarySpinner(cameras, systemStatus)
                    }
                } else {
                    if (isAdded) {
                        showQuickToast("Failed to fetch camera list from server")
                        populateEmptySpinners()
                    }
                }
            } catch (e: Exception) {
                if (isAdded) {
                    showQuickToast("Server offline — offline mode settings loaded")
                    populateEmptySpinners()
                }
            } finally {
                isSpinnersLoading = false
            }
        }
    }

    private fun populatePersonalFocusSpinner(cameras: List<CameraInfo>) {
        val displayList = mutableListOf<String>()
        displayList.add("Global Active Camera (Default)")
        
        for (i in cameras.indices) {
            displayList.add(cameras[i].displayTitle(i + 1))
        }

        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, displayList)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerFocusCamera.adapter = adapter

        // Set current selection
        val savedFocus = prefs.getPersonalFocusCamera()
        if (savedFocus == null) {
            binding.spinnerFocusCamera.setSelection(0)
        } else {
            val index = cameras.indexOfFirst { it.id == savedFocus }
            if (index != -1) {
                binding.spinnerFocusCamera.setSelection(index + 1)
            } else {
                binding.spinnerFocusCamera.setSelection(0)
            }
        }

        binding.spinnerFocusCamera.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (isSpinnersLoading) return
                if (position == 0) {
                    prefs.setPersonalFocusCamera(null)
                    showQuickToast("Personal Focus: Default (Global Primary)")
                } else {
                    val selectedCamera = cameras[position - 1]
                    prefs.setPersonalFocusCamera(selectedCamera.id)
                    showQuickToast("Personal Focus set to: ${selectedCamera.name ?: selectedCamera.id}")
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun populateGlobalPrimarySpinner(cameras: List<CameraInfo>, status: SystemStatus?) {
        if (cameras.isEmpty()) {
            populateEmptySpinners()
            return
        }

        val displayList = cameras.mapIndexed { idx, cam -> cam.displayTitle(idx + 1) }
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, displayList)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerGlobalPrimaryCamera.adapter = adapter
        binding.spinnerGlobalPrimaryCamera.isEnabled = true

        // Find the current global active primary camera
        val currentPrimaryId = status?.active_detection_camera 
            ?: cameras.find { it.is_primary == true }?.id

        if (currentPrimaryId != null) {
            val index = cameras.indexOfFirst { it.id == currentPrimaryId }
            if (index != -1) {
                binding.spinnerGlobalPrimaryCamera.setSelection(index)
            }
        }

        binding.spinnerGlobalPrimaryCamera.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (isSpinnersLoading) return
                val selectedCamera = cameras[position]
                
                // Call API to set global primary camera
                scope.launch {
                    try {
                        val body = mapOf("action" to "set_primary")
                        val response = withContext(Dispatchers.IO) {
                            RetrofitClient.api.controlCamera(selectedCamera.id, body).execute()
                        }
                        if (response.isSuccessful) {
                            showQuickToast("Global primary camera updated to: ${selectedCamera.name ?: selectedCamera.id}")
                        } else {
                            showQuickToast("Failed to update server camera focus")
                        }
                    } catch (e: Exception) {
                        showQuickToast("Network error: failed to update global primary camera")
                    }
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun populateEmptySpinners() {
        if (!isAdded) return
        // Local Focus Spinner can still have the default option
        val focusList = listOf("Global Active Camera (Default)")
        val focusAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, focusList)
        focusAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerFocusCamera.adapter = focusAdapter
        binding.spinnerFocusCamera.setSelection(0)

        // Global Primary Spinner will show a placeholder and be disabled
        val globalList = listOf("No cameras available")
        val globalAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, globalList)
        globalAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerGlobalPrimaryCamera.adapter = globalAdapter
        binding.spinnerGlobalPrimaryCamera.isEnabled = false
    }

    override fun onDestroyView() {
        super.onDestroyView()
        job.cancel()
        _binding = null
    }
}
