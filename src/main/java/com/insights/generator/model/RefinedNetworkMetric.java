package com.insights.generator.model;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "refined_network_metrics")
@Data
public class RefinedNetworkMetric {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "\"timestamp\"")
    private LocalDateTime timestamp;

    @Column(name = "hour_of_day")
    private Integer hourOfDay;

    @Column(name = "is_peak_hour")
    private Integer isPeakHour;

    @Column(name = "region")
    private String region;

    @Column(name = "state")
    private String state;

    @Column(name = "city")
    private String city;

    @Column(name = "network_band")
    private String networkBand;

    @Column(name = "environment_type")
    private String environmentType;

    @Column(name = "avg_latency_ms")
    private BigDecimal avgLatencyMs;

    @Column(name = "download_speed_mbps")
    private BigDecimal downloadSpeedMbps;

    @Column(name = "upload_speed_mbps")
    private BigDecimal uploadSpeedMbps;

    @Column(name = "packet_loss_pct")
    private BigDecimal packetLossPct;

    @Column(name = "active_users")
    private Integer activeUsers;

    @Column(name = "network_utilization_pct")
    private BigDecimal networkUtilizationPct;

    @Column(name = "congestion_level")
    private String congestionLevel;

    @Column(name = "dropped_calls")
    private Integer droppedCalls;

    @Column(name = "weather_condition")
    private String weatherCondition;

    @Column(name = "quality_score")
    private BigDecimal qualityScore;

    @Column(name = "device_model")
    private String deviceModel;

    @Column(name = "carrier")
    private String carrier;
}