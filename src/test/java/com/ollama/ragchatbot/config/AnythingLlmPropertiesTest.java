package com.ollama.ragchatbot.config;

import com.ollama.ragchatbot.OllamaRagChatbotApplication;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;

import java.util.concurrent.ExecutorService;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that all {@link AnythingLlmProperties} fields bind correctly from
 * {@code @TestPropertySource}.  Infrastructure beans that talk to Ollama /
 * ChromaDB are mocked so no external services need to be running.
 */
@SpringBootTest(classes = OllamaRagChatbotApplication.class)
@TestPropertySource(properties = {
        "anythingllm.llm-provider=ollama",
        "anythingllm.ollama-base-path=http://localhost:11434",
        "anythingllm.ollama-model-pref=llama3",
        "anythingllm.ollama-model-token-limit=2048",
        "anythingllm.embedding-engine=ollama",
        "anythingllm.embedding-base-path=http://localhost:11434",
        "anythingllm.embedding-model-pref=nomic-embed-text",
        "anythingllm.vector-db=chroma",
        "anythingllm.chroma-base-url=http://localhost:8000",
        "anythingllm.chroma-collection=test-collection",
        "anythingllm.rag-max-results=5",
        "anythingllm.rag-min-score=0.7"
})
class AnythingLlmPropertiesTest {

    // Mock the beans that connect to Ollama / ChromaDB so no real server is needed.
    @MockBean
    ChatLanguageModel chatLanguageModel;
    @MockBean
    StreamingChatLanguageModel streamingChatLanguageModel;
    @MockBean
    EmbeddingModel embeddingModel;
    @MockBean
    @SuppressWarnings("rawtypes")
    EmbeddingStore embeddingStore;
    @MockBean
    ExecutorService executorService;

    @Autowired
    private AnythingLlmProperties properties;

    @Test
    void shouldBindAnythingLlmProperties() {
        assertThat(properties.llmProvider()).isEqualTo("ollama");
        assertThat(properties.ollamaBasePath()).isEqualTo("http://localhost:11434");
        assertThat(properties.ollamaModelPref()).isEqualTo("llama3");
        assertThat(properties.embeddingEngine()).isEqualTo("ollama");
        assertThat(properties.embeddingModelPref()).isEqualTo("nomic-embed-text");
        assertThat(properties.vectorDb()).isEqualTo("chroma");
        assertThat(properties.chromaBaseUrl()).isEqualTo("http://localhost:8000");
        assertThat(properties.chromaCollection()).isEqualTo("test-collection");
        assertThat(properties.ragMaxResults()).isEqualTo(5);
        assertThat(properties.ragMinScore()).isEqualTo(0.7);
    }
}

