package com.insights.generator.kafka.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class RealtimeSummaryResponse {

    private Long totalRegions;

    private Long criticalRegions;

    private Long warningRegions;

    private Long normalRegions;

    private Long activeAlerts;

    private Double averageNetworkHealth;

    private String topAffectedRegion;

    private LocalDateTime lastUpdated;
}