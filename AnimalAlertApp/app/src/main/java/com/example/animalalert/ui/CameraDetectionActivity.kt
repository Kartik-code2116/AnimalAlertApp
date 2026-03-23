package com.example.animalalert.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.util.Log
import androidx.camera.core.ImageProxy
import androidx.core.app.ActivityCompat
import com.example.animalalert.R
import com.example.animalalert.databinding.ActivityCameraDetectionBinding
import com.example.animalalert.ml.YOLOv8Detector
import com.example.animalalert.model.DetectionHistory
import com.example.animalalert.utils.PreferenceManager
import kotlinx.coroutines.*
import java.io.ByteArrayOutputStream
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CameraDetectionActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCameraDetectionBinding
    private var imageCapture: ImageCapture? = null
    private var imageAnalyzer: ImageAnalysis? = null
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var detector: YOLOv8Detector
    private lateinit var preferenceManager: PreferenceManager
    private var lastDetectionTime: Long = 0
    private val detectionCooldown = 5000L // 5 seconds between detections

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCameraDetectionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        preferenceManager = PreferenceManager(this)
        cameraExecutor = Executors.newSingleThreadExecutor()

        // Initialize detector
        detector = YOLOv8Detector(this)
        if (!detector.loadModel()) {
            val errorMsg = """
                Failed to load YOLOv8 model.
                
                Possible issues:
                1. Model file not in assets folder
                2. Model needs TorchScript conversion
                3. Check Logcat for details
                
                See README_MODEL_SETUP.md for help.
            """.trimIndent()
            Toast.makeText(this, "Model load failed. Check Logcat.", Toast.LENGTH_LONG).show()
            Log.e(TAG, errorMsg)
        } else {
            Toast.makeText(this, "Model loaded successfully!", Toast.LENGTH_SHORT).show()
        }

        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(
                this,
                REQUIRED_PERMISSIONS,
                REQUEST_CODE_PERMISSIONS
            )
        }

        binding.btnBack.setOnClickListener {
            finish()
        }

        binding.btnToggleDetection.setOnClickListener {
            toggleDetection()
        }
    }

    private fun toggleDetection() {
        if (imageAnalyzer == null) {
            startImageAnalysis()
            binding.btnToggleDetection.text = "Stop Detection"
            binding.tvStatus.text = "🔴 Detection Active"
        } else {
            stopImageAnalysis()
            binding.btnToggleDetection.text = "Start Detection"
            binding.tvStatus.text = "⚪ Detection Stopped"
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(binding.previewView.surfaceProvider)
                }

            imageCapture = ImageCapture.Builder().build()

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this as LifecycleOwner,
                    cameraSelector,
                    preview,
                    imageCapture
                )
            } catch (exc: Exception) {
                Toast.makeText(this, "Camera initialization failed: ${exc.message}", Toast.LENGTH_SHORT).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun startImageAnalysis() {
        imageAnalyzer = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .also {
                it.setAnalyzer(cameraExecutor) { imageProxy ->
                    processImage(imageProxy)
                }
            }

        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.bindToLifecycle(
                    this,
                    cameraSelector,
                    imageAnalyzer
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error binding image analyzer: ${e.message}")
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun stopImageAnalysis() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            cameraProvider.unbind(imageAnalyzer)
            imageAnalyzer = null
        }, ContextCompat.getMainExecutor(this))
    }

    private fun processImage(imageProxy: ImageProxy) {
        try {
            val bitmap = imageProxy.toBitmap()
            val detections = detector.detect(bitmap)

            runOnUiThread {
                if (detections.isNotEmpty()) {
                    val currentTime = System.currentTimeMillis()
                    if (currentTime - lastDetectionTime > detectionCooldown) {
                        handleDetections(detections)
                        lastDetectionTime = currentTime
                    }
                } else {
                    binding.tvDetectionInfo.text = "No animals detected"
                }
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error processing image: ${e.message}")
        } finally {
            imageProxy.close()
        }
    }

    private fun handleDetections(detections: List<YOLOv8Detector.Detection>) {
        val bestDetection = detections.first()
        
        binding.tvDetectionInfo.text = """
            Animal: ${bestDetection.className}
            Confidence: ${(bestDetection.confidence * 100).toInt()}%
        """.trimIndent()

        // Save to detection history
        val detection = DetectionHistory(
            id = UUID.randomUUID().toString(),
            animalType = bestDetection.className,
            confidence = bestDetection.confidence * 100,
            location = getCurrentLocationString(),
            latitude = null, // Could get from GPS
            longitude = null,
            timestamp = System.currentTimeMillis(),
            dangerLevel = DetectionHistory.calculateDangerLevel(bestDetection.className, bestDetection.confidence * 100)
        )

        preferenceManager.addDetectionHistory(detection)

        // Trigger alert if enabled
        if (preferenceManager.isNotificationEnabled()) {
            // You can trigger the alert service here
            Toast.makeText(this, "Animal detected: ${bestDetection.className}!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun getCurrentLocationString(): String {
        // In a real implementation, get from GPS
        return "Camera Location"
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                Toast.makeText(this, "Camera permission denied", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        detector.release()
    }

    companion object {
        private const val TAG = "CameraDetection"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }

    private fun ImageProxy.toBitmap(): Bitmap {
        val yBuffer = planes[0].buffer
        val uBuffer = planes[1].buffer
        val vBuffer = planes[2].buffer

        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()

        val nv21 = ByteArray(ySize + uSize + vSize)

        yBuffer.get(nv21, 0, ySize)
        vBuffer.get(nv21, ySize, vSize)
        uBuffer.get(nv21, ySize + vSize, uSize)

        val yuvImage = YuvImage(nv21, ImageFormat.NV21, this.width, this.height, null)
        val out = ByteArrayOutputStream()
        yuvImage.compressToJpeg(Rect(0, 0, this.width, this.height), 50, out)
        val imageBytes = out.toByteArray()
        return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
    }
}

