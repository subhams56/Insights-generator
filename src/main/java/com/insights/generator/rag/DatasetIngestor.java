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

    @Value("classpath:data/5g_network_data.csv")
    private Resource csvFile;

    // Batch size of 100-500 is optimal for cloud databases like Neon
    private static final int BATCH_SIZE = 200;

    public DatasetIngestor(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void loadDataOnStartup() {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM network_metrics", Long.class);

        if (count != null && count > 0) {
            logger.info("Database already contains {} records. Skipping CSV ingestion.", count);
            return;
        }

        logger.info("Starting Batch CSV data ingestion into network_metrics via Cloud Link...");

        String sql = "INSERT INTO network_metrics (timestamp, region_id, cell_id, avg_latency_ms, download_speed_mbps, upload_speed_mbps, packet_loss_pct, active_users) " +
                "VALUES (CAST(? AS TIMESTAMP), ?, ?, CAST(? AS NUMERIC), CAST(? AS NUMERIC), CAST(? AS NUMERIC), CAST(? AS NUMERIC), CAST(? AS INTEGER))";

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(csvFile.getInputStream(), StandardCharsets.UTF_8));
             CSVParser csvParser = new CSVParser(reader, CSVFormat.DEFAULT.withFirstRecordAsHeader().withIgnoreHeaderCase().withTrim())) {

            List<Object[]> batchArgs = new ArrayList<>();

            for (CSVRecord record : csvParser) {
                try {
                    Object[] params = new Object[]{
                            record.get("Timestamp"),
                            record.get("Location"),
                            record.get("Device Model"),
                            cleanNumeric(record.get("Latency (ms)")),
                            cleanNumeric(record.get("Download Speed (Mbps)")),
                            cleanNumeric(record.get("Upload Speed (Mbps)")),
                            cleanNumeric(record.get("Jitter (ms)")),
                            cleanInt(record.get("Ping to Google (ms)"))
                    };
                    batchArgs.add(params);

                    // When batch size is reached, push to DB and clear list
                    if (batchArgs.size() >= BATCH_SIZE) {
                        jdbcTemplate.batchUpdate(sql, batchArgs);
                        batchArgs.clear();
                        logger.info("Inserted batch of {} rows...", BATCH_SIZE);
                    }
                } catch (Exception e) {
                    logger.warn("Skipping bad row: {} - Reason: {}", record.getRecordNumber(), e.getMessage());
                }
            }

            // Final push for remaining records
            if (!batchArgs.isEmpty()) {
                jdbcTemplate.batchUpdate(sql, batchArgs);
            }

            logger.info("Successfully finished Cloud Ingestion into Neon.");

        } catch (Exception e) {
            logger.error("Failed to load CSV data: {}", e.getMessage(), e);
        }
    }

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