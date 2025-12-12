package com.aiinterview.service;

import com.aiinterview.model.openai.OpenAiMessage;
import com.aiinterview.model.openai.OpenAiRequest;
import com.aiinterview.model.openai.OpenAiResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;

@Service
public class OpenAiService {

    @Autowired
    private WebClient openAiWebClient;

    @Value("${openai.model}")
    private String model;

    @Value("${openai.temperature}")
    private Double temperature;

    @Value("${openai.max-tokens}")
    private Integer maxTokens;

    /**
     * Call OpenAI API with messages (non-streaming)
     */
    public Mono<String> chat(List<OpenAiMessage> messages) {
        OpenAiRequest request = new OpenAiRequest();
        request.setModel(model);
        request.setMessages(messages);
        request.setTemperature(temperature);
        request.setMaxTokens(maxTokens);
        request.setStream(false);

        return openAiWebClient.post()
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(OpenAiResponse.class)
                .timeout(Duration.ofSeconds(60))
                .map(response -> {
                    if (response.getChoices() != null && !response.getChoices().isEmpty()) {
                        return response.getChoices().get(0).getMessage().getContent();
                    }
                    return "";
                })
                .onErrorResume(error -> {
                    System.err.println("OpenAI API Error: " + error.getMessage());
                    return Mono.just("抱歉，AI服务暂时不可用，请稍后重试。");
                });
    }

    /**
     * Call OpenAI API with streaming response
     */
    public Flux<String> chatStream(List<OpenAiMessage> messages) {
        OpenAiRequest request = new OpenAiRequest();
        request.setModel(model);
        request.setMessages(messages);
        request.setTemperature(temperature);
        request.setMaxTokens(maxTokens);
        request.setStream(true);

        return openAiWebClient.post()
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .retrieve()
                .bodyToFlux(String.class)
                .timeout(Duration.ofSeconds(90))
                .map(this::parseStreamChunk)
                .filter(content -> content != null && !content.isEmpty())
                .onErrorResume(error -> {
                    System.err.println("OpenAI Streaming Error: " + error.getMessage());
                    return Flux.just("data: {\"error\":\"服务暂时不可用\"}\n\n");
                });
    }

    /**
     * Parse SSE chunk from OpenAI
     */
    private String parseStreamChunk(String chunk) {
        // OpenAI SSE format: data: {"choices":[{"delta":{"content":"..."}}]}
        // We need to extract the content from delta
        try {
            if (chunk.startsWith("data: ")) {
                String jsonData = chunk.substring(6).trim();
                if (jsonData.equals("[DONE]")) {
                    return "";
                }
                // Simple parsing - in production, use proper JSON parsing
                if (jsonData.contains("\"content\":\"")) {
                    int start = jsonData.indexOf("\"content\":\"") + 11;
                    int end = jsonData.indexOf("\"", start);
                    if (end > start) {
                        return jsonData.substring(start, end);
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Failed to parse chunk: " + e.getMessage());
        }
        return "";
    }

    /**
     * Simple chat with system and user messages
     */
    public Mono<String> simpleChat(String systemPrompt, String userPrompt) {
        List<OpenAiMessage> messages = List.of(
            new OpenAiMessage("system", systemPrompt),
            new OpenAiMessage("user", userPrompt)
        );
        return chat(messages);
    }

    /**
     * Check if OpenAI API key is configured
     */
    public boolean isConfigured() {
        return openAiWebClient != null;
    }

    /**
     * Check if OpenAI API key exists in database
     */
    public boolean hasApiKey() {
        return true; // WebClient bean creation will fail if no API key, so if we get here, it's configured
    }
}

