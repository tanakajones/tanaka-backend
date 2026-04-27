package com.urban.settlement.controller;

import com.urban.settlement.service.DashboardService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for dashboard analytics
 * 
 * Objective 5: Real-time analytics dashboard
 * 
 * Endpoints:
 * - GET /api/dashboard/metrics - Get comprehensive KPIs
 * - GET /api/dashboard/heatmap - Get heatmap data for Leaflet
 */
@RestController
@RequestMapping("/api/dashboard")
@CrossOrigin(origins = "*")
public class DashboardController {

    @Autowired
    private DashboardService dashboardService;

    /**
     * GET /api/dashboard/metrics
     * Get comprehensive dashboard metrics
     * 
     * Returns:
     * - Total reports
     * - Pending vs resolved counts
     * - Average response/resolution times
     * - Resolution rate
     * - Cost per resolution
     * - Issues by category/severity
     */
    @GetMapping("/metrics")
    public ResponseEntity<DashboardService.DashboardMetricsDTO> getMetrics() {
        DashboardService.DashboardMetricsDTO metrics = dashboardService.getMetrics();
        return ResponseEntity.ok(metrics);
    }

    /**
     * GET /api/dashboard/heatmap
     * Get heatmap data for Leaflet visualization
     * 
     * Returns array of [lat, lng, intensity]
     */
    @GetMapping("/heatmap")
    public ResponseEntity<List<double[]>> getHeatmapData() {
        List<double[]> heatmapData = dashboardService.getHeatmapData();
        return ResponseEntity.ok(heatmapData);
    }

    /**
     * GET /api/dashboard/hotspots
     * Get grouped hotspots for advanced visualization
     */
    @GetMapping("/hotspots")
    public ResponseEntity<List<DashboardService.HotspotDTO>> getHotspots() {
        List<DashboardService.HotspotDTO> hotspots = dashboardService.getHotspots();
        return ResponseEntity.ok(hotspots);
    }
}
