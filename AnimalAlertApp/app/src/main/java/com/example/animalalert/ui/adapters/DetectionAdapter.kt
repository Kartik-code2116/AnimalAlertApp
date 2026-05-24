package com.example.animalalert.ui.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView
import com.example.animalalert.R
import com.example.animalalert.model.DetectionHistory
import java.util.*

class DetectionAdapter(
    private var detections: List<DetectionHistory>,
    private val onItemClick: (DetectionHistory) -> Unit,
    private val onDeleteClick: (DetectionHistory) -> Unit = {},
    private val onIconClick: (DetectionHistory) -> Unit = {},
    private val showDeleteButton: Boolean = false
) : RecyclerView.Adapter<DetectionAdapter.DetectionViewHolder>() {

    fun updateData(newDetections: List<DetectionHistory>) {
        val diffCallback = object : androidx.recyclerview.widget.DiffUtil.Callback() {
            override fun getOldListSize() = detections.size
            override fun getNewListSize() = newDetections.size

            override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                return detections[oldItemPosition].id == newDetections[newItemPosition].id
            }

            override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                return detections[oldItemPosition] == newDetections[newItemPosition]
            }
        }
        val diffResult = androidx.recyclerview.widget.DiffUtil.calculateDiff(diffCallback)
        detections = newDetections
        diffResult.dispatchUpdatesTo(this)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DetectionViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_detection, parent, false)
        return DetectionViewHolder(view)
    }

    override fun onBindViewHolder(holder: DetectionViewHolder, position: Int) {
        holder.bind(detections[position])
    }

    override fun getItemCount(): Int = detections.size

    inner class DetectionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val cardView: View = itemView.findViewById(R.id.cardDetection)
        private val detIconContainer: View = itemView.findViewById(R.id.detIconContainer)
        private val tvDetIcon: TextView = itemView.findViewById(R.id.tvDetIcon)
        private val tvAnimalType: TextView = itemView.findViewById(R.id.tvAnimalType)
        private val tvConfidence: TextView = itemView.findViewById(R.id.tvConfidence)
        private val tvLocation: TextView = itemView.findViewById(R.id.tvLocation)
        private val tvTime: TextView = itemView.findViewById(R.id.tvTime)
        private val tvDangerLevel: TextView = itemView.findViewById(R.id.tvDangerLevel)
        private val btnDelete: android.widget.ImageButton? = itemView.findViewById(R.id.btnDelete)

        fun bind(detection: DetectionHistory) {
            val animal = detection.animalType ?: "Unknown"
            tvAnimalType.text = animal
            tvDetIcon.text = iconForAnimal(animal)

            // HTML-like meta line: relative time + Cam-XX (camera id not available yet).
            val relativeTime = relativeTimeAgo(detection.timestamp)
            tvTime.text = relativeTime
            tvLocation.text = cameraPlaceholder(detection.id)

            // Hidden in the updated layout, but keep for completeness/debug.
            tvConfidence.text = "Confidence: ${detection.confidence}%"

            // HTML-like badge: LV 1..5
            tvDangerLevel.text = "LV ${detection.dangerLevel}"
            val dangerColor = detection.getDangerColor()
            tvDangerLevel.setTextColor(dangerColor)

            val badgeBg = (dangerColor and 0x00FFFFFF) or 0x22000000 // semi-transparent
            tvDangerLevel.setBackgroundColor(badgeBg)

            // Set overall card tint.
            val cardBg = (dangerColor and 0x00FFFFFF) or 0x11000000 // subtle tint
            cardView.setBackgroundColor(cardBg)

            // Tint the icon container slightly as well.
            tvDetIcon.setBackgroundColor(cardBg)
            
            itemView.setOnClickListener {
                onItemClick(detection)
            }
            
            detIconContainer.setOnClickListener {
                onIconClick(detection)
            }
            
            btnDelete?.visibility = if (showDeleteButton) View.VISIBLE else View.GONE
            btnDelete?.setOnClickListener {
                onDeleteClick(detection)
            }
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

    private fun cameraPlaceholder(detectionId: String): String {
        // Stable placeholder mapping until the backend/model includes camera_id.
        val cam = (kotlin.math.abs(detectionId.hashCode()) % 3) + 1 // 1..3
        return "Cam-%02d".format(cam)
    }

    private fun iconForAnimal(animal: String): String {
        val type = animal.lowercase(Locale.getDefault())
        return when {
            type.contains("bear") -> "🐻"
            type.contains("wolf") -> "🐺"
            type.contains("lion") -> "🦁"
            type.contains("tiger") -> "🐯"
            type.contains("snake") -> "🐍"
            type.contains("cobra") -> "🐍"
            type.contains("fox") -> "🦊"
            type.contains("rabbit") -> "🐰"
            type.contains("deer") -> "🦌"
            type.contains("wolf") -> "🐺"
            else -> "🐾"
        }
    }
}


