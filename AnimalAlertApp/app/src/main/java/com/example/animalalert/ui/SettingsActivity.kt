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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var preferenceManager: PreferenceManager

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
        val url = binding.etServerUrl.text.toString().trim()

        if (url.isEmpty()) {
            binding.etServerUrl.error = "URL is required"
            return
        }

        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            binding.etServerUrl.error = "URL must start with http:// or https://"
            return
        }

        // Update RetrofitClient with new URL
        RetrofitClient.setBaseUrl(url)
        preferenceManager.setServerUrl(url)

        binding.tvCurrentUrl.text = "Current: ${RetrofitClient.getBaseUrl()}"

        Toast.makeText(this, "Testing connection...", Toast.LENGTH_SHORT).show()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = RetrofitClient.api.getLatestAlert().execute()
                withContext(Dispatchers.Main) {
                    if (response.isSuccessful) {
                        Toast.makeText(this@SettingsActivity, "Connection successful!", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this@SettingsActivity, "Server responded with error", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@SettingsActivity, "Connection failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
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
}
