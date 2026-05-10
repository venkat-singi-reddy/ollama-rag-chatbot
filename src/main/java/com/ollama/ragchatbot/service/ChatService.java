package com.ollama.ragchatbot.service;

import dev.langchain4j.model.chat.ChatLanguageModel;
import org.springframework.stereotype.Service;

@Service
public class ChatService {

    private final ChatLanguageModel chatLanguageModel;

    public ChatService(ChatLanguageModel chatLanguageModel) {
        this.chatLanguageModel = chatLanguageModel;
    }

    public String ask(String message) {
        return chatLanguageModel.generate(message);
    }
}
