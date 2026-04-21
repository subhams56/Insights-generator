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

/**
 * <h3>Schema Metadata Ingestor</h3>
 * <p>
 * This class acts as the "Instruction Manual" for the AI. It is responsible for injecting
 * technical knowledge about our database structure into the Vector Store (pgvector).
 * </p>
 * * <b>Key Responsibilities:</b>
 * <ul>
 * <li>Defines the 5G Network database schema in plain English for the LLM.</li>
 * <li>Provides business constraints (e.g., reminding the AI that all data is from June 2024).</li>
 * <li>Maps natural language terms like 'churn' or 'stability' to specific database columns.</li>
 * <li>Supplies a list of valid regions to prevent the AI from querying non-existent data.</li>
 * </ul>
 * * <p>
 * This ingestion runs automatically once the application is fully started and ready.
 * </p>
 */

@Component
public class SchemaMetadataIngestor {

    private static final Logger logger = LoggerFactory.getLogger(SchemaMetadataIngestor.class);
    private final VectorStore vectorStore;

    public SchemaMetadataIngestor(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void ingestSchemaMetadata() {
        logger.info("Initializing 5G Schema Metadata in Vector Store...");

        // The "Brain" of your RAG - providing schema, regions, and the 2024 time constraint.
        String networkDataSchema = """
            Table: network_metrics
            Description: Comprehensive performance metrics for the 5G network. 
            
            DATA RELEVANCE CONSTRAINTS:
            - ALL DATA IS FROM JUNE 2024. 
            - If a user asks for 'today', 'this month', or 'current data', you MUST query for JUNE 2024.
            
            AVAILABLE REGIONS (Values in 'region_id' column): 
            Mumbai, Kolkata, New York, Delhi, Chennai, Tokyo, San Francisco, Berlin.
            
            Columns:
            - timestamp (TIMESTAMP): Recorded time (Data exists only for 2024-06).
            - region_id (VARCHAR): City name. Use ONLY from the AVAILABLE REGIONS list.
            - cell_id (VARCHAR): Tower/Device identifier.
            - avg_latency_ms (NUMERIC): Network delay.
            - download_speed_mbps (NUMERIC): Downlink speed.
            - upload_speed_mbps (NUMERIC): Uplink speed.
            - packet_loss_pct (NUMERIC): Packet loss. Use as primary metric for 'CHURN' or 'STABILITY'.
            - active_users (INTEGER): User count.
            
            FALLBACK RULE:
            If a user asks for a region NOT in the list (e.g., Andhra Pradesh), return a SQL query that selects the missing status:
            SELECT 'Region not found. Please choose from: Mumbai, Kolkata, New York, Delhi, Chennai, Tokyo, San Francisco, Berlin' AS status;
            """;

        Document schemaDoc = new Document(networkDataSchema, Map.of(
                "type", "schema",
                "domain", "5G_telecom",
                "table", "network_metrics"
        ));

        // Add to pgvector store
        vectorStore.add(List.of(schemaDoc));
        logger.info("Schema metadata with 2024 time-context and region constraints ingested successfully.");
    }
}