package com.urban.settlement.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.urban.settlement.model.Cluster;
import com.urban.settlement.model.Issue;
import com.urban.settlement.repository.ClusterRepository;
import com.urban.settlement.repository.IssueRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.geo.GeoJsonPoint;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * DBSCAN clustering service for hotspot detection
 * 
 * Objective 2: Hotspot detection & clustering
 * - Groups similar nearby issues (within 1km)
 * - Detects duplicates
 * - Prevents duplicate resource allocation
 */
@Service
public class ClusteringService {

    private static final Logger logger = LoggerFactory.getLogger(ClusteringService.class);
    private static final String PYTHON_SCRIPT_PATH = "ml_scripts/cluster_issues.py";

    @Autowired
    private PythonExecutorService pythonExecutor;

    @Autowired
    private IssueRepository issueRepository;

    @Autowired
    private ClusterRepository clusterRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Detect hotspots using DBSCAN clustering
     * 
     * Algorithm: DBSCAN (Density-Based Spatial Clustering)
     * - Epsilon: 1km
     * - MinPoints: 3 issues
     * - Distance Metric: Haversine
     * 
     * @return List of detected clusters
     * @throws IOException if clustering fails
     */
    public List<ClusterDTO> detectHotspots() throws IOException {

        logger.info("Starting hotspot detection");

        // 1. Fetch unclustered pending issues
        List<Issue> issues = issueRepository.findUnclusteredIssuesByStatus(
                com.urban.settlement.model.enums.IssueStatus.PENDING);

        if (issues.isEmpty()) {
            logger.info("No unclustered issues found");
            return new ArrayList<>();
        }

        logger.info("Found {} unclustered issues", issues.size());

        // 2. Prepare JSON input for Python script
        String issuesJson = prepareIssuesJson(issues);

        try {
            // 3. Execute clustering script
            JsonNode result = pythonExecutor.executePythonScriptWithInput(
                    PYTHON_SCRIPT_PATH,
                    issuesJson);

            // 4. Parse cluster results
            List<ClusterDTO> clusters = parseClusters(result);
            
            // 5. Update issues and save clusters
            updateIssuesWithClusters(clusters);
            return clusters;
        } catch (Exception e) {
            logger.warn("Python clustering failed, using Java-native fallback: {}", e.getMessage());
            List<ClusterDTO> clusters = performJavaNativeClustering(issues);
            updateIssuesWithClusters(clusters);
            return clusters;
        }
    }

    /**
     * Simple Java-native clustering (Centroid-based)
     * Groups issues within 500m of each other
     */
    private List<ClusterDTO> performJavaNativeClustering(List<Issue> issues) {
        List<ClusterDTO> clusters = new ArrayList<>();
        double clusterRadiusKm = 0.5; // 500m radius

        for (Issue issue : issues) {
            boolean assigned = false;
            for (ClusterDTO cluster : clusters) {
                double distance = calculateDistance(
                    issue.getLocation().getY(), issue.getLocation().getX(),
                    cluster.getCenter().getY(), cluster.getCenter().getX());
                
                if (distance <= clusterRadiusKm) {
                    cluster.getIssueIds().add(issue.getId());
                    assigned = true;
                    break;
                }
            }

            if (!assigned) {
                List<String> issueIds = new ArrayList<>();
                issueIds.add(issue.getId());
                clusters.add(new ClusterDTO(
                    UUID.randomUUID().toString(),
                    issue.getLocation(),
                    issueIds,
                    issue.getCategory().name(),
                    issue.getSeverity().name(),
                    1));
            }
        }
        return clusters;
    }

