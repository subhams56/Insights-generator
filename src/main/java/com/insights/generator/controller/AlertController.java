package com.insights.generator.controller;

import com.insights.generator.repository.AnomalyAlertRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/alerts")
public class AlertController {

    private final AnomalyAlertRepository alertRepository;

    public AlertController(AnomalyAlertRepository alertRepository) {
        this.alertRepository = alertRepository;
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
}