import cv2
import requests
import base64
import time
import json

# --- CONFIGURATION ---
SERVER_IP = "192.168.1.100"  # <-- Change this to your computer's IP address!
SERVER_PORT = 5000
CAMERA_ID = "webcam_pc_1"
CAMERA_LOCATION = "18.5204, 73.8567" # <-- Change to your coordinates for map!

API_URL = f"http://{SERVER_IP}:{SERVER_PORT}"

def register_camera():
    try:
        data = {
            "camera_id": CAMERA_ID,
            "location": CAMERA_LOCATION
        }
        res = requests.post(f"{API_URL}/register/camera", json=data)
        print("Registration:", res.json())
    except Exception as e:
        print("Registration failed (is server running?):", e)

def send_frame(frame):
    try:
        # 1. Convert frame to base64
        _, buffer = cv2.imencode('.jpg', frame)
        img_base64 = base64.b64encode(buffer).decode('utf-8')

        # 2. Send to backend
        payload = {
            "camera_id": CAMERA_ID,
            "image": img_base64
        }
        response = requests.post(f"{API_URL}/camera/detect", json=payload)
        
        if response.status_code == 200:
            result = response.json()
            if result.get("animal_detected"):
                print(f"🚨 ALERT! {result['animal_type']} (Conf: {result['confidence']})")
            else:
                print("Safe - No target animals")
        else:
            print("Error from server:", response.status_code)
            
    except Exception as e:
        print("Network error:", e)

def main():
    print("🚀 Starting PC Webcam Client...")
    register_camera()
    
    cap = cv2.VideoCapture(0) # Open default PC webcam
    
    if not cap.isOpened():
        print("Error: Could not open webcam")
        return

    last_detect_time = 0
    detect_cooldown = 2.0 # Detect every 2 seconds

    try:
        while True:
            ret, frame = cap.read()
            if not ret:
                break

            # Show preview
            cv2.imshow('Animal Detecting Webcam (PC)', frame)

            # Throttle detection to save network/CPU
            current_time = time.time()
            if current_time - last_detect_time > detect_cooldown:
                send_frame(frame)
                last_detect_time = current_time

            # Press 'q' to quit
            if cv2.waitKey(1) & 0xFF == ord('q'):
                break
                
    finally:
        cap.release()
        cv2.destroyAllWindows()

if __name__ == "__main__":
    main()
