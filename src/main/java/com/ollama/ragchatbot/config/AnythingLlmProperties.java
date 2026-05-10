package com.ollama.ragchatbot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Typed configuration for the Ollama RAG chatbot, aligned with AnythingLLM's
 * environment-variable conventions so existing setups can be reused without change.
 *
 * <p>All values can be overridden via environment variables (see {@code application.yml}).
 */
@ConfigurationProperties(prefix = "anythingllm")
public record AnythingLlmProperties(

        // ── LLM ──────────────────────────────────────────────────────────────
        String llmProvider,
        String ollamaBasePath,
        String ollamaModelPref,
        Integer ollamaModelTokenLimit,

        // ── Embedding ────────────────────────────────────────────────────────
        String embeddingEngine,
        String embeddingBasePath,
        String embeddingModelPref,

        // ── Vector DB ────────────────────────────────────────────────────────
        String vectorDb,
        /** Base URL of the ChromaDB HTTP server (e.g. {@code http://localhost:8000}). */
        String chromaBaseUrl,
        /** Chroma collection name used to store document embeddings. */
        String chromaCollection,

        // ── RAG retrieval ────────────────────────────────────────────────────
        /** Maximum number of chunks returned per semantic search. */
        Integer ragMaxResults,
        /**
         * Minimum cosine-similarity score (0–1) a chunk must have to be included
         * in the context window.
         */
        Double ragMinScore,
        /** Maximum number of characters per document chunk. */
        Integer ragChunkSize,
        /** Number of overlapping characters between consecutive chunks. */
        Integer ragChunkOverlap

) {
}
