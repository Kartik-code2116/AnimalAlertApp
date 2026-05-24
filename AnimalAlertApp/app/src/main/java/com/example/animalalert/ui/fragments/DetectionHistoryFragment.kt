package com.example.animalalert.ui.fragments

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.animalalert.R
import com.example.animalalert.databinding.FragmentDetectionHistoryBinding
import com.example.animalalert.model.DetectionHistory
import com.example.animalalert.ui.adapters.DetectionAdapter
import com.example.animalalert.utils.PreferenceManager
import com.example.animalalert.utils.ServerSyncManager
import kotlinx.coroutines.launch
import java.util.Locale

class DetectionHistoryFragment : Fragment() {

    private var _binding: FragmentDetectionHistoryBinding? = null
    private val binding get() = _binding!!
    private lateinit var preferenceManager: PreferenceManager
    private lateinit var adapter: DetectionAdapter
    private var allHistory = listOf<DetectionHistory>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDetectionHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        preferenceManager = PreferenceManager(requireContext())

        setupViews()
        setupListeners()
        loadHistoryData()
    }

    private fun setupViews() {
        binding.swipeRefreshLayout.setColorSchemeResources(R.color.accent)
        binding.swipeRefreshLayout.setProgressBackgroundColorSchemeResource(R.color.bg_card2)

        adapter = DetectionAdapter(
            detections = emptyList(),
            onItemClick = { detection ->
                showCameraFeedDialog(detection)
            },
            onDeleteClick = { detection ->
                preferenceManager.removeDetectionHistory(detection.id)
                loadHistoryData()
                Toast.makeText(requireContext(), "Detection deleted", Toast.LENGTH_SHORT).show()
            },
            onIconClick = { detection ->
                showCameraFeedDialog(detection)
            },
            showDeleteButton = true
        )

        binding.recyclerViewHistory.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerViewHistory.adapter = adapter
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        binding.btnClearAll.setOnClickListener {
            showClearAllConfirmationDialog()
        }

        binding.swipeRefreshLayout.setOnRefreshListener {
            syncHistoryFromServer()
        }

        binding.btnClearSearch.setOnClickListener {
            binding.etSearch.text?.clear()
        }

        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s?.toString()?.trim() ?: ""
                binding.btnClearSearch.visibility = if (query.isNotEmpty()) View.VISIBLE else View.GONE
                filterHistory(query)
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun loadHistoryData() {
        allHistory = preferenceManager.getDetectionHistory()
        val query = binding.etSearch.text?.toString()?.trim() ?: ""
        filterHistory(query)
    }

    private fun filterHistory(query: String) {
        val filtered = if (query.isEmpty()) {
            allHistory
        } else {
            allHistory.filter {
                it.animalType?.lowercase(Locale.getDefault())?.contains(query.lowercase(Locale.getDefault())) == true
            }
        }

        adapter.updateData(filtered)

        if (filtered.isEmpty()) {
            binding.recyclerViewHistory.visibility = View.GONE
            binding.layoutEmpty.visibility = View.VISIBLE
        } else {
            binding.recyclerViewHistory.visibility = View.VISIBLE
            binding.layoutEmpty.visibility = View.GONE
        }
    }

    private fun syncHistoryFromServer() {
        lifecycleScope.launch {
            binding.swipeRefreshLayout.isRefreshing = true
            val added = ServerSyncManager.syncHistoryFromServer(requireContext())
            binding.swipeRefreshLayout.isRefreshing = false
            loadHistoryData()
            if (added > 0) {
                Toast.makeText(requireContext(), "Synced $added new detections", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showClearAllConfirmationDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("Clear History")
            .setMessage("Are you sure you want to permanently clear all local detection history?")
            .setPositiveButton("Clear") { _, _ ->
                preferenceManager.clearDetectionHistory()
                loadHistoryData()
                Toast.makeText(requireContext(), "History cleared", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showCameraFeedDialog(detection: DetectionHistory) {
        val context = requireContext()
        val dialogView = layoutInflater.inflate(R.layout.dialog_camera_feed, null)
        
        val dialog = AlertDialog.Builder(context)
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
        val camName = cameraLabelForDetection(detection.id)
        if (lat != null && lng != null && lat != 0.0 && lng != 0.0) {
            tvDialogCoords.text = "📍 Coordinates: %.4f, %.4f (%s)".format(lat, lng, camName)
        } else {
            tvDialogCoords.text = "📍 Location: ${detection.location ?: "N/A"}"
        }

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
            try {
                val act = requireActivity() as com.example.animalalert.ui.MainActivity
                act.binding.bottomNavigation.selectedItemId = R.id.nav_map
            } catch (_: Exception) {}
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

    private fun cameraLabelForDetection(detectionId: String): String {
        val cam = (kotlin.math.abs(detectionId.hashCode()) % 3) + 1
        return "#$cam Camera"
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
