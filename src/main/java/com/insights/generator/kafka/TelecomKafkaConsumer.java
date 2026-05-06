package com.insights.generator.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.insights.generator.kafka.dto.TelecomMetricEvent;
import com.insights.generator.model.RefinedNetworkMetric;
import com.insights.generator.repository.RefinedNetworkMetricRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class TelecomKafkaConsumer {

    private final RefinedNetworkMetricRepository repository;

    private final ObjectMapper objectMapper =
            new ObjectMapper().registerModule(new JavaTimeModule());

    // =========================================================
    // REAL-TIME INGESTION (CURRENT FLOW)
    // =========================================================

    @KafkaListener(
            topics = "telecom-network-metrics",
            groupId = "telecom-insights-group"
    )
    public void consume(String message) {

        try {

            TelecomMetricEvent event =
                    objectMapper.readValue(message, TelecomMetricEvent.class);

            RefinedNetworkMetric metric = mapToEntity(event);

            repository.save(metric);

            log.info("Saved Telecom Metric to PostgreSQL");

        } catch (Exception e) {

            log.error("Failed to process Kafka message", e);
        }
    }

    // =========================================================
    // FUTURE BATCH INGESTION SUPPORT
    // =========================================================

    public void consumeBatch(List<String> messages) {

        try {

            List<RefinedNetworkMetric> metrics = new ArrayList<>();

            for (String message : messages) {

                TelecomMetricEvent event =
                        objectMapper.readValue(message, TelecomMetricEvent.class);

                metrics.add(mapToEntity(event));
            }

            repository.saveAll(metrics);

            log.info("Batch Insert Completed. Records Saved: {}", metrics.size());

        } catch (Exception e) {

            log.error("Failed to process Kafka batch", e);
        }
    }

    // =========================================================
    // COMMON ENTITY MAPPING
    // =========================================================

    private RefinedNetworkMetric mapToEntity(TelecomMetricEvent event) {

        RefinedNetworkMetric metric = new RefinedNetworkMetric();

        metric.setTimestamp(event.getTimestamp());

        metric.setHourOfDay(event.getHourOfDay());

        metric.setIsPeakHour(event.getIsPeakHour());

        metric.setRegion(event.getRegion());

        metric.setState(event.getState());

        metric.setCity(event.getCity());

        metric.setNetworkBand(event.getNetworkBand());

        metric.setEnvironmentType(event.getEnvironmentType());

        metric.setAvgLatencyMs(event.getAvgLatencyMs());

        metric.setDownloadSpeedMbps(event.getDownloadSpeedMbps());

        metric.setUploadSpeedMbps(event.getUploadSpeedMbps());

        metric.setPacketLossPct(event.getPacketLossPct());

        metric.setActiveUsers(event.getActiveUsers());

        metric.setNetworkUtilizationPct(event.getNetworkUtilizationPct());

        metric.setCongestionLevel(event.getCongestionLevel());

        metric.setDroppedCalls(event.getDroppedCalls());

        metric.setWeatherCondition(event.getWeatherCondition());

        metric.setQualityScore(event.getQualityScore());

        metric.setDeviceModel(event.getDeviceModel());

        metric.setCarrier(event.getCarrier());

        return metric;
    }
}