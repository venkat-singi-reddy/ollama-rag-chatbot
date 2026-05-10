package com.ollama.ragchatbot.config;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.ollama.OllamaEmbeddingModel;
import dev.langchain4j.model.ollama.OllamaStreamingChatModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.chroma.ChromaEmbeddingStore;
import dev.langchain4j.data.segment.TextSegment;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Spring bean definitions for the Ollama LLM stack:
 * <ul>
 *   <li>Blocking chat model – used by the legacy {@code /api/v1/chat} endpoint.</li>
 *   <li>Streaming chat model – used by the SSE {@code /api/chat/stream} endpoint.</li>
 *   <li>Embedding model – used for both document ingestion and query embedding.</li>
 *   <li>ChromaDB embedding store – persistent vector storage for RAG retrieval.</li>
 *   <li>Virtual-thread executor – offloads blocking I/O from carrier threads (Java 21+).</li>
 * </ul>
 */
@Configuration
public class OllamaModelConfig {

    /**
     * Blocking {@link ChatLanguageModel} backed by Ollama.
     * Retained for the legacy {@code /api/v1/chat} endpoint.
     */
    @Bean
    ChatLanguageModel chatLanguageModel(AnythingLlmProperties properties) {
        if (!"ollama".equalsIgnoreCase(properties.llmProvider())) {
            throw new IllegalStateException(
                    "anythingllm.llm-provider must be 'ollama' (current: '" + properties.llmProvider() + "')");
        }

        OllamaChatModel.OllamaChatModelBuilder builder = OllamaChatModel.builder()
                .baseUrl(properties.ollamaBasePath())
                .modelName(properties.ollamaModelPref());

        if (properties.ollamaModelTokenLimit() != null) {
            builder.numPredict(properties.ollamaModelTokenLimit());
        }

        return builder.build();
    }

    /**
     * Streaming {@link StreamingChatLanguageModel} backed by Ollama.
     * Tokens are delivered token-by-token to the SSE endpoint.
     */
    @Bean
    StreamingChatLanguageModel streamingChatLanguageModel(AnythingLlmProperties properties) {
        OllamaStreamingChatModel.OllamaStreamingChatModelBuilder builder =
                OllamaStreamingChatModel.builder()
                        .baseUrl(properties.ollamaBasePath())
                        .modelName(properties.ollamaModelPref());

        if (properties.ollamaModelTokenLimit() != null) {
            builder.numPredict(properties.ollamaModelTokenLimit());
        }

        return builder.build();
    }

    /**
     * Ollama-based {@link EmbeddingModel} used to convert text chunks and user
     * queries into dense vector representations.
     */
    @Bean
    EmbeddingModel embeddingModel(AnythingLlmProperties properties) {
        return OllamaEmbeddingModel.builder()
                .baseUrl(properties.embeddingBasePath())
                .modelName(properties.embeddingModelPref())
                .build();
    }

    /**
     * ChromaDB-backed {@link EmbeddingStore} that persists document embeddings.
     * The HTTP client is configured at startup; the actual network call happens
     * only when embeddings are read or written, so the application starts even
     * if ChromaDB is temporarily unavailable.
     */
    @Bean
    EmbeddingStore<TextSegment> chromaEmbeddingStore(AnythingLlmProperties properties) {
        return ChromaEmbeddingStore.builder()
                .baseUrl(properties.chromaBaseUrl())
                .collectionName(properties.chromaCollection())
                .build();
    }

    /**
     * Java 21 virtual-thread executor used to offload blocking Ollama/Chroma
     * HTTP calls from Tomcat carrier threads, maximising throughput without
     * switching to a fully reactive stack.
     */
    @Bean
    ExecutorService virtualThreadExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}
