package com.insights.generator.rag;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class SchemaMetadataIngestor {

    private static final Logger logger = LoggerFactory.getLogger(SchemaMetadataIngestor.class);
    private final VectorStore vectorStore;

    public SchemaMetadataIngestor(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void ingestSchemaMetadata() {
        logger.info("Initializing US-Centric 5G Schema Metadata in Vector Store...");

        // Updated Schema Document based on refined_network_data
        String networkDataSchema = """
            Table: refined_network_metrics
            Description: US-Centric comprehensive performance metrics for a 5G telecom network. 
            
            DATA RELEVANCE CONSTRAINTS:
            - Historical tracking from June 2024 to May 2025. 
            
            EXACT SCHEMA COLUMNS (DO NOT INVENT NAMES):
            - timestamp (TIMESTAMP): The exact time of the record. Use this for date/time filtering (e.g., timestamp >= '2024-11-01').
            - hour_of_day (INTEGER): 0-23.
            - is_peak_hour (INTEGER): 1 for peak, 0 for off-peak.
            - region (VARCHAR): Northeast, Midwest, South, West.
            - state (VARCHAR): NY, IL, TX, CA, FL, WA.
            - city (VARCHAR): Specific city names.
            - network_band (VARCHAR): 5G mmWave, 5G Sub-6, 4G LTE.
            - environment_type (VARCHAR): Urban, Suburban, Rural.
            - avg_latency_ms (NUMERIC): Use this for latency queries.
            - download_speed_mbps (NUMERIC): Downlink speed.
            - upload_speed_mbps (NUMERIC): Uplink speed.
            - packet_loss_pct (NUMERIC): Packet loss percentage.
            - active_users (INTEGER): Total users connected.
            - network_utilization_pct (NUMERIC): Network load/utilization.
            - congestion_level (VARCHAR): 'Low', 'Moderate', 'High', 'Critical'.
            - dropped_calls (INTEGER): Count of failed connections.
            - weather_condition (VARCHAR): 'Clear', 'Rain', etc.
            - quality_score (NUMERIC): Derived overall network health KPI.
            
            CRITICAL SQL RULES:
            - NEVER use columns like 'latency_ms', 'record_date', or 'packet_loss_percentage'. ONLY use the exact names listed above.
            - If a user asks for a region/state not in the valid list, return: SELECT 'Target region not in active US deployment zones' AS status;
            """;

        Document schemaDoc = new Document(networkDataSchema, Map.of(
                "type", "schema",
                "domain", "5G_telecom_US",
                "table", "refined_network_metrics"
        ));

        // Note: You might want to TRUNCATE your vector_store table before running this to clear the old schema embeddings!
        vectorStore.add(List.of(schemaDoc));
        logger.info("US-Centric Schema metadata ingested successfully.");
    }
}