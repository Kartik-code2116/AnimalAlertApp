# YOLOv8 Model Conversion Guide for Android

## Important: PyTorch Mobile Requirements

PyTorch Mobile requires models to be in **TorchScript** format, not regular PyTorch `.pt` files.

## Step 1: Convert Your YOLOv8 Model to TorchScript

### Option A: Using Ultralytics (Recommended)

```python
from ultralytics import YOLO

# Load your model
model = YOLO('best(1).pt')

# Export to TorchScript format for mobile
model.export(format='torchscript', imgsz=640)

# This creates: best(1).torchscript.pt
```

### Option B: Manual TorchScript Conversion

```python
import torch
from ultralytics import YOLO

# Load model
model = YOLO('best(1).pt')

# Get the PyTorch model
pytorch_model = model.model

# Convert to TorchScript
pytorch_model.eval()
example = torch.rand(1, 3, 640, 640)  # Input shape
traced_model = torch.jit.trace(pytorch_model, example)

# Save
traced_model.save('best(1).torchscript.pt')
```

## Step 2: Update the App Code

After conversion, update `YOLOv8Detector.kt`:

```kotlin
private val modelFileNames = arrayOf(
    "best(1).torchscript.pt",  // TorchScript format
    "best(1).pt",              // Fallback
    "best (1).pt"
)
```

## Step 3: Place File in Assets

1. Copy `best(1).torchscript.pt` to:
   ```
   app/src/main/assets/best(1).torchscript.pt
   ```

2. Or rename it to match existing code:
   ```
   app/src/main/assets/best(1).pt
   ```

## Alternative: Use TensorFlow Lite (Easier)

If TorchScript conversion is difficult, convert to TensorFlow Lite:

```python
from ultralytics import YOLO

model = YOLO('best(1).pt')
model.export(format='tflite', imgsz=640)
```

Then update the app to use TensorFlow Lite instead of PyTorch Mobile.

## Troubleshooting

### Error: "Model not loading"
- ✅ Check file is in `app/src/main/assets/`
- ✅ Verify filename matches exactly
- ✅ Ensure model is in TorchScript format
- ✅ Check Logcat for detailed error messages

### Error: "UnsatisfiedLinkError"
- PyTorch Mobile native libraries not loaded
- Rebuild the project
- Check if PyTorch Mobile dependency is correct

### Model too large?
- Consider quantizing the model
- Use TensorFlow Lite for better compression
- Reduce model size during training

## Quick Test

After placing the model file:
1. Build the project
2. Check Logcat for "YOLOv8Detector" messages
3. Look for "Model loaded successfully" message


