package com.urban.settlement.controller;

import com.urban.settlement.model.Issue;
import com.urban.settlement.model.enums.IssueCategory;
import com.urban.settlement.model.enums.IssueStatus;
import com.urban.settlement.repository.IssueRepository;
import com.urban.settlement.service.DashboardService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Controller for generating data-driven analysis reports
 */
@RestController
@RequestMapping("/api/reports")
@CrossOrigin(origins = "*")
public class ReportingController {

    @Autowired
    private IssueRepository issueRepository;

    @Autowired
    private DashboardService dashboardService;

    /**
     * Get summary data for reporting dashboard
     */
    @GetMapping("/summary")
    public ResponseEntity<?> getReportSummary() {
        DashboardService.DashboardMetricsDTO metrics = dashboardService.getMetrics();
        
        Map<String, Object> summary = new HashMap<>();
        
        // Basic grouping from metrics
        summary.put("byCategory", metrics.getIssuesByCategory());
        summary.put("byStatus", Map.of(
            "PENDING", metrics.getPendingCount(),
            "IN_PROGRESS", metrics.getInProgressCount(),
            "RESOLVED", metrics.getResolvedCount(),
            "REJECTED", metrics.getRejectedCount()
        ));
        summary.put("bySeverity", metrics.getIssuesBySeverity());
        
        // Advanced analytics
        summary.put("totalReports", metrics.getTotalReports());
        summary.put("avgResponseTime", metrics.getAvgResponseTime());
        summary.put("avgResolutionTime", metrics.getAvgResolutionTime());
        summary.put("resolutionRate", metrics.getResolutionRate());
        summary.put("costPerResolution", metrics.getCostPerResolution());
        summary.put("todayReports", metrics.getTodayReports());
        summary.put("weekReports", metrics.getWeekReports());

        // Categorize by ward/location (keep for map/location charts)
        List<Issue> allIssues = issueRepository.findAll();
        Map<String, Long> byWard = allIssues.stream()
                .filter(i -> i.getWardId() != null)
                .collect(Collectors.groupingBy(Issue::getWardId, Collectors.counting()));
        summary.put("byWard", byWard);

        // System efficiency score (custom formula)
        double efficiency = (metrics.getResolutionRate() * 0.6) + 
                            (Math.max(0, (48 - metrics.getAvgResolutionTime()) / 48) * 40);
        summary.put("efficiencyScore", Math.min(100, Math.round(efficiency)));

        return ResponseEntity.ok(summary);
    }

    /**
     * Export reports as CSV
     */
    @GetMapping("/export/csv")
    public void exportToCSV(HttpServletResponse response) throws IOException {
        response.setContentType("text/csv");
        response.setHeader("Content-Disposition", "attachment; filename=infrastructure_report_" + LocalDateTime.now() + ".csv");

        PrintWriter writer = response.getWriter();
        writer.println("ID,Title,Category,Severity,Status,ReportedAt,Ward,Officers");

        List<Issue> issues = issueRepository.findAll();
        for (Issue issue : issues) {
            writer.printf("%s,%s,%s,%s,%s,%s,%s,%s\n",
                    issue.getId(),
                    escapeCsv(issue.getTitle()),
                    issue.getCategory(),
                    issue.getSeverity(),
                    issue.getStatus(),
                    issue.getReportedAt(),
                    issue.getWardId(),
                    String.join("|", issue.getAssignedOfficerIds())
            );
        }
        writer.flush();
        writer.close();
    }

    private String escapeCsv(String text) {
        if (text == null) return "";
        return text.replace(",", ";").replace("\n", " ");
    }
}
