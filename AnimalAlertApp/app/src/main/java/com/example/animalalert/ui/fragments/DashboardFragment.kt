package com.example.animalalert.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.animalalert.R
import com.example.animalalert.databinding.FragmentDashboardBinding
import com.example.animalalert.model.DetectionHistory
import com.example.animalalert.model.AlertResponse
import com.example.animalalert.network.RetrofitClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.withContext
import java.util.Locale

class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!
    private lateinit var detectionAdapter: com.example.animalalert.ui.adapters.DetectionAdapter
    private lateinit var preferenceManager: com.example.animalalert.utils.PreferenceManager
    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.Main + job)

    private var currentActiveCount: Int = 0

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
        loadRecentDetections()
        updateHeaderFromPrefs()
        updateStats(activeCount = 0)
        fetchLatestAlertAndUpdateActiveBanner()
    }

    private fun setupViews() {
        binding.swipeRefreshLayout.setColorSchemeResources(R.color.accent)
        binding.swipeRefreshLayout.setProgressBackgroundColorSchemeResource(R.color.bg_card2)
        binding.swipeRefreshLayout.setOnRefreshListener {
            refreshData()
        }

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
                updateStats(activeCount = currentActiveCount)
                Toast.makeText(requireContext(), "Detection deleted", Toast.LENGTH_SHORT).show()
            },
            onIconClick = { detection ->
                showCameraFeedDialog(detection)
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

    private fun updateHeaderFromPrefs() {
        val b = _binding ?: return
        // Header elements are part of the HTML phone mockup.
        val name = preferenceManager.getUserName().ifEmpty { "User" }
        val greeting = getTimeBasedGreeting()
        b.tvUserName.text = "$name 👋"
        b.tvTitle.text = greeting
        b.tvLiveBadge.text = "SYSTEM ACTIVE · 3 CAMERAS"
    }

    private fun getTimeBasedGreeting(): String {
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        return when (hour) {
            in 5..11 -> "Good Morning,"
            in 12..17 -> "Good Afternoon,"
            in 18..21 -> "Good Evening,"
            else -> "Good Night,"
        }
    }

    private fun updateStats(activeCount: Int) {
        val b = _binding ?: return
        val today = preferenceManager.getTodayDetections()
        val total = preferenceManager.getTotalDetections()
        currentActiveCount = activeCount

        animateCounter(b.tvDetectionsToday, today, 800)
        animateCounter(b.tvActiveAlerts, activeCount, 400)
        animateCounter(b.tvAccuracy, total, 800)
    }

    private fun loadRecentDetections() {
        val b = _binding ?: return
        val detections = preferenceManager.getDetectionHistory().take(3)
        if (detections.isEmpty()) {
            b.recyclerViewDashboardDetections.visibility = View.GONE
            b.tvEmptyRecent.visibility = View.VISIBLE
        } else {
            b.recyclerViewDashboardDetections.visibility = View.VISIBLE
            b.tvEmptyRecent.visibility = View.GONE
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

    private fun fetchLatestAlertAndUpdateActiveBanner() {
        scope.launch {
            val alert: AlertResponse? = try {
                withContext(Dispatchers.IO) {
                    val response = RetrofitClient.api.getLatestAlert().execute()
                    if (response.isSuccessful) response.body() else null
                }
            } catch (_: Exception) {
                null
            }

            val b = _binding ?: return@launch
            if (alert?.animal_detected == true && DetectionHistory.isWildAnimal(alert.animal_type)) {
                currentActiveCount = 1
                val dangerLevel = DetectionHistory.calculateDangerLevel(alert.animal_type, alert.confidence)

                b.cardActiveAlert.visibility = View.VISIBLE
                b.tvActiveAlertLabel.text = "⚠ Active Alert"
                b.tvActiveAlertAnimal.text = "${iconForAnimal(alert.animal_type)} ${alert.animal_type ?: "Unknown"}"
                b.tvActiveAlertConfidence.text =
                    "Confidence: ${alert.confidence}% · Danger Level $dangerLevel"

                // Load dynamic live photo preview
                if (!alert.image.isNullOrEmpty()) {
                    val bitmap = decodeBase64ToBitmap(alert.image)
                    if (bitmap != null) {
                        b.ivDashboardLiveAlertPhoto.setImageBitmap(bitmap)
                        b.cardDashboardLiveImageContainer.visibility = View.VISIBLE
                    } else {
                        b.cardDashboardLiveImageContainer.visibility = View.GONE
                    }
                } else {
                    b.cardDashboardLiveImageContainer.visibility = View.GONE
                }

                val location = alert.location ?: "Unknown"
                val relative = relativeTimeAgo(alert.timestamp)
                b.tvActiveAlertLocation.text = "📍 $location · $relative"

                updateStats(activeCount = 1)
            } else {
                b.cardActiveAlert.visibility = View.GONE
                updateStats(activeCount = 0)
            }
            b.swipeRefreshLayout.isRefreshing = false
        }
    }

    private fun relativeTimeAgo(timestamp: Long): String {
        val normalizedTimestamp = if (timestamp < 1000000000000L) timestamp * 1000 else timestamp
        val diffMs = System.currentTimeMillis() - normalizedTimestamp
        if (diffMs < 0) return "now"

        val minutes = diffMs / 60000L
        if (minutes < 1) return "just now"
        if (minutes < 60) return "${minutes} min ago"

        val hours = minutes / 60
        if (hours < 24) return "${hours} hr ago"

        val days = hours / 24
        return "${days} d ago"
    }

    private fun iconForAnimal(animalType: String?): String {
        val type = animalType?.lowercase(Locale.getDefault()) ?: ""
        return when {
            type.contains("bear") -> "🐻"
            type.contains("wolf") -> "🐺"
            type.contains("lion") -> "🦁"
            type.contains("tiger") -> "🐯"
            type.contains("snake") || type.contains("cobra") -> "🐍"
            type.contains("fox") -> "🦊"
            type.contains("rabbit") -> "🐰"
            type.contains("deer") -> "🦌"
            else -> "🐾"
        }
    }

    private fun handleQuickActionClick(view: View) {
        when (view.id) {
            R.id.card_start_monitoring -> {
                try {
                    val activity = requireActivity() as com.example.animalalert.ui.MainActivity
                    activity.triggerMapAnimation = true
                    activity.binding.bottomNavigation.selectedItemId = R.id.nav_map
                } catch (e: Exception) {
                    Toast.makeText(requireContext(), "Failed to launch scanner", Toast.LENGTH_SHORT).show()
                }
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
        loadRecentDetections()
        updateStats(activeCount = currentActiveCount)
        fetchLatestAlertAndUpdateActiveBanner()
    }

    private fun showCameraFeedDialog(detection: DetectionHistory) {
        val context = requireContext()
        val dialogView = layoutInflater.inflate(R.layout.dialog_camera_feed, null)
        
        val dialog = androidx.appcompat.app.AlertDialog.Builder(context)
            .setView(dialogView)
            .create()
            
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        
        val ivAnimalPhoto = dialogView.findViewById<android.widget.ImageView>(R.id.ivAnimalPhoto)
        val tvCameraWatermark = dialogView.findViewById<android.widget.TextView>(R.id.tvCameraWatermark)
        val tvTimeWatermark = dialogView.findViewById<android.widget.TextView>(R.id.tvTimeWatermark)
        val tvTargetName = dialogView.findViewById<android.widget.TextView>(R.id.tvTargetName)
        val tvDialogAnimalName = dialogView.findViewById<android.widget.TextView>(R.id.tvDialogAnimalName)
        val tvDialogCoords = dialogView.findViewById<android.widget.TextView>(R.id.tvDialogCoords)
        val tvDialogConfidence = dialogView.findViewById<android.widget.TextView>(R.id.tvDialogConfidence)
        val dialogConfidenceProgress = dialogView.findViewById<android.widget.ProgressBar>(R.id.dialogConfidenceProgress)
        val dialogDangerBadge = dialogView.findViewById<android.widget.TextView>(R.id.dialogDangerBadge)
        val dialogSubtitle = dialogView.findViewById<android.widget.TextView>(R.id.dialogSubtitle)
        val btnDialogClose = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnDialogClose)
        val btnDialogMap = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnDialogMap)
        val viewLivePulse = dialogView.findViewById<android.view.View>(R.id.viewLivePulse)
        val layoutTargetBox = dialogView.findViewById<android.view.View>(R.id.layoutTargetBox)

        val animalName = detection.animalType ?: "Unknown"
        tvDialogAnimalName.text = "${iconForAnimal(animalName)} $animalName Detected"
        tvTargetName.text = "${animalName.uppercase(Locale.getDefault())} [${detection.confidence.toInt()}%]"
        
        val lat = detection.latitude
        val lng = detection.longitude
        if (lat != null && lng != null && lat != 0.0 && lng != 0.0) {
            val camName = cameraPlaceholder(detection.id)
            tvDialogCoords.text = "📍 Coordinates: %.4f, %.4f (%s)".format(lat, lng, camName)
        } else {
            tvDialogCoords.text = "📍 Location: ${detection.location ?: "N/A"}"
        }
        
        val camName = cameraPlaceholder(detection.id)
        dialogSubtitle.text = "Camera: $camName  ·  Live Capture"
        tvCameraWatermark.text = "$camName [LIVE]"
        
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        tvTimeWatermark.text = sdf.format(java.util.Date(detection.timestamp))
        
        tvDialogConfidence.text = "${detection.confidence.toInt()}%"
        dialogConfidenceProgress.progress = detection.confidence.toInt()
        
        val dangerColor = detection.getDangerColor()
        dialogConfidenceProgress.progressTintList = android.content.res.ColorStateList.valueOf(dangerColor)
        
        dialogDangerBadge.text = "LV ${detection.dangerLevel}"
        dialogDangerBadge.setTextColor(dangerColor)
        dialogDangerBadge.setBackgroundColor((dangerColor and 0x00FFFFFF) or 0x22000000)

        // Bounding box setup - set target box boundary stroke color programmatically
        val boxDrawable = layoutTargetBox.background as? android.graphics.drawable.GradientDrawable
        boxDrawable?.mutate()
        boxDrawable?.setStroke((2 * resources.displayMetrics.density).toInt(), dangerColor)
        boxDrawable?.setColor(android.graphics.Color.TRANSPARENT)
        tvTargetName.setTextColor(dangerColor)

        val blinkAnim = android.view.animation.AlphaAnimation(0.2f, 1.0f).apply {
            duration = 600
            repeatMode = android.view.animation.Animation.REVERSE
            repeatCount = android.view.animation.Animation.INFINITE
        }
        viewLivePulse.startAnimation(blinkAnim)

        if (!detection.image.isNullOrEmpty()) {
            val bitmap = decodeBase64ToBitmap(detection.image)
            if (bitmap != null) {
                ivAnimalPhoto.setImageBitmap(bitmap)
            } else {
                ivAnimalPhoto.setImageResource(R.drawable.wildtrack5_o)
            }
        } else {
            ivAnimalPhoto.setImageResource(R.drawable.wildtrack5_o)
        }

        btnDialogClose.setOnClickListener {
            dialog.dismiss()
        }

        btnDialogMap.setOnClickListener {
            dialog.dismiss()
            navigateToTab(R.id.nav_map)
        }

        dialog.show()
    }

    private fun decodeBase64ToBitmap(base64Str: String): android.graphics.Bitmap? {
        return try {
            val decodedBytes = android.util.Base64.decode(base64Str, android.util.Base64.DEFAULT)
            android.graphics.BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
        } catch (e: Exception) {
            null
        }
    }

    private fun cameraPlaceholder(detectionId: String): String {
        val cam = (kotlin.math.abs(detectionId.hashCode()) % 3) + 1
        return "Cam-%02d".format(cam)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        job.cancel()
    }
}