    private double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        double theta = lon1 - lon2;
        double dist = Math.sin(Math.toRadians(lat1)) * Math.sin(Math.toRadians(lat2))
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.cos(Math.toRadians(theta));
        dist = Math.acos(dist);
        dist = Math.toDegrees(dist);
        return dist * 60 * 1.1515 * 1.609344;
    }

    /**
     * Prepare issues as JSON for Python script
     */
    private String prepareIssuesJson(List<Issue> issues) throws IOException {
        ArrayNode issuesArray = objectMapper.createArrayNode();

        for (Issue issue : issues) {
            ObjectNode issueNode = objectMapper.createObjectNode();
            issueNode.put("id", issue.getId());
            issueNode.put("lat", issue.getLocation().getY());
            issueNode.put("lng", issue.getLocation().getX());
            issueNode.put("category", issue.getCategory().name());
            issueNode.put("description", issue.getDescription() != null ? issue.getDescription() : "");

            issuesArray.add(issueNode);
        }

        return objectMapper.writeValueAsString(issuesArray);
    }

    /**
     * Parse cluster results from Python output
     */
    private List<ClusterDTO> parseClusters(JsonNode result) {
        List<ClusterDTO> clusters = new ArrayList<>();

        JsonNode clustersNode = result.get("clusters");
        if (clustersNode != null && clustersNode.isArray()) {
            for (JsonNode clusterNode : clustersNode) {
                String groupId = clusterNode.get("groupId").asText();

                JsonNode centerNode = clusterNode.get("center");
                double lat = centerNode.get("lat").asDouble();
                double lng = centerNode.get("lng").asDouble();
                GeoJsonPoint center = new GeoJsonPoint(lng, lat);

                List<String> issueIds = new ArrayList<>();
                JsonNode issueIdsNode = clusterNode.get("issueIds");
                if (issueIdsNode.isArray()) {
                    for (JsonNode idNode : issueIdsNode) {
                        issueIds.add(idNode.asText());
                    }
                }

                String category = clusterNode.get("category").asText();
                String severityLevel = clusterNode.get("severityLevel").asText();
                int issueCount = clusterNode.get("issueCount").asInt();

                ClusterDTO cluster = new ClusterDTO(
                        groupId, center, issueIds, category, severityLevel, issueCount);

                clusters.add(cluster);
            }
        }

        return clusters;
    }

    /**
     * Update issues with groupId and save cluster entities
     */
    private void updateIssuesWithClusters(List<ClusterDTO> clusters) {
        for (ClusterDTO clusterDTO : clusters) {
            // Save cluster entity
            Cluster cluster = new Cluster(
                    clusterDTO.getGroupId(),
                    clusterDTO.getCenter(),
                    clusterDTO.getIssueIds(),
                    clusterDTO.getCategory(),
                    clusterDTO.getSeverityLevel());
            cluster.setIssueCount(clusterDTO.getIssueCount());
            clusterRepository.save(cluster);

            // Update issues with groupId
            for (String issueId : clusterDTO.getIssueIds()) {
                issueRepository.findById(issueId).ifPresent(issue -> {
                    issue.setGroupId(clusterDTO.getGroupId());
                    issueRepository.save(issue);
                });
            }
        }
    }

    /**
     * Find duplicate issues (same cluster)
     */
    public List<Issue> findDuplicates(String issueId) {
        return issueRepository.findById(issueId)
                .map(issue -> {
                    if (issue.getGroupId() != null) {
                        return issueRepository.findByGroupId(issue.getGroupId());
                    }
                    return new ArrayList<Issue>();
                })
                .orElse(new ArrayList<>());
    }

    /**
     * Cluster DTO for API responses
     */
    public static class ClusterDTO {
        private final String groupId;
        private final GeoJsonPoint center;
        private final List<String> issueIds;
        private final String category;
        private final String severityLevel;
        private final int issueCount;

        public ClusterDTO(String groupId, GeoJsonPoint center, List<String> issueIds,
                String category, String severityLevel, int issueCount) {
            this.groupId = groupId;
            this.center = center;
            this.issueIds = issueIds;
            this.category = category;
            this.severityLevel = severityLevel;
            this.issueCount = issueCount;
        }

        public String getGroupId() {
            return groupId;
        }

        public GeoJsonPoint getCenter() {
            return center;
        }

        public List<String> getIssueIds() {
            return issueIds;
        }

        public String getCategory() {
            return category;
        }

        public String getSeverityLevel() {
            return severityLevel;
        }

        public int getIssueCount() {
            return issueCount;
        }
    }
}
