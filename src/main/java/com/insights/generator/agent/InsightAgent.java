package com.insights.generator.agent;

import com.insights.generator.model.QueryLog;
import com.insights.generator.repository.QueryLogRepository;
import com.insights.generator.repository.SafeSqlExecutor;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
//import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

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

	private final String SYSTEM_PROMPT =
		"""
You are a PostgreSQL expert for a US-based 5G Telecom analytics dataset.

SCHEMA CONTEXT:
{schema_context}

CRITICAL RULES:
1. The dataset contains HISTORICAL telecom data spanning from JUNE 2024 to MAY 2025.

2. ALWAYS return EXACTLY ONE valid PostgreSQL SELECT statement.

3. STRICT SCHEMA ADHERENCE:
   - You MUST ONLY use exact column names from the SCHEMA CONTEXT.
   - NEVER invent columns.
   - NEVER assume columns exist.
   - If a field is missing from schema context, do not query it.

4. ONLY generate READ-ONLY SQL:
   - SELECT statements only.
   - NEVER use INSERT, UPDATE, DELETE, DROP, ALTER, CREATE, TRUNCATE.

5. NEVER generate multiple SQL statements.

6. NEVER repeat the query.

7. NEVER explain the SQL.

8. NEVER wrap SQL in markdown.

9. ALWAYS use PostgreSQL-compatible syntax.

10. PostgreSQL does NOT allow SELECT aliases inside HAVING clauses.
    Repeat the full aggregate expression instead.

11. Use COALESCE(metric, 0) where numeric values may be null.

12. ALWAYS include a LIMIT clause:
    - Use LIMIT 100 by default.
    - Use LIMIT 20 for broad analytical questions.
    - Only omit LIMIT if the user explicitly requests ALL records.

13. For aggregation queries:
    - Prefer GROUP BY with aggregate functions.
    - Avoid returning massive raw datasets.

14. Prefer summarized analytical queries over raw telemetry dumps.

15. For comparisons involving multiple values:
    - Prefer IN (...) instead of repetitive OR conditions.

16. RETURN ONLY RAW SQL. NO COMMENTS. NO EXPLANATION. NO PREFIX TEXT.
""";

	private static final String INSIGHT_SYSTEM_PROMPT =
		"""
You are a Senior US Telecom Network Data Analyst.

RAW DATA:
{raw_data}

INSTRUCTIONS:
1. Transform the raw data into executive-friendly telecom insights.

2. Keep the response under 10 sentences.

3. Focus on:
   - trends
   - anomalies
   - correlations
   - performance patterns
   - business/network impact

4. NEVER repeat the full dataset.

5. NEVER dump raw rows unnecessarily.

6. Summarize statistically whenever possible.

7. Use concise Markdown formatting:
   - bullets
   - bold metrics
   - short sections

8. NEVER mention SQL, databases, JSON, or technical backend details.

9. If data is empty:
   respond with:
   "There is no data available for this specific query in the current reporting period."

10. Prioritize telecom-relevant reasoning:
   - latency
   - dropped calls
   - weather impact
   - carrier performance
   - device-specific degradation
   - network band behavior

11. Be concise, professional, analytical, and executive-friendly.
""";

	public InsightAgent(
		ChatClient.Builder chatClientBuilder,
		NLQAgent nlqAgent,
		QueryLogRepository logRepository,
		@Value("${spring.ai.openai.chat.options.model}") String model,
		VectorStore vectorStore,
		SafeSqlExecutor sqlExecutor
	) {
		this.logRepository = logRepository;
		this.nlqAgent = nlqAgent;
		this.vectorStore = vectorStore;
		this.sqlExecutor = sqlExecutor;
		this.defaultModel = model;

		OpenAiChatOptions options = OpenAiChatOptions.builder().build();
		options.setModel(model);

		this.chatClient = chatClientBuilder.defaultOptions(options).build();
	}

	public Map<String, Object> analyze(String userQuestion, String requestedModel) {
		String targetModel = (requestedModel != null && !requestedModel.trim().isEmpty())
			? requestedModel
			: defaultModel;
		logger.info("Insight Agent beginning analysis for: '{}' using Model: {}", userQuestion, targetModel);

		if (cacheEnabled) {
			String squeezedQuestion = userQuestion.replaceAll("[^a-zA-Z0-9]", "").toLowerCase();
			Optional<QueryLog> cachedEntry = logRepository.findSmartLexicalMatch(squeezedQuestion);

			if (cachedEntry.isPresent()) {
				logger.info(
					" Cache HIT! Matched '{}' with stored question: '{}'",
					userQuestion,
					cachedEntry.get().getQuestion()
				);

				return Map.of(
					"agent",
					"INSIGHT_AGENT (Cached)",
					"question",
					userQuestion,
					"response",
					cachedEntry.get().getResponse(),
					"rawData",
					cachedEntry.get().getRawData(),
					"source",
					"CACHE"
				);
			}
		}

		try {
			// Pass model downstream for the SQL generation LLM call
			List<Map<String, Object>> rawData = fetchRawDataOnly(userQuestion, requestedModel);

			if (rawData == null || rawData.isEmpty()) {
				return Map.of(
					"question",
					userQuestion,
					"response",
					"No data found to analyze for this request.",
					"agent",
					"INSIGHT_AGENT"
				);
			}

			String rawDataString;

			if (rawData.size() > 50) {
				logger.warn("Large dataset detected ({} rows). Truncating before insight generation.", rawData.size());

				rawDataString = rawData.subList(0, 50).toString() + "\n\n[TRUNCATED: Showing first 50 rows only]";
			} else {
				rawDataString = rawData.toString();
			}

			if (rawDataString.length() > 15000) {
				rawDataString = rawDataString.substring(0, 15000) + "\n\n[DATA TRUNCATED DUE TO SIZE]";
			}
			// Build Insight Prompt
			String mergedInsightPrompt =
				"""
%s

USER QUESTION:
%s
""".formatted(
						INSIGHT_SYSTEM_PROMPT.replace("{raw_data}", rawDataString),
						userQuestion
					);

			var promptSpec = chatClient.prompt().user(mergedInsightPrompt);

			// DYNAMIC MODEL OVERRIDE
			if (requestedModel != null && !requestedModel.trim().isEmpty()) {
				promptSpec.options(OpenAiChatOptions.builder().model(requestedModel).build());
			}

			String insightResponse = promptSpec.call().content();
			logger.info("Insight generation complete.");

			if (cacheEnabled) {
				saveToCache(userQuestion, insightResponse, rawData.toString());
			}

			return Map.of(
				"agent",
				"INSIGHT_AGENT",
				"question",
				userQuestion,
				"response",
				insightResponse,
				"rawData",
				rawData, // Maintained as object for JSON serialization
				"source",
				"LLM-RAG",
				"modelUsed",
				targetModel
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
				SearchRequest.builder().query(userQuestion).topK(2).build()
			);
			String schemaContext = similarDocuments.stream().map(Document::getText).collect(Collectors.joining("\n"));

			String mergedSqlPrompt =
				"""
%s

USER QUESTION:
%s
""".formatted(
						SYSTEM_PROMPT.replace("{schema_context}", schemaContext),
						userQuestion
					);

			var sqlPromptSpec = chatClient.prompt().user(mergedSqlPrompt);

			// DYNAMIC MODEL OVERRIDE
			if (requestedModel != null && !requestedModel.trim().isEmpty()) {
				sqlPromptSpec.options(OpenAiChatOptions.builder().model(requestedModel).build());
			}

			String rawSqlResponse = sqlPromptSpec.call().content();
			String cleanSql = cleanSqlOutput(rawSqlResponse);
			logger.info("Generated Insight SQL:\n---\n{}\n---", cleanSql);

			return sqlExecutor.executeReadOnlyQuery(cleanSql);
		} catch (Exception e) {
			logger.error("Error fetching raw data for insight: ", e);
			throw new RuntimeException("Failed to fetch raw data for analysis.");
		}
	}

	private String cleanSqlOutput(String sql) {
		if (sql == null) {
			return "";
		}

		sql = sql.replaceAll("```sql|```", "").trim();

		// Remove accidental multi-statement outputs
		int firstSemicolon = sql.indexOf(";");

		if (firstSemicolon != -1) {
			sql = sql.substring(0, firstSemicolon + 1);
		}

		// Remove inline comments
		sql = sql.replaceAll("--.*", "").trim();

		return sql;
	}
}
