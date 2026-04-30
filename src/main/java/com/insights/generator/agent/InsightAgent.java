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

/**
 * InsightAgent is a Spring-managed service responsible for transforming raw database query results
 * into executive-friendly business insights using LLM-powered analysis.
 *
 * PRIMARY RESPONSIBILITIES:
 * - Orchestrate the analysis workflow by delegating SQL execution to NLQAgent
 * - Process raw data through Google Generative AI to generate business insights
 * - Manage a query response cache to optimize performance and reduce LLM API calls
 * - Persist frequently accessed insights for faster retrieval
 *
 * ANALYSIS FLOW:
 * 1. User submits a natural language question via analyze(String)
 * 2. Cache lookup is performed (if enabled) using lexical matching
 * 3. If not cached, NLQAgent executes RAG + SQL to fetch raw data
 * 4. Raw data is sent to Google Generative AI with a curated system prompt
 * 5. LLM generates a professional, Markdown-formatted business insight
 * 6. Result is cached and returned to the caller
 *
 * KEY FEATURES:
 * - L1 Cache Layer: Reduces latency by matching user questions against historical queries
 * - Smart Lexical Matching: Normalizes questions (removes special characters, lowercases) for better cache hits
 * - Professional Output Formatting: Ensures insights focus on business value with no technical jargon
 * - Error Resilience: Gracefully handles failures with informative error messages
 *
 * CONFIGURATION PROPERTIES:
 * - spring.ai.google.genai.chat.options.model: The Google Generative AI model to use
 * - agent.cache.enabled: Enable/disable query caching (default: true)
 *
 * DEPENDENCIES:
 * - ChatClient: Spring AI client for LLM interaction
 * - NLQAgent: Handles Natural Language Query execution and RAG
 * - QueryLogRepository: Manages query cache persistence
 *
 * DOMAIN CONTEXT:
 * This agent specializes in 5G Telecom Data Analysis and generates insights optimized
 * for senior stakeholders and business decision-makers. It assumes data is available
 * for the current reporting period (June 2024) and handles empty result sets gracefully.
 *
 * @author Your Name
 * @version 1.0
 * @see NLQAgent
 * @see QueryLogRepository
 * @since 1.0
 */

@Service
public class InsightAgent {

    private static final Logger logger = LoggerFactory.getLogger(InsightAgent.class);

    private final ChatClient chatClient;
    private final NLQAgent nlqAgent;
    private final QueryLogRepository logRepository;
    private final VectorStore vectorStore;
    private final SafeSqlExecutor sqlExecutor;

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

    // FIX 1: Pass the model value directly into the constructor
    public InsightAgent(ChatClient.Builder chatClientBuilder,
                        NLQAgent nlqAgent,
                        QueryLogRepository logRepository,
                        @Value("${spring.ai.google.genai.chat.options.model}") String model, VectorStore vectorStore, SafeSqlExecutor sqlExecutor) {

        this.logRepository = logRepository;
        this.nlqAgent = nlqAgent;
        this.vectorStore = vectorStore;
        this.sqlExecutor = sqlExecutor;

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
            List<Map<String, Object>> rawData = fetchRawDataOnly(userQuestion);

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


    public List<Map<String, Object>> fetchRawDataOnly(String userQuestion) {
        logger.info("NLQ Agent fetching raw data for Insight Agent...");
        try {
            // 1. Retrieve Schema Metadata (RAG)
            List<Document> similarDocuments = vectorStore.similaritySearch(
                    SearchRequest.builder()
                            .query(userQuestion)
                            .topK(2)
                            .build()
            );
            // 1. Get Schema Context
            String schemaContext = similarDocuments.stream()
                    .map(Document::getText)
                    .collect(Collectors.joining("\n"));

            // 2. Generate SQL
            String rawSqlResponse = chatClient.prompt()
                    .system(s -> s.text(SYSTEM_PROMPT).param("schema_context", schemaContext))
                    .user(userQuestion)
                    .call()
                    .content();

            String cleanSql = cleanSqlOutput(rawSqlResponse);

            // 3. Execute and return raw data directly
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