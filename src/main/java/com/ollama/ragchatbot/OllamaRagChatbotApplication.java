package com.ollama.ragchatbot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class OllamaRagChatbotApplication {

    public static void main(String[] args) {
        SpringApplication.run(OllamaRagChatbotApplication.class, args);
    }
}
