package com.insights.generator.handler;

import java.util.Map;

public interface GuardrailResponseHandler {
    /**
     * Handles queries that fall below the semantic similarity threshold.
     */
    Map<String, Object> handleOffTopic(String userQuestion);
}