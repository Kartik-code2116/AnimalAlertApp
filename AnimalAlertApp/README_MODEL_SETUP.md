# YOLOv8 Model Setup Instructions

## How to Add Your YOLOv8 Model

### Step 1: Convert Your Model (Recommended)

Your `best(1).pt` file is a PyTorch model. For best performance on Android, you have two options:

#### Option A: Use PyTorch Mobile (Easier - Already Set Up)
1. Place your `best(1).pt` file directly in:
   ```
   app/src/main/assets/best(1).pt
   ```
2. The app will automatically load it using PyTorch Mobile.

#### Option B: Convert to TensorFlow Lite (Better Performance)
1. Convert your YOLOv8 model to TensorFlow Lite format:
   ```python
   # Use ultralytics export function
   from ultralytics import YOLO
   model = YOLO('best(1).pt')
   model.export(format='tflite')  # Creates best(1).tflite
   ```
2. Place the `.tflite` file in:
   ```
   app/src/main/assets/best(1).tflite
   ```
3. Update `YOLOv8Detector.kt` to use TensorFlow Lite instead of PyTorch.

### Step 2: Update Class Names

Edit `YOLOv8Detector.kt` and update the `CLASS_NAMES` array to match your model's classes:

```kotlin
private val CLASS_NAMES = arrayOf(
    "your_class_1", "your_class_2", "your_class_3",
    // ... add all your animal classes
)
```

### Step 3: Build and Run

1. Sync Gradle files
2. Build the project
3. Place your model file in the assets folder
4. Run the app

## Features Available

✅ Real-time camera detection
✅ Automatic animal detection using YOLOv8
✅ Detection history tracking
✅ Integration with alert system
✅ Danger level calculation
✅ Notification support

## Model Requirements

- Input size: 640x640 (standard YOLOv8)
- Format: PyTorch (.pt) or TensorFlow Lite (.tflite)
- Output: Bounding boxes with class predictions

## Troubleshooting

### Model not loading?
- Check if file is in `app/src/main/assets/` folder
- Verify file name matches exactly: `best(1).pt`
- Check Logcat for error messages

### Poor detection performance?
- Consider converting to TensorFlow Lite for better performance
- Reduce input image size if needed
- Adjust confidence threshold in code

### Camera not working?
- Check camera permissions
- Ensure device has a camera
- Check Logcat for camera errors


