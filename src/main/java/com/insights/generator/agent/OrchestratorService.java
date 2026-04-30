package com.insights.generator.agent;

import com.insights.generator.handler.GuardrailResponseHandler;
import com.insights.generator.model.AgentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class OrchestratorService {

    private static final Logger logger = LoggerFactory.getLogger(OrchestratorService.class);

    private final SimpleVectorStore intentRouterStore;
    private final NLQAgent nlqAgent;
    private final GuardrailResponseHandler guardrailHandler;
    private final InsightAgent insightAgent;

    // Guardrail Threshold: 0.75
    private static final double ROUTING_CONFIDENCE_THRESHOLD = 0.65;

    public OrchestratorService(EmbeddingModel embeddingModel, NLQAgent nlqAgent, GuardrailResponseHandler guardrailHandler, InsightAgent insightAgent) {
        // Initialize the SimpleVectorStore using the builder
        this.intentRouterStore = SimpleVectorStore.builder(embeddingModel).build();
        this.nlqAgent = nlqAgent;
        this.guardrailHandler = guardrailHandler;
        this.insightAgent = insightAgent;

        initializeIntentRoutes();
    }

    private void initializeIntentRoutes() {
        logger.info("Initializing US-Centric Semantic Router Intents in RAM...");

        List<Document> intents = List.of(
                // NLQ Agent Intents (Raw Data Fetching, simple lookups)
                new Document("Fetch current network metrics, latency, speed, and packet loss.", Map.of("agent", AgentType.NLQ_AGENT.name())),
                new Document("What is the average download and upload speed customers are experiencing in Texas?", Map.of("agent", AgentType.NLQ_AGENT.name())),
                new Document("Show me latency and performance metrics for specific network bands like 5G mmWave or 4G LTE.", Map.of("agent", AgentType.NLQ_AGENT.name())),
                new Document("Give me the list of cities or regions which are experiencing dropped calls higher than a specific threshold.", Map.of("agent", AgentType.NLQ_AGENT.name())),
                new Document("Which state has the lowest packet loss?", Map.of("agent", AgentType.NLQ_AGENT.name())),
                new Document("Get the raw numbers for New York and California.", Map.of("agent", AgentType.NLQ_AGENT.name())),

                // Insight Agent Intents (Analysis, Comparisons, Summaries, Explanations, Weather/Environment impacts)
                new Document("Analyze performance trends and give me a summary of network utilization.", Map.of("agent", AgentType.INSIGHT_AGENT.name())),
                new Document("Compare regions and explain why one is performing better or worse regarding dropped calls.", Map.of("agent", AgentType.INSIGHT_AGENT.name())),
                new Document("Compare the latency and speed between Urban and Rural environments and explain the difference.", Map.of("agent", AgentType.INSIGHT_AGENT.name())),
                new Document("Give me an executive summary of the network status and how weather conditions like rain impact our mmWave bands.", Map.of("agent", AgentType.INSIGHT_AGENT.name())),
                new Document("Look at the congestion level data and explain the root cause or give me an analysis.", Map.of("agent", AgentType.INSIGHT_AGENT.name()))
        );

        intentRouterStore.add(intents);
    }
    public Object routeAndExecute(String userQuestion) {
        long startTime = System.currentTimeMillis();
        logger.info("Orchestrator analyzing semantic intent for: '{}'", userQuestion);

        // 1. Perform strict Semantic Search (Top 1 only, threshold restored)
        List<Document> matches = intentRouterStore.similaritySearch(
                SearchRequest.builder()
                        .query(userQuestion)
                        .topK(1)
                        .similarityThreshold(ROUTING_CONFIDENCE_THRESHOLD)
                        .build()
        );

        long routingTime = System.currentTimeMillis() - startTime;

        // 2. THE GUARDRAIL: Reject unrelated topics
        if (matches.isEmpty()) {
            logger.warn("Guardrail triggered. No semantic match above {} threshold. (Took {}ms)", ROUTING_CONFIDENCE_THRESHOLD, routingTime);
            return guardrailHandler.handleOffTopic(userQuestion);
        }

        // 3. Extract target agent
        String agentName = (String) matches.get(0).getMetadata().get("agent");
        AgentType targetAgent = AgentType.valueOf(agentName);

        logger.info("Semantic match found: {} (Took {}ms)", targetAgent, routingTime);

        // 4. Route execution
        return switch (targetAgent) {
            case NLQ_AGENT -> nlqAgent.processQuestionV2(userQuestion);
            case INSIGHT_AGENT -> insightAgent.analyze(userQuestion);
            default -> guardrailHandler.handleOffTopic(userQuestion);
        };
    }
}