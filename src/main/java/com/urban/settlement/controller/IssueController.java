package com.urban.settlement.controller;

import com.urban.settlement.model.Issue;
import com.urban.settlement.model.enums.IssueStatus;
import com.urban.settlement.service.ImageClassificationService;
import com.urban.settlement.service.IssueService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.geo.GeoJsonPoint;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * REST controller for Issue management and AI classification
 * 
 * Endpoints:
 * - POST /api/issues/classify - Upload and classify image
 * - GET /api/issues - Get all issues
 * - GET /api/issues/{id} - Get issue by ID
 * - PUT /api/issues/{id}/status - Update issue status
 */
@RestController
@RequestMapping("/api/issues")
@CrossOrigin(origins = "*")
public class IssueController {

    @Autowired
    private IssueService issueService;

    @Autowired
    private ImageClassificationService classificationService;

    /**
     * POST /api/issues/classify
     * Upload image and automatically classify issue
     * 
     * Objective 1: Automatic classification & severity prediction
     */
    @PostMapping("/classify")
    public ResponseEntity<?> classifyAndCreateIssue(
            @RequestParam("image") MultipartFile image,
            @RequestParam("title") String title,
            @RequestParam("description") String description,
            @RequestParam("latitude") double latitude,
            @RequestParam("longitude") double longitude,
            @RequestParam(value = "wardId", required = false) String wardId,
            @RequestParam(value = "reportedBy", required = false) String reportedBy) {

        try {
            // 1. Classify image
            ImageClassificationService.ClassificationResult result = classificationService.classifyImage(image);

            // 2. Create issue with AI predictions
            Issue issue = new Issue();
            issue.setTitle(title);
            issue.setDescription(description);
            issue.setLocation(new GeoJsonPoint(longitude, latitude));
            issue.setCategory(result.getCategory());
            issue.setSeverity(result.getSeverity());
            issue.setConfidence(result.getConfidence());
            issue.setImagePath(result.getImagePath());
            issue.setWardId(wardId);

            // Always set reportedBy from authenticated user for security
            org.springframework.security.core.Authentication authentication = org.springframework.security.core.context.SecurityContextHolder
                    .getContext().getAuthentication();
            if (authentication != null
                    && authentication.getPrincipal() instanceof com.urban.settlement.model.User) {
                com.urban.settlement.model.User user = (com.urban.settlement.model.User) authentication
                        .getPrincipal();
                reportedBy = user.getId();
            } else if (authentication != null) {
                reportedBy = authentication.getName();
            }
            
            issue.setReportedBy(reportedBy);

            Issue savedIssue = issueService.createIssue(issue);

            // 3. Build response
            Map<String, Object> response = new HashMap<>();
            response.put("issueId", savedIssue.getId());
            response.put("category", result.getCategory());
            response.put("severity", result.getSeverity());
            response.put("confidence", result.getConfidence());
            response.put("imagePath", result.getImagePath());
            response.put("status", savedIssue.getStatus());

            return ResponseEntity.ok(response);

        } catch (IOException e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Classification failed: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    /**
     * GET /api/issues
     * Get all issues with pagination
     */
    @GetMapping
    public ResponseEntity<Page<Issue>> getAllIssues(Pageable pageable) {
        Page<Issue> issues = issueService.getAllIssues(pageable);
        return ResponseEntity.ok(issues);
    }

    /**
     * GET /api/issues/{id}
     * Get issue by ID
     */
    @GetMapping("/{id}")
    public ResponseEntity<Issue> getIssueById(@PathVariable String id) {
        Issue issue = issueService.getIssueById(id);
        return ResponseEntity.ok(issue);
    }

    /**
     * GET /api/issues/my-reports
     * Get issues reported by the current user
     */
    @GetMapping("/my-reports")
    public ResponseEntity<Page<Issue>> getMyReports(Pageable pageable) {
        org.springframework.security.core.Authentication auth = org.springframework.security.core.context.SecurityContextHolder
                .getContext().getAuthentication();
        String userId = null;
        String email = null;
        
        if (auth != null && auth.getPrincipal() instanceof com.urban.settlement.model.User) {
            com.urban.settlement.model.User user = (com.urban.settlement.model.User) auth.getPrincipal();
            userId = user.getId();
            email = user.getEmail();
        } else if (auth != null) {
            userId = auth.getName();
            email = auth.getName();
        }

        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        Page<Issue> issues = issueService.getIssuesByReporter(userId, email, pageable);
        return ResponseEntity.ok(issues);
    }

    /**
     * GET /api/issues/status/{status}
     * Get issues by status
     */
    @GetMapping("/status/{status}")
    public ResponseEntity<Page<Issue>> getIssuesByStatus(
            @PathVariable IssueStatus status,
            Pageable pageable) {
        Page<Issue> issues = issueService.getIssuesByStatus(status, pageable);
        return ResponseEntity.ok(issues);
    }

    /**
     * GET /api/issues/ward/{wardId}
     * Get issues by ward
     */
    @GetMapping("/ward/{wardId}")
    public ResponseEntity<List<Issue>> getIssuesByWard(@PathVariable String wardId) {
        List<Issue> issues = issueService.getIssuesByWard(wardId);
        return ResponseEntity.ok(issues);
    }

    /**
     * GET /api/issues/officer/{officerId}
     * Get issues assigned to officer
     */
    @GetMapping("/officer/{officerId}")
    public ResponseEntity<List<Issue>> getIssuesByOfficer(@PathVariable String officerId) {
        List<Issue> issues = issueService.getIssuesByOfficer(officerId);
        return ResponseEntity.ok(issues);
    }

    /**
     * GET /api/issues/nearby
     * Find issues near location
     */
    @GetMapping("/nearby")
    public ResponseEntity<List<Issue>> findNearbyIssues(
            @RequestParam double latitude,
            @RequestParam double longitude,
            @RequestParam(defaultValue = "1.0") double radiusKm) {
        List<Issue> issues = issueService.findIssuesNearLocation(latitude, longitude, radiusKm);
        return ResponseEntity.ok(issues);
    }

    /**
     * PUT /api/issues/{id}/status
     * Update issue status
     */
    @PutMapping("/{id}/status")
    public ResponseEntity<Issue> updateStatus(
            @PathVariable String id,
            @RequestParam IssueStatus status) {
        Issue updated = issueService.updateStatus(id, status);
        return ResponseEntity.ok(updated);
    }

    /**
     * PUT /api/issues/{id}/assign
     * Assign issue to officer
     */
    @PutMapping("/{id}/assign")
    public ResponseEntity<Issue> assignOfficer(
            @PathVariable String id,
            @RequestParam String officerId) {
        Issue updated = issueService.assignOfficer(id, officerId);
        return ResponseEntity.ok(updated);
    }

    /**
     * PUT /api/issues/{id}
     * Update issue
     */
    @PutMapping("/{id}")
    public ResponseEntity<Issue> updateIssue(
            @PathVariable String id,
            @RequestBody Issue issue) {
        Issue updated = issueService.updateIssue(id, issue);
        return ResponseEntity.ok(updated);
    }

    /**
     * DELETE /api/issues/{id}
     * Delete issue
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteIssue(@PathVariable String id) {
        issueService.deleteIssue(id);
        return ResponseEntity.noContent().build();
    }
}
