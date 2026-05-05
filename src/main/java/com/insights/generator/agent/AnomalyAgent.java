package com.insights.generator.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.insights.generator.model.AnomalyAlert;
import com.insights.generator.repository.AnomalyAlertRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class AnomalyAgent {

    private static final Logger logger = LoggerFactory.getLogger(AnomalyAgent.class);

    private final JdbcTemplate jdbcTemplate;
    private final ChatClient chatClient;
    private final AnomalyAlertRepository alertRepository;
    private final ObjectMapper objectMapper;

    // --- Dynamic Properties ---
    @Value("${anomaly.agent.suppression-hours:4}")
    private int suppressionHours;

    @Value("${anomaly.agent.threshold.quality-score:0.40}")
    private double qualityScoreThreshold;

    @Value("${anomaly.agent.threshold.quality-drops:5}")
    private int qualityDropsThreshold;

    @Value("${anomaly.agent.threshold.mmwave-latency:100}")
    private int mmWaveLatencyThreshold;

    @Value("${anomaly.agent.threshold.device-fault-drops:5000}")
    private int deviceFaultDropsThreshold;

    private final String SYSTEM_PROMPT = """
            You are an automated Network Operations Center (NOC) Diagnostic AI.
            Your job is to analyze anomalous telecom data and write a concise, urgent alert for executives.
            
            RULES:
            1. Keep the alert under 3 sentences.
            2. Identify the root cause if visible (e.g., correlate dropped calls with bad weather or specific bands).
            3. Start the message with a strong action verb.
            4. Use Markdown for emphasis.
            
            RAW ANOMALY DATA TO DIAGNOSE:
            {raw_data}
            """;

    public AnomalyAgent(JdbcTemplate jdbcTemplate,
                        ChatClient.Builder chatClientBuilder,
                        AnomalyAlertRepository alertRepository,
                        @Value("${spring.ai.google.genai.chat.options.model}") String defaultModel) {
        this.jdbcTemplate = jdbcTemplate;
        this.alertRepository = alertRepository;
        this.objectMapper = new ObjectMapper();

        GoogleGenAiChatOptions options = GoogleGenAiChatOptions.builder().model(defaultModel).build();
        this.chatClient = chatClientBuilder.defaultOptions(options).build();
    }

    // Configurable polling rate using fixedRateString
    @Scheduled(fixedRateString = "${anomaly.agent.polling-rate:900000}")
    public void monitorNetworkHealth() {
        logger.info("Anomaly Agent waking up... Running heuristic watchdog queries.");

        try {
            // 1. The LLM-Powered Complex Check
            checkQualityDegradationWithLLM();

            // 2. The Pure-SQL mmWave Latency Check
            checkMmWaveLatencyFault();

            // 3. The Pure-SQL Vendor Interoperability Check
            checkDeviceFirmwareFault();

        } catch (Exception e) {
            logger.error("Error during Anomaly Agent polling cycle: ", e);
        }
    }

    // ========================================================================
    // WATCHDOG 1: Complex Degradation (Uses LLM for Root Cause Analysis)
    // ========================================================================
    private void checkQualityDegradationWithLLM() throws Exception {
        String sql = """
            SELECT state, city, network_band, weather_condition,
                   ROUND(AVG(quality_score), 2) as avg_quality,
                   SUM(dropped_calls) as recent_dropped_calls
            FROM refined_network_metrics
            WHERE quality_score < ? AND dropped_calls > ? 
            GROUP BY state, city, network_band, weather_condition
            ORDER BY recent_dropped_calls DESC
            LIMIT 3;
            """;

        List<Map<String, Object>> anomalies = jdbcTemplate.queryForList(sql, qualityScoreThreshold, qualityDropsThreshold);
        if (anomalies.isEmpty()) return;

        // The state we are about to alert on (e.g., "CA")
        String state = (String) anomalies.get(0).get("state");

        // --- NEW CACHING/SUPPRESSION LOGIC ---
        // If we already alerted about this state recently, DO NOT call the LLM again.
        if (alertRepository.recentAlertExistsForTopic(state, suppressionHours)) {
            logger.info("Anomaly detected for {}, but an alert was already sent recently. Suppressing LLM call.", state);
            return;
        }
        // -------------------------------------

        logger.warn("Found {} degraded sectors. Waking up LLM for diagnosis.", anomalies.size());
        String rawDataJson = objectMapper.writeValueAsString(anomalies);

        String diagnosis = chatClient.prompt()
                .system(s -> s.text(SYSTEM_PROMPT).param("raw_data", rawDataJson))
                .user("Draft a critical alert summarizing this network failure.")
                .call()
                .content();

        AnomalyAlert alert = new AnomalyAlert("CRITICAL", "Quality Collapse in " + state, diagnosis, rawDataJson);
        alertRepository.save(alert);
    }

    // ========================================================================
    // WATCHDOG 2: Hardware Fault Check (Pure SQL & Java Code)
    // ========================================================================
    private void checkMmWaveLatencyFault() throws Exception {
        String sql = """
            SELECT state, city, network_band, MAX(avg_latency_ms) as peak_latency
            FROM refined_network_metrics
            WHERE network_band IN ('n258', 'n260') AND avg_latency_ms > ?
            GROUP BY state, city, network_band
            ORDER BY peak_latency DESC
            LIMIT 1;
            """;

        List<Map<String, Object>> hardwareFaults = jdbcTemplate.queryForList(sql, mmWaveLatencyThreshold);
        if (hardwareFaults.isEmpty()) return;

        Map<String, Object> fault = hardwareFaults.get(0);
        String state = (String) fault.get("state");
        String city = (String) fault.get("city");
        Number peakLatency = (Number) fault.get("peak_latency");

        // Hardcoded diagnostic message (No LLM cost)
        String hardcodedMessage = String.format(
                "**Hardware Alert:** High-frequency mmWave bands in **%s (%s)** are experiencing severe latency spikes of **%sms**. " +
                        "This far exceeds the <%sms SLA for this band. Dispatch field engineers to check backhaul transport and fiber connections.",
                city, state, peakLatency, mmWaveLatencyThreshold
        );

        String rawDataJson = objectMapper.writeValueAsString(hardwareFaults);
        AnomalyAlert alert = new AnomalyAlert("WARNING", "mmWave Latency Fault: " + state, hardcodedMessage, rawDataJson);
        alertRepository.save(alert);
        logger.info("Saved pure-SQL mmWave latency alert.");
    }

    // ========================================================================
    // WATCHDOG 3: Device/Vendor Fault Check (Pure SQL & Java Code)
    // ========================================================================
    private void checkDeviceFirmwareFault() throws Exception {
        String sql = """
            SELECT device_model, carrier, SUM(dropped_calls) as total_drops
            FROM refined_network_metrics
            GROUP BY device_model, carrier
            HAVING SUM(dropped_calls) > ?
            ORDER BY total_drops DESC
            LIMIT 1;
            """;

        List<Map<String, Object>> deviceFaults = jdbcTemplate.queryForList(sql, deviceFaultDropsThreshold);
        if (deviceFaults.isEmpty()) return;

        Map<String, Object> fault = deviceFaults.get(0);
        String device = (String) fault.get("device_model");
        String carrier = (String) fault.get("carrier");
        Number drops = (Number) fault.get("total_drops");

        // Hardcoded diagnostic message (No LLM cost)
        String hardcodedMessage = String.format(
                "**Vendor Interoperability Alert:** An unusually high concentration of dropped calls (**%s**) has been detected for **%s** users on the **%s** carrier profile. " +
                        "Recommend checking recent OEM firmware updates or carrier bundle configurations.",
                drops, device, carrier
        );

        String rawDataJson = objectMapper.writeValueAsString(deviceFaults);
        AnomalyAlert alert = new AnomalyAlert("WARNING", "Firmware Conflict: " + device, hardcodedMessage, rawDataJson);
        alertRepository.save(alert);
        logger.info("Saved pure-SQL device fault alert.");
    }
}