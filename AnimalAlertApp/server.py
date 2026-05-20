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

# Wild animals list (to alert on)
WILD_ANIMALS = {
    "bear", "elephant", "zebra", "giraffe", "tiger", "lion", "leopard", "wolf", 
    "snake", "boar", "wild boar", "deer", "fox", "coyote", "raccoon", "skunk", 
    "moose", "elk", "bison", "crocodile", "alligator"
}

# Danger categorization logic
DANGEROUS_ANIMALS = {"bear", "elephant", "zebra", "giraffe", "tiger", "lion", "leopard", "wolf", "snake", "crocodile"}

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
                
                # Check if detected COCO class is a wild animal of interest (ignore person, pets, etc.)
                if class_name in WILD_ANIMALS:
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
            message = "Dangerous wildlife detected!" if is_dangerous else "Wildlife sighted!"
        else:
            # Reset alert state if frame is clear or contains non-wild objects (like humans)
            latest_alert = {
                "animal_detected": False,
                "animal_type": None,
                "confidence": 0.0,
                "location": cameras[camera_id],
                "timestamp": int(time.time())
            }
            message = "No wild animals detected."
            
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
