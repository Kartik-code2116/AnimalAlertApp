# 🐾 Animal Alert App

Animal Alert App is an Android application that connects to an **external camera server** to detect, track, and alert users about nearby wildlife or dangerous animals in real-time. The app receives detection data from a Python-based backend (YOLOv8), displays alerts with audible sirens, and plots animal sightings on an interactive Google Map.

---

## ✨ Features

| Feature | Description |
|---|---|
| **Server-Based Detection** | Connects to a Python Flask backend running YOLOv8 for animal detection via an external webcam. No on-device ML required. |
| **Background Alert Service** | A foreground service (`AlertService`) continuously polls the server for new alerts every 3 seconds. |
| **Audible Siren** | Plays a looping alarm at maximum volume when a dangerous animal is detected. User can manually stop it. |
| **Smart Danger Classification** | Automatically classifies threat levels (1–5) based on species (e.g., Bears → Very High, Rabbits → Low). |
| **Interactive Google Map** | Plots detection locations with color-coded pins (🔴 Dangerous, 🔵 Safe) using Google Maps SDK. |
| **Multi-channel Notifications** | Supports In-App push notifications, SMS alerts, and Email alerts (configurable per user). |
| **Detection Dashboard** | Displays daily and total detection statistics, recent alert history with timestamps and threat levels. |
| **User Authentication** | Login and Registration screens backed by a REST API. |
| **User Profile Management** | Configure notification preferences (SMS, Email, In-App), update emergency contact details, and logout. |

---

## 🏗️ Architecture Overview

```
com.example.animalalert/
├── Service/                    # Background services
│   └── AlertService.kt
├── model/                      # Data models
│   ├── AlertResponse.kt
│   ├── BackendRequests.kt
│   ├── BackendResponses.kt
│   └── DetectionHistory.kt
├── network/                    # API layer
│   ├── ApiService.kt
│   └── RetrofitClient.kt
├── ui/                         # Activities, Fragments, Adapters
│   ├── LoginActivity.kt
│   ├── RegisterActivity.kt
│   ├── MainActivity.kt
│   ├── adapters/
│   │   └── DetectionAdapter.kt
│   └── fragments/
│       ├── DashboardFragment.kt
│       ├── MapFragment.kt
│       ├── AlertSystemFragment.kt
│       └── ProfileFragment.kt
└── utils/                      # Utility helpers
    ├── PreferenceManager.kt
    ├── NotificationHelper.kt
    ├── SMSHelper.kt
    └── EmailHelper.kt
```

---

## 📦 Component Details

### 🔧 Service Layer

#### `AlertService.kt`
A **Foreground Service** that runs persistently and is the heart of the alert system.
- Polls the backend API endpoint `/latest-alert` every **3 seconds** using Kotlin Coroutines (`Dispatchers.IO`).
- When an animal is detected:
  - Plays a **looping siren** at maximum alarm volume.
  - Increments daily and total detection counts in `PreferenceManager`.
  - Saves detection to history (with deduplication via `lastDetectionId`).
  - Sends notifications via in-app push, SMS, or email (based on user preferences).
  - Implements a **60-second cooldown** between notifications to prevent spam.
- Supports a `STOP_SIREN` intent action so users can silence the alarm.
- Runs as a sticky foreground service with a persistent notification.

---

### 📐 Data Models

#### `AlertResponse.kt`
Represents the JSON response from the `/latest-alert` endpoint:
- `animal_detected` (Boolean) — whether an animal was found.
- `animal_type` (String?) — species name (e.g., "bear", "snake").
- `confidence` (Float) — detection confidence percentage.
- `location` (String?) — GPS coordinates as `"lat, lng"`.
- `timestamp` (Long) — Unix timestamp of the detection.

