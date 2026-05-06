package com.insights.generator.controller;

import com.insights.generator.kafka.dto.RealtimeAlertResponse;
import com.insights.generator.kafka.dto.RealtimeSummaryResponse;
import com.insights.generator.repository.AnomalyAlertRepository;
import com.insights.generator.repository.RealtimeHourlyMetricRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/alerts")
public class AlertController {

    private final AnomalyAlertRepository alertRepository;
    private final RealtimeHourlyMetricRepository realtimeRepository;

    public AlertController(AnomalyAlertRepository alertRepository, RealtimeHourlyMetricRepository realtimeRepository) {
        this.alertRepository = alertRepository;
        this.realtimeRepository = realtimeRepository;
    }

    @GetMapping("/unread")
    public ResponseEntity<List<Map<String, Object>>> getUnreadAlerts() {
        return ResponseEntity.ok(alertRepository.getUnreadAlerts());
    }

    @PostMapping("/{id}/read")
    public ResponseEntity<Void> markAlertAsRead(@PathVariable Long id) {
        alertRepository.markAsRead(id);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/realtime")
    public ResponseEntity<List<RealtimeAlertResponse>> getRealtimeAlerts() {

        List<RealtimeAlertResponse> response =
                realtimeRepository.getLatestRealtimeAlerts()
                        .stream()
                        .map(metric -> new RealtimeAlertResponse(

                                metric.getCreatedAt(),

                                metric.getSeverity(),

                                metric.getState(),

                                metric.getCity(),

                                metric.getNetworkBand(),

                                metric.getAvgLatencyMs(),

                                metric.getAvgPacketLossPct(),

                                metric.getAvgQualityScore(),

                                metric.getActiveAlerts(),

                                metric.getSummary()
                        ))
                        .toList();

        return ResponseEntity.ok(response);
    }

    @GetMapping("/realtime/summary")
    public ResponseEntity<RealtimeSummaryResponse> getRealtimeSummary() {

        RealtimeSummaryResponse response =
                new RealtimeSummaryResponse(

                        realtimeRepository.countTotalRegions(),

                        realtimeRepository.countCriticalRegions(),

                        realtimeRepository.countWarningRegions(),

                        realtimeRepository.countNormalRegions(),

                        realtimeRepository.countActiveAlerts(),

                        realtimeRepository.getAverageNetworkHealth(),

                        realtimeRepository.getTopAffectedRegion(),

                        realtimeRepository.getLastUpdated()
                );

        return ResponseEntity.ok(response);
    }
}