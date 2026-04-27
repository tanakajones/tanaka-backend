package com.urban.settlement.service;

import com.urban.settlement.model.Task;
import com.urban.settlement.repository.TaskRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Service for Task management
 * Handles task assignment and completion tracking
 */
@Service
public class TaskService {

    private static final Logger logger = LoggerFactory.getLogger(TaskService.class);

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private IssueService issueService;

    @Autowired
    private OfficerService officerService;

    /**
     * Create new task
     */
    public Task createTask(Task task) {
        task.setAssignedAt(LocalDateTime.now());
        Task saved = taskRepository.save(task);
        logger.info("Created task: {}", saved.getId());
        return saved;
    }

    /**
     * Get task by ID
     */
    public Task getTaskById(String id) {
        return taskRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Task not found: " + id));
    }

    /**
     * Get all tasks
     */
    public List<Task> getAllTasks() {
        return taskRepository.findAll();
    }

    /**
     * Get active tasks for officer
     */
    public List<Task> getActiveTasksByOfficer(String officerId) {
        return taskRepository.findByOfficerIdAndCompletedAtIsNull(officerId);
    }

    /**
     * Get completed tasks for officer
     */
    public List<Task> getCompletedTasksByOfficer(String officerId) {
        return taskRepository.findByOfficerIdAndCompletedAtIsNotNull(officerId);
    }

    /**
     * Complete task
     */
    public Task completeTask(String taskId, String notes, Integer qualityRating) {
        Task task = getTaskById(taskId);

        task.complete();
        task.setNotes(notes);
        task.setQualityRating(qualityRating);

        Task completed = taskRepository.save(task);

        // Update issue status
        issueService.updateStatus(task.getIssueId(),
                com.urban.settlement.model.enums.IssueStatus.RESOLVED);

        // Decrement officer workload
        officerService.decrementWorkload(task.getOfficerId());

        logger.info("Completed task: {}", taskId);

        return completed;
    }

    /**
     * Update task notes
     */
    public Task updateNotes(String taskId, String notes) {
        Task task = getTaskById(taskId);
        task.setNotes(notes);
        return taskRepository.save(task);
    }

    /**
     * Get tasks by issue
     */
    public List<Task> getTasksByIssue(String issueId) {
        return taskRepository.findByIssueId(issueId);
    }

    /**
     * Get overdue tasks
     */
    public List<Task> getOverdueTasks() {
        LocalDateTime cutoff = LocalDateTime.now().minusHours(24);
        return taskRepository.findOverdueTasks(cutoff);
    }

    /**
     * Delete task
     */
    public void deleteTask(String id) {
        taskRepository.deleteById(id);
        logger.info("Deleted task: {}", id);
    }
}
