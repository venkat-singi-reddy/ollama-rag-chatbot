package com.ollama.ragchatbot.web;

import com.ollama.ragchatbot.config.AnythingLlmProperties;
import com.ollama.ragchatbot.service.ChatService;
import com.ollama.ragchatbot.service.DocumentIngestionService;
import com.ollama.ragchatbot.service.RagChatService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;

/**
 * Unified REST controller exposing both the legacy v1 endpoints and the new
 * RAG-aware streaming/document-ingestion endpoints.
 *
 * <h3>Endpoint overview</h3>
 * <table border="1">
 * <tr><th>Method</th><th>Path</th><th>Description</th></tr>
 * <tr><td>GET</td><td>/api/v1/health</td><td>Health check (legacy, retained)</td></tr>
 * <tr><td>POST</td><td>/api/v1/chat</td><td>Blocking chat via LLM (legacy, retained)</td></tr>
 * <tr><td>GET</td><td>/api/chat/stream</td><td>Token-streaming RAG chat via SSE</td></tr>
 * <tr><td>DELETE</td><td>/api/chat/session/{id}</td><td>Clear a chat session</td></tr>
 * <tr><td>POST</td><td>/api/documents/upload</td><td>Upload &amp; ingest a file</td></tr>
 * <tr><td>POST</td><td>/api/documents/text</td><td>Ingest plain text</td></tr>
 * </table>
 */
@RestController
public class ChatController {

    private final ChatService chatService;
    private final RagChatService ragChatService;
    private final DocumentIngestionService documentIngestionService;
    private final AnythingLlmProperties properties;

    public ChatController(
            ChatService chatService,
            RagChatService ragChatService,
            DocumentIngestionService documentIngestionService,
            AnythingLlmProperties properties) {
        this.chatService = chatService;
        this.ragChatService = ragChatService;
        this.documentIngestionService = documentIngestionService;
        this.properties = properties;
    }

    // ── Legacy endpoints (v1) ─────────────────────────────────────────────────

    /**
     * Health check – reports the active LLM provider and model.
     *
     * @deprecated Retained for backward compatibility. Prefer the new RAG endpoints.
     */
    @GetMapping(path = "/api/v1/health", produces = MediaType.APPLICATION_JSON_VALUE)
    public HealthResponse health() {
        return new HealthResponse("ok", properties.llmProvider(), properties.ollamaModelPref());
    }

    /**
     * Simple blocking chat – passes the message directly to the LLM without RAG.
     *
     * @deprecated Use {@code GET /api/chat/stream} for context-aware streaming chat.
     */
    @PostMapping(path = "/api/v1/chat",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ChatResponse chat(@Valid @RequestBody ChatRequest request) {
        String answer = chatService.ask(request.message());
        return new ChatResponse(answer, properties.llmProvider(), properties.ollamaModelPref());
    }

    // ── RAG streaming chat ────────────────────────────────────────────────────

    /**
     * Token-streaming RAG chat via <a href="https://developer.mozilla.org/en-US/docs/Web/API/Server-sent_events">Server-Sent Events</a>.
     *
     * <p>Each SSE {@code data} event carries one token. A final {@code done} event
     * with payload {@code [DONE]} signals the end of the stream.
     *
     * @param message   the user's question
     * @param sessionId conversation session ID (defaults to {@code "default"})
     */
    @GetMapping(path = "/api/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamChat(
            @RequestParam @NotBlank String message,
            @RequestParam(defaultValue = "default") String sessionId) {

        SseEmitter emitter = new SseEmitter(120_000L); // 2-minute timeout
        ragChatService.streamChat(sessionId, message, emitter);
        return emitter;
    }

    /**
     * Clears the in-memory history for the given session.
     *
     * @param sessionId the session to clear
     */
    @DeleteMapping(path = "/api/chat/session/{sessionId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> clearSession(@PathVariable String sessionId) {
        boolean existed = ragChatService.clearSession(sessionId);
        return ResponseEntity.ok(Map.of("sessionId", sessionId, "cleared", existed));
    }

    // ── Document ingestion ────────────────────────────────────────────────────

    /**
     * Uploads a file, parses it with Apache Tika, splits it into chunks,
     * embeds each chunk, and stores the result in ChromaDB.
     *
     * <p>Supported formats: PDF, DOCX, PPTX, TXT, HTML, Markdown, and
     * <a href="https://tika.apache.org/2.9.2/formats.html">everything else Tika handles</a>.
     *
     * @param file the multipart file to ingest
     */
    @PostMapping(path = "/api/documents/upload",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> uploadDocument(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Uploaded file is empty."));
        }
        try {
            int chunks = documentIngestionService.ingestFile(file);
            return ResponseEntity.ok(Map.of(
                    "filename", file.getOriginalFilename(),
                    "chunks", chunks,
                    "status", "ingested"));
        } catch (IOException e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Failed to read file: " + e.getMessage()));
        }
    }

    /**
     * Ingests a plain-text snippet directly, without a file upload.
     *
     * @param request contains {@code text} and an optional {@code source} label
     */
    @PostMapping(path = "/api/documents/text",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> ingestText(@Valid @RequestBody TextIngestionRequest request) {
        int chunks = documentIngestionService.ingestText(request.text(), request.source());
        return ResponseEntity.ok(Map.of(
                "source", request.source(),
                "chunks", chunks,
                "status", "ingested"));
    }

    // ── Request / response records ────────────────────────────────────────────

    /** @deprecated See {@link #chat(ChatRequest)}. */
    public record ChatRequest(@NotBlank String message) {
    }

    /** @deprecated See {@link #chat(ChatRequest)}. */
    public record ChatResponse(String answer, String provider, String model) {
    }

    /** @deprecated See {@link #health()}. */
    public record HealthResponse(String status, String provider, String model) {
    }

    /** Request body for {@link #ingestText(TextIngestionRequest)}. */
    public record TextIngestionRequest(
            @NotBlank String text,
            @NotBlank String source) {
    }
}

