#!/usr/bin/env python3
"""
Route Optimization Script
Optimizes task assignment and routing using Hungarian algorithm + OR-Tools VRP

Usage: python optimize_routes.py (reads JSON from stdin)
Input: JSON with officers and issues
Output: JSON with optimized assignments and routes
"""

import sys
import json
import numpy as np
from scipy.optimize import linear_sum_assignment
import math

def haversine_distance(lat1, lon1, lat2, lon2):
    """Calculate distance in kilometers"""
    from math import radians, cos, sin, asin, sqrt
    
    lat1, lon1, lat2, lon2 = map(radians, [lat1, lon1, lat2, lon2])
    dlat = lat2 - lat1
    dlon = lon2 - lon1
    a = sin(dlat/2)**2 + cos(lat1) * cos(lat2) * sin(dlon/2)**2
    c = 2 * asin(sqrt(a))
    r = 6371  # Earth radius in km
    
    return c * r

def calculate_skill_match_score(officer_skills, issue_category):
    """
    Calculate skill match score (0-1)
    1.0 = perfect match, 0.0 = no match
    """
    # Map categories to required skills
    category_skill_map = {
        "ROAD_DAMAGE": "ROAD_REPAIR",
        "DRAINAGE": "DRAINAGE_MAINTENANCE",
        "WASTE": "WASTE_MANAGEMENT",
        "TRAFFIC_LIGHT": "ELECTRICAL"
    }
    
    required_skill = category_skill_map.get(issue_category, "GENERAL")
    
    if required_skill in officer_skills:
        return 1.0
    elif "GENERAL" in officer_skills:
        return 0.5
    else:
        return 0.3  # Can still do it, but not ideal

def calculate_cost_matrix(officers, issues):
    """
    Calculate cost matrix for Hungarian algorithm
    
    Cost factors:
    - Distance (officer location to issue location)
    - Skill mismatch penalty
    - Workload penalty
    """
    n_officers = len(officers)
    n_issues = len(issues)
    
    # Create cost matrix
    cost_matrix = np.zeros((n_officers, n_issues))
    
    for i, officer in enumerate(officers):
        for j, issue in enumerate(issues):
            # Distance cost
            if officer.get('location') and issue.get('location'):
                distance = haversine_distance(
                    officer['location']['lat'],
                    officer['location']['lng'],
                    issue['location']['lat'],
                    issue['location']['lng']
                )
            else:
                distance = 10.0  # Default distance
            
            # Skill match score
            skill_score = calculate_skill_match_score(
                officer.get('skills', []),
                issue.get('category', 'GENERAL')
            )
            
            # Workload penalty
            workload = officer.get('workload', 0)
            max_tasks = officer.get('maxTasksPerDay', 8)
            workload_penalty = workload / max_tasks if max_tasks > 0 else 0
            
            # Severity priority (higher severity = lower cost)
            severity_map = {"LOW": 1.0, "MEDIUM": 0.8, "HIGH": 0.6, "CRITICAL": 0.4}
            severity_factor = severity_map.get(issue.get('severity', 'MEDIUM'), 0.8)
            
            # Total cost (lower is better)
            # Weights: Proximity (0.35), Skills (0.3), Workload (0.2), Performance/Strength (0.1), Severity (0.05)
            # performance_factor: higher score = lower cost
            performance_score = officer.get('performanceScore', 100.0)
            performance_factor = (100.0 - performance_score) / 100.0 if performance_score > 0 else 1.0

            cost = (distance * 0.35 +                    # Proximity
                    (1 - skill_score) * 20 * 0.3 +       # Skill mismatch
                    workload_penalty * 15 * 0.2 +        # Workload balance
                    performance_factor * 10 * 0.1 +      # Stronger officers preferred for complex tasks
                    severity_factor * 5 * 0.05)          # Severity priority
            
            cost_matrix[i][j] = cost
    
    return cost_matrix

