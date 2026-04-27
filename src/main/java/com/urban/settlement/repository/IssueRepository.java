package com.urban.settlement.repository;

import com.urban.settlement.model.Issue;
import com.urban.settlement.model.enums.IssueCategory;
import com.urban.settlement.model.enums.IssueStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.Point;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Repository for Issue entity with geospatial and custom query methods
 */
@Repository
public interface IssueRepository extends MongoRepository<Issue, String> {

    /**
     * Find issues near a location within specified distance
     * Used for proximity searches and clustering
     */
    List<Issue> findByLocationNear(Point location, Distance distance);

    /**
     * Find all issues in a cluster group
     * Used for duplicate detection and hotspot analysis
     */
    List<Issue> findByGroupId(String groupId);

    /**
     * Find issues by category, status, and date range
     * Used for analytics and reporting
     */
    List<Issue> findByCategoryAndStatusAndReportedAtBetween(
            IssueCategory category,
            IssueStatus status,
            LocalDateTime start,
            LocalDateTime end);

    /**
     * Find issues by status with pagination
     */
    Page<Issue> findByStatus(IssueStatus status, Pageable pageable);

    /**
     * Find issues by category with pagination
     */
    Page<Issue> findByCategory(IssueCategory category, Pageable pageable);

    /**
     * Find issues by severity
     */
    List<Issue> findBySeverity(com.urban.settlement.model.enums.Severity severity);

    /**
     * Find issues assigned to a specific officer
     */
    List<Issue> findByAssignedOfficerIdsContaining(String officerId);

    /**
     * Find issues by ward
     */
    List<Issue> findByWardId(String wardId);

    /**
     * Find pending issues without any assigned officers
     * Used for task assignment optimization
     */
    List<Issue> findByStatusAndAssignedOfficerIdsIsEmpty(IssueStatus status);

    /**
     * Find issues reported in date range
     */
    List<Issue> findByReportedAtBetween(LocalDateTime start, LocalDateTime end);

    /**
     * Count issues by status
     */
    long countByStatus(IssueStatus status);

    /**
     * Count issues by category
     */
    long countByCategory(IssueCategory category);

    /**
     * Find issues by category and ward
     */
    List<Issue> findByCategoryAndWardId(IssueCategory category, String wardId);

    /**
     * Custom query to find issues without groupId (not yet clustered)
     */
    @Query("{ 'groupId': null, 'status': ?0 }")
    List<Issue> findUnclusteredIssuesByStatus(IssueStatus status);

    /**
     * Find issues with high confidence AI classification
     */
    @Query("{ 'confidence': { $gte: ?0 } }")
    List<Issue> findByConfidenceGreaterThanEqual(Double minConfidence);

    List<Issue> findByWardIdAndReportedAtBetween(String wardId, LocalDateTime startDate, LocalDateTime now);

    /**
     * Find issues by reporter with pagination
     * Robust version: checks both ID and email to handle legacy data
     */
    @Query("{ '$or': [ { 'reportedBy': ?0 }, { 'reportedBy': ?1 } ] }")
    Page<Issue> findByReportedBy(String userId, String email, Pageable pageable);

    Page<Issue> findByReportedBy(String reportedBy, Pageable pageable);
}
