package com.urban.settlement.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.urban.settlement.model.Issue;
import com.urban.settlement.model.Officer;
import com.urban.settlement.model.Task;
import com.urban.settlement.repository.IssueRepository;
import com.urban.settlement.repository.OfficerRepository;
import com.urban.settlement.repository.TaskRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.geo.GeoJsonPoint;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Intelligent resource optimization service
 * 
 * Objective 4: Intelligent resource optimization
 * - Auto-assigns tasks based on skills, workload, proximity, severity
 * - Generates optimized daily routes
 * - Minimizes total travel distance
 * 
 * Algorithms:
 * - Hungarian Algorithm for task assignment (O(n³))
 * - OR-Tools VRP for route optimization
 */
@Service
public class OptimizationService {

    private static final Logger logger = LoggerFactory.getLogger(OptimizationService.class);
    private static final String PYTHON_SCRIPT_PATH = "ml_scripts/optimize_routes.py";

    @Autowired
    private PythonExecutorService pythonExecutor;

    @Autowired
    private IssueRepository issueRepository;

    @Autowired
    private OfficerRepository officerRepository;

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private OfficerService officerService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Optimize task assignment and routing
     * 
     * @param issueIds List of issue IDs to assign
     * @return List of optimized assignments
     * @throws IOException if optimization fails
     */
    @org.springframework.transaction.annotation.Transactional
    public List<TaskAssignmentDTO> optimizeTaskAssignment(List<String> issueIds) throws IOException {

        logger.info("Optimizing task assignment for {} issues", issueIds.size());

        // 1. Fetch available officers
        // This now includes both the Officer collection and Users with role OFFICER
        List<Officer> availableOfficers = officerService.getAvailableOfficers();
        
        logger.info("Found {} consolidated available officers for task assignment", availableOfficers.size());

        if (availableOfficers.isEmpty()) {
            throw new IllegalStateException("No available officers for task assignment");
        }

        // 2. Fetch issues
        List<Issue> issues = issueRepository.findAllById(issueIds);

        if (issues.isEmpty()) {
            throw new IllegalArgumentException("No valid issues found");
        }

        // 3. Prepare JSON input
        String inputJson = prepareOptimizationInput(availableOfficers, issues);

        try {
            // 4. Execute optimization script
            JsonNode result = pythonExecutor.executePythonScriptWithInput(
                    PYTHON_SCRIPT_PATH,
                    inputJson);

            // 5. Parse assignments
            List<TaskAssignmentDTO> assignments = parseAssignments(result);
            createTasksFromAssignments(assignments);
            return assignments;
        } catch (Exception e) {
            logger.error("Optimization failed: {}", e.getMessage());
            throw new RuntimeException("Optimization engine failure: " + e.getMessage(), e);
        }
    }

    /**
     * Prepare JSON input for optimization script
     */
    private String prepareOptimizationInput(List<Officer> officers, List<Issue> issues)
            throws IOException {

        ObjectNode root = objectMapper.createObjectNode();

        // Officers array
        ArrayNode officersArray = objectMapper.createArrayNode();
        for (Officer officer : officers) {
            ObjectNode officerNode = objectMapper.createObjectNode();
            officerNode.put("id", officer.getId());
            officerNode.put("name", officer.getName());

            if (officer.getCurrentLocation() != null) {
                ObjectNode locationNode = objectMapper.createObjectNode();
                locationNode.put("lat", officer.getCurrentLocation().getY());
                locationNode.put("lng", officer.getCurrentLocation().getX());
                officerNode.set("location", locationNode);
            }

            ArrayNode skillsArray = objectMapper.createArrayNode();
            for (String skill : officer.getSkills()) {
                skillsArray.add(skill);
            }
            officerNode.set("skills", skillsArray);
            officerNode.put("workload", officer.getWorkload());
            officerNode.put("maxTasksPerDay", officer.getMaxTasksPerDay());
            officerNode.put("performanceScore", officer.getPerformanceScore() != null ? officer.getPerformanceScore() : 100.0);

            officersArray.add(officerNode);
        }
        root.set("officers", officersArray);

        // Issues array
        ArrayNode issuesArray = objectMapper.createArrayNode();
        for (Issue issue : issues) {
            ObjectNode issueNode = objectMapper.createObjectNode();
            issueNode.put("id", issue.getId());
            issueNode.put("category", issue.getCategory().name());
            issueNode.put("severity", issue.getSeverity().name());

            if (issue.getLocation() != null) {
                ObjectNode locationNode = objectMapper.createObjectNode();
                locationNode.put("lat", issue.getLocation().getY());
                locationNode.put("lng", issue.getLocation().getX());
                issueNode.set("location", locationNode);
            }

            issuesArray.add(issueNode);
        }
        root.set("issues", issuesArray);
        
        // Add HQ location (Harare Institute of Technology)
        ObjectNode hqNode = objectMapper.createObjectNode();
        hqNode.put("lat", -17.8465);
        hqNode.put("lng", 31.0069);
        root.set("hq", hqNode);

        return objectMapper.writeValueAsString(root);
    }

