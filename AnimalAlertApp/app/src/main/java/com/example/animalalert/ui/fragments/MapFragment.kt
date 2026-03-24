package com.example.animalalert.ui.fragments

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.example.animalalert.R
import com.example.animalalert.databinding.FragmentMapBinding
import com.example.animalalert.model.AlertResponse
import com.example.animalalert.network.RetrofitClient
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import kotlinx.coroutines.*
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.core.content.res.ResourcesCompat
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory

class MapFragment : Fragment(), OnMapReadyCallback {

    private var _binding: FragmentMapBinding? = null
    private val binding get() = _binding!!

    private var googleMap: GoogleMap? = null
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var currentLocation: Location? = null
    private val markers = mutableListOf<Marker>()
    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.Main + job)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMapBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())
        
        val mapFragment = childFragmentManager.findFragmentById(R.id.map) as SupportMapFragment?
        mapFragment?.getMapAsync(this)
        
        binding.btnRefresh.setOnClickListener {
            fetchLatestAlert()
        }
        
        binding.btnMyLocation.setOnClickListener {
            getCurrentLocation()
        }
        
        // Check if we need to show a specific detection
        checkForDetectionToShow()
    }
    
    private fun checkForDetectionToShow(): Boolean {
        arguments?.let { args ->
            val lat = args.getDouble("detection_lat", 0.0)
            val lng = args.getDouble("detection_lng", 0.0)
            val location = args.getString("detection_location")
            val animalType = args.getString("detection_animal_type")
            val confidence = args.getFloat("detection_confidence", 0f)
            val dangerLevel = args.getInt("detection_danger", 1)
            
            if (lat != 0.0 && lng != 0.0) {
                // If map is already ready, show detection immediately
                googleMap?.let {
                    showAllDetectionsOnMap(lat, lng, animalType, dangerLevel)
                    return true
                }
                // Otherwise, it will be shown in onMapReady
                return true
            }
        }
        return false
    }
    
    private fun showAllDetectionsOnMap(
        focusLat: Double? = null,
        focusLng: Double? = null,
        focusAnimalType: String? = null,
        focusDangerLevel: Int = 1
    ) {
        googleMap?.clear()
        markers.clear()
        
        val historyList = com.example.animalalert.utils.PreferenceManager(requireContext()).getDetectionHistory()
        
        for (detection in historyList) {
            val lat = detection.latitude ?: continue
            val lng = detection.longitude ?: continue
            val latLng = LatLng(lat, lng)
            val dangerText = detection.getDangerLevelText()
            val isWild = detection.dangerLevel >= 3
            val dotIcon = if (isWild) vectorToBitmap(R.drawable.ic_red_dot) else vectorToBitmap(R.drawable.ic_blue_dot)
            val circleStroke = if (isWild) android.graphics.Color.RED else android.graphics.Color.BLUE
            val circleFill = if (isWild) 0x33FF0000 else 0x330000FF
            
            val marker = googleMap?.addMarker(
                MarkerOptions()
                    .position(latLng)
                    .title("Animal Detected: ${detection.animalType ?: "Unknown"}")
                    .snippet("Confidence: ${detection.confidence}% | Danger: $dangerText | Location: ${detection.location ?: "N/A"}")
                    .anchor(0.5f, 0.5f) // Center the dot
                    .icon(dotIcon)
            )
            marker?.let { markers.add(it) }
            
            googleMap?.addCircle(
                com.google.android.gms.maps.model.CircleOptions()
                    .center(latLng)
                    .radius(150.0)
                    .strokeWidth(2f)
                    .strokeColor(circleStroke)
                    .fillColor(circleFill)
            )
        }
        
        if (focusLat != null && focusLng != null && focusLat != 0.0 && focusLng != 0.0) {
            val latLng = LatLng(focusLat, focusLng)
            googleMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15f))
            
            val dangerText = when (focusDangerLevel) {
                1 -> "Low"; 2 -> "Moderate"; 3 -> "Medium"; 4 -> "High"; 5 -> "Very High"; else -> "Unknown"
            }
            binding.tvStatus.text = "📍 Focused on: ${focusAnimalType ?: "Unknown"} (Danger: $dangerText)"
        } else if (historyList.isNotEmpty()) {
            val first = historyList.first()
            if (first.latitude != null && first.longitude != null) {
                googleMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(first.latitude, first.longitude), 12f))
                binding.tvStatus.text = "📍 Showing All Recent Detections"
            }
        }
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        
        if (ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            map.isMyLocationEnabled = true
            getCurrentLocation()
        } else {
            requestLocationPermission()
        }
        
        map.uiSettings.isZoomControlsEnabled = true
        map.uiSettings.isMyLocationButtonEnabled = false
        map.uiSettings.isCompassEnabled = true
        
        // Check if we need to show a specific detection first
        arguments?.let { args ->
            val lat = args.getDouble("detection_lat", 0.0)
            val lng = args.getDouble("detection_lng", 0.0)
            val location = args.getString("detection_location")
            val animalType = args.getString("detection_animal_type")
            val confidence = args.getFloat("detection_confidence", 0f)
            val dangerLevel = args.getInt("detection_danger", 1)
            
            if (lat != 0.0 && lng != 0.0) {
                showAllDetectionsOnMap(lat, lng, animalType, dangerLevel)
            } else {
                fetchLatestAlert()
                startPeriodicUpdates()
            }
        } ?: run {
            fetchLatestAlert()
            startPeriodicUpdates()
        }
    }

    private fun requestLocationPermission() {
        requestPermissions(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ),
            LOCATION_PERMISSION_REQUEST_CODE
        )
    }
    
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            onPermissionResult(
                grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
            )
        }
    }

    @SuppressLint("MissingPermission")
    fun onPermissionResult(granted: Boolean) {
        if (granted) {
            googleMap?.isMyLocationEnabled = true
            getCurrentLocation()
        } else {
            Toast.makeText(context, "Location permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    private fun getCurrentLocation() {
        if (ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                location?.let {
                    currentLocation = it
                    val latLng = LatLng(it.latitude, it.longitude)
                    googleMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15f))
                }
            }
        }
    }

    private fun fetchLatestAlert() {
        scope.launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    RetrofitClient.api.getLatestAlert().execute()
                }
                if (response.isSuccessful) {
                    response.body()?.let { alert ->
                        displayAlertOnMap(alert)
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Error fetching alert: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun displayAlertOnMap(alert: AlertResponse) {
        if (alert.animal_detected && alert.location != null) {
            // Parse location (assuming format: "latitude,longitude" or address)
            val locationParts = alert.location.split(",")
            if (locationParts.size == 2) {
                try {
                    val lat = locationParts[0].trim().toDouble()
                    val lng = locationParts[1].trim().toDouble()
                    val latLng = LatLng(lat, lng)
                    
                    // Clear old markers completely and replot everything
                    googleMap?.clear()
                    markers.clear()
                    
                    // Plot historical detections
                    showAllDetectionsOnMap()
                    
                    val liveDanger = com.example.animalalert.model.DetectionHistory.calculateDangerLevel(alert.animal_type, alert.confidence)
                    val isWild = liveDanger >= 3
                    val liveDotIcon = if (isWild) vectorToBitmap(R.drawable.ic_red_dot) else vectorToBitmap(R.drawable.ic_blue_dot)
                    val liveCircleStroke = if (isWild) android.graphics.Color.RED else android.graphics.Color.BLUE
                    val liveCircleFill = if (isWild) 0x33FF0000 else 0x330000FF
                    
                    // Add new LIVE marker
                    val marker = googleMap?.addMarker(
                        MarkerOptions()
                            .position(latLng)
                            .title("LIVE: ${alert.animal_type ?: "Unknown"}")
                            .snippet("Confidence: ${alert.confidence}%")
                            .anchor(0.5f, 0.5f) // Center the dot
                            .icon(liveDotIcon)
                    )
                    marker?.let { markers.add(it) }
                    
                    // Add LIVE red/blue circle
                    googleMap?.addCircle(
                        com.google.android.gms.maps.model.CircleOptions()
                            .center(latLng)
                            .radius(150.0)
                            .strokeWidth(3f)
                            .strokeColor(liveCircleStroke)
                            .fillColor(liveCircleFill)
                    )
                    
                    // Move camera to marker
                    googleMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15f))
                    
                    // Show info window
                    marker?.showInfoWindow()
                    
                    binding.tvStatus.text = "Animal detected: ${alert.animal_type ?: "Unknown"}"
                } catch (e: Exception) {
                    Toast.makeText(context, "Invalid location format", Toast.LENGTH_SHORT).show()
                }
            } else {
                binding.tvStatus.text = "Animal detected but location not available"
            }
        } else {
            binding.tvStatus.text = "No active alerts"
            markers.forEach { it.remove() }
            markers.clear()
        }
    }

    private fun startPeriodicUpdates() {
        scope.launch {
            while (true) {
                delay(5000) // Update every 5 seconds
                fetchLatestAlert()
            }
        }
    }

    private fun vectorToBitmap(drawableId: Int): BitmapDescriptor? {
        val drawable = ResourcesCompat.getDrawable(resources, drawableId, null) ?: return null
        val bitmap = Bitmap.createBitmap(
            drawable.intrinsicWidth,
            drawable.intrinsicHeight,
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return BitmapDescriptorFactory.fromBitmap(bitmap)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        job.cancel()
        _binding = null
    }

    companion object {
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1001
    }
}

