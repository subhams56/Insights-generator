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
//import org.springframework.ai.google.genai.GoogleGenAiChatModel;
//import org.springframework.ai.google.genai.GoogleGenAiChatOptions; // <-- NEW IMPORT
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

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

	private final String REFINER_PROMPT =
		"""
You are a professional US Telecom Network Data Analyst.

USER QUESTION:
{user_question}

DATABASE RESULT:
{raw_data}

INSTRUCTIONS:
1. Interpret the DATABASE RESULT as the direct answer to the USER QUESTION.

2. Keep the response under 8 sentences.

3. Summarize patterns, trends, and important observations.

4. NEVER repeat raw rows unnecessarily.

5. NEVER restate the full dataset.

6. If the dataset is large:
   - summarize statistically
   - identify trends
   - mention only important records

7. If the DATABASE RESULT is empty:
   clearly state that no matching data was found.

8. Be concise, analytical, and professional.

9. Focus on telecom/network insights when relevant.

10. NEVER mention SQL queries or database internals.
""";

	public NLQAgent(
		ChatClient.Builder chatClientBuilder,
		VectorStore vectorStore,
		SafeSqlExecutor sqlExecutor,
		QueryLogRepository logRepository,
		OpenAiChatModel chatModel
	) {
		this.chatClient = chatClientBuilder.build();
		this.vectorStore = vectorStore;
		this.sqlExecutor = sqlExecutor;
		this.logRepository = logRepository;
		this.currentModelName = chatModel.getDefaultOptions().getModel();
	}

	public Object processQuestion(String userQuestion, String requestedModel) {
		List<Document> similarDocuments = vectorStore.similaritySearch(
			SearchRequest.builder().query(userQuestion).topK(2).build()
		);

		String schemaContext = similarDocuments.stream().map(Document::getText).collect(Collectors.joining("\n"));

		String targetModel = (requestedModel != null && !requestedModel.trim().isEmpty())
			? requestedModel
			: currentModelName;
		logger.info("Directing SQL Generation to Model: {}", targetModel);
		logger.info("Generating SQL for question: {}", userQuestion);

		try {
			// Build Prompt
			String mergedPrompt =
				"""
%s

USER QUESTION:
%s
""".formatted(
						SYSTEM_PROMPT.replace("{schema_context}", schemaContext),
						userQuestion
					);

			var promptSpec = chatClient.prompt().user(mergedPrompt);

			// DYNAMIC MODEL OVERRIDE
			if (requestedModel != null && !requestedModel.trim().isEmpty()) {
				promptSpec.options(OpenAiChatOptions.builder().model(requestedModel).build());
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
				"error",
				"SERVICE_INTERRUPTION",
				"details",
				errorMessage.contains("429") ? "AI Quota exceeded. Please wait a moment." : errorMessage
			);
		}
	}

	public Map<String, Object> processQuestionV2(String userQuestion, String requestedModel) {
		logger.info("Cache is {}", caching ? "ENABLED" : "DISABLED");
		if (caching) {
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
					"NLQ_AGENT (Cache)",
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

		// Pass requestedModel to raw data fetcher
		Object rawResponse = processQuestion(userQuestion, requestedModel);

		if (rawResponse instanceof Map && ((Map<?, ?>) rawResponse).containsKey("error")) {
			return (Map<String, Object>) rawResponse;
		}

		try {
			String dataString;

			if (rawResponse instanceof List<?> rawList && rawList.size() > 50) {
				logger.warn("Large dataset detected ({} rows). Truncating before refiner call.", rawList.size());

				dataString = rawList.subList(0, 50).toString() + "\n\n[TRUNCATED: Showing first 50 rows only]";
			} else {
				dataString = rawResponse.toString();
			}
			if (dataString.length() > 15000) {
				dataString = dataString.substring(0, 15000) + "\n\n[DATA TRUNCATED DUE TO SIZE]";
			}
			String targetModel = (requestedModel != null && !requestedModel.trim().isEmpty())
				? requestedModel
				: currentModelName;
			logger.info("Directing Refiner to Model: {}", targetModel);

			String mergedRefinerPrompt =
				"""
%s

USER REQUEST:
Please summarize the findings.
""".formatted(
						REFINER_PROMPT.replace("{user_question}", userQuestion).replace("{raw_data}", dataString)
					);

			var refinerPromptSpec = chatClient.prompt().user(mergedRefinerPrompt);

			// DYNAMIC MODEL OVERRIDE
			if (requestedModel != null && !requestedModel.trim().isEmpty()) {
				refinerPromptSpec.options(OpenAiChatOptions.builder().model(requestedModel).build());
			}

			String refinedAnswer = refinerPromptSpec.call().content();

			logRepository.save(new QueryLog(userQuestion, refinedAnswer, rawResponse.toString()));

			return Map.of(
				"agent",
				"NLQ_AGENT",
				"question",
				userQuestion,
				"response",
				refinedAnswer,
				"rawData",
				rawResponse,
				"source",
				"LLM-RAG",
				"modelUsed",
				targetModel // Optional: Expose which model served the request
			);
		} catch (Exception e) {
			logger.error("Refiner LLM Call Failed: {}", e.getMessage());
			return Map.of(
				"error",
				"REFINER_UNAVAILABLE",
				"raw_data",
				rawResponse,
				"details",
				"Could not generate human-readable summary, but raw data is available."
			);
		}
	}

	private String cleanSqlOutput(String sql) {
		if (sql == null) {
			return "";
		}

		sql = sql.replaceAll("```sql|```", "").trim();

		// Remove accidental duplicate statements
		if (sql.contains(";")) {
			int firstSemicolon = sql.indexOf(";");
			if (firstSemicolon != -1) {
				sql = sql.substring(0, firstSemicolon + 1);
			}
		}

		// Defensive cleanup
		sql = sql.replaceAll("--.*", "").trim();

		return sql;
	}
}
