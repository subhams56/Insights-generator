package com.insights.generator.kafka.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class RealtimeAlertResponse {

    private LocalDateTime createdAt;

    private String severity;

    private String state;

    private String city;

    private String networkBand;

    private BigDecimal avgLatencyMs;

    private BigDecimal avgPacketLossPct;

    private BigDecimal avgQualityScore;

    private Integer activeAlerts;

    private String summary;
}