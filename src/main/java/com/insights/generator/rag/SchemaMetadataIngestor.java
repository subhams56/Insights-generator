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
        logger.info("Initializing 5G Schema Metadata in Vector Store...");

        // In a real scenario, this could be read from a JSON or Markdown file
//        String networkDataSchema = """
//            Table: network_metrics
//            Description: Contains hourly aggregated 5G network performance data.
//            Columns:
//            - timestamp (TIMESTAMP): The time the metric was recorded.
//            - region_id (VARCHAR): The geographic region code (e.g., 'North', 'South').
//            - cell_id (VARCHAR): The unique identifier for the 5G cell tower.
//            - avg_latency_ms (NUMERIC): The average network latency in milliseconds.
//            - download_speed_mbps (NUMERIC): Average download throughput.
//            - upload_speed_mbps (NUMERIC): Average upload throughput.
//            - packet_loss_pct (NUMERIC): Percentage of packet loss.
//            - active_users (INTEGER): Number of connected user equipments (UEs).
//            """;

        String networkDataSchema = """
    Table: network_metrics
    Description: Contains performance metrics for the 5G network across various regions.
    Columns:
    - timestamp (TIMESTAMP): The date and time when the metrics were recorded.
    - region_id (VARCHAR): The name of the city or region (e.g., 'Berlin', 'Delhi', 'Kolkata').
    - cell_id (VARCHAR): The unique identifier for the specific cell tower or device model.
    - avg_latency_ms (NUMERIC): The average network delay in milliseconds. Use this for questions about 'latency', 'ping', or 'delay'.
    - download_speed_mbps (NUMERIC): Average download throughput in Megabits per second.
    - upload_speed_mbps (NUMERIC): Average upload throughput in Megabits per second.
    - packet_loss_pct (NUMERIC): Percentage of data packets lost during transmission. Use this as a proxy for 'churn', 'stability', or 'quality' issues.
    - active_users (INTEGER): Total number of users connected to the cell at that time.
    """;

        Document schemaDoc = new Document(networkDataSchema, Map.of(
                "type", "schema",
                "domain", "5G_telecom",
                "table", "network_metrics"
        ));

        // Add to pgvector
        vectorStore.add(List.of(schemaDoc));
        logger.info("Schema metadata ingested successfully.");
    }
}