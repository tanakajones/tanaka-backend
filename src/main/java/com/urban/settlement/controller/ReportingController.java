package com.urban.settlement.controller;

import com.urban.settlement.model.Issue;
import com.urban.settlement.model.enums.IssueCategory;
import com.urban.settlement.model.enums.IssueStatus;
import com.urban.settlement.repository.IssueRepository;
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

    /**
     * Get summary data for reporting dashboard
     */
    @GetMapping("/summary")
    public ResponseEntity<?> getReportSummary() {
        List<Issue> allIssues = issueRepository.findAll();

        Map<String, Object> summary = new HashMap<>();
        
        // Categorize by category
        Map<String, Long> byCategory = allIssues.stream()
                .collect(Collectors.groupingBy(i -> i.getCategory().name(), Collectors.counting()));
        summary.put("byCategory", byCategory);

        // Categorize by status
        Map<String, Long> byStatus = allIssues.stream()
                .collect(Collectors.groupingBy(i -> i.getStatus().name(), Collectors.counting()));
        summary.put("byStatus", byStatus);

        // Categorize by ward/location
        Map<String, Long> byWard = allIssues.stream()
                .filter(i -> i.getWardId() != null)
                .collect(Collectors.groupingBy(Issue::getWardId, Collectors.counting()));
        summary.put("byWard", byWard);

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
