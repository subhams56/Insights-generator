package com.insights.generator.handler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class DefaultGuardrailHandler implements GuardrailResponseHandler {

    private static final Logger logger = LoggerFactory.getLogger(DefaultGuardrailHandler.class);

    @Override
    public Map<String, Object> handleOffTopic(String userQuestion) {
        logger.warn("GUARDRAIL TRIGGERED: Rejecting off-topic query -> '{}'", userQuestion);

        return Map.of(
                "status", "rejected",
                "error", "OUT_OF_DOMAIN",
                "answer", "I am a specialized 5G Network Assistant. I can help you analyze latency, cell performance, and regional network metrics, but I cannot answer unrelated questions.",
                "source", "guardrail"
        );
    }
}