#### `DetectionHistory.kt`
A `@Parcelize` data class storing past detections locally. Includes:
- Unique `id`, `animalType`, `confidence`, `location`, `latitude`, `longitude`, `timestamp`, and `dangerLevel`.
- **`calculateDangerLevel()`** — Static method that classifies animals into 5 threat tiers:
  - **Level 5 (Very High):** Bear, Wolf, Lion, Tiger, Leopard, Crocodile, Alligator, Venomous Snake.
  - **Level 4 (High):** Wild Boar, Snake, Moose, Elk, Bison, high-confidence Deer.
  - **Level 3 (Medium):** Deer, Fox, Coyote, Raccoon, Skunk.
  - **Level 2 (Moderate):** Rabbit, Squirrel, Bird, Cat, Dog.
  - **Level 1 (Low):** Everything else.
- **`getDangerColor()`** — Maps danger levels to colors (Green → Red).

#### `BackendRequests.kt`
- `CameraRegisterRequest` — Registers an external camera with `camera_id` and `location`.
- `CameraDetectRequest` — Sends a base64-encoded image frame for server-side detection.

#### `BackendResponses.kt`
- `HealthResponse` — Server health check.
- `GenericBackendResponse` — Generic status/message response.
- `CameraDetectResponse` — Detection result with `dangerous` flag, list of `DetectionItem` objects.

---

### 🌐 Network Layer

