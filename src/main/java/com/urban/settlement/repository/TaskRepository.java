package com.urban.settlement.repository;

import com.urban.settlement.model.Task;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Repository for Task entity with completion and performance queries
 */
@Repository
public interface TaskRepository extends MongoRepository<Task, String> {

    List<Task> findByOfficerIdAndCompletedAtIsNull(String officerId);

    /**
     * Find all active tasks globally
     */
    List<Task> findByCompletedAtIsNull();

    /**
     * Find completed tasks for an officer
     */
    List<Task> findByOfficerIdAndCompletedAtIsNotNull(String officerId);

    /**
     * Find tasks completed in date range
     * Used for analytics and performance metrics
     */
    List<Task> findByCompletedAtBetween(LocalDateTime start, LocalDateTime end);

    /**
     * Find tasks assigned in date range
     */
    List<Task> findByAssignedAtBetween(LocalDateTime start, LocalDateTime end);

    /**
     * Find task by issue ID
     */
    List<Task> findByIssueId(String issueId);

    /**
     * Find all tasks for an officer
     */
    List<Task> findByOfficerId(String officerId);

    /**
     * Count active tasks for an officer
     */
    long countByOfficerIdAndCompletedAtIsNull(String officerId);

    /**
     * Find tasks with quality rating
     */
    List<Task> findByQualityRatingGreaterThanEqual(Integer minRating);

    /**
     * Custom query to find overdue tasks (assigned > 24 hours ago, not completed)
     */
    @Query("{ 'assignedAt': { $lt: ?0 }, 'completedAt': null }")
    List<Task> findOverdueTasks(LocalDateTime cutoffTime);

    /**
     * Find tasks by route order for an officer
     * Used for daily route planning
     */
    @Query("{ 'officerId': ?0, 'completedAt': null }")
    List<Task> findActiveTasksByOfficerIdOrderByRouteOrderAsc(String officerId);

    /**
     * Calculate average completion time for an officer
     */
    @Query(value = "{ 'officerId': ?0, 'actualDuration': { $exists: true } }", fields = "{ 'actualDuration': 1 }")
    List<Task> findCompletedTasksForPerformanceCalculation(String officerId);
}
