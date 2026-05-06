package com.insights.generator.kafka.dto;

import lombok.Data;


@Data
public class RealtimeAggregationBucket {

    private int count;

    private double totalLatency;

    private double totalDownload;

    private double totalPacketLoss;

    private double totalQuality;

    private int totalUsers;

    private int alertCount;
}