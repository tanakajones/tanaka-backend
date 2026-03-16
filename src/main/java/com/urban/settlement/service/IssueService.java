package com.urban.settlement.service;

import com.urban.settlement.model.Issue;
import com.urban.settlement.model.enums.IssueStatus;
import com.urban.settlement.repository.IssueRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.Metrics;
import org.springframework.data.geo.Point;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Core service for Issue management
 * Handles CRUD operations and status transitions
 */
@Service
public class IssueService {

    private static final Logger logger = LoggerFactory.getLogger(IssueService.class);

    @Autowired
    private IssueRepository issueRepository;

    /**
     * Create new issue
     */
    public Issue createIssue(Issue issue) {
        issue.setReportedAt(LocalDateTime.now());
        issue.setStatus(IssueStatus.PENDING);
        Issue saved = issueRepository.save(issue);
        logger.info("Created issue: {}", saved.getId());
        return saved;
    }

    /**
     * Get issue by ID
     */
    public Issue getIssueById(String id) {
        return issueRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Issue not found: " + id));
    }

    /**
     * Get all issues with pagination
     */
    public Page<Issue> getAllIssues(Pageable pageable) {
        return issueRepository.findAll(pageable);
    }

    /**
     * Get issues by status
     */
    public Page<Issue> getIssuesByStatus(IssueStatus status, Pageable pageable) {
        return issueRepository.findByStatus(status, pageable);
    }

    /**
     * Get issues by category
     */
    public Page<Issue> getIssuesByCategory(com.urban.settlement.model.enums.IssueCategory category,
            Pageable pageable) {
        return issueRepository.findByCategory(category, pageable);
    }

    /**
     * Find issues near a location
     */
    public List<Issue> findIssuesNearLocation(double latitude, double longitude, double radiusKm) {
        Point location = new Point(latitude, longitude);
        Distance distance = new Distance(radiusKm, Metrics.KILOMETERS);
        return issueRepository.findByLocationNear(location, distance);
    }

    /**
     * Update issue status
     */
    public Issue updateStatus(String issueId, IssueStatus newStatus) {
        Issue issue = getIssueById(issueId);
        IssueStatus oldStatus = issue.getStatus();

        issue.setStatus(newStatus);

        if (newStatus == IssueStatus.RESOLVED) {
            issue.setResolvedAt(LocalDateTime.now());
        }

        Issue updated = issueRepository.save(issue);
        logger.info("Updated issue {} status: {} -> {}", issueId, oldStatus, newStatus);

        return updated;
    }

    /**
     * Update issue
     */
    public Issue updateIssue(String id, Issue updatedIssue) {
        Issue existing = getIssueById(id);

        if (updatedIssue.getTitle() != null) {
            existing.setTitle(updatedIssue.getTitle());
        }
        if (updatedIssue.getDescription() != null) {
            existing.setDescription(updatedIssue.getDescription());
        }
        if (updatedIssue.getSeverity() != null) {
            existing.setSeverity(updatedIssue.getSeverity());
        }

        return issueRepository.save(existing);
    }

    /**
     * Delete issue
     */
    public void deleteIssue(String id) {
        issueRepository.deleteById(id);
        logger.info("Deleted issue: {}", id);
    }

    /**
     * Get issues by ward
     */
    public List<Issue> getIssuesByWard(String wardId) {
        return issueRepository.findByWardId(wardId);
    }

    /**
     * Get issues by assigned officer
     */
    public List<Issue> getIssuesByOfficer(String officerId) {
        return issueRepository.findByAssignedOfficerId(officerId);
    }

    /**
     * Get pending unassigned issues
     */
    public List<Issue> getPendingUnassignedIssues() {
        return issueRepository.findByStatusAndAssignedOfficerIdIsNull(IssueStatus.PENDING);
    }

    @Autowired
    private OfficerService officerService;

    /**
     * Assign issue to officer
     */
    public Issue assignOfficer(String issueId, String officerId) {
        Issue issue = getIssueById(issueId);
        String oldOfficerId = issue.getAssignedOfficerId();

        // If shifting from another officer, decrement their workload
        if (oldOfficerId != null && !oldOfficerId.equals(officerId)) {
            officerService.decrementWorkload(oldOfficerId);
        }

        // Assign new officer and increment workload
        issue.setAssignedOfficerId(officerId);
        if (issue.getStatus() == IssueStatus.PENDING) {
            issue.setStatus(IssueStatus.IN_PROGRESS);
        }

        Issue saved = issueRepository.save(issue);
        officerService.incrementWorkload(officerId);

        logger.info("Assigned issue {} to officer {}", issueId, officerId);
        return saved;
    }

    /**
     * Get issues by reporter (both ID and email)
     */
    public Page<Issue> getIssuesByReporter(String userId, String email, Pageable pageable) {
        return issueRepository.findByReportedBy(userId, email, pageable);
    }

    public Page<Issue> getIssuesByReporter(String reportedBy, Pageable pageable) {
        return issueRepository.findByReportedBy(reportedBy, pageable);
    }

    /**
     * Count issues by status
     */
    public long countByStatus(IssueStatus status) {
        return issueRepository.countByStatus(status);
    }
}
