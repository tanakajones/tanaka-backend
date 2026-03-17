#!/usr/bin/env python3
"""
Image Classification Script
Classifies infrastructure issues using CNN model (ResNet50)

Usage: python classify_image.py <image_path>
Output: JSON with category, severity, and confidence
"""

import sys
import json
import os
import traceback

# Calculate absolute paths relative to script directory
SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
# Model path is now absolute relative to project root (assuming ml_scripts is in root)
MODEL_PATH = os.path.abspath(os.path.join(SCRIPT_DIR, "..", "ml_models", "pothole_classifier.h5"))

# Category and severity mappings
CATEGORIES = ["ROAD_DAMAGE", "DRAINAGE", "WASTE_ORGANIC", "WASTE_PLASTIC", "WASTE_METAL", "WASTE_CONSTRUCTION", "TRAFFIC_LIGHT"]
SEVERITIES = ["LOW", "MEDIUM", "HIGH", "CRITICAL"]

def fallback_classification(img_path, error_msg=None):
    """
    Fallback rule-based classification when model or dependencies are unavailable
    """
    if error_msg:
        # Log to stderr so it doesn't break JSON parsing of stdout
        print(f"DEBUG: {error_msg}", file=sys.stderr)
        
    # Simple rule-based waste detection for fallback
    # If image path contains certain keywords, classify as waste
    path_lower = img_path.lower()
    if any(k in path_lower for k in ["trash", "waste", "garbage", "rubbish", "plastic"]):
        return {
            "category": "WASTE_PLASTIC", # Default waste type
            "severity": "MEDIUM",
            "confidence": 0.7,
            "mode": "fallback_heuristic"
        }
        
    # For demo/fallback: default to ROAD_DAMAGE with HIGH severity
    return {
        "category": "ROAD_DAMAGE",
        "severity": "HIGH",
        "confidence": 0.85,
        "mode": "fallback"
    }

def predict_category_and_severity(img_path):
    """
    Predict category and severity from image
    """
    try:
        # Check if dependencies can be imported
        try:
            import numpy as np
            import tensorflow as tf
            from tensorflow.keras.models import load_model
            from tensorflow.keras.preprocessing import image
            from tensorflow.keras.applications.resnet50 import preprocess_input
        except ImportError as e:
            return fallback_classification(img_path, f"Missing dependencies: {str(e)}")

        # Check if model exists
        if not os.path.exists(MODEL_PATH):
            return fallback_classification(img_path, f"Model file not found at {MODEL_PATH}")
            
        # Load model using keras
        model = load_model(MODEL_PATH)
        
        # Preprocess image
        img = image.load_img(img_path, target_size=(224, 224))
        img_array = image.img_to_array(img)
        img_array = np.expand_dims(img_array, axis=0)
        img_array = preprocess_input(img_array)
        
        # Predict
        predictions = model.predict(img_array, verbose=0)
        
        # Parse predictions (assuming multi-output model or specific shape)
        if isinstance(predictions, list):
            category_pred = predictions[0][0]
            severity_pred = predictions[1][0] if len(predictions) > 1 else predictions[0][0]
        else:
            # Handle single output models
            category_pred = predictions[0]
            severity_pred = predictions[0]

        # Get category
        category_idx = np.argmax(category_pred)
        category_confidence = float(category_pred[category_idx])
        category = CATEGORIES[category_idx] if category_idx < len(CATEGORIES) else CATEGORIES[0]
        
        # Get severity
        severity_idx = np.argmax(severity_pred)
        severity = SEVERITIES[severity_idx] if severity_idx < len(SEVERITIES) else SEVERITIES[0]
        
        return {
            "category": category,
            "severity": severity,
            "confidence": round(category_confidence, 4),
            "mode": "ai"
        }
        
    except Exception as e:
        # Log traceback to stderr for debugging
        traceback.print_exc(file=sys.stderr)
        return fallback_classification(img_path, f"Error during prediction: {str(e)}")

def main():
    try:
        if len(sys.argv) < 2:
            print(json.dumps({"error": "Image path required"}))
            sys.exit(1)
        
        img_path = sys.argv[1]
        
        # Ensure img_path is absolute if possible
        if not os.path.isabs(img_path):
            img_path = os.path.join(os.getcwd(), img_path)

        if not os.path.exists(img_path):
            print(json.dumps({"error": f"Image not found: {img_path}"}))
            sys.exit(1)
        
        # Classify image
        result = predict_category_and_severity(img_path)
        
        # Ensure result is valid JSON and printed alone on stdout
        print(json.dumps(result))

    except Exception as e:
        # Ultimate fallback to ensure valid JSON is ALWAYS emitted
        print(json.dumps({
            "category": "ROAD_DAMAGE",
            "severity": "HIGH",
            "confidence": 0.5,
            "error": str(e)
        }))

if __name__ == "__main__":
    main()
