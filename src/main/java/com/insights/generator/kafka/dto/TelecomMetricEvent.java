package com.insights.generator.kafka.dto;


import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class TelecomMetricEvent {

    private LocalDateTime timestamp;

    private Integer hourOfDay;

    private Integer isPeakHour;

    private String region;

    private String state;

    private String city;

    private String networkBand;

    private String environmentType;

    private BigDecimal avgLatencyMs;

    private BigDecimal downloadSpeedMbps;

    private BigDecimal uploadSpeedMbps;

    private BigDecimal packetLossPct;

    private Integer activeUsers;

    private BigDecimal networkUtilizationPct;

    private String congestionLevel;

    private Integer droppedCalls;

    private String weatherCondition;

    private BigDecimal qualityScore;

    private String deviceModel;

    private String carrier;
}