#### `ApiService.kt`
Retrofit interface defining the REST API endpoints:
| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/latest-alert` | Fetch the most recent animal detection alert. |
| `GET` | `/health` | Check if the backend server is reachable. |
| `POST` | `/register/camera` | Register an external camera with the server. |
| `POST` | `/camera/detect` | Send a camera frame (base64) for detection. |

#### `RetrofitClient.kt`
Singleton Retrofit client configured with:
- OkHttp with **BASIC** logging interceptor for debugging.
- Connect timeout: **15s**, Read/Write timeout: **30s**.
- Base URL points to the Flask detection server (configurable IP).

---

### 🖥️ UI Layer

#### Activities

| Activity | Description |
|---|---|
| `LoginActivity` | User login screen. Authenticates against the backend and stores session in `PreferenceManager`. |
| `RegisterActivity` | New user registration screen. Sends registration data to the backend API. |
| `MainActivity` | Main container activity with Bottom Navigation. Hosts 4 fragments and auto-starts the `AlertService` as a foreground service on launch. Handles deep-link intents to jump to the Map with a specific detection highlighted. |

#### Fragments

| Fragment | Description |
|---|---|
| `DashboardFragment` | Displays **today's detections**, **total detections**, and **active alerts** as stat cards. Refreshes counts dynamically from `PreferenceManager`. |
| `MapFragment` | Google Maps integration. Plots all saved detections with **color-coded markers** (🔴 Red dot = Dangerous, 🔵 Blue dot = Safe). Uses reverse geocoding to show human-readable location names. Supports deep-linking to a specific detection coordinate. |
| `AlertSystemFragment` | Real-time alert monitoring panel. Polls `/latest-alert` every 3 seconds and updates the UI with alert status, animal type, confidence, and location. Shows a scrollable RecyclerView of recent detection history. Supports starting/stopping the alert service and siren. |
| `ProfileFragment` | User profile management. Configure notification channels (In-App, SMS, Email), update phone number and email for emergency alerts, and logout. |

#### Adapters

| Adapter | Description |
|---|---|
| `DetectionAdapter` | RecyclerView adapter for detection history items. Displays animal type, confidence, danger level badge (color-coded), timestamp, and reverse-geocoded location. Supports click-to-view-on-map and swipe-to-delete. |

---

### 🛠️ Utility Layer

#### `PreferenceManager.kt`
SharedPreferences wrapper that manages all local app state:
- **User session:** Login status, username, email, phone number.
- **Statistics:** Today's detections (auto-resets daily), total detections.
- **Detection history:** Stores/retrieves a JSON array of `DetectionHistory` objects.
- **Notification settings:** Toggles for in-app, SMS, and email notifications.

#### `NotificationHelper.kt`
Creates and displays Android notification-channel-based push notifications when an animal is detected. Shows the animal type, confidence, and danger level.

#### `SMSHelper.kt`
Sends SMS text alerts to the user's configured emergency phone number using the system `SmsManager`.

#### `EmailHelper.kt`
Composes and launches an email intent with detection details. (Note: Email via intent only works from foreground activities, not background services.)

---

## 📡 External Camera Client

### `webcam_client.py`
A Python script that runs on a PC to stream webcam frames to the backend server for detection.
- Captures frames from the default webcam using **OpenCV**.
- Encodes each frame as **base64 JPEG** and sends it to the `/camera/detect` endpoint.
- Throttles detection to one frame every **2 seconds** to save bandwidth.
- Registers the camera with the server on startup with a configurable `CAMERA_ID` and GPS `CAMERA_LOCATION`.

**Usage:**
```bash
pip install opencv-python requests
python webcam_client.py
```

---

## ⚙️ Tech Stack

| Component | Technology |
|---|---|
| **Platform** | Android (Min SDK 24, Target SDK 34) |
| **Language** | Kotlin |
| **Networking** | Retrofit 2.9 + OkHttp 4.9 + Gson Converter |
| **Mapping** | Google Play Services Maps 18.2 + Location 21.0 |
| **Navigation** | Bottom Navigation with Fragment transactions |
| **Async** | Kotlin Coroutines (SupervisorJob + CoroutineScope) |
| **UI** | ViewBinding, RecyclerView, CardView, ConstraintLayout, Material Design |
| **Image Loading** | Glide 4.16 |
| **Backend** | Python Flask + YOLOv8 (external server) |
| **Camera Client** | Python OpenCV (webcam_client.py) |

---

## 🚀 Getting Started

### Prerequisites

- Android Studio (Hedgehog or newer recommended).
- A physical Android device or Emulator running **Android 7.0 (API 24)** or higher.
- A valid **Google Maps API Key**.
- The Python detection server running on the same network.

### Installation & Setup

1. **Clone the repository:**
   ```bash
   git clone https://github.com/Kartik-code2116/AnimalAlertApp.git
   ```

2. **Open the project** in Android Studio (`AnimalAlertApp/AnimalAlertApp` directory).

3. **Configure Google Maps API Key:**
   Add your key to `local.properties`:
   ```properties
   MAPS_API_KEY=YOUR_GOOGLE_MAPS_API_KEY
   ```

4. **Set the Server IP:**
   Edit `RetrofitClient.kt` and update the `BASE_URL` to point to your Python server's IP address:
   ```kotlin
   private const val BASE_URL = "http://YOUR_SERVER_IP:5000/"
   ```

5. **Start the Detection Server:**
   Run the Python Flask backend with YOLOv8 on your PC.

6. **Start the Webcam Client** (optional):
   ```bash
   python webcam_client.py
   ```

7. **Build and Run** the Android app on your device.

---

## 🔐 Permissions Required

| Permission | Purpose |
|---|---|
| `INTERNET` | Communicate with the detection server. |
| `ACCESS_FINE_LOCATION` | Plot detections on the map with GPS coordinates. |
| `ACCESS_COARSE_LOCATION` | Approximate location for map features. |
| `POST_NOTIFICATIONS` | Display alert notifications (Android 13+). |
| `FOREGROUND_SERVICE` | Run the AlertService as a persistent foreground service.              |
| `SEND_SMS` | Send SMS alerts to emergency contacts. |
| `CAMERA` | Reserved for future on-device camera features. |

---

## 📊 App Flow

```
┌─────────────┐     ┌──────────────┐      ┌──────────────────┐
│  Login /    │────▶│  MainActivity│────▶│  AlertService    │
│  Register   │     │  (4 Tabs)    │      │   (Background)   │
└─────────────┘     └──────────────┘      └──────────────────┘
                           │                       │
                    ┌──────┼──────┐          Polls /latest-alert
                    │      │      │          every 3 seconds
                    ▼      ▼      ▼                │
              Dashboard  Map   Alert    ◀──────────┘
              Fragment  Fragment System    Triggers siren,
                                Fragment   notifications,
                                    │      detection history
                                    ▼
                              Profile
                              Fragment
```

---

## 📝 License

This project is for educational purposes.
