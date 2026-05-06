package com.insights.generator.realtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.insights.generator.kafka.dto.RealtimeAggregationBucket;
import com.insights.generator.kafka.dto.TelecomMetricEvent;
import com.insights.generator.model.RealtimeHourlyMetric;
import com.insights.generator.repository.RealtimeHourlyMetricRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class RealtimeAggregatorService {

    private final RealtimeHourlyMetricRepository repository;

    private final ObjectMapper objectMapper =
            new ObjectMapper().registerModule(new JavaTimeModule());

    // =========================================================
    // In-Memory Rolling Aggregation Store
    // =========================================================

    private final Map<String, RealtimeAggregationBucket> rollingMetrics =
            new ConcurrentHashMap<>();

    // =========================================================
    // Kafka Stream Consumer
    // =========================================================

    @KafkaListener(
            topics = "telecom-network-metrics",
            groupId = "realtime-dashboard-group"
    )
    public void consumeRealtimeMetric(String message) {

        try {

            TelecomMetricEvent event =
                    objectMapper.readValue(message, TelecomMetricEvent.class);

            String key =
                    event.getState() + "|" +
                            event.getCity() + "|" +
                            event.getNetworkBand();

            rollingMetrics.compute(key, (k, bucket) -> {

                if (bucket == null) {
                    bucket = new RealtimeAggregationBucket();
                }

                bucket.setCount(bucket.getCount() + 1);

                bucket.setTotalLatency(
                        bucket.getTotalLatency()
                                + event.getAvgLatencyMs().doubleValue()
                );

                bucket.setTotalDownload(
                        bucket.getTotalDownload()
                                + event.getDownloadSpeedMbps().doubleValue()
                );

                bucket.setTotalPacketLoss(
                        bucket.getTotalPacketLoss()
                                + event.getPacketLossPct().doubleValue()
                );

                bucket.setTotalQuality(
                        bucket.getTotalQuality()
                                + event.getQualityScore().doubleValue()
                );

                bucket.setTotalUsers(
                        bucket.getTotalUsers()
                                + event.getActiveUsers()
                );

                // Simple realtime alert heuristic

                if (event.getQualityScore().doubleValue() < 0.40
                        || event.getPacketLossPct().doubleValue() > 5.0
                        || event.getAvgLatencyMs().doubleValue() > 120) {

                    bucket.setAlertCount(bucket.getAlertCount() + 1);
                }

                return bucket;
            });

        } catch (Exception e) {

            log.error("Failed realtime aggregation", e);
        }
    }

    // =========================================================
    // Flush Rolling Metrics Every Minute
    // =========================================================

    @Scheduled(fixedRate = 60000)
    public void flushRealtimeMetrics() {

        if (rollingMetrics.isEmpty()) {

            log.info("No realtime metrics to flush.");

            return;
        }

        log.info("Flushing realtime aggregation metrics...");

        rollingMetrics.forEach((key, bucket) -> {

            try {

                String[] parts = key.split("\\|");

                String state = parts[0];
                String city = parts[1];
                String band = parts[2];

                int count = Math.max(bucket.getCount(), 1);

                double avgLatency =
                        bucket.getTotalLatency() / count;

                double avgDownload =
                        bucket.getTotalDownload() / count;

                double avgPacketLoss =
                        bucket.getTotalPacketLoss() / count;

                double avgQuality =
                        bucket.getTotalQuality() / count;

                String congestionLevel =
                        determineCongestion(bucket.getTotalUsers());

                String severity =
                        determineSeverity(bucket.getAlertCount());

                String summary =
                        buildSummary(
                                state,
                                city,
                                band,
                                avgLatency,
                                avgPacketLoss,
                                avgQuality,
                                bucket.getAlertCount()
                        );

                RealtimeHourlyMetric metric =
                        new RealtimeHourlyMetric();

                metric.setCreatedAt(LocalDateTime.now());

                metric.setState(state);

                metric.setCity(city);

                metric.setNetworkBand(band);

                metric.setAvgLatencyMs(
                        BigDecimal.valueOf(avgLatency)
                                .setScale(2, RoundingMode.HALF_UP)
                );

                metric.setAvgDownloadSpeedMbps(
                        BigDecimal.valueOf(avgDownload)
                                .setScale(2, RoundingMode.HALF_UP)
                );

                metric.setAvgPacketLossPct(
                        BigDecimal.valueOf(avgPacketLoss)
                                .setScale(2, RoundingMode.HALF_UP)
                );

                metric.setAvgQualityScore(
                        BigDecimal.valueOf(avgQuality)
                                .setScale(2, RoundingMode.HALF_UP)
                );

                metric.setTotalActiveUsers(
                        bucket.getTotalUsers()
                );

                metric.setCongestionLevel(congestionLevel);

                metric.setActiveAlerts(bucket.getAlertCount());

                metric.setSeverity(severity);

                metric.setSummary(summary);

                repository.save(metric);

            } catch (Exception e) {

                log.error("Failed flushing realtime metric", e);
            }
        });

        rollingMetrics.clear();

        log.info("Realtime aggregation flush completed.");
    }

    // =========================================================
    // Severity Logic
    // =========================================================

    private String determineSeverity(int alertCount) {

        if (alertCount >= 10) {
            return "CRITICAL";
        }

        if (alertCount >= 5) {
            return "WARNING";
        }

        return "NORMAL";
    }

    // =========================================================
    // Congestion Logic
    // =========================================================

    private String determineCongestion(int users) {

        if (users > 20000) {
            return "HIGH";
        }

        if (users > 10000) {
            return "MEDIUM";
        }

        return "LOW";
    }

    // =========================================================
    // Human Readable Summary
    // =========================================================

    private String buildSummary(
            String state,
            String city,
            String band,
            double latency,
            double packetLoss,
            double quality,
            int alerts
    ) {

        return String.format(
                "Realtime network telemetry for %s (%s) on %s shows avg latency %.2f ms, packet loss %.2f%%, quality score %.2f with %d active alert conditions detected.",
                city,
                state,
                band,
                latency,
                packetLoss,
                quality,
                alerts
        );
    }
}