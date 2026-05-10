package com.ollama.ragchatbot.web;

import com.ollama.ragchatbot.config.AnythingLlmProperties;
import com.ollama.ragchatbot.service.ChatService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api/v1", produces = MediaType.APPLICATION_JSON_VALUE)
public class ChatController {

    private final ChatService chatService;
    private final AnythingLlmProperties properties;

    public ChatController(ChatService chatService, AnythingLlmProperties properties) {
        this.chatService = chatService;
        this.properties = properties;
    }

    @GetMapping("/health")
    public HealthResponse health() {
        return new HealthResponse("ok", properties.llmProvider(), properties.ollamaModelPref());
    }

    @PostMapping(path = "/chat", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ChatResponse chat(@Valid @RequestBody ChatRequest request) {
        String answer = chatService.ask(request.message());
        return new ChatResponse(answer, properties.llmProvider(), properties.ollamaModelPref());
    }

    public record ChatRequest(@NotBlank String message) {
    }

    public record ChatResponse(String answer, String provider, String model) {
    }

    public record HealthResponse(String status, String provider, String model) {
    }
}
