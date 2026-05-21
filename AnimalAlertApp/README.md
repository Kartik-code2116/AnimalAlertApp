# Animal Alert App

Animal Alert App is an Android application designed to detect, track, and alert users about nearby wildlife or dangerous animals. The app uses advanced machine learning (YOLOv8) and location services to provide real-time updates and keep you safe.

## Features

- **Live Camera Detection**: Uses an on-device YOLOv8 ML model via PyTorch to detect various animal species in real-time right from your smartphone camera.
- **Background Alert Service**: A continuous background service that polls for new alerts from external sensors or APIs, triggering an audible siren and notifications when an animal is detected nearby.
- **Smart Danger Analysis**: Automatically classifies the threat level (Mild to Very High) based on the species detected (e.g. Bears, Wolves, Snakes) and their confidence score.
- **Live Interactive Map**: Google Maps integration showing the exact coordinates of recent animal sightings, represented by intuitive danger-coded pins.
- **Multi-channel Notifications**: Configure the app to notify you via in-app push notifications, SMS text messages, and Email alerts when danger is near.
- **Detection Dashboard & History**: View your daily and total detection statistics smoothly on your dashboard, and scroll through the complete history of alerts with timestamps and threat levels.

## Tech Stack

- **Platform**: Android (Min SDK 24, Target SDK 34)
- **Language**: Kotlin
- **Machine Learning**: PyTorch Mobile (TorchVision) / TensorFlow Lite options (processing YOLOv8 output)
- **Networking**: Retrofit, OkHttp 
- **Mapping**: Google Play Services Maps & Location client
- **Asynchronous Operations**: Kotlin Coroutines & Flow
- **Architecture/UI Components**: Fragments, ViewBinding, RecyclerView with DiffUtil smoothly animating list updates, Bottom Navigation, CameraX

## Getting Started

### Prerequisites

- Android Studio (Electric Eel or newer recommended)
- A physical Android device or Emulator running Android 7.0 (API 24) or higher limit (A physical device is required to test camera and accurate GPS features).
- A valid Google Maps API Key.

### Installation & Setup

1. **Clone the repository** (or download the source code):
   ```bash
   git clone https://github.com/YourUsername/AnimalAlertApp.git
   ```

2. **Open the Project**: open the `AnimalAlertApp` directory in Android Studio.

3. **Provide API Keys**:
   - Add your Google Maps API key in the `AndroidManifest.xml` or your `local.properties` file.
   - Set the WildTrack server URL in **Settings → Server URL** (saved in `PreferenceManager`). The app syncs `RetrofitClient` from that value on launch.

4. **WildTrack backend (separate project)**:
   - Run the production Flask server from the **Animal_alert server** project (`server.py` with MongoDB, `/api/cameras`, `/api/alerts`, `/api/auth/*`).
   - Do not use a local `server.py` inside this Android repo — it was removed to avoid confusion with the real backend.

5. **YOLOv8 Model Setup**: 
   - Ensure your `.pt` or `.torchscript.pt` YOLOv8 model is placed inside the `app/src/main/assets/` directory.
   - Edit the `CLASS_NAMES` array inside `YOLOv8Detector.kt` to precisely match the classes your model was trained on.

6. **Build and Run**: Deploy the app on your physical device to test camera inferences and map features.

## App Architecture & Performance

The latest iteration of Animal Alert features major performance optimizations:
- Network calls for polling the API are efficiently processed off the main thread using `Dispatchers.IO` to maintain 60FPS UI rendering.
- `AlertService` safely schedules background tasks without redundant polling loops or memory leaks.
- Real-time list updates in the Detection History utilize `DiffUtil` for seamless RecyclerView animations without jank. 
- Fast, natively parsed tensor arrays interpret YOLOv8 multi-dimensional bounding boxes directly onto the image buffers.

## Permissions Required
The app requires the following permissions to operate its core features:
- `CAMERA`: For real-time ML inference.
- `ACCESS_FINE_LOCATION` & `ACCESS_COARSE_LOCATION`: To map animal detections.
- `POST_NOTIFICATIONS`: To alert you of dangers (Android 13+).
- *Optional*: SMS and Email permissions based on your desired alert avenues.
