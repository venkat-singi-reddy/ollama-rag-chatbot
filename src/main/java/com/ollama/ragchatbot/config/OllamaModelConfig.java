package com.ollama.ragchatbot.config;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OllamaModelConfig {

    @Bean
    ChatLanguageModel chatLanguageModel(AnythingLlmProperties properties) {
        if (!"ollama".equalsIgnoreCase(properties.llmProvider())) {
            throw new IllegalStateException("LLM_PROVIDER must be set to ollama for this service");
        }

        OllamaChatModel.OllamaChatModelBuilder builder = OllamaChatModel.builder()
                .baseUrl(properties.ollamaBasePath())
                .modelName(properties.ollamaModelPref());

        if (properties.ollamaModelTokenLimit() != null) {
            builder.numPredict(properties.ollamaModelTokenLimit());
        }

        return builder.build();
    }
}
