package com.insights.generator.agent;

import com.insights.generator.model.QueryLog;
import com.insights.generator.repository.QueryLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class InsightAgent {

    private static final Logger logger = LoggerFactory.getLogger(InsightAgent.class);

    private final ChatClient chatClient;
    private final NLQAgent nlqAgent;
    private final QueryLogRepository logRepository;

    @Value("${agent.cache.enabled:true}")
    private boolean cacheEnabled;

    private static final String INSIGHT_SYSTEM_PROMPT = """
            You are a Senior 5G Telecom Data Analyst. 
            Your job is to take raw, structured database results and transform them into an executive-friendly business insight.
            
            Guidelines:
            1. DO NOT mention SQL, databases, or JSON formatting.
            2. FOCUS ON BUSINESS VALUE: Calculate percentage differences, identify outliers (best/worst performers), and summarize trends.
            3. FORMATTING: Use Markdown. Use bolding for key metrics (e.g., **15.2 Mbps**). Use bullet points if comparing multiple items.
            4. TONE: Professional, confident, and analytical.
            5. If the raw data is empty, state: "There is no data available for this specific query in the current reporting period (June 2024)."
            
            RAW DATA TO ANALYZE:
            {raw_data}
            """;

    // FIX 1: Pass the model value directly into the constructor
    public InsightAgent(ChatClient.Builder chatClientBuilder,
                        NLQAgent nlqAgent,
                        QueryLogRepository logRepository,
                        @Value("${spring.ai.google.genai.chat.options.model}") String model) {

        this.logRepository = logRepository;
        this.nlqAgent = nlqAgent;

        GoogleGenAiChatOptions options = GoogleGenAiChatOptions.builder().build();
        options.setModel(model); // 'model' is now safely populated!

        this.chatClient = chatClientBuilder
                .defaultOptions(options)
                .build();
    }

    public Map<String, Object> analyze(String userQuestion) {
        logger.info("Insight Agent beginning analysis for: '{}'", userQuestion);

        // --- L1 CACHE LOOKUP ---
        if (cacheEnabled) {
            String squeezedQuestion = userQuestion.replaceAll("[^a-zA-Z0-9]", "").toLowerCase();

            // 2. Query the database using the smart match
            Optional<QueryLog> cachedEntry = logRepository.findSmartLexicalMatch(squeezedQuestion);

            if (cachedEntry.isPresent()) {
                logger.info(" Cache HIT! Matched '{}' with stored question: '{}'",
                        userQuestion, cachedEntry.get().getQuestion());

                return Map.of(
                        "agent", "INSIGHT_AGENT (Cached)",
                        "question", userQuestion,
                        "response", cachedEntry.get().getResponse(),
                        "rawData", cachedEntry.get().getRawData(),
                        "source", "CACHE"
                );
            }
        } else {
            logger.info("Cache is DISABLED");
        }

        try {
            // 1. Delegate the heavy lifting (RAG + SQL + Execution) to the NLQ Agent
            List<Map<String, Object>> rawData = nlqAgent.fetchRawDataOnly(userQuestion);

            if (rawData == null || rawData.isEmpty()) {
                return Map.of(
                        "question", userQuestion,
                        "response", "No data found to analyze for this request.",
                        "agent", "INSIGHT_AGENT"
                );
            }

            // 2. Pass the data to the LLM to generate the business insight
            String insightResponse = chatClient.prompt()
                    .system(s -> s.text(INSIGHT_SYSTEM_PROMPT).param("raw_data", rawData.toString()))
                    .user(userQuestion)
                    .call()
                    .content();

            logger.info("Insight generation complete.");

            // --- PERSIST TO CACHE ---
            if (cacheEnabled) {
                saveToCache(userQuestion, insightResponse, rawData.toString());
            }

            // 3. Standard Map structure for your controller/UI
            return Map.of(
                    "agent", "INSIGHT_AGENT",
                    "question", userQuestion,
                    "response", insightResponse,
                    "rawData", rawData.toString(),
                    "source", "LLM-RAG"

            );

        } catch (Exception e) {
            logger.error("Insight Analysis failed: ", e);
            return Map.of("error", "Analysis failed", "details", e.getMessage());
        }
    }

    private void saveToCache(String userQuestion, String insightResponse, String rawResponse) {
        logRepository.save(new QueryLog(userQuestion, insightResponse, rawResponse));
    }
}