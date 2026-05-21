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
import com.example.animalalert.model.CameraInfo
import com.example.animalalert.network.RetrofitClient
import com.example.animalalert.utils.MapPinHelper
import com.example.animalalert.utils.ServerSyncManager
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

    private val cameraData = mutableListOf<CameraInfo>()
    private val cameraMarkers = mutableListOf<Marker>()
    private val cameraCircles = mutableListOf<com.google.android.gms.maps.model.Circle>()
    private val detectionCircles = mutableListOf<com.google.android.gms.maps.model.Circle>()
    private var scanJob: Job? = null
    private var cameraSyncJob: Job? = null

    companion object {
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1001
        // Pune fallback when server is unreachable (matches production server defaults)
        private val FALLBACK_CAMERA_LOCATIONS = listOf(
            LatLng(18.5204, 73.8567),
            LatLng(18.5210, 73.8570),
            LatLng(18.5198, 73.8560)
        )
    }

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
        
        binding.mapSwipeRefresh.setColorSchemeResources(R.color.accent)
        binding.mapSwipeRefresh.setProgressBackgroundColorSchemeResource(R.color.bg_card2)
        binding.mapSwipeRefresh.setOnRefreshListener {
            performMapRefresh(showAnimation = true)
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
                    showAllDetectionsOnMap(lat, lng, animalType, dangerLevel, confidence)
                    return true
                }
                // Otherwise, it will be shown in onMapReady
                return true
            }
        }
        return false
    }
    
    data class DangerColorConfig(
        val drawableId: Int,
        val strokeColor: Int,
        val fillColor: Int
    )

    private fun getColorConfigForDangerLevel(dangerLevel: Int): DangerColorConfig {
        return when (dangerLevel) {
            5 -> DangerColorConfig(R.drawable.ic_red_dot, android.graphics.Color.RED, 0x33FF0000)
            4 -> DangerColorConfig(R.drawable.ic_orange_dot, android.graphics.Color.parseColor("#FF9800"), 0x33FF9800)
            3 -> DangerColorConfig(R.drawable.ic_yellow_dot, android.graphics.Color.parseColor("#FFEB3B"), 0x33FFEB3B)
            2 -> DangerColorConfig(R.drawable.ic_blue_dot, android.graphics.Color.parseColor("#2196F3"), 0x332196F3)
            else -> DangerColorConfig(R.drawable.ic_green_dot, android.graphics.Color.GREEN, 0x3300FF00)
        }
    }

    private fun showAllDetectionsOnMap(
        focusLat: Double? = null,
        focusLng: Double? = null,
        focusAnimalType: String? = null,
        focusDangerLevel: Int = 1,
        focusConfidence: Float = 90f
    ) {
        val ctx = context ?: return
        // Remove only detection layer markers/circles, keeping camera layers intact!
        markers.forEach { it.remove() }
        markers.clear()
        detectionCircles.forEach { it.remove() }
        detectionCircles.clear()
        
        val historyList = com.example.animalalert.utils.PreferenceManager(ctx).getDetectionHistory()
        
        var foundFocusInHistory = false
        for (detection in historyList) {
            val lat = detection.latitude ?: continue
            val lng = detection.longitude ?: continue
            val latLng = LatLng(lat, lng)
            val dangerText = detection.getDangerLevelText()
            
            val colorConfig = getColorConfigForDangerLevel(detection.dangerLevel)
            val dotIcon = vectorToBitmap(colorConfig.drawableId)
            val circleStroke = colorConfig.strokeColor
            val circleFill = colorConfig.fillColor
            
            val isFocusMatch = focusLat != null && focusLng != null && 
                    kotlin.math.abs(lat - focusLat) < 0.0001 && 
                    kotlin.math.abs(lng - focusLng) < 0.0001
            
            if (isFocusMatch) {
                foundFocusInHistory = true
            }
            
            val marker = googleMap?.addMarker(
                MarkerOptions()
                    .position(latLng)
                    .title("Animal Detected: ${detection.animalType ?: "Unknown"}")
                    .snippet("Confidence: ${detection.confidence}% | Danger: $dangerText | Location: ${detection.location ?: "N/A"}")
                    .anchor(0.5f, 0.5f) // Center the dot
                    .icon(dotIcon)
            )
            marker?.let { 
                markers.add(it)
                if (isFocusMatch) {
                    it.showInfoWindow()
                }
            }
            
            val circle = googleMap?.addCircle(
                com.google.android.gms.maps.model.CircleOptions()
                    .center(latLng)
                    .radius(150.0)
                    .strokeWidth(2f)
                    .strokeColor(circleStroke)
                    .fillColor(circleFill)
            )
            circle?.let { detectionCircles.add(it) }
        }
        
        // If the focused coordinates were not in the saved history list, plot them dynamically!
        if (focusLat != null && focusLng != null && focusLat != 0.0 && focusLng != 0.0 && !foundFocusInHistory) {
            val latLng = LatLng(focusLat, focusLng)
            val colorConfig = getColorConfigForDangerLevel(focusDangerLevel)
            val dotIcon = vectorToBitmap(colorConfig.drawableId)
            val circleStroke = colorConfig.strokeColor
            val circleFill = colorConfig.fillColor
            
            val dangerText = when (focusDangerLevel) {
                1 -> "Low"; 2 -> "Moderate"; 3 -> "Medium"; 4 -> "High"; 5 -> "Very High"; else -> "Unknown"
            }
            
            val marker = googleMap?.addMarker(
                MarkerOptions()
                    .position(latLng)
                    .title("Animal Detected: ${focusAnimalType ?: "Unknown"}")
                    .snippet("Confidence: ${focusConfidence}% | Danger: $dangerText")
                    .anchor(0.5f, 0.5f)
                    .icon(dotIcon)
            )
            marker?.let {
                markers.add(it)
                it.showInfoWindow()
            }
            
            val circle = googleMap?.addCircle(
                com.google.android.gms.maps.model.CircleOptions()
                    .center(latLng)
                    .radius(150.0)
                    .strokeWidth(2.5f)
                    .strokeColor(circleStroke)
                    .fillColor(circleFill)
            )
            circle?.let { detectionCircles.add(it) }
        }
        
        if (focusLat != null && focusLng != null && focusLat != 0.0 && focusLng != 0.0) {
            val latLng = LatLng(focusLat, focusLng)
            googleMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15f))
            
            val dangerText = when (focusDangerLevel) {
                1 -> "Low"; 2 -> "Moderate"; 3 -> "Medium"; 4 -> "High"; 5 -> "Very High"; else -> "Unknown"
            }
            binding.tvMapSubtitle.text = "📍 Focused on: ${focusAnimalType ?: "Unknown"} (Danger: $dangerText)"
        } else if (historyList.isNotEmpty()) {
            val first = historyList.first()
            if (first.latitude != null && first.longitude != null) {
                googleMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(first.latitude, first.longitude), 12f))
                binding.tvMapSubtitle.text = "📍 Showing All Recent Detections"
            }
        }
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        
        val ctx = context ?: return
        if (ContextCompat.checkSelfPermission(
                ctx,
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
        
        ServerSyncManager.configureRetrofit(ctx)
        performMapRefresh(showAnimation = true)
        startCameraSync()

        // Check if we need to show a specific detection first
        arguments?.let { args ->
            val lat = args.getDouble("detection_lat", 0.0)
            val lng = args.getDouble("detection_lng", 0.0)
            val location = args.getString("detection_location")
            val animalType = args.getString("detection_animal_type")
            val confidence = args.getFloat("detection_confidence", 0f)
            val dangerLevel = args.getInt("detection_danger", 1)
            val triggerAnim = args.getBoolean("trigger_monitoring_animation", false)
            
            if (lat != 0.0 && lng != 0.0) {
                showAllDetectionsOnMap(lat, lng, animalType, dangerLevel, confidence)
            } else {
                fetchLatestAlert()
                startPeriodicUpdates()
            }

            if (triggerAnim) {
                val focusCam = getCameraPositions().firstOrNull()?.first
                    ?: FALLBACK_CAMERA_LOCATIONS.first()
                googleMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(focusCam, 16f))
                Toast.makeText(ctx, "Surveillance sensors activated and scanning...", Toast.LENGTH_LONG).show()
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
        val ctx = context ?: return
        if (ContextCompat.checkSelfPermission(
                ctx,
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
                if (e !is CancellationException) {
                    android.util.Log.e("MapFragment", "Failed to fetch alert: ${e.message}")
                    if (_binding != null) {
                        binding.tvMapSubtitle.text = "Offline Mode · Monitoring Active"
                    }
                }
            }
        }
    }

    private fun displayAlertOnMap(alert: AlertResponse) {
        val ctx = context ?: return
        if (_binding == null) return
        val lat = alert.latitude ?: alert.location?.split(",")?.getOrNull(0)?.trim()?.toDoubleOrNull()
        val lng = alert.longitude ?: alert.location?.split(",")?.getOrNull(1)?.trim()?.toDoubleOrNull()

        if (alert.animal_detected && lat != null && lng != null) {
            try {
                val latLng = LatLng(lat, lng)

                markers.forEach { it.remove() }
                markers.clear()
                detectionCircles.forEach { it.remove() }
                detectionCircles.clear()

                showAllDetectionsOnMap()

                val liveDanger = com.example.animalalert.model.DetectionHistory.calculateDangerLevel(alert.animal_type, alert.confidence)
                val colorConfig = getColorConfigForDangerLevel(liveDanger)
                val liveDotIcon = vectorToBitmap(colorConfig.drawableId)
                val liveCircleStroke = colorConfig.strokeColor
                val liveCircleFill = colorConfig.fillColor

                val marker = googleMap?.addMarker(
                    MarkerOptions()
                        .position(latLng)
                        .title("LIVE: ${alert.animal_type ?: "Unknown"}")
                        .snippet("Confidence: ${alert.confidence}%")
                        .anchor(0.5f, 0.5f)
                        .icon(liveDotIcon)
                )
                marker?.let { markers.add(it) }

                val liveCircle = googleMap?.addCircle(
                    com.google.android.gms.maps.model.CircleOptions()
                        .center(latLng)
                        .radius(150.0)
                        .strokeWidth(3f)
                        .strokeColor(liveCircleStroke)
                        .fillColor(liveCircleFill)
                )
                liveCircle?.let { detectionCircles.add(it) }

                googleMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15f))
                marker?.showInfoWindow()

                binding.tvMapSubtitle.text = "Animal detected: ${alert.animal_type ?: "Unknown"}"
            } catch (e: Exception) {
                if (e !is CancellationException) {
                    context?.let { Toast.makeText(it, "Invalid location format", Toast.LENGTH_SHORT).show() }
                }
            }
        } else if (alert.animal_detected) {
            binding.tvMapSubtitle.text = "Animal detected but location not available"
        } else {
            updateMapHeader()
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

    private suspend fun loadCamerasFromServer(): Boolean {
        return try {
            val resp = withContext(Dispatchers.IO) {
                RetrofitClient.api.getCameras().execute()
            }
            if (resp.isSuccessful) {
                val list = resp.body() ?: emptyList()
                withContext(Dispatchers.Main) {
                    cameraData.clear()
                    cameraData.addAll(list)
                }
                true
            } else {
                false
            }
        } catch (e: Exception) {
            if (e !is CancellationException) {
                android.util.Log.w("MapFragment", "Failed to load cameras: ${e.message}")
            }
            false
        }
    }

    private fun performMapRefresh(showAnimation: Boolean) {
        if (showAnimation && _binding != null) {
            binding.mapSwipeRefresh.isRefreshing = true
        }
        scope.launch {
            loadCamerasFromServer()
            if (_binding != null && googleMap != null) {
                plotCameras()
                updateMapHeader()
            }
            fetchLatestAlert()
            if (_binding != null) {
                binding.mapSwipeRefresh.isRefreshing = false
            }
        }
    }

    private fun updateMapHeader() {
        val b = _binding ?: return
        val city = cameraData.mapNotNull { it.deploymentCity() }.firstOrNull() ?: "Surveillance"
        val activeCount = cameraData.count { it.status != "offline" }
        val total = cameraData.size
        val primary = cameraData.count { it.is_primary == true }

        b.tvStatus.text = "$city — camera map"
        b.tvMapSubtitle.text = if (total > 0) {
            "$total cameras · $activeCount active · $primary primary · $city"
        } else {
            "No cameras on server · check connection"
        }

        b.tvCameraLegend.text = if (cameraData.isNotEmpty()) {
            cameraData.sortedBy { it.camera_number ?: Int.MAX_VALUE }
                .joinToString("  ") { cam ->
                    val idx = cam.camera_number ?: (cameraData.indexOf(cam) + 1)
                    cam.displayTitle(idx)
                }
        } else {
            ""
        }
    }

    private fun startCameraSync() {
        cameraSyncJob?.cancel()
        cameraSyncJob = scope.launch {
            while (isActive) {
                delay(30_000)
                if (_binding != null && googleMap != null) {
                    performMapRefresh(showAnimation = true)
                }
            }
        }
    }

    private fun parseLatLng(location: String): LatLng? {
        val parts = location.split(",")
        if (parts.size != 2) return null
        return try {
            LatLng(parts[0].trim().toDouble(), parts[1].trim().toDouble())
        } catch (_: Exception) {
            null
        }
    }

    private fun getCameraPositions(): List<Pair<LatLng, CameraInfo?>> {
        val fromServer = cameraData.mapNotNull { cam ->
            parseLatLng(cam.location)?.let { pos -> pos to cam }
        }
        if (fromServer.isNotEmpty()) return fromServer

        return FALLBACK_CAMERA_LOCATIONS.map { it to null }
    }

    private fun plotCameras() {
        val map = googleMap ?: return
        val ctx = context ?: return

        cameraMarkers.forEach { it.remove() }
        cameraMarkers.clear()

        updateMapHeader()

        val positions = getCameraPositions()
        for ((index, pair) in positions.withIndex()) {
            val (latLng, info) = pair
            val fallbackNum = index + 1
            val status = info?.status?.takeIf { !it.isNullOrBlank() } ?: "active"
            val offline = status == "offline"
            val isPrimary = info?.is_primary == true
            val pinStyle = MapPinHelper.pinStyleFor(status, isPrimary)
            val pinNumber = info?.displayNumber(fallbackNum) ?: fallbackNum

            val title = info?.displayTitle(fallbackNum)
                ?: "#$pinNumber Camera $fallbackNum"
            val snippet = info?.displaySnippet(fallbackNum)
                ?: "#$pinNumber · ${status.uppercase()}"

            val pinIcon = MapPinHelper.createNumberedPin(ctx, pinNumber, pinStyle)
            val marker = map.addMarker(
                MarkerOptions()
                    .position(latLng)
                    .title(if (offline) "⏸ $title" else title)
                    .snippet(snippet)
                    .icon(pinIcon)
                    .anchor(0.5f, 0.5f)
                    .alpha(if (offline) 0.75f else 1f)
            )
            marker?.let { cameraMarkers.add(it) }
        }

        startRadarScanAnimation()
    }

    private fun startRadarScanAnimation() {
        scanJob?.cancel()
        cameraCircles.forEach { it.remove() }
        cameraCircles.clear()

        val positions = getCameraPositions()
        for ((loc, info) in positions) {
            val pinStyle = MapPinHelper.pinStyleFor(info?.status, info?.is_primary == true)
            val offline = info?.status == "offline"
            val stroke = MapPinHelper.colorFor(pinStyle)
            val fill = (stroke and 0x00FFFFFF) or 0x1A000000
            val circle = googleMap?.addCircle(
                com.google.android.gms.maps.model.CircleOptions()
                    .center(loc)
                    .radius(if (offline) 35.0 else 50.0)
                    .strokeWidth(2.5f)
                    .strokeColor(stroke)
                    .fillColor(fill)
            )
            circle?.let { cameraCircles.add(it) }
        }

        // Coroutine to pulse the circles
        scanJob = scope.launch {
            var offset = 0.0
            while (isActive) {
                offset = (offset + 6.0) % 150.0
                val radius = 50.0 + offset
                val alphaPercent = (1.0 - (offset / 150.0)).coerceIn(0.0, 1.0)
                
                val fillAlphaHex = String.format("%02X", (alphaPercent * 25).toInt())
                val strokeAlphaHex = String.format("%02X", (alphaPercent * 200).toInt())
                
                val fillColor = android.graphics.Color.parseColor("#${fillAlphaHex}00C853")
                val strokeColor = android.graphics.Color.parseColor("#${strokeAlphaHex}00C853")
                
                withContext(Dispatchers.Main) {
                    for (circle in cameraCircles) {
                        circle.radius = radius
                        circle.fillColor = fillColor
                        circle.strokeColor = strokeColor
                    }
                }
                delay(60) // High frame rate for butter-smooth scanning pulses!
            }
        }
    }

    /** Called from toolbar refresh when the map tab is visible. */
    fun refreshFromToolbar() {
        performMapRefresh(showAnimation = true)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        scanJob?.cancel()
        cameraSyncJob?.cancel()
        job.cancel()
        _binding = null
    }
}

