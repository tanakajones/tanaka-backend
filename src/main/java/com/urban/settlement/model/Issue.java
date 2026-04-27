package com.urban.settlement.model;

import com.urban.settlement.model.enums.IssueCategory;
import com.urban.settlement.model.enums.IssueStatus;
import com.urban.settlement.model.enums.Severity;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.geo.GeoJsonPoint;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.GeoSpatialIndexed;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Core entity for infrastructure issue reports
 * Supports AI classification, severity prediction, and geospatial clustering
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "issues")
@CompoundIndexes({
    @CompoundIndex(name = "category_severity_status_idx", 
                   def = "{'category': 1, 'severity': 1, 'status': 1}"),
    @CompoundIndex(name = "status_reportedAt_idx", 
                   def = "{'status': 1, 'reportedAt': -1}")
})
public class Issue {
    
    @Id
    private String id;
    
    private String title;
    
    private String description;
    
    @Indexed
    private IssueCategory category;
    
    private Severity severity;
    
    /**
     * File path to uploaded image (stored in server directory)
     * Format: /uploads/issues/{uuid}.jpg
     */
    private String imagePath;
    
    /**
     * GeoJSON Point for MongoDB geospatial queries
     * Supports 2dsphere index for proximity searches
     */
    @GeoSpatialIndexed(type = org.springframework.data.mongodb.core.index.GeoSpatialIndexType.GEO_2DSPHERE)
    private GeoJsonPoint location;
    
    @Indexed
    private IssueStatus status;
    
    @Indexed
    private LocalDateTime reportedAt;
    
    private LocalDateTime resolvedAt;
    
    /**
     * Cluster identifier for duplicate detection and hotspot grouping
     * Issues with same groupId are considered duplicates or in same hotspot
     */
    @Indexed
    private String groupId;
    
    /**
     * List of assigned officer identifiers
     */
    @Indexed
    private List<String> assignedOfficerIds = new ArrayList<>();
    
    /**
     * AI classification confidence score (0.0 - 1.0)
     * Higher values indicate more confident predictions
     */
    private Double confidence;
    
    /**
     * User who reported the issue (from JWT authentication)
     */
    private String reportedBy;
    
    /**
     * Additional metadata for analytics
     */
    private String wardId;
    
    public Issue(String title, String description, GeoJsonPoint location) {
        this.title = title;
        this.description = description;
        this.location = location;
        this.status = IssueStatus.PENDING;
        this.reportedAt = LocalDateTime.now();
    }
}
