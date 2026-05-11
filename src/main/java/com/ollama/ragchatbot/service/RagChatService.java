package com.ollama.ragchatbot.service;

import com.ollama.ragchatbot.config.AnythingLlmProperties;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;

/**
 * Retrieval-Augmented Generation (RAG) chat service.
 *
 * <p>For every user message the pipeline is:
 * <ol>
 *   <li><b>Embed</b> – convert the query to a dense vector with the Ollama embedding model.</li>
 *   <li><b>Retrieve</b> – search ChromaDB for the most relevant document chunks.</li>
 *   <li><b>Augment</b> – inject retrieved context into the system prompt.</li>
 *   <li><b>Generate</b> – call the Ollama chat model (blocking or streaming).</li>
 *   <li><b>Update session</b> – append messages to the in-memory session history.</li>
 * </ol>
 *
 * <p>Session history is kept in a {@link ConcurrentHashMap} keyed by a caller-supplied
 * session ID (e.g. a UUID). Use {@link #clearSession(String)} to reset a conversation.
 */
@Slf4j
@Service
public class RagChatService {

    /** System prompt preamble; context chunks are appended below. */
    private static final String SYSTEM_PREAMBLE =
            "You are a helpful AI assistant. "
            + "Use the context below – if provided – to answer the user's question accurately. "
            + "If the context does not contain enough information, answer from your general knowledge "
            + "and tell the user that the context was not sufficient.\n\n";

    private final ChatLanguageModel chatModel;
    private final StreamingChatLanguageModel streamingChatModel;
    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> embeddingStore;
    private final ExecutorService executor;
    private final int maxResults;
    private final double minScore;

    /** In-memory per-session chat history (UserMessage / AiMessage alternating). */
    private final Map<String, List<ChatMessage>> sessions = new ConcurrentHashMap<>();

    public RagChatService(
            ChatLanguageModel chatModel,
            StreamingChatLanguageModel streamingChatModel,
            EmbeddingModel embeddingModel,
            EmbeddingStore<TextSegment> embeddingStore,
            ExecutorService executor,
            AnythingLlmProperties properties) {

        this.chatModel = chatModel;
        this.streamingChatModel = streamingChatModel;
        this.embeddingModel = embeddingModel;
        this.embeddingStore = embeddingStore;
        this.executor = executor;
        this.maxResults = properties.ragMaxResults() != null ? properties.ragMaxResults() : 5;
        this.minScore   = properties.ragMinScore()   != null ? properties.ragMinScore()   : 0.7;
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Blocking RAG chat – returns the full answer as a String.
     *
     * @param sessionId identifies the conversation (arbitrary string; e.g. UUID)
     * @param userMessage the user's question
     * @return the model's answer
     */
    public String chat(String sessionId, String userMessage) {
        List<ChatMessage> history = session(sessionId);
        List<ChatMessage> messages = buildMessages(userMessage, history);

        Response<AiMessage> response = chatModel.generate(messages);
        String answer = response.content().text();

        // Persist exchange in session history
        history.add(UserMessage.from(userMessage));
        history.add(AiMessage.from(answer));

        return answer;
    }

    /**
     * Non-blocking, token-streaming RAG chat delivered via a Spring
     * {@link SseEmitter}.
     *
     * <p>Each token is sent as a plain-text SSE {@code data} event. A final
     * {@code done} event with payload {@code [DONE]} signals the end of the stream.
     *
     * @param sessionId   identifies the conversation
     * @param userMessage the user's question
     * @param emitter     the SSE emitter to write tokens into
     */
    public void streamChat(String sessionId, String userMessage, SseEmitter emitter) {
        List<ChatMessage> history = session(sessionId);
        List<ChatMessage> messages = buildMessages(userMessage, history);
        StringBuilder fullResponse = new StringBuilder();

        // Run on a virtual thread so the Tomcat thread is released immediately.
        executor.execute(() ->
                streamingChatModel.generate(messages, new StreamingResponseHandler<AiMessage>() {

                    @Override
                    public void onNext(String token) {
                        fullResponse.append(token);
                        try {
                            emitter.send(SseEmitter.event().data(token));
                        } catch (IOException e) {
                            log.debug("SSE client disconnected mid-stream: {}", e.getMessage());
                            emitter.completeWithError(e);
                        }
                    }

                    @Override
                    public void onComplete(Response<AiMessage> response) {
                        // Persist exchange only after the full response is known.
                        history.add(UserMessage.from(userMessage));
                        history.add(AiMessage.from(fullResponse.toString()));
                        try {
                            emitter.send(SseEmitter.event().name("done").data("[DONE]"));
                        } catch (IOException ignored) {
                            // Client may have already closed; nothing to do.
                        }
                        emitter.complete();
                    }

                    @Override
                    public void onError(Throwable error) {
                        log.error("Streaming error for session '{}': {}", sessionId, error.getMessage());
                        emitter.completeWithError(error);
                    }
                })
        );
    }

    /**
     * Removes the in-memory session history for the given session ID.
     *
     * @param sessionId the session to clear
     * @return {@code true} if a session existed and was removed
     */
    public boolean clearSession(String sessionId) {
        boolean existed = sessions.containsKey(sessionId);
        sessions.remove(sessionId);
        log.info("Cleared session '{}'", sessionId);
        return existed;
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /** Returns (or lazily creates) the mutable history list for {@code sessionId}. */
    private List<ChatMessage> session(String sessionId) {
        return sessions.computeIfAbsent(sessionId, k -> new ArrayList<>());
    }

    /**
     * Builds the full message list for the model:
     * {@code [SystemMessage(context), ...history, UserMessage(query)]}.
     */
    private List<ChatMessage> buildMessages(String userMessage, List<ChatMessage> history) {
        List<String> context = retrieveContext(userMessage);
        String systemPrompt = buildSystemPrompt(context);

        List<ChatMessage> messages = new ArrayList<>();
        messages.add(SystemMessage.from(systemPrompt));
        messages.addAll(history);
        messages.add(UserMessage.from(userMessage));
        return messages;
    }

    /**
     * Embeds {@code query} and searches ChromaDB for the closest chunks.
     * Returns an empty list on any retrieval error so the LLM can still answer
     * from its parametric knowledge.
     */
    private List<String> retrieveContext(String query) {
        try {
            Embedding queryEmbedding = embeddingModel.embed(query).content();
            EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                    .queryEmbedding(queryEmbedding)
                    .maxResults(maxResults)
                    .minScore(minScore)
                    .build();
            return embeddingStore.search(request).matches().stream()
                    .map(EmbeddingMatch::embedded)
                    .map(TextSegment::text)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("Context retrieval failed (will answer without context): {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * Builds the system prompt, optionally injecting the retrieved context chunks.
     */
    private String buildSystemPrompt(List<String> contextChunks) {
        if (contextChunks.isEmpty()) {
            return SYSTEM_PREAMBLE;
        }
        StringBuilder sb = new StringBuilder(SYSTEM_PREAMBLE);
        sb.append("Context:\n");
        for (int i = 0; i < contextChunks.size(); i++) {
            sb.append("[").append(i + 1).append("] ").append(contextChunks.get(i)).append("\n\n");
        }
        return sb.toString();
    }
}
