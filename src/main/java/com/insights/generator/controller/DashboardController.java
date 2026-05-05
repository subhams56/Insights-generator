package com.insights.generator.controller;

import com.insights.generator.repository.DashboardRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/dashboard")
public class DashboardController {

    private final DashboardRepository dashboardRepository;

    public DashboardController(DashboardRepository dashboardRepository) {
        this.dashboardRepository = dashboardRepository;
    }

    @GetMapping("/kpis")
    public ResponseEntity<Map<String, Object>> getKPIs() {
        return ResponseEntity.ok(dashboardRepository.getAggregateKPIs());
    }

    @GetMapping("/trends/hourly")
    public ResponseEntity<List<Map<String, Object>>> getHourlyTrends() {
        return ResponseEntity.ok(dashboardRepository.getHourlyUtilizationTrend());
    }

    @GetMapping("/breakdown/bands")
    public ResponseEntity<List<Map<String, Object>>> getBandBreakdown() {
        return ResponseEntity.ok(dashboardRepository.getBandPerformance());
    }

    @GetMapping("/alerts/states")
    public ResponseEntity<List<Map<String, Object>>> getWorstStates() {
        return ResponseEntity.ok(dashboardRepository.getWorstPerformingStates());
    }

    // NEW: Individual endpoint for Carrier data
    @GetMapping("/breakdown/carriers")
    public ResponseEntity<List<Map<String, Object>>> getCarrierPerformance() {
        return ResponseEntity.ok(dashboardRepository.getCarrierPerformance());
    }

    // UPDATED: Include carrierPerformance in the main payload
    @GetMapping("/full")
    public ResponseEntity<Map<String, Object>> getFullDashboard() {
        return ResponseEntity.ok(Map.of(
                "kpis", dashboardRepository.getAggregateKPIs(),
                "hourlyTrends", dashboardRepository.getHourlyUtilizationTrend(),
                "bandPerformance", dashboardRepository.getBandPerformance(),
                "worstStates", dashboardRepository.getWorstPerformingStates(),
                "carrierPerformance", dashboardRepository.getCarrierPerformance() // <-- Added here
        ));
    }
}