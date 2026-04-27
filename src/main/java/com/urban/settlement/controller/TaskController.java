package com.urban.settlement.controller;

import com.urban.settlement.model.Task;
import com.urban.settlement.service.OptimizationService;
import com.urban.settlement.service.TaskService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * REST controller for task management and optimization
 * 
 * Objective 4: Intelligent resource optimization
 * 
 * Endpoints:
 * - POST /api/tasks/optimize - Optimize task assignment
 * - GET /api/tasks/officer/{officerId} - Get tasks for officer
 * - PUT /api/tasks/{id}/complete - Complete task
 */
@RestController
@RequestMapping("/api/tasks")
@CrossOrigin(origins = "*")
public class TaskController {

    @Autowired
    private TaskService taskService;

    @Autowired
    private OptimizationService optimizationService;

    /**
     * POST /api/tasks/optimize
     * Optimize task assignment and routing
     * 
     * Uses Hungarian algorithm + OR-Tools VRP
     */
    @PostMapping("/optimize")
    public ResponseEntity<?> optimizeTasks(@RequestBody Map<String, List<String>> request) {
        try {
            List<String> issueIds = request.get("issueIds");

            if (issueIds == null || issueIds.isEmpty()) {
                Map<String, String> error = new HashMap<>();
                error.put("error", "issueIds required");
                return ResponseEntity.badRequest().body(error);
            }

            List<OptimizationService.TaskAssignmentDTO> assignments = optimizationService
                    .optimizeTaskAssignment(issueIds);

            Map<String, Object> response = new HashMap<>();
            response.put("assignments", assignments);
            response.put("count", assignments.size());

            return ResponseEntity.ok(response);

        } catch (IOException e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Optimization failed: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    /**
     * GET /api/tasks/officer/{officerId}
     * Get tasks for specific officer
     */
    @GetMapping("/officer/{officerId}")
    public ResponseEntity<?> getTasksByOfficer(
            @PathVariable String officerId,
            @RequestParam(defaultValue = "active") String type) {

        List<Task> tasks;

        if ("active".equals(type)) {
            tasks = taskService.getActiveTasksByOfficer(officerId);
        } else if ("completed".equals(type)) {
            tasks = taskService.getCompletedTasksByOfficer(officerId);
        } else {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Invalid type. Use 'active' or 'completed'");
            return ResponseEntity.badRequest().body(error);
        }

        return ResponseEntity.ok(tasks);
    }

    /**
     * GET /api/tasks/{id}
     * Get task by ID
     */
    @GetMapping("/{id}")
    public ResponseEntity<Task> getTaskById(@PathVariable String id) {
        Task task = taskService.getTaskById(id);
        return ResponseEntity.ok(task);
    }

    /**
     * PUT /api/tasks/{id}/complete
     * Mark task as completed
     */
    @PutMapping("/{id}/complete")
    public ResponseEntity<Task> completeTask(
            @PathVariable String id,
            @RequestBody(required = false) Map<String, Object> body) {

        String notes = body != null ? (String) body.get("notes") : null;
        Integer qualityRating = body != null ? (Integer) body.get("qualityRating") : null;

        Task completed = taskService.completeTask(id, notes, qualityRating);
        return ResponseEntity.ok(completed);
    }

    /**
     * PUT /api/tasks/{id}/notes
     * Update task notes
     */
    @PutMapping("/{id}/notes")
    public ResponseEntity<Task> updateNotes(
            @PathVariable String id,
            @RequestBody Map<String, String> body) {
        String notes = body.get("notes");
        Task updated = taskService.updateNotes(id, notes);
        return ResponseEntity.ok(updated);
    }

    /**
     * GET /api/tasks/overdue
     * Get overdue tasks
     */
    @GetMapping("/overdue")
    public ResponseEntity<List<Task>> getOverdueTasks() {
        List<Task> tasks = taskService.getOverdueTasks();
        return ResponseEntity.ok(tasks);
    }
}
