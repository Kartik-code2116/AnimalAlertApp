# WildTrack (Animal Alert App) Architecture & Integration Specification

This document provides a comprehensive technical overview of the **WildTrack (Animal Alert)** Android application. It acts as an integration blueprint for developers and AI agents to construct a matching backend server, run Machine Learning (ML) inference models on CCTV camera feeds, and push live wildlife alerts directly to the app.

---

## 1. Network Communication & Configuration

The Android client relies on **Retrofit2** paired with **OkHttpClient** to communicate with the central server. The network layer has been designed for **fail-fast speed** and low latency:

*   **Default Base URL**: `http://10.30.201.240:5000/` (Dynamic Base URL is fully supported and can be updated in the App Settings).
*   **Connection Timeout**: `3 seconds`
*   **Read / Write Timeout**: `5 seconds`
*   **Default Communication Port**: `5000`

---

## 2. API Endpoint Schemas

The backend server must implement the following REST API endpoints under the configured Base URL. All payloads must be encoded in `application/json`.

### 1. `GET /latest-alert`
**Description**: Polled continuously by the mobile client to retrieve the most recent animal sighting.
*   **Response Model (`AlertResponse`)**:
    ```json
    {
      "animal_detected": true,
      "animal_type": "Bear",
      "confidence": 94.2,
      "location": "37.4220,-122.0841",
      "timestamp": 1716035240
    }
    ```
*   **Field Semantics**:
    *   `animal_detected` (`Boolean`): Set to `true` to raise an alarm.
    *   `animal_type` (`String?`): Name of the animal class detected (e.g., "Tiger", "Bear", "Deer", "Rabbit").
    *   `confidence` (`Float`): Confidence score of the ML inference model (range: `0.0` to `100.0`).
    *   `location` (`String?`): Sighting GPS coordinates formatted as a single string: `"latitude,longitude"`.
    *   `timestamp` (`Long`): Sighting event timestamp (supports standard Unix seconds or milliseconds).

---

### 2. `GET /health`
**Description**: General server health check.
*   **Response Model (`HealthResponse`)**:
    ```json
    {
      "status": "healthy"
    }
    ```

---

### 3. `POST /register/camera`
**Description**: Registers a newly deployed CCTV/surveillance camera unit.
*   **Payload Model (`CameraRegisterRequest`)**:
    ```json
    {
      "camera_id": "CAM_01",
      "location": "37.4220,-122.0841"
    }
    ```
*   **Response Model (`GenericBackendResponse`)**:
    ```json
    {
      "status": "success",
      "message": "Camera CAM_01 registered at grid 37.4220,-122.0841"
    }
    ```

---

### 4. `POST /camera/detect`
**Description**: Submits a video frame from a CCTV feed to the server to trigger ML detection logic.
*   **Payload Model (`CameraDetectRequest`)**:
    ```json
    {
      "camera_id": "CAM_01",
      "image": "/9j/4AAQSkZJRgABAQEASABIAAD..."
    }
    ```
    *(Note: `image` contains the Base64-encoded string of the captured JPG frame)*
*   **Response Model (`CameraDetectResponse`)**:
    ```json
    {
      "status": "success",
      "camera_id": "CAM_01",
      "dangerous": true,
      "detections": [
        {
          "class_name": "Tiger",
          "confidence": 98.4
        }
      ],
      "message": "Dangerous wildlife detected!"
    }
    ```

---

## 3. App Core Systems & Business Logic

### A. Background Sighting Listener (`AlertService.kt`)
*   **Foreground Mode**: Operates as a persistent Android Foreground Service bound to a custom notification channel (`"alert_service_channel"`). It remains alive even when the app is closed.
*   **Polling Interval**: Polls `GET /latest-alert` every **3000ms** (3 seconds).
*   **Alarm Siren**: If `animal_detected` is `true` and the threat has not been acknowledged, it forces the device's alarm stream (`STREAM_ALARM`) to maximum volume and plays a looping raw audio siren (`siren.mp3`).
*   **Dynamic Logging**: Increments sighting counters (Today Detections, Total Detections) stored in `SharedPreferences` and writes a parcelized detection object to the local history database.
*   **Notifications Routing**:
    *   **Push Notifications**: Dispatches an instant rich notification to the tray.
    *   **SMS Alert**: Sends an automated SMS to the configured security contact phone number using the device's GSM chip when high-danger animals are detected.

### B. Google Maps Surveillance (`MapFragment.kt`)
*   **Camera Plotting**: Statically plots the 3 primary monitoring camera units on the map using a distinct custom camera vector icon (`ic_camera.xml`).
*   **Pulsing Sonar Radar Sweeps**: Executes a Kotlin Coroutine loop on the UI thread rendering expanding green scanner rings around each camera marker. The rings expand outward up to 200 meters and fade out smoothly to 0% opacity dynamically.
*   **Sighting Plotting**:
    *   **High Danger Sightings (Level 3-5)**: Red warning dot with a wide red boundary ring.
    *   **Low Danger Sightings (Level 1-2)**: Blue warning dot with a blue boundary ring.
