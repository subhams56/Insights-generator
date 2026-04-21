package com.insights.generator.agent;

import com.insights.generator.model.QueryLog;
import com.insights.generator.repository.QueryLogRepository;
import com.insights.generator.repository.SafeSqlExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class NLQAgent {

    @Value("${cache.enabled}")
    private boolean caching;

    private static final Logger logger = LoggerFactory.getLogger(NLQAgent.class);
    private final ChatClient chatClient;
    private final VectorStore vectorStore;
    private final SafeSqlExecutor sqlExecutor;
    private final QueryLogRepository logRepository;


    private final String SYSTEM_PROMPT = """
    You are a PostgreSQL expert for a 5G Telecom dataset.
    
    SCHEMA CONTEXT:
    {schema_context}

    CRITICAL RULES:
    1. The dataset contains HISTORICAL data from JUNE 2024. If the user asks for 'this month' or 'now', use '2024-06-01' as the reference point.
    2. ALWAYS return a valid SQL SELECT statement.
    3. Use COALESCE(metric, 0) to avoid nulls.
    4. Only return the SQL code, no explanations.
    """;

    private final String REFINER_PROMPT = """
    You are a professional 5G Telecom Data Analyst. 
    You are presented with a User's Question and the Result of a database query specifically designed to answer that question.
    
    USER QUESTION: {user_question}
    DATABASE RESULT: {raw_data}
    
    INSTRUCTIONS:
    1. Interpret the DATABASE RESULT as the direct answer to the USER QUESTION. 
    2. If the result contains a single value (like 'Berlin'), state it clearly as the answer (e.g., 'Berlin has the lowest packet loss').
    3. Do not apologize for 'only' having one region; that region is the result of the filtering logic.
    4. If the DATABASE RESULT is empty or null, explain that no data matches the criteria for the period of June 2024.
    5. Be confident, concise, and professional.
    """;

    public NLQAgent(ChatClient.Builder chatClientBuilder, VectorStore vectorStore, SafeSqlExecutor sqlExecutor, QueryLogRepository logRepository, GoogleGenAiChatModel chatModel) {
        this.chatClient = chatClientBuilder.build();
        this.vectorStore = vectorStore;
        this.sqlExecutor = sqlExecutor;
        this.logRepository = logRepository;
        this.currentModelName = chatModel.getDefaultOptions().getModel();
    }

    private final String currentModelName;

    public Object processQuestion(String userQuestion) {
        // 1. Retrieve Schema Metadata (RAG)
        List<Document> similarDocuments = vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query(userQuestion)
                        .topK(2)
                        .build()
        );

        String schemaContext = similarDocuments.stream()
                .map(Document::getText)
                .collect(Collectors.joining("\n"));

        logger.info("Directing query to Model: {}", currentModelName);
        logger.info("Generating SQL for question: {}", userQuestion);

        try {
            // 2. Generate SQL using ChatClient with system/user prompt
            String generatedSql = chatClient.prompt()
                    .system(sp -> sp.text(SYSTEM_PROMPT).param("schema_context", schemaContext))
                    .user(userQuestion)
                    .call()
                    .content();

            // Clean markdown backticks
            generatedSql = cleanSqlOutput(generatedSql);
            logger.info("Generated SQL for Execution: \n---\n{}\n---", generatedSql);

            // 3. Execute Query
            List<Map<String, Object>> queryResults = sqlExecutor.executeReadOnlyQuery(generatedSql);
            return queryResults;

        } catch (Exception e) {
            // Handle Quota (429) or other API/SQL issues gracefully
            logger.error("FULL ERROR DETAIL: ", e);
            String errorMessage = e.getMessage();
            logger.error("Error in SQL generation/execution flow: {}", errorMessage);

            return Map.of(
                    "error", "SERVICE_INTERRUPTION",
                    "details", errorMessage.contains("429") ? "AI Quota exceeded. Please wait a moment." : errorMessage
            );
        }
    }

    public Map<String, Object> processQuestionV2(String userQuestion) {
        logger.info("Cache is {}", caching ? "ENABLED" : "DISABLED");
        if(caching) {
            // 1. CHECK PERSISTENT LOG CACHE
            Optional<QueryLog> cachedEntry = logRepository.findFirstByQuestionOrderByCreatedAtDesc(userQuestion);


            if (cachedEntry.isPresent()) {
                logger.info("Cache HIT: Returning stored results for: {}", userQuestion);
                return Map.of(
                        "agent", "NLQ_AGENT (Cache)",
                        "question", userQuestion,
                        "response", cachedEntry.get().getResponse(),
                        "rawData", cachedEntry.get().getRawData(),
                        "source", "CACHE"
                );
            }
        }

        Object rawResponse = processQuestion(userQuestion);

        // If the inner processQuestion returned an error map, return it immediately
        if (rawResponse instanceof Map && ((Map<?, ?>) rawResponse).containsKey("error")) {
            return (Map<String, Object>) rawResponse;
        }

        try {
            // Convert raw results to string for the LLM
            String dataString = rawResponse.toString();

            // 2. Call LLM for the second time to "Refine" the data into English
            String refinedAnswer = chatClient.prompt()
                    .system(sp -> sp.text(REFINER_PROMPT)
                            .param("user_question", userQuestion)
                            .param("raw_data", dataString))
                    .user("Please summarize the findings.")
                    .call()
                    .content();

            // 3. Save successful interaction to log
            logRepository.save(new QueryLog(userQuestion, refinedAnswer, rawResponse.toString()));

            return Map.of(
                    "agent", "NLQ_AGENT",
                    "question", userQuestion,
                    "response", refinedAnswer,
                    "rawData", rawResponse,
                    "source", "LLM-RAG"
            );

        } catch (Exception e) {
            logger.error("Refiner LLM Call Failed: {}", e.getMessage());
            return Map.of(
                    "error", "REFINER_UNAVAILABLE",
                    "raw_data", rawResponse,
                    "details", "Could not generate human-readable summary, but raw data is available."
            );
        }
    }

    /**
     * Used by the Insight Agent. Generates and executes SQL, but returns the raw
     * JSON/List data instead of a refined English string.
     */
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