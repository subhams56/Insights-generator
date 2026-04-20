package com.insights.generator.config;

import org.springframework.ai.google.genai.GoogleGenAiEmbeddingConnectionDetails;
import org.springframework.ai.google.genai.text.GoogleGenAiTextEmbeddingModel;
import org.springframework.ai.google.genai.text.GoogleGenAiTextEmbeddingOptions;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class GeminiEmbeddingConfig {

    @Bean
    @Primary
    public GoogleGenAiTextEmbeddingModel googleGenAiTextEmbedding(GoogleGenAiEmbeddingConnectionDetails connectionDetails) {
        // Use the current active model: gemini-embedding-001
        GoogleGenAiTextEmbeddingOptions options = GoogleGenAiTextEmbeddingOptions.builder()
                .model("gemini-embedding-001") // Updated model ID
                .dimensions(768)               // Force the 768 size for HNSW compatibility
                .build();

        return new GoogleGenAiTextEmbeddingModel(connectionDetails, options);
    }
}