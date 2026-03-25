package com.example.animalalert.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.example.animalalert.R
import com.example.animalalert.Service.AlertService
import com.example.animalalert.databinding.ActivityMainBinding
import androidx.appcompat.app.AppCompatDelegate
import com.example.animalalert.ui.fragments.AlertSystemFragment
import com.example.animalalert.ui.fragments.DashboardFragment
import com.example.animalalert.ui.fragments.MapFragment
import com.example.animalalert.ui.fragments.ProfileFragment
import com.example.animalalert.ui.fragments.SettingsFragment
import com.example.animalalert.utils.PreferenceManager

class MainActivity : AppCompatActivity() {

    lateinit var binding: ActivityMainBinding
    private lateinit var preferenceManager: PreferenceManager

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (!allGranted) {
            Toast.makeText(this, "Location permission required for map features", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)

        // Apply saved theme BEFORE setContentView to avoid flicker
        preferenceManager = PreferenceManager(this)
        applyThemeSetting()

        setContentView(binding.root)

        // Setup toolbar as support action bar
        setSupportActionBar(binding.toolbar)

        // Check if logged in
        if (!preferenceManager.isLoggedIn()) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        // Request permissions if needed
        requestPermissions()

        // Start alert service
        try {
            val intent = Intent(this, AlertService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to start alert service", Toast.LENGTH_SHORT).show()
        }

        setupBottomNavigation()

        // Check if we need to show a specific detection on map
        val showDetection = intent.getBooleanExtra("show_detection", false)
        if (showDetection) {
            // Navigate to MapFragment with detection data
            val mapFragment = MapFragment().apply {
                arguments = Bundle().apply {
                    putDouble("detection_lat", intent.getDoubleExtra("detection_lat", 0.0))
                    putDouble("detection_lng", intent.getDoubleExtra("detection_lng", 0.0))
                    putString("detection_location", intent.getStringExtra("detection_location"))
                    putString("detection_animal_type", intent.getStringExtra("detection_animal_type"))
                    putFloat("detection_confidence", intent.getFloatExtra("detection_confidence", 0f))
                    putInt("detection_danger", intent.getIntExtra("detection_danger", 1))
                }
            }
            loadFragment(mapFragment)
            binding.bottomNavigation.selectedItemId = R.id.nav_map
        } else {
            loadFragment(DashboardFragment())
        }
    }

    private fun requestPermissions() {
        val permissionsToRequest = mutableListOf<String>()

        // Location permissions
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }

        // For Android 13+ (API 33)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        if (permissionsToRequest.isNotEmpty()) {
            locationPermissionLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }

    private fun setupBottomNavigation() {
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_dashboard -> {
                    loadFragment(DashboardFragment())
                    true
                }
                R.id.nav_map -> {
                    loadFragment(MapFragment())
                    true
                }
                R.id.nav_alert -> {
                    loadFragment(AlertSystemFragment())
                    true
                }
                R.id.nav_profile -> {
                    loadFragment(ProfileFragment())
                    true
                }
                else -> false
            }
        }
    }

    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        
        intent?.let {
            val showDetection = it.getBooleanExtra("show_detection", false)
            if (showDetection) {
                // Navigate to MapFragment with detection data
                val mapFragment = MapFragment().apply {
                    arguments = Bundle().apply {
                        putDouble("detection_lat", it.getDoubleExtra("detection_lat", 0.0))
                        putDouble("detection_lng", it.getDoubleExtra("detection_lng", 0.0))
                        putString("detection_location", it.getStringExtra("detection_location"))
                        putString("detection_animal_type", it.getStringExtra("detection_animal_type"))
                        putFloat("detection_confidence", it.getFloatExtra("detection_confidence", 0f))
                        putInt("detection_danger", it.getIntExtra("detection_danger", 1))
                    }
                }
                loadFragment(mapFragment)
                binding.bottomNavigation.selectedItemId = R.id.nav_map
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.toolbar_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_refresh -> {
                refreshAppData()
                true
            }
            R.id.action_scan_now -> {
                navigateToScan()
                true
            }
            R.id.action_notifications -> {
                showNotificationsSettings()
                true
            }
            R.id.action_emergency_contacts -> {
                showEmergencyContacts()
                true
            }
            R.id.action_history -> {
                showDetectionHistory()
                true
            }
            R.id.action_share_location -> {
                shareMyLocation()
                true
            }
            R.id.action_settings -> {
                openSettings()
                true
            }
            R.id.action_help -> {
                showHelp()
                true
            }
            R.id.action_logout -> {
                showLogoutConfirmation()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun refreshAppData() {
        Toast.makeText(this, "Refreshing data...", Toast.LENGTH_SHORT).show()
        // Restart the alert service to refresh
        try {
            val intent = Intent(this, AlertService::class.java)
            stopService(intent)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
            Toast.makeText(this, "Data refreshed successfully", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Refresh failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun navigateToScan() {
        binding.bottomNavigation.selectedItemId = R.id.nav_alert
        loadFragment(AlertSystemFragment())
        Toast.makeText(this, "Starting scan...", Toast.LENGTH_SHORT).show()
    }

    private fun showNotificationsSettings() {
        Toast.makeText(this, "Notifications settings", Toast.LENGTH_SHORT).show()
        // TODO: Navigate to notification settings fragment
    }

    private fun showEmergencyContacts() {
        AlertDialog.Builder(this)
            .setTitle("Emergency Contacts")
            .setMessage("\u2022 Forest Department: 1926\n\u2022 Wildlife SOS: +91-9876543210\n\u2022 Local Police: 100")
            .setPositiveButton("Call Forest Dept") { _, _ ->
                val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:1926"))
                startActivity(intent)
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun showDetectionHistory() {
        Toast.makeText(this, "Detection history coming soon", Toast.LENGTH_SHORT).show()
        // TODO: Navigate to history fragment
    }

    private fun shareMyLocation() {
        // TODO: Implement location sharing
        Toast.makeText(this, "Share location feature coming soon", Toast.LENGTH_SHORT).show()
    }

    private fun openSettings() {
        loadFragment(SettingsFragment())
        // Deselect all bottom-nav items (Menu is not Kotlin Iterable)
        val menu = binding.bottomNavigation.menu
        menu.setGroupCheckable(0, true, false)
        for (i in 0 until menu.size()) menu.getItem(i).isChecked = false
        menu.setGroupCheckable(0, true, true)
    }

    /** Apply dark / light / system theme from saved preference. */
    fun applyThemeSetting() {
        val mode = when {
            preferenceManager.isFollowSystemTheme() -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            preferenceManager.isDarkMode()          -> AppCompatDelegate.MODE_NIGHT_YES
            else                                    -> AppCompatDelegate.MODE_NIGHT_NO
        }
        AppCompatDelegate.setDefaultNightMode(mode)
    }

    private fun showHelp() {
        AlertDialog.Builder(this)
            .setTitle("Help & Support")
            .setMessage("Animal Alert App helps you:\n\n" +
                    "\u2022 Detect wild animals using AI\n" +
                    "\u2022 Get real-time alerts\n" +
                    "\u2022 View animals on map\n" +
                    "\u2022 Report animal sightings\n\n" +
                    "For support, contact: support@animalalert.com")
            .setPositiveButton("OK", null)
            .show()
    }

    private fun showLogoutConfirmation() {
        AlertDialog.Builder(this)
            .setTitle("Logout")
            .setMessage("Are you sure you want to logout?")
            .setPositiveButton("Logout") { _, _ ->
                preferenceManager.clearAll()
                val intent = Intent(this, LoginActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}