import sys
import json
import importlib

def check_dependencies():
    dependencies = [
        "tensorflow",
        "ultralytics",
        "sklearn",
        "numpy",
        "pandas",
        "cv2",
        "PIL",
        "ortools"
    ]
    
    results = {}
    for dep in dependencies:
        try:
            importlib.import_module(dep)
            results[dep] = "OK"
        except ImportError as e:
            results[dep] = f"Error: {str(e)}"
            
    return results

if __name__ == "__main__":
    status = {
        "python_version": sys.version,
        "executable": sys.executable,
        "dependencies": check_dependencies()
    }
    print(json.dumps(status, indent=2))
