package com.ollama.ragchatbot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "anythingllm")
public record AnythingLlmProperties(
        String llmProvider,
        String ollamaBasePath,
        String ollamaModelPref,
        Integer ollamaModelTokenLimit,
        String embeddingEngine,
        String embeddingBasePath,
        String embeddingModelPref,
        String vectorDb
) {
}
