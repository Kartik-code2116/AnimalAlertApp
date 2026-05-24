package com.example.animalalert.ui

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.example.animalalert.R
import com.example.animalalert.databinding.ActivitySettingsBinding
import com.example.animalalert.network.RetrofitClient
import com.example.animalalert.utils.PreferenceManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var preferenceManager: PreferenceManager
    
    private val activityScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var isFocusInitializing = true
    private var isGlobalInitializing = true
    private var currentPrimaryCameraId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        preferenceManager = PreferenceManager(this)
        RetrofitClient.configure(this)

        setupToolbar()
        loadSettings()
        setupListeners()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)

        binding.toolbar.setNavigationOnClickListener {
            finish()
        }
    }

    private fun loadSettings() {
        // Dark Mode - Remove listener temporarily to prevent programmatic trigger cycles
        binding.switchDarkMode.setOnCheckedChangeListener(null)
        binding.switchDarkMode.isChecked = preferenceManager.isDarkMode()

        // Server URL
        val savedUrl = preferenceManager.getServerUrl()
        binding.etServerUrl.setText(savedUrl)
        binding.tvCurrentUrl.text = "Current: ${RetrofitClient.getBaseUrl()}"

        // Detection Settings
        binding.switchDangerOnly.isChecked = preferenceManager.isDangerOnly()

        // Clamp values to slider range to prevent crash if stored preference is out of bounds
        val confidenceVal = preferenceManager.getConfidenceThreshold().toFloat()
            .coerceIn(binding.sliderConfidence.valueFrom, binding.sliderConfidence.valueTo)
        binding.sliderConfidence.value = confidenceVal
        binding.tvConfidenceValue.text = "${confidenceVal.toInt()}%"

        val pollVal = preferenceManager.getPollIntervalSec().toFloat()
            .coerceIn(binding.sliderPollInterval.valueFrom, binding.sliderPollInterval.valueTo)
        binding.sliderPollInterval.value = pollVal
        binding.tvPollInterval.text = "${pollVal.toInt()} seconds"

        // App Settings
        binding.switchAutoStart.isChecked = preferenceManager.isAutoStartService()
        binding.switchInAppSound.isChecked = preferenceManager.isInAppSound()

        // Map Settings
        binding.switchShowHistory.isChecked = preferenceManager.isShowHistoryOnMap()
        binding.switchAutoCenter.isChecked = preferenceManager.isAutoCenterMap()

        // Version
        try {
            val versionName = packageManager.getPackageInfo(packageName, 0).versionName
            binding.tvVersion.text = "Version $versionName"
        } catch (e: Exception) {
            binding.tvVersion.text = "Version 1.0.0"
        }

        // Restore theme listeners after loading completed
        setupThemeListeners()

        // Asynchronously load registered cameras
        fetchAndPopulateCameras()
    }

    private fun setupThemeListeners() {
        binding.switchDarkMode.setOnCheckedChangeListener { _, isChecked ->
            preferenceManager.setDarkMode(isChecked)
            preferenceManager.setFollowSystemTheme(false)
            applyTheme()
            Toast.makeText(this, if (isChecked) "Dark mode enabled" else "Light mode enabled", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupListeners() {
        // Theme listeners are now managed dynamically inside setupThemeListeners()
        setupThemeListeners()

        // Server URL
        binding.btnTestConnection.setOnClickListener {
            testConnection()
        }

        binding.btnResetUrl.setOnClickListener {
            binding.etServerUrl.setText("http://10.30.201.240:5000")
            RetrofitClient.resetToDefault()
            preferenceManager.setServerUrl("http://10.30.201.240:5000")
            binding.tvCurrentUrl.text = "Current: ${RetrofitClient.getBaseUrl()}"
            Toast.makeText(this, "Reset to default URL", Toast.LENGTH_SHORT).show()
            // Reload cameras list on server update
            fetchAndPopulateCameras()
        }

        // Detection Settings
        binding.switchDangerOnly.setOnCheckedChangeListener { _, isChecked ->
            preferenceManager.setDangerOnly(isChecked)
        }

        binding.sliderConfidence.addOnChangeListener { _, value, _ ->
            preferenceManager.setConfidenceThreshold(value.toInt())
            binding.tvConfidenceValue.text = "${value.toInt()}%"
        }

        binding.sliderPollInterval.addOnChangeListener { _, value, _ ->
            preferenceManager.setPollIntervalSec(value.toInt())
            binding.tvPollInterval.text = "${value.toInt()} seconds"
        }

        // App Settings
        binding.switchAutoStart.setOnCheckedChangeListener { _, isChecked ->
            preferenceManager.setAutoStartService(isChecked)
        }

        binding.switchInAppSound.setOnCheckedChangeListener { _, isChecked ->
            preferenceManager.setInAppSound(isChecked)
        }

        // Map Settings
        binding.switchShowHistory.setOnCheckedChangeListener { _, isChecked ->
            preferenceManager.setShowHistoryOnMap(isChecked)
        }

        binding.switchAutoCenter.setOnCheckedChangeListener { _, isChecked ->
            preferenceManager.setAutoCenterMap(isChecked)
        }

        // Data Management
        binding.btnClearHistory.setOnClickListener {
            showClearHistoryConfirmDialog()
        }

        binding.btnResetSettings.setOnClickListener {
            showResetSettingsConfirmDialog()
        }
    }

    private fun fetchAndPopulateCameras() {
        binding.spinnerFocusCamera.isEnabled = false
        binding.spinnerGlobalPrimaryCamera.isEnabled = false

        activityScope.launch {
            try {
                val responseResult = withContext(Dispatchers.IO) {
                    try {
                        val response = RetrofitClient.api.getCameras().execute()
                        if (response.isSuccessful) response.body() else null
                    } catch (e: Exception) {
                        null
                    }
                }

                if (responseResult != null && responseResult.isNotEmpty()) {
                    populateFocusCameraSpinner(responseResult)
                    populateGlobalCameraSpinner(responseResult)
                } else {
                    setupOfflineFallback()
                }
            } catch (e: Exception) {
                setupOfflineFallback()
            }
        }
    }

    private fun populateFocusCameraSpinner(cameras: List<com.example.animalalert.model.CameraInfo>) {
        isFocusInitializing = true
        val focusTitles = mutableListOf<String>()
        focusTitles.add("Global Active Camera (Default)")

        var selectedIndex = 0
        val savedFocusId = preferenceManager.getPersonalFocusCamera()

        for (i in cameras.indices) {
            val camera = cameras[i]
            focusTitles.add(camera.displayTitle(i + 1))
            if (camera.id == savedFocusId) {
                selectedIndex = i + 1
            }
        }

        val adapter = android.widget.ArrayAdapter(this, android.R.layout.simple_spinner_item, focusTitles)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerFocusCamera.adapter = adapter
        binding.spinnerFocusCamera.isEnabled = true
        binding.spinnerFocusCamera.setSelection(selectedIndex)

        binding.spinnerFocusCamera.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                if (isFocusInitializing) {
                    isFocusInitializing = false
                    return
                }
                if (position == 0) {
                    preferenceManager.setPersonalFocusCamera(null)
                    Toast.makeText(this@SettingsActivity, "Using global primary camera", Toast.LENGTH_SHORT).show()
                } else {
                    val selectedCam = cameras[position - 1]
                    preferenceManager.setPersonalFocusCamera(selectedCam.id)
                    Toast.makeText(this@SettingsActivity, "Focused on ${selectedCam.name ?: selectedCam.id}", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }
    }

    private fun populateGlobalCameraSpinner(cameras: List<com.example.animalalert.model.CameraInfo>) {
        isGlobalInitializing = true
        val globalTitles = mutableListOf<String>()
        var selectedIndex = 0

        val primaryCamera = cameras.find { it.is_primary == true }
        currentPrimaryCameraId = primaryCamera?.id

        for (i in cameras.indices) {
            val camera = cameras[i]
            globalTitles.add(camera.displayTitle(i + 1))
            if (camera.id == currentPrimaryCameraId) {
                selectedIndex = i
            }
        }

        val adapter = android.widget.ArrayAdapter(this, android.R.layout.simple_spinner_item, globalTitles)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerGlobalPrimaryCamera.adapter = adapter
        binding.spinnerGlobalPrimaryCamera.isEnabled = true
        binding.spinnerGlobalPrimaryCamera.setSelection(selectedIndex)

        binding.spinnerGlobalPrimaryCamera.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                if (isGlobalInitializing) {
                    isGlobalInitializing = false
                    return
                }
                val selectedCam = cameras[position]
                if (selectedCam.id == currentPrimaryCameraId) return

                val body = mapOf("action" to "set_primary")
                RetrofitClient.api.controlCamera(selectedCam.id, body).enqueue(object : retrofit2.Callback<com.example.animalalert.model.GenericBackendResponse> {
                    override fun onResponse(
                        call: retrofit2.Call<com.example.animalalert.model.GenericBackendResponse>,
                        response: retrofit2.Response<com.example.animalalert.model.GenericBackendResponse>
                    ) {
                        if (response.isSuccessful) {
                            currentPrimaryCameraId = selectedCam.id
                            Toast.makeText(this@SettingsActivity, "Server primary set to ${selectedCam.name ?: selectedCam.id}", Toast.LENGTH_SHORT).show()
                        } else {
                            isGlobalInitializing = true
                            val prevIndex = cameras.indexOfFirst { it.id == currentPrimaryCameraId }
                            if (prevIndex >= 0) binding.spinnerGlobalPrimaryCamera.setSelection(prevIndex)
                            Toast.makeText(this@SettingsActivity, "Failed to update server camera", Toast.LENGTH_SHORT).show()
                        }
                    }

                    override fun onFailure(call: retrofit2.Call<com.example.animalalert.model.GenericBackendResponse>, t: Throwable) {
                        isGlobalInitializing = true
                        val prevIndex = cameras.indexOfFirst { it.id == currentPrimaryCameraId }
                        if (prevIndex >= 0) binding.spinnerGlobalPrimaryCamera.setSelection(prevIndex)
                        Toast.makeText(this@SettingsActivity, "Offline: failed to update primary camera", Toast.LENGTH_SHORT).show()
                    }
                })
            }

            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }
    }

    private fun setupOfflineFallback() {
        isFocusInitializing = true
        val focusTitles = listOf("Global Active Camera (Default)")
        val focusAdapter = android.widget.ArrayAdapter(this, android.R.layout.simple_spinner_item, focusTitles)
        focusAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerFocusCamera.adapter = focusAdapter
        binding.spinnerFocusCamera.isEnabled = true
        binding.spinnerFocusCamera.setSelection(0)
        binding.spinnerFocusCamera.onItemSelectedListener = null

        isGlobalInitializing = true
        val globalTitles = listOf("No cameras available (Offline)")
        val globalAdapter = android.widget.ArrayAdapter(this, android.R.layout.simple_spinner_item, globalTitles)
        globalAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerGlobalPrimaryCamera.adapter = globalAdapter
        binding.spinnerGlobalPrimaryCamera.isEnabled = false
        binding.spinnerGlobalPrimaryCamera.onItemSelectedListener = null
    }

    private fun applyTheme() {
        val followSystem = preferenceManager.isFollowSystemTheme()
        val darkMode = preferenceManager.isDarkMode()

        when {
            followSystem -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
            darkMode -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            else -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        }
    }

    private fun testConnection() {
        var url = binding.etServerUrl.text.toString().trim()

        if (url.isEmpty()) {
            binding.etServerUrl.error = "URL is required"
            return
        }

        // Auto-prepend http:// if scheme is missing
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "http://$url"
            binding.etServerUrl.setText(url)
        }

        // Update RetrofitClient with new URL
        RetrofitClient.setBaseUrl(url)
        preferenceManager.setServerUrl(url)

        binding.tvCurrentUrl.text = "Current: ${RetrofitClient.getBaseUrl()}"

        Toast.makeText(this, "Testing connection...", Toast.LENGTH_SHORT).show()

        activityScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    // Try health endpoint first (lightweight check, doesn't depend on database records)
                    try {
                        val healthResp = RetrofitClient.api.getHealth().execute()
                        if (healthResp.isSuccessful) {
                            return@withContext Pair(true, healthResp.code())
                        }
                    } catch (e: Exception) {
                        // Ignored, fallback below
                    }

                    // Fallback to latest alert endpoint
                    try {
                        val alertResp = RetrofitClient.api.getLatestAlert().execute()
                        Pair(alertResp.isSuccessful, alertResp.code())
                    } catch (e: Exception) {
                        throw e
                    }
                }

                val (isSuccessful, code) = result
                if (isSuccessful) {
                    Toast.makeText(this@SettingsActivity, "Connection successful!", Toast.LENGTH_SHORT).show()
                    // Reload cameras list on connection test success
                    fetchAndPopulateCameras()
                } else {
                    Toast.makeText(this@SettingsActivity, "Server responded with error: $code", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@SettingsActivity, "Connection failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showClearHistoryConfirmDialog() {
        AlertDialog.Builder(this)
            .setTitle("Clear Detection History")
            .setMessage("This will permanently delete all detection history. Are you sure?")
            .setPositiveButton("Clear") { _, _ ->
                preferenceManager.clearDetectionHistory()
                Toast.makeText(this, "Detection history cleared", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showResetSettingsConfirmDialog() {
        AlertDialog.Builder(this)
            .setTitle("Reset All Settings")
            .setMessage("This will reset all settings to default values. Are you sure?")
            .setPositiveButton("Reset") { _, _ ->
                resetAllSettings()
                Toast.makeText(this, "Settings reset to default", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun resetAllSettings() {
        // Reset all preferences except user data
        val name = preferenceManager.getUserName()
        val email = preferenceManager.getUserEmail()
        val phone = preferenceManager.getUserPhone()

        preferenceManager.clearAll()

        // Restore user data
        preferenceManager.saveUserData(name, email, phone)
        preferenceManager.setLoggedIn(true)

        // Reload UI
        loadSettings()
    }

    override fun onBackPressed() {
        super.onBackPressed()
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        activityScope.cancel()
    }
}
