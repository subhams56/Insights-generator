package com.insights.generator.rag;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Component
public class DatasetIngestor {

    private static final Logger logger = LoggerFactory.getLogger(DatasetIngestor.class);
    private final JdbcTemplate jdbcTemplate;

    // 1. Updated File Path
    @Value("classpath:data/refined_network_data.csv")
    private Resource csvFile;

    private static final int BATCH_SIZE = 200;

    public DatasetIngestor(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void loadDataOnStartup() {
        // 2. Point to the new table
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM refined_network_metrics", Long.class);

        if (count != null && count > 0) {
            logger.info("Database already contains {} records in refined_network_metrics. Skipping ingestion.", count);
            return;
        }

        logger.info("Starting Batch CSV data ingestion into refined_network_metrics...");

        // 3. Updated SQL to match new schema
        String sql = "INSERT INTO refined_network_metrics " +
                "(timestamp, hour_of_day, is_peak_hour, region, state, city, network_band, environment_type, " +
                "avg_latency_ms, download_speed_mbps, upload_speed_mbps, packet_loss_pct, active_users, " +
                "network_utilization_pct, congestion_level, dropped_calls, weather_condition, quality_score, " +
                "device_model, carrier) " + // <--- ADDED HERE
                "VALUES (CAST(? AS TIMESTAMP), CAST(? AS INTEGER), CAST(? AS INTEGER), ?, ?, ?, ?, ?, " +
                "CAST(? AS NUMERIC), CAST(? AS NUMERIC), CAST(? AS NUMERIC), CAST(? AS NUMERIC), CAST(? AS INTEGER), " +
                "CAST(? AS NUMERIC), ?, CAST(? AS INTEGER), ?, CAST(? AS NUMERIC), ?, ?)";

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(csvFile.getInputStream(), StandardCharsets.UTF_8));
             CSVParser csvParser = new CSVParser(reader, CSVFormat.DEFAULT.withFirstRecordAsHeader().withIgnoreHeaderCase().withTrim())) {

            List<Object[]> batchArgs = new ArrayList<>();

            for (CSVRecord record : csvParser) {
                try {
                    // 4. Map exact headers from the new CSV
                    Object[] params = new Object[]{
                            record.get("timestamp"),
                            cleanInt(record.get("hour_of_day")),
                            cleanInt(record.get("is_peak_hour")),
                            record.get("region"),
                            record.get("state"),
                            record.get("city"),
                            record.get("network_band"),
                            record.get("environment_type"),
                            cleanNumeric(record.get("avg_latency_ms")),
                            cleanNumeric(record.get("download_speed_mbps")),
                            cleanNumeric(record.get("upload_speed_mbps")),
                            cleanNumeric(record.get("packet_loss_pct")),
                            cleanInt(record.get("active_users")),
                            cleanNumeric(record.get("network_utilization_pct")),
                            record.get("congestion_level"),
                            cleanInt(record.get("dropped_calls")),
                            record.get("weather_condition"),
                            cleanNumeric(record.get("quality_score")),
                            record.get("Device Model"),
                            record.get("Carrier")
                    };
                    batchArgs.add(params);

                    if (batchArgs.size() >= BATCH_SIZE) {
                        jdbcTemplate.batchUpdate(sql, batchArgs);
                        batchArgs.clear();
                        logger.info("Inserted batch of {} rows...", BATCH_SIZE);
                    }
                } catch (Exception e) {
                    logger.warn("Skipping bad row: {} - Reason: {}", record.getRecordNumber(), e.getMessage());
                }
            }

            if (!batchArgs.isEmpty()) {
                jdbcTemplate.batchUpdate(sql, batchArgs);
            }

            logger.info("Successfully finished Data Ingestion into refined_network_metrics.");

        } catch (Exception e) {
            logger.error("Failed to load CSV data: {}", e.getMessage(), e);
        }
    }

    private String cleanNumeric(String val) {
        if (val == null || val.isEmpty()) return "0";
        return val.replaceAll("[^0-9.]", "");
    }

    private Integer cleanInt(String val) {
        if (val == null || val.isEmpty()) return 0;
        String cleaned = val.replaceAll("[^0-9]", "");
        return cleaned.isEmpty() ? 0 : Integer.parseInt(cleaned);
    }
}