def optimize_assignment(officers, issues):
    """
    Assign ALL issues to officers using a hybrid approach:
    1. Hungarian algorithm for optimal base 1-to-1 matching
    2. Greedy assignment for remaining issues to nearest/most suitable officer
    """
    if not officers or not issues:
        return {}
    
    n_officers = len(officers)
    n_issues = len(issues)
    
    # Calculate cost matrix
    cost_matrix = calculate_cost_matrix(officers, issues)
    
    # Run Hungarian algorithm for base assignment
    officer_indices, issue_indices = linear_sum_assignment(cost_matrix)
    
    assignments = {}
    assigned_issue_indices = set()
    
    # 1. Base assignments (1-to-1)
    for officer_idx, issue_idx in zip(officer_indices, issue_indices):
        officer_id = officers[officer_idx]['id']
        if officer_id not in assignments:
            assignments[officer_id] = {'officer': officers[officer_idx], 'issues': []}
        assignments[officer_id]['issues'].append(issues[issue_idx])
        assigned_issue_indices.add(issue_idx)
    
    # 2. Assign remaining issues (if any)
    remaining_issue_indices = [i for i in range(n_issues) if i not in assigned_issue_indices]
    
    for issue_idx in remaining_issue_indices:
        # Find best officer for this issue (lowest cost in matrix)
        # But also consider their current workload in this optimization run
        best_officer_idx = -1
        min_cost = float('inf')
        
        for o_idx in range(n_officers):
            # Cost from matrix + significant penalty for each already assigned issue in this run
            current_workload = len(assignments.get(officers[o_idx]['id'], {'issues': []})['issues'])
            max_tasks = officers[o_idx].get('maxTasksPerDay', 8)
            
            # Skip if officer is already at capacity
            if current_workload >= max_tasks: continue
            
            # Higher penalty (10.0) ensures better distribution across all available officers
            cost = cost_matrix[o_idx][issue_idx] + (current_workload * 10.0)
            
            if cost < min_cost:
                min_cost = cost
                best_officer_idx = o_idx
        
        if best_officer_idx != -1:
            officer_id = officers[best_officer_idx]['id']
            if officer_id not in assignments:
                assignments[officer_id] = {'officer': officers[best_officer_idx], 'issues': []}
            assignments[officer_id]['issues'].append(issues[issue_idx])
    
    return assignments

def optimize_route(hq_location, issues):
    """
    Optimize route starting from HQ, visiting all issues, and returning to HQ
    Uses nearest neighbor heuristic
    """
    if not issues:
        return [], 0.0
    
    # Start from HQ
    current_lat = hq_location['lat']
    current_lng = hq_location['lng']
    
    remaining_issues = issues.copy()
    route = []
    total_distance = 0.0
    order = 1
    
    while remaining_issues:
        # Find nearest issue
        min_distance = float('inf')
        nearest_issue = None
        nearest_idx = -1
        
        for idx, issue in enumerate(remaining_issues):
            distance = haversine_distance(
                current_lat, current_lng,
                issue['location']['lat'],
                issue['location']['lng']
            )
            
            if distance < min_distance:
                min_distance = distance
                nearest_issue = issue
                nearest_idx = idx
        
        # Add to route
        route.append({
            "lat": nearest_issue['location']['lat'],
            "lng": nearest_issue['location']['lng'],
            "order": order,
            "issueId": nearest_issue['id']
        })
        
        total_distance += min_distance
        
        # Update current location
        current_lat = nearest_issue['location']['lat']
        current_lng = nearest_issue['location']['lng']
        
        # Remove from remaining
        remaining_issues.pop(nearest_idx)
        order += 1
    
    # Return to HQ
    dist_back_to_hq = haversine_distance(
        current_lat, current_lng,
        hq_location['lat'],
        hq_location['lng']
    )
    total_distance += dist_back_to_hq
    
    return route, total_distance

def main():
    # Read JSON from stdin
    input_data = sys.stdin.read()
    
    try:
        data = json.loads(input_data)
    except json.JSONDecodeError as e:
        print(json.dumps({"error": f"Invalid JSON: {str(e)}"}))
        sys.exit(1)
    
    officers = data.get('officers', [])
    issues = data.get('issues', [])
    hq = data.get('hq', {"lat": -17.8465, "lng": 31.0069}) # Default to HIT HQ
    
    # Optimize assignment
    assignments = optimize_assignment(officers, issues)
    
    # Optimize routes for each officer
    results = []
    for officer_id, assignment_data in assignments.items():
        officer = assignment_data['officer']
        assigned_issues = assignment_data['issues']
        
        # Optimize route starting and ending at HQ
        optimized_route, total_distance = optimize_route(
            hq,
            assigned_issues
        )
        
        # Estimate duration (assume 20 min per issue + travel time)
        # Travel time: assume 40 km/h average speed in urban settlement
        travel_time_min = (total_distance / 40.0) * 60
        estimated_duration = len(assigned_issues) * 20 + int(travel_time_min)
        
        # Operational cost tracking (Simplified)
        # Fuel cost: assume 0.1 liters per km, $1.5 per liter
        fuel_cost = total_distance * 0.1 * 1.5
        # Personnel cost: assume $10 per hour
        personnel_cost = (estimated_duration / 60.0) * 10
        total_cost = fuel_cost + personnel_cost
        
        result = {
            "officerId": officer_id,
            "officerName": officer.get('name', officer_id),
            "assignedIssues": [issue['id'] for issue in assigned_issues],
            "optimizedRoute": optimized_route,
            "totalDistance": round(total_distance, 2),
            "estimatedDuration": estimated_duration,
            "costs": {
                "fuel": round(fuel_cost, 2),
                "personnel": round(personnel_cost, 2),
                "total": round(total_cost, 2)
            }
        }
        
        results.append(result)
    
    # Output result
    output = {
        "assignments": results
    }
    
    print(json.dumps(output))

if __name__ == "__main__":
    main()
