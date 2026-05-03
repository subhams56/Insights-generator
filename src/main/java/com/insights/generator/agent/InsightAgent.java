package com.insights.generator.agent;

import com.insights.generator.model.QueryLog;
import com.insights.generator.repository.QueryLogRepository;
import com.insights.generator.repository.SafeSqlExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class InsightAgent {

    private static final Logger logger = LoggerFactory.getLogger(InsightAgent.class);

    private final ChatClient chatClient;
    private final NLQAgent nlqAgent;
    private final QueryLogRepository logRepository;
    private final VectorStore vectorStore;
    private final SafeSqlExecutor sqlExecutor;
    private final String defaultModel; // Storing default for logging

    @Value("${agent.cache.enabled:true}")
    private boolean cacheEnabled;

    private final String SYSTEM_PROMPT = """
    You are a PostgreSQL expert for a US-based 5G Telecom dataset.
    
    SCHEMA CONTEXT:
    {schema_context}

    CRITICAL RULES:
    1. The dataset contains HISTORICAL data spanning from JUNE 2024 to MAY 2025. 
    2. ALWAYS return a valid SQL SELECT statement.
    3. STRICT SCHEMA ADHERENCE: You MUST ONLY use the exact column names provided in the SCHEMA CONTEXT. DO NOT invent or assume column names like 'record_date' or 'latency_ms'. If it is not in the context, do not query it.
    4. Use COALESCE(metric, 0) to avoid nulls.
    5. Only return the SQL code, no explanations.
    """;

    private static final String INSIGHT_SYSTEM_PROMPT = """
            You are a Senior 5G Telecom Data Analyst for a major US carrier. 
            Your job is to take raw, structured database results and transform them into an executive-friendly business insight.
            
            Guidelines:
            1. DO NOT mention SQL, databases, or JSON formatting.
            2. FOCUS ON BUSINESS VALUE: Look for correlations. For example, does bad weather correlate with dropped calls on mmWave bands? Do rural areas have higher latency?
            3. FORMATTING: Use Markdown. Use bolding for key metrics (e.g., **15.2 Mbps**). Use bullet points if comparing multiple items.
            4. TONE: Professional, confident, and analytical.
            5. If the raw data is empty, state: "There is no data available for this specific query in the current reporting period."
            
            RAW DATA TO ANALYZE:
            {raw_data}
            """;

    public InsightAgent(ChatClient.Builder chatClientBuilder,
                        NLQAgent nlqAgent,
                        QueryLogRepository logRepository,
                        @Value("${spring.ai.google.genai.chat.options.model}") String model, VectorStore vectorStore, SafeSqlExecutor sqlExecutor) {

        this.logRepository = logRepository;
        this.nlqAgent = nlqAgent;
        this.vectorStore = vectorStore;
        this.sqlExecutor = sqlExecutor;
        this.defaultModel = model;

        GoogleGenAiChatOptions options = GoogleGenAiChatOptions.builder().build();
        options.setModel(model);

        this.chatClient = chatClientBuilder
                .defaultOptions(options)
                .build();
    }

    public Map<String, Object> analyze(String userQuestion, String requestedModel) {
        String targetModel = (requestedModel != null && !requestedModel.trim().isEmpty()) ? requestedModel : defaultModel;
        logger.info("Insight Agent beginning analysis for: '{}' using Model: {}", userQuestion, targetModel);

        if (cacheEnabled) {
            String squeezedQuestion = userQuestion.replaceAll("[^a-zA-Z0-9]", "").toLowerCase();
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
        }

        try {
            // Pass model downstream for the SQL generation LLM call
            List<Map<String, Object>> rawData = fetchRawDataOnly(userQuestion, requestedModel);

            if (rawData == null || rawData.isEmpty()) {
                return Map.of(
                        "question", userQuestion,
                        "response", "No data found to analyze for this request.",
                        "agent", "INSIGHT_AGENT"
                );
            }

            // Build Insight Prompt
            var promptSpec = chatClient.prompt()
                    .system(s -> s.text(INSIGHT_SYSTEM_PROMPT).param("raw_data", rawData.toString()))
                    .user(userQuestion);

            // DYNAMIC MODEL OVERRIDE
            if (requestedModel != null && !requestedModel.trim().isEmpty()) {
                promptSpec.options(GoogleGenAiChatOptions.builder().model(requestedModel).build());
            }

            String insightResponse = promptSpec.call().content();
            logger.info("Insight generation complete.");

            if (cacheEnabled) {
                saveToCache(userQuestion, insightResponse, rawData.toString());
            }

            return Map.of(
                    "agent", "INSIGHT_AGENT",
                    "question", userQuestion,
                    "response", insightResponse,
                    "rawData", rawData, // Maintained as object for JSON serialization
                    "source", "LLM-RAG",
                    "modelUsed", targetModel
            );

        } catch (Exception e) {
            logger.error("Insight Analysis failed: ", e);
            return Map.of("error", "Analysis failed", "details", e.getMessage());
        }
    }

    private void saveToCache(String userQuestion, String insightResponse, String rawResponse) {
        logRepository.save(new QueryLog(userQuestion, insightResponse, rawResponse));
    }


    public List<Map<String, Object>> fetchRawDataOnly(String userQuestion, String requestedModel) {
        logger.info("NLQ Agent fetching raw data for Insight Agent...");
        try {
            List<Document> similarDocuments = vectorStore.similaritySearch(
                    SearchRequest.builder()
                            .query(userQuestion)
                            .topK(2)
                            .build()
            );
            String schemaContext = similarDocuments.stream()
                    .map(Document::getText)
                    .collect(Collectors.joining("\n"));

            var sqlPromptSpec = chatClient.prompt()
                    .system(s -> s.text(SYSTEM_PROMPT).param("schema_context", schemaContext))
                    .user(userQuestion);

            // DYNAMIC MODEL OVERRIDE
            if (requestedModel != null && !requestedModel.trim().isEmpty()) {
                sqlPromptSpec.options(GoogleGenAiChatOptions.builder().model(requestedModel).build());
            }

            String rawSqlResponse = sqlPromptSpec.call().content();
            String cleanSql = cleanSqlOutput(rawSqlResponse);

            return sqlExecutor.executeReadOnlyQuery(cleanSql);

        } catch (Exception e) {
            logger.error("Error fetching raw data for insight: ", e);
            throw new RuntimeException("Failed to fetch raw data for analysis.");
        }
    }

    private String cleanSqlOutput(String sql) {
        if (sql == null) return "";
        return sql.replaceAll("```sql|```", "").trim();
    }
}