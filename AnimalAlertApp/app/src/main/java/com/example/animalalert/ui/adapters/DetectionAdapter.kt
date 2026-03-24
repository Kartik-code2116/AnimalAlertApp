package com.example.animalalert.ui.adapters

import android.location.Address
import android.location.Geocoder
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView
import com.example.animalalert.R
import com.example.animalalert.model.DetectionHistory
import java.text.SimpleDateFormat
import java.util.*

class DetectionAdapter(
    private var detections: List<DetectionHistory>,
    private val onItemClick: (DetectionHistory) -> Unit,
    private val onDeleteClick: (DetectionHistory) -> Unit = {}
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
        private val cardView: CardView = itemView.findViewById(R.id.cardDetection)
        private val tvAnimalType: TextView = itemView.findViewById(R.id.tvAnimalType)
        private val tvConfidence: TextView = itemView.findViewById(R.id.tvConfidence)
        private val tvLocation: TextView = itemView.findViewById(R.id.tvLocation)
        private val tvTime: TextView = itemView.findViewById(R.id.tvTime)
        private val tvDangerLevel: TextView = itemView.findViewById(R.id.tvDangerLevel)
        private val btnDelete: android.widget.ImageButton? = itemView.findViewById(R.id.btnDelete)

        fun bind(detection: DetectionHistory) {
            tvAnimalType.text = detection.animalType ?: "Unknown Animal"
            tvConfidence.text = "Confidence: ${detection.confidence}%"
            
            // Try to get a readable address if coordinates are provided
            val displayLocation = if (detection.latitude != null && detection.longitude != null) {
                try {
                    val geocoder = Geocoder(itemView.context, Locale.getDefault())
                    val addresses = geocoder.getFromLocation(detection.latitude, detection.longitude, 1)
                    if (addresses != null && addresses.isNotEmpty()) {
                        val address = addresses[0]
                        "${address.locality ?: ""}, ${address.adminArea ?: ""}".trim { it <= ' ' || it == ',' }
                    } else {
                        detection.location ?: "Location: N/A"
                    }
                } catch (e: Exception) {
                    detection.location ?: "Location: N/A"
                }
            } else {
                detection.location ?: "Location: N/A"
            }
            
            tvLocation.text = displayLocation
            
            val dateFormat = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
            val normalizedTime = if (detection.timestamp < 1000000000000L) detection.timestamp * 1000 else detection.timestamp
            tvTime.text = dateFormat.format(Date(normalizedTime))
            
            tvDangerLevel.text = "Danger: ${detection.getDangerLevelText()}"
            tvDangerLevel.setTextColor(detection.getDangerColor())
            
            // Set card background color based on danger level
            val alphaColor = (detection.getDangerColor() and 0x00FFFFFF) or 0x10000000
            cardView.setCardBackgroundColor(alphaColor)
            
            itemView.setOnClickListener {
                onItemClick(detection)
            }
            
            btnDelete?.setOnClickListener {
                onDeleteClick(detection)
            }
        }
    }
}


