package com.insights.generator.agent;

import com.insights.generator.model.QueryLog;
import com.insights.generator.repository.QueryLogRepository;
import com.insights.generator.repository.SafeSqlExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class NLQAgent {

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

    public NLQAgent(ChatClient.Builder chatClientBuilder, VectorStore vectorStore, SafeSqlExecutor sqlExecutor, QueryLogRepository logRepository) {
        this.chatClient = chatClientBuilder.build();
        this.vectorStore = vectorStore;
        this.sqlExecutor = sqlExecutor;
        this.logRepository = logRepository;
    }

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

        logger.info("Generating SQL for question: {}", userQuestion);

        // 2. Generate SQL using ChatClient with system/user prompt
        String generatedSql = chatClient.prompt()
                .system(sp -> sp.text(SYSTEM_PROMPT).param("schema_context", schemaContext))
                .user(userQuestion)
                .call()
                .content();

        // Clean markdown backticks
        generatedSql = cleanSqlOutput(generatedSql);

        // 3. Execute and Log
        try {
            List<Map<String, Object>> queryResults = sqlExecutor.executeReadOnlyQuery(generatedSql);

            // Convert results to string for the log table
            String responseData = queryResults.isEmpty() ? "No results found" : queryResults.toString();

            // FIX: Use userQuestion (the parameter name)
            logRepository.save(new QueryLog(userQuestion, responseData));

            return queryResults;

        } catch (Exception e) {
            String errorMessage = "Error: " + e.getMessage();
            logRepository.save(new QueryLog(userQuestion, errorMessage));

            logger.error("Failed to execute GenAI query", e);
            return Map.of(
                    "error", errorMessage,
                    "attempted_sql", generatedSql
            );
        }
    }

    private String cleanSqlOutput(String sql) {
        if (sql == null) return "";
        return sql.replaceAll("```sql|```", "").trim();
    }
}