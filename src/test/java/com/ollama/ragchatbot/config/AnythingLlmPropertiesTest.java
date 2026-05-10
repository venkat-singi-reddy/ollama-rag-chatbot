package com.ollama.ragchatbot.config;

import com.ollama.ragchatbot.OllamaRagChatbotApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = OllamaRagChatbotApplication.class)
@TestPropertySource(properties = {
        "anythingllm.llm-provider=ollama",
        "anythingllm.ollama-base-path=http://localhost:11434",
        "anythingllm.ollama-model-pref=llama3",
        "anythingllm.ollama-model-token-limit=2048",
        "anythingllm.embedding-engine=ollama",
        "anythingllm.embedding-base-path=http://localhost:11434",
        "anythingllm.embedding-model-pref=nomic-embed-text",
        "anythingllm.vector-db=lancedb"
})
class AnythingLlmPropertiesTest {

    @Autowired
    private AnythingLlmProperties properties;

    @Test
    void shouldBindAnythingLlmProperties() {
        assertThat(properties.llmProvider()).isEqualTo("ollama");
        assertThat(properties.ollamaBasePath()).isEqualTo("http://localhost:11434");
        assertThat(properties.ollamaModelPref()).isEqualTo("llama3");
        assertThat(properties.embeddingEngine()).isEqualTo("ollama");
        assertThat(properties.embeddingModelPref()).isEqualTo("nomic-embed-text");
        assertThat(properties.vectorDb()).isEqualTo("lancedb");
    }
}
