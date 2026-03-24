package com.example.animalalert.ml

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import org.pytorch.IValue
import org.pytorch.Module
import org.pytorch.Tensor
import org.pytorch.torchvision.TensorImageUtils
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class YOLOv8Detector(private val context: Context) {

    private var model: Module? = null
    private val inputSize = 640 // YOLOv8 standard input size

    companion object {
        private const val TAG = "YOLOv8Detector"
        // Preferred model file name — set this to the exact model you want to use.
        // If not found, falls back to the first .pt file in assets.
        private const val PREFERRED_MODEL = "yolov8n.pt"

        // IMPORTANT: You MUST update these class names to match YOUR model's classes
        private val CLASS_NAMES = arrayOf(
            "person", "bicycle", "car", "motorcycle", "airplane", "bus", "train", "truck", "boat",
            "traffic light", "fire hydrant", "stop sign", "parking meter", "bench", "bird", "cat",
            "dog", "horse", "sheep", "cow", "elephant", "bear", "zebra", "giraffe", "backpack",
            "umbrella", "handbag", "tie", "suitcase", "frisbee", "skis", "snowboard", "sports ball",
            "kite", "baseball bat", "baseball glove", "skateboard", "surfboard", "tennis racket",
            "bottle", "wine glass", "cup", "fork", "knife", "spoon", "bowl", "banana", "apple",
            "sandwich", "orange", "broccoli", "carrot", "hot dog", "pizza", "donut", "cake", "chair",
            "couch", "potted plant", "bed", "dining table", "toilet", "tv", "laptop", "mouse", "remote",
            "keyboard", "cell phone", "microwave", "oven", "toaster", "sink", "refrigerator", "book",
            "clock", "vase", "scissors", "teddy bear", "hair drier", "toothbrush"
        )
    }

    fun loadModel(): Boolean {
        return try {
            Log.d(TAG, "Starting model loading process...")

            // First, check if PyTorch is properly initialized
            try {
                // Simple test to check if PyTorch is working
                val testTensor = Tensor.fromBlob(floatArrayOf(1f, 2f, 3f), longArrayOf(3))
                Log.d(TAG, "PyTorch library loaded successfully")
            } catch (e: UnsatisfiedLinkError) {
                Log.e(TAG, "PyTorch native libraries not found. Make sure to include proper dependencies in build.gradle:")
                Log.e(TAG, "implementation 'org.pytorch:pytorch_android_lite:1.13.1'")
                Log.e(TAG, "implementation 'org.pytorch:pytorch_android_torchvision_lite:1.13.1'")
                return false
            }

            // Check if model file exists in assets
            val modelFileName = findModelInAssets()
            if (modelFileName == null) {
                Log.e(TAG, "No model file found in assets. Please add your model to assets folder.")
                Log.e(TAG, "Supported formats: .pt or .torchscript.pt")
                return false
            }

            Log.d(TAG, "Found model file: $modelFileName")

            // Copy model from assets to internal storage
            val modelFile = copyModelFromAssets(modelFileName)
            if (modelFile == null || !modelFile.exists() || modelFile.length() == 0L) {
                Log.e(TAG, "Failed to copy model file from assets")
                return false
            }

            Log.d(TAG, "Loading PyTorch model from: ${modelFile.absolutePath} (${modelFile.length()} bytes)")

            // Load the model
            model = Module.load(modelFile.absolutePath)
            Log.d(TAG, "✅ YOLOv8 model loaded successfully!")

            // Test with a dummy tensor to ensure model works
            try {
                val dummyInput = Tensor.fromBlob(FloatArray(3 * inputSize * inputSize) { 0.5f },
                    longArrayOf(1, 3, inputSize.toLong(), inputSize.toLong()))
                val dummyOutput = model?.forward(IValue.from(dummyInput))
                Log.d(TAG, "Model test inference successful")
            } catch (e: Exception) {
                Log.e(TAG, "Model loaded but inference test failed: ${e.message}")
                // Don't return false here - model might still work for real images
            }

            true
        } catch (e: Exception) {
            Log.e(TAG, "Error loading model: ${e.message}", e)
            false
        }
    }

    private fun findModelInAssets(): String? {
        return try {
            val assetManager = context.assets
            val files = assetManager.list("")
            if (files != null) {
                Log.d(TAG, "Files in assets folder: ${files.joinToString(", ")}")

                val modelFiles = files.filter { it.endsWith(".pt") || it.endsWith(".pth") }

                // Prefer the explicitly configured model file
                modelFiles.find { it == PREFERRED_MODEL }?.let { preferred ->
                    Log.d(TAG, "Using preferred model: $preferred")
                    return preferred
                }

                // Fallback to first available model
                modelFiles.firstOrNull()?.let { fallback ->
                    Log.w(TAG, "Preferred model '$PREFERRED_MODEL' not found, falling back to: $fallback")
                    return fallback
                }
            }
            Log.e(TAG, "No .pt or .pth files found in assets")
            null
        } catch (e: Exception) {
            Log.e(TAG, "Error listing assets: ${e.message}")
            null
        }
    }

    private fun copyModelFromAssets(fileName: String): File? {
        return try {
            val assetManager = context.assets
            val inputStream = assetManager.open(fileName)

            // Create internal storage file
            val modelFile = File(context.filesDir, "model.pt")

            // Copy file
            FileOutputStream(modelFile).use { outputStream ->
                inputStream.copyTo(outputStream)
            }
            inputStream.close()

            Log.d(TAG, "Model copied to: ${modelFile.absolutePath} (${modelFile.length()} bytes)")
            modelFile
        } catch (e: Exception) {
            Log.e(TAG, "Error copying model from assets: ${e.message}")
            null
        }
    }

    data class Detection(
        val className: String,
        val confidence: Float,
        val x1: Float,
        val y1: Float,
        val x2: Float,
        val y2: Float
    )

    fun detect(bitmap: Bitmap): List<Detection> {
        if (model == null) {
            Log.e(TAG, "Model not loaded. Call loadModel() first.")
            return emptyList()
        }

        return try {
            // Preprocess image
            val resizedBitmap = Bitmap.createScaledBitmap(bitmap, inputSize, inputSize, true)

            // Convert bitmap to tensor - YOLOv8 expects normalized [0,1] range
            val inputTensor = TensorImageUtils.bitmapToFloat32Tensor(
                resizedBitmap,
                floatArrayOf(0f, 0f, 0f),  // Mean (0,0,0) for YOLO
                floatArrayOf(1f, 1f, 1f)   // Std (1,1,1) for YOLO
            )

            // Run inference - YOLOv8 expects input shape [1, 3, 640, 640]
            Log.d(TAG, "Running inference...")
            val startTime = System.currentTimeMillis()
            val output = model!!.forward(IValue.from(inputTensor)).toTensor()
            val inferenceTime = System.currentTimeMillis() - startTime
            Log.d(TAG, "Inference completed in ${inferenceTime}ms")

            // Post-process results
            parseYOLOv8Output(output, bitmap.width, bitmap.height)
        } catch (e: Exception) {
            Log.e(TAG, "Error during detection: ${e.message}", e)
            emptyList()
        }
    }

    private fun parseYOLOv8Output(
        output: Tensor,
        originalWidth: Int,
        originalHeight: Int
    ): List<Detection> {
        val detections = mutableListOf<Detection>()

        try {
            // YOLOv8 output format: [1, 84, 8400] where:
            // - 84 = 4 (bbox) + 80 (classes for COCO)
            // - 8400 = number of predictions (80*80 + 40*40 + 20*20)

            val shape = output.shape()
            Log.d(TAG, "Output shape: ${shape.joinToString()}")

            // Check if output is transposed (common in YOLOv8 exports)
            val numPredictions = shape[2].toInt()
            val numFeatures = shape[1].toInt()

            Log.d(TAG, "Num predictions: $numPredictions, Num features: $numFeatures")

            val outputData = output.dataAsFloatArray

            for (i in 0 until numPredictions) {
                // Get bounding box (cx, cy, w, h) - normalized to 0-1
                // Feature indexes: 0=cx, 1=cy, 2=w, 3=h
                val cx = outputData[0 * numPredictions + i]
                val cy = outputData[1 * numPredictions + i]
                val w = outputData[2 * numPredictions + i]
                val h = outputData[3 * numPredictions + i]

                // Convert to pixel coordinates
                val x1 = (cx - w/2) * originalWidth
                val y1 = (cy - h/2) * originalHeight
                val x2 = (cx + w/2) * originalWidth
                val y2 = (cy + h/2) * originalHeight

                // Find max class score
                var maxScore = 0f
                var maxClassIndex = 0

                for (j in 4 until numFeatures) {
                    val score = outputData[j * numPredictions + i]
                    if (score > maxScore) {
                        maxScore = score
                        maxClassIndex = j - 4
                    }
                }

                // Apply confidence threshold
                if (maxScore > 0.25f) { // Lower threshold for testing
                    val className = if (maxClassIndex < CLASS_NAMES.size) {
                        CLASS_NAMES[maxClassIndex]
                    } else {
                        "class_$maxClassIndex"
                    }

                    detections.add(
                        Detection(
                            className = className,
                            confidence = maxScore,
                            x1 = x1,
                            y1 = y1,
                            x2 = x2,
                            y2 = y2
                        )
                    )
                    Log.d(TAG, "Detection: $className (${String.format("%.2f", maxScore)}) at [$x1, $y1, $x2, $y2]")
                }
            }

            // Apply NMS (Non-Maximum Suppression)
            val filteredDetections = nonMaximumSuppression(detections)
            Log.d(TAG, "Found ${filteredDetections.size} detections after NMS")

            return filteredDetections

        } catch (e: Exception) {
            Log.e(TAG, "Error parsing YOLO output: ${e.message}", e)
        }

        return detections
    }

    private fun nonMaximumSuppression(
        detections: List<Detection>,
        iouThreshold: Float = 0.5f
    ): List<Detection> {
        if (detections.isEmpty()) return emptyList()

        val sorted = detections.sortedByDescending { it.confidence }.toMutableList()
        val selected = mutableListOf<Detection>()

        while (sorted.isNotEmpty()) {
            val current = sorted.removeAt(0)
            selected.add(current)

            val iterator = sorted.iterator()
            while (iterator.hasNext()) {
                val candidate = iterator.next()
                if (calculateIOU(current, candidate) >= iouThreshold) {
                    iterator.remove()
                }
            }
        }

        return selected
    }

    private fun calculateIOU(det1: Detection, det2: Detection): Float {
        val x1 = maxOf(det1.x1, det2.x1)
        val y1 = maxOf(det1.y1, det2.y1)
        val x2 = minOf(det1.x2, det2.x2)
        val y2 = minOf(det1.y2, det2.y2)

        val interArea = maxOf(0f, x2 - x1) * maxOf(0f, y2 - y1)
        val box1Area = (det1.x2 - det1.x1) * (det1.y2 - det1.y1)
        val box2Area = (det2.x2 - det2.x1) * (det2.y2 - det2.y1)

        return interArea / (box1Area + box2Area - interArea)
    }

    fun release() {
        model = null
        Log.d(TAG, "Model released")
    }
}