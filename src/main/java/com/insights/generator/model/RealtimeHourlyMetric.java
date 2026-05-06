package com.insights.generator.model;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "realtime_hourly_metrics")
@Data
public class RealtimeHourlyMetric {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private LocalDateTime createdAt;

    private String state;

    private String city;

    private String networkBand;

    private BigDecimal avgLatencyMs;

    private BigDecimal avgDownloadSpeedMbps;

    private BigDecimal avgPacketLossPct;

    private BigDecimal avgQualityScore;

    private Integer totalActiveUsers;

    private String congestionLevel;

    private Integer activeAlerts;

    private String severity;

    @Column(columnDefinition = "TEXT")
    private String summary;
}