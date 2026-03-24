package com.example.animalalert.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.example.animalalert.R
import com.example.animalalert.Service.AlertService
import com.example.animalalert.databinding.ActivityMainBinding
import com.example.animalalert.ui.fragments.AlertSystemFragment
import com.example.animalalert.ui.fragments.DashboardFragment
import com.example.animalalert.ui.fragments.MapFragment
import com.example.animalalert.ui.fragments.ProfileFragment
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
        setContentView(binding.root)

        preferenceManager = PreferenceManager(this)

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
}