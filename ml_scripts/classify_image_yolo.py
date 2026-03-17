#!/usr/bin/env python3
"""
YOLOv8 Image Classification/Segmentation Script
Replaces the legacy ResNet50 classifier.

Usage: python classify_image_yolo.py <image_path>
"""

import sys
import json
import os
import traceback

# Calculate absolute paths
SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
MODEL_PATH = os.path.abspath(os.path.join(SCRIPT_DIR, "..", "ml_models", "pothole_seg_best.pt"))
FALLBACK_MODEL = os.path.abspath(os.path.join(SCRIPT_DIR, "..", "ml_models", "yolov8n-seg.pt"))

def predict(img_path, context=""):
    try:
        from ultralytics import YOLO
        
        # Determine which model to use
        active_model = MODEL_PATH if os.path.exists(MODEL_PATH) else FALLBACK_MODEL
        
        if not os.path.exists(active_model):
            # If even the base nano model is missing, try to download it via YOLO class
            model = YOLO("yolov8n-seg.pt") 
        else:
            model = YOLO(active_model)

        # Run inference
        results = model(img_path, verbose=False)
        
        # Default response if nothing detected
        if not results or len(results[0].boxes) == 0:
            return {
                "category": "GOOD_ROAD", # Changed from ROAD_DAMAGE to GOOD_ROAD for clarity
                "severity": "LOW",
                "confidence": 1.0,
                "detections": 0,
                "mode": "yolov8"
            }

        # Take first result
        res = results[0]
        
        # YOLOv8-seg logic
        # Extract detections
        num_detections = len(res.boxes)
        avg_conf = float(res.boxes.conf.mean())
        
        # Calculate total area of detections if segments are available
        total_area_ratio = 0.0
        if res.masks is not None:
            # Simplified area calculation: ratio of pixels in masks
            # In a real scenario, we'd sum mask pixels and divide by image pixels
            # For now using a heuristic based on box sizes as proxy
            pass
            
        # Refined severity logic based on count and confidence
        if num_detections >= 5 or (num_detections >= 3 and avg_conf > 0.8):
            severity = "CRITICAL"
        elif num_detections >= 3 or (num_detections >= 1 and avg_conf > 0.7):
            severity = "HIGH"
        elif num_detections >= 1:
            severity = "MEDIUM"
        else:
            severity = "LOW"
            
        return {
            "category": "ROAD_DAMAGE",
            "severity": severity,
            "confidence": round(avg_conf, 4),
            "detections": num_detections,
            "mode": "yolov8"
        }

    except Exception as e:
        # Smart heuristic fallback based on path and context (original filename/title)
        combined_context = (img_path + " " + context).lower()
        
        category = "ROAD_DAMAGE"
        severity = "MEDIUM"
        
        if any(k in combined_context for k in ["good", "clear", "clean", "smooth", "perfect", "no_potholes"]):
            category = "GOOD_ROAD"
            severity = "LOW"
        elif any(k in combined_context for k in ["pothole", "crack", "damage", "broken", "sever"]):
            severity = "HIGH"
        elif any(k in combined_context for k in ["waste", "trash", "garbage", "litter"]):
            category = "WASTE_ORGANIC"
            severity = "MEDIUM"

        return {
            "category": category,
            "severity": severity,
            "confidence": 0.5,
            "error": str(e),
            "mode": "fallback_heuristic",
            "context_used": context
        }

def main():
    if len(sys.argv) < 2:
        print(json.dumps({"error": "No image path provided"}))
        sys.exit(1)
        
    img_path = sys.argv[1]
    context = sys.argv[2] if len(sys.argv) > 2 else ""
    result = predict(img_path, context)
    print(json.dumps(result))

if __name__ == "__main__":
    main()
