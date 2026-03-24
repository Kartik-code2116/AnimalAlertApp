package com.example.animalalert.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.location.Location
import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.animalalert.R
import com.example.animalalert.databinding.ActivityCameraDetectionBinding
import com.example.animalalert.model.CameraDetectRequest
import com.example.animalalert.model.CameraRegisterRequest
import com.example.animalalert.model.DetectionHistory
import com.example.animalalert.network.RetrofitClient
import com.example.animalalert.utils.PreferenceManager
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
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
    private lateinit var preferenceManager: PreferenceManager
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var currentLocation: Location? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var isDetecting = false
    private var backendHealthy = false
    private val cameraId = "android_cam_1"
    private var lastDetectionTime: Long = 0
    private val detectionCooldown = 1200L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCameraDetectionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        preferenceManager = PreferenceManager(this)
        cameraExecutor = Executors.newSingleThreadExecutor()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        fetchCurrentLocation()

        binding.btnToggleDetection.isEnabled = false
        checkBackendAndRegisterCamera()

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
        if (!backendHealthy) {
            Toast.makeText(this, "Backend unavailable. Start Flask server first.", Toast.LENGTH_SHORT).show()
            return
        }
        if (!isDetecting) {
            startImageAnalysis()
            binding.btnToggleDetection.text = "Stop Detection"
            binding.tvStatus.text = "🔴 Detection Active"
            isDetecting = true
        } else {
            stopImageAnalysis()
            binding.btnToggleDetection.text = "Start Detection"
            binding.tvStatus.text = "⚪ Detection Stopped"
            isDetecting = false
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
                    this,
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
                cameraProvider.unbind(imageAnalyzer)
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
            imageAnalyzer?.let { analyzer -> cameraProvider.unbind(analyzer) }
            imageAnalyzer = null
        }, ContextCompat.getMainExecutor(this))
    }

    private fun processImage(imageProxy: ImageProxy) {
        try {
            val bitmap = imageProxy.toBitmap()
            if (bitmap.width == 0 || bitmap.height == 0) {
                return
            }
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastDetectionTime > detectionCooldown) {
                lastDetectionTime = currentTime
                sendFrameToBackend(bitmap)
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error processing image: ${e.message}")
        } finally {
            imageProxy.close()
        }
    }

    private fun sendFrameToBackend(bitmap: Bitmap) {
        scope.launch {
            try {
                val imageBase64 = withContext(Dispatchers.Default) { encodeBitmapToBase64(bitmap) }
                val response = withContext(Dispatchers.IO) {
                    RetrofitClient.api.detectFromCamera(
                        CameraDetectRequest(
                            camera_id = cameraId,
                            image = imageBase64
                        )
                    ).execute()
                }

                if (!response.isSuccessful) {
                    binding.tvDetectionInfo.text = "Backend error: ${response.code()}"
                    return@launch
                }

                val body = response.body()
                val detections = body?.detections.orEmpty()
                if (detections.isEmpty()) {
                    binding.tvDetectionInfo.text = "No animals detected"
                    return@launch
                }

                val best = detections.maxByOrNull { it.confidence ?: 0f }
                val animal = best?.class_name ?: "Unknown"
                val conf = ((best?.confidence ?: 0f) * 100f)
                binding.tvDetectionInfo.text = "Animal: $animal\nConfidence: ${conf.toInt()}%"

                val lat = currentLocation?.latitude
                val lng = currentLocation?.longitude
                val locationStr = if (lat != null && lng != null) "$lat,$lng" else "Camera Location"
                val detection = DetectionHistory(
                    id = UUID.randomUUID().toString(),
                    animalType = animal,
                    confidence = conf,
                    location = locationStr,
                    latitude = lat,
                    longitude = lng,
                    timestamp = System.currentTimeMillis(),
                    dangerLevel = DetectionHistory.calculateDangerLevel(animal, conf)
                )
                preferenceManager.addDetectionHistory(detection)
            } catch (e: Exception) {
                Log.e(TAG, "Backend detect failed: ${e.message}", e)
                binding.tvDetectionInfo.text = "Detection failed: ${e.localizedMessage ?: "Unknown error"}"
            }
        }
    }

    private fun encodeBitmapToBase64(bitmap: Bitmap): String {
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 70, out)
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }

    private fun checkBackendAndRegisterCamera() {
        scope.launch {
            try {
                val health = withContext(Dispatchers.IO) { RetrofitClient.api.getHealth().execute() }
                backendHealthy = health.isSuccessful && health.body()?.status == "ok"
                if (!backendHealthy) {
                    binding.tvStatus.text = "Backend offline"
                    Toast.makeText(
                        this@CameraDetectionActivity,
                        "Backend not reachable. Check base URL and Flask server.",
                        Toast.LENGTH_LONG
                    ).show()
                    return@launch
                }

                val locationLabel = currentLocation?.let { "${it.latitude},${it.longitude}" } ?: "Mobile Camera"
                withContext(Dispatchers.IO) {
                    RetrofitClient.api.registerCamera(
                        CameraRegisterRequest(
                            camera_id = cameraId,
                            location = locationLabel
                        )
                    ).execute()
                }
                binding.tvStatus.text = "Backend connected"
                binding.btnToggleDetection.isEnabled = true
            } catch (e: Exception) {
                backendHealthy = false
                binding.tvStatus.text = "Backend offline"
                Log.e(TAG, "Health/register failed: ${e.message}", e)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun fetchCurrentLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED
        ) {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                currentLocation = location
            }
        }
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
        scope.cancel()
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
            ?: Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
    }
}

