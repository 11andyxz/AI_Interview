package com.aiinterview.integration;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Example Spring REST controller showing a simple integration point for the local model adapter.
 * This is a minimal example; adapt to your project's authentication, async patterns and error handling.
 */
@RestController
@RequestMapping("/api/v1/local-model")
public class LocalModelController {

    private final LocalModelAdapter adapter;

    public LocalModelController() {
        // In production, inject via Spring and read from configuration
        this.adapter = new LocalModelAdapter("http://127.0.0.1:7860/v1/chat/completions", "llama-2-7b-chat");
    }

    @PostMapping("/chat")
    public String chat(@RequestBody String prompt) throws Exception {
        String messagesJson = LocalModelAdapter.singlePromptMessagesJson(prompt);
        // Use conservative defaults; caller/service layer should validate and adapt
        return adapter.callLocalModel(messagesJson, 256, 0.0);
    }
}
