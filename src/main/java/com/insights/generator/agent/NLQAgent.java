package com.insights.generator.agent;

import com.insights.generator.repository.SafeSqlExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.PromptTemplate;
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

    private final String SYSTEM_PROMPT = """
        You are an expert PostgreSQL database architect analyzing telecom 5G data.
        Your task is to convert the user's natural language question into a valid, safe PostgreSQL SELECT query.
        
        CRITICAL RULES:
        1. ONLY output the raw SQL query. Do not include markdown formatting (like ```sql).
        2. Do not include any explanations or conversational text.
        3. ONLY use the tables and columns provided in the Schema Context.
        4. NEVER generate INSERT, UPDATE, DELETE, or DROP statements.
        
        Schema Context:
        {schema_context}
        """;

    public NLQAgent(ChatClient.Builder chatClientBuilder, VectorStore vectorStore, SafeSqlExecutor sqlExecutor) {
        this.chatClient = chatClientBuilder.build();
        this.vectorStore = vectorStore;
        this.sqlExecutor = sqlExecutor;
    }

    public Object processQuestion(String userQuestion) {
        // 1. Retrieve Schema Metadata (RAG) using the new Builder pattern
        List<Document> similarDocuments = vectorStore.similaritySearch(
                SearchRequest.builder() // Use builder instead of constructor
                        .query(userQuestion)
                        .topK(2)
                        .build()
        );

        // 2. Extract content using getText() instead of getContent()
        String schemaContext = similarDocuments.stream()
                .map(Document::getText) // Method name changed in M4
                .collect(Collectors.joining("\n"));

        // 3. Generate SQL using ChatClient
        PromptTemplate promptTemplate = new PromptTemplate(SYSTEM_PROMPT);
        String systemMessage = promptTemplate.create(Map.of("schema_context", schemaContext)).getContents();

        logger.info("Generating SQL for question: {}", userQuestion);
        String generatedSql = chatClient.prompt()
                .system(systemMessage)
                .user(userQuestion)
                .call()
                .content();

        // Clean up LLM output in case it ignored the "no markdown" rule
        generatedSql = cleanSqlOutput(generatedSql);

        // 3. Execute the SQL
        try {
            List<Map<String, Object>> queryResults = sqlExecutor.executeReadOnlyQuery(generatedSql);
            return queryResults;

            // Note: For a true executive view, you could make a *second* ChatClient call here
            // passing the 'queryResults' back to the LLM to generate a natural language summary
            // of the data (e.g., "The average latency for Region A is 12ms.")

        } catch (Exception e) {
            logger.error("Failed to execute GenAI query", e);
            return Map.of("error", "Unable to retrieve data: " + e.getMessage(), "attempted_sql", generatedSql);
        }
    }

    private String cleanSqlOutput(String sql) {
        if (sql.startsWith("```sql")) {
            sql = sql.substring(6);
        }
        if (sql.startsWith("```")) {
            sql = sql.substring(3);
        }
        if (sql.endsWith("```")) {
            sql = sql.substring(0, sql.length() - 3);
        }
        return sql.trim();
    }
}