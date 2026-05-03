package com.insights.generator.agent;

import com.insights.generator.model.QueryLog;
import com.insights.generator.repository.QueryLogRepository;
import com.insights.generator.repository.SafeSqlExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions; // <-- NEW IMPORT
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
    private final String currentModelName;


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

    private final String REFINER_PROMPT = """
    You are a professional 5G Telecom Data Analyst for the US market. 
    You are presented with a User's Question and the Result of a database query specifically designed to answer that question.
    
    USER QUESTION: {user_question}
    DATABASE RESULT: {raw_data}
    
    INSTRUCTIONS:
    1. Interpret the DATABASE RESULT as the direct answer to the USER QUESTION. 
    2. If the result contains a single value (like 'New York'), state it clearly as the answer (e.g., 'New York has the lowest packet loss').
    3. Do not apologize for 'only' having one region; that region is the result of the filtering logic.
    4. If the DATABASE RESULT is empty or null, explain that no data matches the criteria for the tracked period.
    5. Be confident, concise, and professional.
    """;

    public NLQAgent(ChatClient.Builder chatClientBuilder, VectorStore vectorStore, SafeSqlExecutor sqlExecutor, QueryLogRepository logRepository, GoogleGenAiChatModel chatModel) {
        this.chatClient = chatClientBuilder.build();
        this.vectorStore = vectorStore;
        this.sqlExecutor = sqlExecutor;
        this.logRepository = logRepository;
        this.currentModelName = chatModel.getDefaultOptions().getModel();
    }

    public Object processQuestion(String userQuestion, String requestedModel) {
        List<Document> similarDocuments = vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query(userQuestion)
                        .topK(2)
                        .build()
        );

        String schemaContext = similarDocuments.stream()
                .map(Document::getText)
                .collect(Collectors.joining("\n"));

        String targetModel = (requestedModel != null && !requestedModel.trim().isEmpty()) ? requestedModel : currentModelName;
        logger.info("Directing SQL Generation to Model: {}", targetModel);
        logger.info("Generating SQL for question: {}", userQuestion);

        try {
            // Build Prompt
            var promptSpec = chatClient.prompt()
                    .system(sp -> sp.text(SYSTEM_PROMPT).param("schema_context", schemaContext))
                    .user(userQuestion);

            // DYNAMIC MODEL OVERRIDE
            if (requestedModel != null && !requestedModel.trim().isEmpty()) {
                promptSpec.options(GoogleGenAiChatOptions.builder().model(requestedModel).build());
            }

            String generatedSql = promptSpec.call().content();
            generatedSql = cleanSqlOutput(generatedSql);
            logger.info("Generated SQL for Execution: \n---\n{}\n---", generatedSql);

            return sqlExecutor.executeReadOnlyQuery(generatedSql);

        } catch (Exception e) {
            logger.error("FULL ERROR DETAIL: ", e);
            String errorMessage = e.getMessage();
            logger.error("Error in SQL generation/execution flow: {}", errorMessage);

            return Map.of(
                    "error", "SERVICE_INTERRUPTION",
                    "details", errorMessage.contains("429") ? "AI Quota exceeded. Please wait a moment." : errorMessage
            );
        }
    }

    public Map<String, Object> processQuestionV2(String userQuestion, String requestedModel) {
        logger.info("Cache is {}", caching ? "ENABLED" : "DISABLED");
        if(caching) {
            String squeezedQuestion = userQuestion.replaceAll("[^a-zA-Z0-9]", "").toLowerCase();
            Optional<QueryLog> cachedEntry = logRepository.findSmartLexicalMatch(squeezedQuestion);

            if (cachedEntry.isPresent()) {
                logger.info(" Cache HIT! Matched '{}' with stored question: '{}'",
                        userQuestion, cachedEntry.get().getQuestion());
                return Map.of(
                        "agent", "NLQ_AGENT (Cache)",
                        "question", userQuestion,
                        "response", cachedEntry.get().getResponse(),
                        "rawData", cachedEntry.get().getRawData(),
                        "source", "CACHE"
                );
            }
        }

        // Pass requestedModel to raw data fetcher
        Object rawResponse = processQuestion(userQuestion, requestedModel);

        if (rawResponse instanceof Map && ((Map<?, ?>) rawResponse).containsKey("error")) {
            return (Map<String, Object>) rawResponse;
        }

        try {
            String dataString = rawResponse.toString();
            String targetModel = (requestedModel != null && !requestedModel.trim().isEmpty()) ? requestedModel : currentModelName;
            logger.info("Directing Refiner to Model: {}", targetModel);

            var refinerPromptSpec = chatClient.prompt()
                    .system(sp -> sp.text(REFINER_PROMPT)
                            .param("user_question", userQuestion)
                            .param("raw_data", dataString))
                    .user("Please summarize the findings.");

            // DYNAMIC MODEL OVERRIDE
            if (requestedModel != null && !requestedModel.trim().isEmpty()) {
                refinerPromptSpec.options(GoogleGenAiChatOptions.builder().model(requestedModel).build());
            }

            String refinedAnswer = refinerPromptSpec.call().content();

            logRepository.save(new QueryLog(userQuestion, refinedAnswer, rawResponse.toString()));

            return Map.of(
                    "agent", "NLQ_AGENT",
                    "question", userQuestion,
                    "response", refinedAnswer,
                    "rawData", rawResponse,
                    "source", "LLM-RAG",
                    "modelUsed", targetModel // Optional: Expose which model served the request
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

    private String cleanSqlOutput(String sql) {
        if (sql == null) return "";
        return sql.replaceAll("```sql|```", "").trim();
    }
}