*   **Danger-Level Classifier Matrix**:
    *   **Level 5 (Very High)**: Bear, Wolf, Lion, Tiger, Leopard, Crocodile, Alligator, Venomous Snake.
    *   **Level 4 (High)**: Wild Boar, General Snake, Moose, Elk, Bison, Deer (Confidence > 80%).
    *   **Level 3 (Medium)**: Deer, Fox, Coyote, Raccoon, Skunk.
    *   **Level 2 (Moderate)**: Rabbit, Squirrel, Bird, Domestic Cat, Domestic Dog.
    *   **Level 1 (Low)**: All other default sightings.

---

## 4. Guide: Writing the Backend Server

Here is a ready-to-run template of the Python server utilizing **Flask** and **YOLOv8** (by Ultralytics) to handle animal detection from CCTV streams and update the app status in real-time.

### Python Backend Template (`server.py`)

```python
import time
import base64
import cv2
import numpy as np
from flask import Flask, jsonify, request
from ultralytics import YOLO

app = Flask(__name__)

# Initialize YOLOv8 Model (Pre-trained on COCO or custom trained on wildlife)
# COCO includes: bear, elephant, zebra, giraffe, bird, cat, dog, horse, sheep, cow.
# For customized wild cats or snakes, load your custom yolov8n.pt model file.
model = YOLO("yolov8n.pt")

# In-memory storage for the latest alert state
latest_alert = {
    "animal_detected": False,
    "animal_type": None,
    "confidence": 0.0,
    "location": "37.4220,-122.0841",  # Matches camera coordinate
    "timestamp": int(time.time())
}

# In-memory camera registry
cameras = {
    "CAM_01": "37.4220,-122.0841",
    "CAM_02": "37.4250,-122.0880",
    "CAM_03": "37.4190,-122.0800"
}

# Danger categorization logic
DANGEROUS_ANIMALS = {"bear", "elephant", "cow", "zebra", "giraffe", "cat", "dog"}

@app.route('/health', methods=['GET'])
def health():
    return jsonify({"status": "healthy"})

@app.route('/latest-alert', methods=['GET'])
def get_latest_alert():
    # Return the latest wildlife alert to the polling Android app
    return jsonify(latest_alert)

@app.route('/register/camera', methods=['POST'])
def register_camera():
    data = request.get_json()
    camera_id = data.get("camera_id")
    location = data.get("location")
    if not camera_id or not location:
        return jsonify({"status": "error", "message": "Missing camera_id or location"}), 400
    
    cameras[camera_id] = location
    return jsonify({
        "status": "success", 
        "message": f"Camera {camera_id} registered at {location}"
    })

@app.route('/camera/detect', methods=['POST'])
def detect_from_camera():
    global latest_alert
    data = request.get_json()
    camera_id = data.get("camera_id")
    image_b64 = data.get("image")
    
    if not camera_id or not image_b64:
        return jsonify({"status": "error", "message": "Missing camera_id or image data"}), 400
    
    if camera_id not in cameras:
        return jsonify({"status": "error", "message": "Camera is not registered"}), 404
        
    try:
        # Decode the Base64 image
        img_bytes = base64.b64decode(image_b64)
        nparr = np.frombuffer(img_bytes, np.uint8)
        frame = cv2.imdecode(nparr, cv2.IMREAD_COLOR)
        
        # Run YOLOv8 Model Inference
        results = model(frame)
        
        detected_animals = []
        is_dangerous = False
        max_confidence = 0.0
        primary_animal = None
        
        for result in results:
            for box in result.boxes:
                class_id = int(box.cls[0])
                class_name = model.names[class_id]
                confidence = float(box.conf[0]) * 100
                
                # Check if detected COCO class is an animal of interest
                detected_animals.append({
                    "class_name": class_name,
                    "confidence": round(confidence, 2)
                })
                
                if confidence > max_confidence:
                    max_confidence = confidence
                    primary_animal = class_name
                    
                if class_name in DANGEROUS_ANIMALS:
                    is_dangerous = True
        
        if len(detected_animals) > 0:
            # Update the latest alert resource for the Android polling service
            latest_alert = {
                "animal_detected": True,
                "animal_type": primary_animal,
                "confidence": round(max_confidence, 2),
                "location": cameras[camera_id],
                "timestamp": int(time.time())
            }
            message = "Wildlife sighted!"
        else:
            # Reset alert state if frame is clear
            latest_alert = {
                "animal_detected": False,
                "animal_type": None,
                "confidence": 0.0,
                "location": cameras[camera_id],
                "timestamp": int(time.time())
            }
            message = "No wildlife detected."
            
        return jsonify({
            "status": "success",
            "camera_id": camera_id,
            "dangerous": is_dangerous,
            "detections": detected_animals,
            "message": message
        })
        
    except Exception as e:
        return jsonify({"status": "error", "message": f"Inference failed: {str(e)}"}), 500

if __name__ == '__main__':
    # Listen on all interfaces on port 5000
    app.run(host='0.0.0.0', port=5000, debug=True)
```

### Setup Steps for the Backend Developer:
1. **Install Dependencies**:
   ```bash
   pip install flask opencv-python numpy ultralytics
   ```
2. **Launch the Server**:
   ```bash
   python server.py
   ```
3. **Trigger ML Sightings**:
   - Send camera feed frames periodically as Base64 strings inside JSON payloads to `POST http://<server_ip>:5000/camera/detect`.
   - The Android app's background service (`AlertService`) will immediately fetch the sightings within 3 seconds, blast the Raw Siren, trigger the SMS Notification, and plot the real-time animal warning marker on the Map!
