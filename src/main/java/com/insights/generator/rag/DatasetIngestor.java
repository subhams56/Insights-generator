package com.insights.generator.rag;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

@Component
public class DatasetIngestor {

    private static final Logger logger = LoggerFactory.getLogger(DatasetIngestor.class);
    private final JdbcClient jdbcClient;

    @Value("classpath:data/5g_network_data.csv")
    private Resource csvFile;

    public DatasetIngestor(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void loadDataOnStartup() {
        // Check if data already exists to avoid duplicate loads
        Long count = jdbcClient.sql("SELECT COUNT(*) FROM network_metrics").query(Long.class).single();

        if (count > 0) {
            logger.info("Database already contains {} records. Skipping CSV ingestion.", count);
            return;
        }

        logger.info("Starting CSV data ingestion into network_metrics...");

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(csvFile.getInputStream(), StandardCharsets.UTF_8));
             CSVParser csvParser = new CSVParser(reader, CSVFormat.DEFAULT.withFirstRecordAsHeader().withIgnoreHeaderCase().withTrim())) {

            for (CSVRecord record : csvParser) {
                try {
                    jdbcClient.sql("INSERT INTO network_metrics (timestamp, region_id, cell_id, avg_latency_ms, download_speed_mbps, upload_speed_mbps, packet_loss_pct, active_users) " +
                                    "VALUES (CAST(? AS TIMESTAMP), ?, ?, CAST(? AS NUMERIC), CAST(? AS NUMERIC), CAST(? AS NUMERIC), CAST(? AS NUMERIC), CAST(? AS INTEGER))")
                            .params(
                                    record.get("Timestamp"),             // Found in CSV
                                    record.get("Location"),              // Mapping "Location" to region_id
                                    record.get("Device Model"),          // Mapping "Device Model" to cell_id (or use index)
                                    cleanNumeric(record.get("Latency (ms)")),
                                    cleanNumeric(record.get("Download Speed (Mbps)")),
                                    cleanNumeric(record.get("Upload Speed (Mbps)")),
                                    cleanNumeric(record.get("Jitter (ms)")), // Using Jitter as proxy for packet loss if not found
                                    cleanInt(record.get("Ping to Google (ms)")) // Mapping a numeric field to active_users for now
                            ).update();
                } catch (Exception e) {
                    logger.warn("Skipping bad row: {} - Reason: {}", record.getRecordNumber(), e.getMessage());
                }
            }

            logger.info("Successfully loaded data from CSV into PostgreSQL.");

        } catch (Exception e) {
            logger.error("Failed to load CSV data: {}", e.getMessage(), e);
        }
    }

    // Helper methods to handle unit strings like "ms" or "Mbps"
    private String cleanNumeric(String val) {
        if (val == null) return "0";
        return val.replaceAll("[^0-9.]", "");
    }

    private Integer cleanInt(String val) {
        if (val == null) return 0;
        String cleaned = val.replaceAll("[^0-9]", "");
        return cleaned.isEmpty() ? 0 : Integer.parseInt(cleaned);
    }
}