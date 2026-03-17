#!/usr/bin/env python3
"""
YOLOv8 Training Template for Waste Classification
Designed for use with Kaggle datasets (e.g. Garbage Classification)

Usage: 
1. Download a dataset in YOLO format from Kaggle
2. Update the 'data' parameter to point to your data.yaml
3. Run this script to fine-tune YOLOv8
"""

import os
from ultralytics import YOLO

def train_model():
    # 1. Load a pre-trained model (YOLOv8 nano is recommended for speed/efficiency)
    model = YOLO("yolov8n-seg.pt") # Use segmentation model as requested in the pipeline

    # 2. Set path to your dataset configuration file
    # Ensure your data.yaml is correctly formatted for YOLOv8
    data_yaml_path = "path/to/your/data.yaml"
    
    if not os.path.exists(data_yaml_path):
        print(f"[!] Warning: Data config {data_yaml_path} not found.")
        print("Please provide a valid YOLO format dataset.")
        return

    # 3. Train the model
    # Adjust epochs and imgsz based on your compute resources
    results = model.train(
        data=data_yaml_path,
        epochs=50,
        imgsz=640,
        batch=16,
        name="waste_classifier_v1",
        project="tanaka_ml"
    )

    print("--- Training Complete ---")
    print(f"Results saved to: {results.save_dir}")
    
    # 4. Export the model to TorchScript or ONNX for production
    # Best model will be at results.save_dir / 'weights' / 'best.pt'
    print("ACTION: Copy the 'best.pt' file to your 'ml_models/' directory.")

if __name__ == "__main__":
    train_model()
