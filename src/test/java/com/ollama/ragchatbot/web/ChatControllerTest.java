package com.ollama.ragchatbot.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ollama.ragchatbot.config.AnythingLlmProperties;
import com.ollama.ragchatbot.service.ChatService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ChatController.class)
class ChatControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ChatService chatService;

    @MockBean
    private AnythingLlmProperties anythingLlmProperties;

    @Test
    void shouldReturnHealthResponse() throws Exception {
        when(anythingLlmProperties.llmProvider()).thenReturn("ollama");
        when(anythingLlmProperties.ollamaModelPref()).thenReturn("llama3.1");

        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"))
                .andExpect(jsonPath("$.provider").value("ollama"))
                .andExpect(jsonPath("$.model").value("llama3.1"));
    }

    @Test
    void shouldReturnChatResponse() throws Exception {
        when(anythingLlmProperties.llmProvider()).thenReturn("ollama");
        when(anythingLlmProperties.ollamaModelPref()).thenReturn("llama3.1");
        when(chatService.ask("hi")).thenReturn("hello");

        mockMvc.perform(post("/api/v1/chat")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new ChatController.ChatRequest("hi"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value("hello"))
                .andExpect(jsonPath("$.provider").value("ollama"))
                .andExpect(jsonPath("$.model").value("llama3.1"));
    }

    @Test
    void shouldValidateMessage() throws Exception {
        mockMvc.perform(post("/api/v1/chat")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new ChatController.ChatRequest(""))))
                .andExpect(status().isBadRequest());
    }
}
