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
    combined_context = (img_path + " " + context).lower()
    
    # 1. Strong Context Check (Pre-inference)
    # If the user explicitly named the file something related to waste or drainage, 
    # it's a very strong indicator that should override a generic "pothole" detection.
    context_category = None
    if any(k in combined_context for k in ["waste", "trash", "garbage", "litter", "rubbish", "dump", "bin"]):
        context_category = "WASTE_ORGANIC"
    elif any(k in combined_context for k in ["sewer", "drain", "pipe", "overflow", "culvert", "clogged", "flooded"]):
        context_category = "SEWER_REPORT"

    try:
        from ultralytics import YOLO
        
        # Determine which model to use
        active_model = MODEL_PATH if os.path.exists(MODEL_PATH) else FALLBACK_MODEL
        
        if not os.path.exists(active_model):
            model = YOLO("yolov8n-seg.pt") 
        else:
            model = YOLO(active_model)

        # Run inference
        results = model(img_path, verbose=False)
        
        # Default response if nothing detected
        if not results or len(results[0].boxes) == 0:
            if context_category:
                 return {
                    "category": context_category,
                    "severity": "MEDIUM",
                    "confidence": 0.7,
                    "mode": "context_only"
                }
            return {
                "category": "GOOD_ROAD", 
                "severity": "LOW",
                "confidence": 1.0,
                "detections": 0,
                "mode": "yolov8"
            }

        # Take first result
        res = results[0]
        
        # YOLOv8 logic
        num_detections = len(res.boxes)
        avg_conf = float(res.boxes.conf.mean())
        
        # Determine category based on class names if available
        detected_category = "ROAD_DAMAGE" # Default for this model
        
        # Map COCO or custom classes if they exist
        if hasattr(res, 'names'):
            for i in range(len(res.boxes)):
                cls_id = int(res.boxes.cls[i])
                label = res.names[cls_id].lower()
                
                # Check for waste-related COCO classes
                if any(w in label for w in ["bottle", "cup", "can", "bag", "box", "garbage"]):
                    detected_category = "WASTE_ORGANIC"
                    break
                # Check for drainage-related (rare in COCO but might be in custom)
                if any(d in label for d in ["drain", "water", "puddle", "flood"]):
                    detected_category = "SEWER_REPORT"
                    break

        # If context says waste/drainage and model is low confidence or detected generic "pothole", override
        if context_category and (detected_category == "ROAD_DAMAGE" or avg_conf < 0.6):
            detected_category = context_category
            avg_conf = max(avg_conf, 0.8) # Boost confidence if context matches

        # Refined severity logic
        if num_detections >= 5 or (num_detections >= 3 and avg_conf > 0.8):
            severity = "CRITICAL"
        elif num_detections >= 3 or (num_detections >= 1 and avg_conf > 0.7):
            severity = "HIGH"
        elif num_detections >= 1:
            severity = "MEDIUM"
        else:
            severity = "LOW"
            
        return {
            "category": detected_category,
            "severity": severity,
            "confidence": round(avg_conf, 4),
            "detections": num_detections,
            "mode": "yolov8_hybrid"
        }

    except Exception as e:
        # Heuristic fallback
        category = context_category if context_category else "ROAD_DAMAGE"
        severity = "MEDIUM"
        
        if not context_category:
            if any(k in combined_context for k in ["good", "clear", "clean", "smooth", "perfect", "no_potholes"]):
                category = "GOOD_ROAD"
                severity = "LOW"
            elif any(k in combined_context for k in ["pothole", "crack", "damage", "broken", "sever"]):
                severity = "HIGH"
            elif any(k in combined_context for k in ["robot", "mechanical", "automated"]):
                category = "ROBOT_DAMAGE"
                severity = "CRITICAL"

        return {
            "category": category,
            "severity": severity,
            "confidence": 0.5,
            "error": str(e),
            "mode": "fallback_heuristic",
            "context_used": context
        }

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
