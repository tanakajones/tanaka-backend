#!/usr/bin/env python3
"""
DBSCAN Clustering Script
Detects hotspots using density-based spatial clustering

Usage: python cluster_issues.py (reads JSON from stdin)
Input: JSON array of issues with id, lat, lng, category, description
Output: JSON with clusters
"""

import sys
import json
import numpy as np
from sklearn.cluster import DBSCAN
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.metrics.pairwise import cosine_similarity

# DBSCAN parameters
EPSILON_KM = 1.0  # 1km radius
MIN_SAMPLES = 3   # Minimum 3 issues to form a cluster

def haversine_distance(lat1, lon1, lat2, lon2):
    """
    Calculate great-circle distance between two points
    Returns distance in kilometers
    """
    from math import radians, cos, sin, asin, sqrt
    
    # Convert to radians
    lat1, lon1, lat2, lon2 = map(radians, [lat1, lon1, lat2, lon2])
    
    # Haversine formula
    dlat = lat2 - lat1
    dlon = lon2 - lon1
    a = sin(dlat/2)**2 + cos(lat1) * cos(lat2) * sin(dlon/2)**2
    c = 2 * asin(sqrt(a))
    
    # Earth radius in kilometers
    r = 6371
    
    return c * r

def calculate_distance_matrix(coords):
    """Calculate pairwise distance matrix using Haversine"""
    n = len(coords)
    dist_matrix = np.zeros((n, n))
    
    for i in range(n):
        for j in range(i+1, n):
            dist = haversine_distance(
                coords[i][0], coords[i][1],
                coords[j][0], coords[j][1]
            )
            dist_matrix[i][j] = dist
            dist_matrix[j][i] = dist
    
    return dist_matrix

def calculate_text_similarity(descriptions):
    """Calculate TF-IDF cosine similarity for descriptions"""
    if len(descriptions) < 2:
        return np.zeros((len(descriptions), len(descriptions)))
    
    try:
        vectorizer = TfidfVectorizer(stop_words='english')
        tfidf_matrix = vectorizer.fit_transform(descriptions)
        similarity_matrix = cosine_similarity(tfidf_matrix)
        return similarity_matrix
    except:
        # Return zeros if TF-IDF fails
        return np.zeros((len(descriptions), len(descriptions)))

def cluster_issues(issues):
    """
    Cluster issues using DBSCAN
    
    Args:
        issues: List of dicts with id, lat, lng, category, description
    
    Returns:
        List of clusters
    """
    if len(issues) < MIN_SAMPLES:
        return []
    
    # Extract coordinates
    coords = np.array([[issue['lat'], issue['lng']] for issue in issues])
    
    # Calculate distance matrix
    dist_matrix = calculate_distance_matrix(coords)
    
    # Run DBSCAN
    clustering = DBSCAN(
        eps=EPSILON_KM,
        min_samples=MIN_SAMPLES,
        metric='precomputed'
    ).fit(dist_matrix)
    
    labels = clustering.labels_
    
    # Calculate text similarity for duplicate detection
    descriptions = [issue.get('description', '') for issue in issues]
    text_similarity = calculate_text_similarity(descriptions)
    
    # Group issues by cluster
    clusters = {}
    for idx, label in enumerate(labels):
        if label == -1:  # Noise point
            continue
        
        if label not in clusters:
            clusters[label] = []
        
        clusters[label].append(idx)
    
    # Build cluster results
    results = []
    for cluster_id, issue_indices in clusters.items():
        # Calculate cluster center (centroid)
        cluster_coords = coords[issue_indices]
        center_lat = float(np.mean(cluster_coords[:, 0]))
        center_lng = float(np.mean(cluster_coords[:, 1]))
        
        # Get issue IDs and severities
        issue_ids = []
        severity_scores = []
        severity_map = {"LOW": 1, "MEDIUM": 2, "HIGH": 3, "CRITICAL": 4}
        inv_severity_map = {1: "LOW", 2: "MEDIUM", 3: "HIGH", 4: "CRITICAL"}
        
        for idx in issue_indices:
            issue_ids.append(issues[idx]['id'])
            sev = issues[idx].get('severityLevel', issues[idx].get('severity', 'MEDIUM'))
            severity_scores.append(severity_map.get(sev, 2))
        
        # Determine dominant category
        categories = [issues[idx]['category'] for idx in issue_indices]
        dominant_category = max(set(categories), key=categories.count)
        
        # Calculate cluster severity (average)
        avg_severity_score = int(round(np.mean(severity_scores)))
        severity_level = inv_severity_map.get(avg_severity_score, "MEDIUM")
        
        # Calculate intensity for heatmap (combination of count and severity)
        intensity = len(issue_ids) * (avg_severity_score / 2.0)
        
        # Calculate average similarity
        if len(issue_indices) > 1:
            similarities = []
            for i in range(len(issue_indices)):
                for j in range(i+1, len(issue_indices)):
                    idx_i = issue_indices[i]
                    idx_j = issue_indices[j]
                    similarities.append(text_similarity[idx_i][idx_j])
            avg_similarity = float(np.mean(similarities)) if similarities else 0.0
        else:
            avg_similarity = 1.0
        
        # Create cluster
        cluster_result = {
            "groupId": f"cluster_{cluster_id}",
            "center": {
                "lat": center_lat,
                "lng": center_lng
            },
            "issueIds": issue_ids,
            "issueCount": len(issue_ids),
            "category": dominant_category,
            "severityLevel": severity_level,
            "intensity": round(intensity, 2),
            "avgSimilarity": round(avg_similarity, 3)
        }
        
        results.append(cluster_result)
    
    return results

def main():
    # Read JSON from stdin
    input_data = sys.stdin.read()
    
    try:
        issues = json.loads(input_data)
    except json.JSONDecodeError as e:
        print(json.dumps({"error": f"Invalid JSON: {str(e)}"}))
        sys.exit(1)
    
    if not isinstance(issues, list):
        print(json.dumps({"error": "Input must be JSON array"}))
        sys.exit(1)
    
    # Cluster issues
    clusters = cluster_issues(issues)
    
    # Output result
    result = {
        "clusters": clusters
    }
    
    print(json.dumps(result))

if __name__ == "__main__":
    main()