    /**
     * Parse optimization results
     */
    private List<TaskAssignmentDTO> parseAssignments(JsonNode result) {
        List<TaskAssignmentDTO> assignments = new ArrayList<>();

        JsonNode assignmentsNode = result.get("assignments");
        if (assignmentsNode != null && assignmentsNode.isArray()) {
            for (JsonNode assignmentNode : assignmentsNode) {
                String officerId = assignmentNode.get("officerId").asText();
                String officerName = assignmentNode.has("officerName") ? assignmentNode.get("officerName").asText() : officerId;

                List<String> assignedIssues = new ArrayList<>();
                JsonNode issuesNode = assignmentNode.get("assignedIssues");
                if (issuesNode.isArray()) {
                    for (JsonNode issueNode : issuesNode) {
                        assignedIssues.add(issueNode.asText());
                    }
                }

                List<RoutePoint> optimizedRoute = new ArrayList<>();
                JsonNode routeNode = assignmentNode.get("optimizedRoute");
                if (routeNode != null && routeNode.isArray()) {
                    for (JsonNode pointNode : routeNode) {
                        double lat = pointNode.get("lat").asDouble();
                        double lng = pointNode.get("lng").asDouble();
                        int order = pointNode.get("order").asInt();
                        String issueId = pointNode.get("issueId").asText();

                        optimizedRoute.add(new RoutePoint(lat, lng, order, issueId));
                    }
                }

                double totalDistance = assignmentNode.get("totalDistance").asDouble();
                int estimatedDuration = assignmentNode.get("estimatedDuration").asInt();

                double totalCost = 0.0;
                if (assignmentNode.has("costs")) {
                    totalCost = assignmentNode.get("costs").get("total").asDouble();
                }

                TaskAssignmentDTO assignment = new TaskAssignmentDTO(
                        officerId, officerName, assignedIssues, optimizedRoute, totalDistance, estimatedDuration, totalCost);

                assignments.add(assignment);
            }
        }

        return assignments;
    }

    /**
     * Create task entities from assignments
     */
    private void createTasksFromAssignments(List<TaskAssignmentDTO> assignments) {
        for (TaskAssignmentDTO assignment : assignments) {
            String officerId = assignment.getOfficerId();
            Officer officer;

            if (officerId.startsWith("USER_")) {
                // This is a virtual officer from the User table, let's materialize it
                String userId = officerId.substring(5);
                com.urban.settlement.model.User user = officerService.getUserById(userId);
                
                // Check if an officer already exists for this user email (safety check)
                java.util.Optional<Officer> existing = officerRepository.findByEmail(user.getEmail());
                if (existing.isPresent()) {
                    officer = existing.get();
                } else {
                    // Create a permanent officer entity
                    officer = new Officer();
                    officer.setName(user.getFirstname() + " " + user.getLastname());
                    officer.setEmail(user.getEmail());
                    officer.setUserId(user.getId());
                    officer.setSkills(officerService.randomizeSkillsForUser(user));
                    officer.setAvailabilityStatus(com.urban.settlement.model.enums.AvailabilityStatus.AVAILABLE);
                    officer = officerRepository.save(officer);
                    logger.info("Materialized user {} into officer entity {}", user.getEmail(), officer.getId());
                }
            } else {
                officer = officerRepository.findById(officerId)
                        .orElseThrow(() -> new IllegalStateException("Officer not found: " + officerId));
            }

            int routeOrder = 1;
            for (String issueId : assignment.getAssignedIssues()) {
                Issue issue = issueRepository.findById(issueId)
                        .orElseThrow(() -> new IllegalStateException("Issue not found"));

                // Create task
                Task task = new Task(issueId, officer.getId());
                task.setRouteOrder(routeOrder++);
                task.setEstimatedDuration(assignment.getEstimatedDuration() /
                        assignment.getAssignedIssues().size());
                taskRepository.save(task);

                // Update issue
                if (!issue.getAssignedOfficerIds().contains(officer.getId())) {
                    issue.getAssignedOfficerIds().add(officer.getId());
                }
                issue.setStatus(com.urban.settlement.model.enums.IssueStatus.IN_PROGRESS);
                issueRepository.save(issue);
            }

            // Update officer workload
            officer.incrementWorkload();
            officerRepository.save(officer);
        }
    }

    /**
     * Task assignment DTO
     */
    public static class TaskAssignmentDTO {
        private final String officerId;
        private final String officerName;
        private final List<String> assignedIssues;
        private final List<RoutePoint> optimizedRoute;
        private final double totalDistance;
        private final int estimatedDuration;
        private final double totalCost;

        public TaskAssignmentDTO(String officerId, String officerName, List<String> assignedIssues,
                List<RoutePoint> optimizedRoute, double totalDistance,
                int estimatedDuration, double totalCost) {
            this.officerId = officerId;
            this.officerName = officerName;
            this.assignedIssues = assignedIssues;
            this.optimizedRoute = optimizedRoute;
            this.totalDistance = totalDistance;
            this.estimatedDuration = estimatedDuration;
            this.totalCost = totalCost;
        }

        public String getOfficerId() {
            return officerId;
        }

        public String getOfficerName() {
            return officerName;
        }

        public List<String> getAssignedIssues() {
            return assignedIssues;
        }

        public List<RoutePoint> getOptimizedRoute() {
            return optimizedRoute;
        }

        public double getTotalDistance() {
            return totalDistance;
        }

        public int getEstimatedDuration() {
            return estimatedDuration;
        }

        public double getTotalCost() {
            return totalCost;
        }
    }

    /**
     * Route point DTO
     */
    public static class RoutePoint {
        private final double lat;
        private final double lng;
        private final int order;
        private final String issueId;

        public RoutePoint(double lat, double lng, int order, String issueId) {
            this.lat = lat;
            this.lng = lng;
            this.order = order;
            this.issueId = issueId;
        }

        public double getLat() {
            return lat;
        }

        public double getLng() {
            return lng;
        }

        public int getOrder() {
            return order;
        }

        public String getIssueId() {
            return issueId;
        }
    }
}
