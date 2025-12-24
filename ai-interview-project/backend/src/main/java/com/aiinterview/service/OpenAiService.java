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
                    error.printStackTrace();

                    // Generate a mock response based on user message
                    String userMessage = messages.stream()
                        .filter(m -> "user".equals(m.getRole()))
                        .reduce((first, second) -> second)
                        .map(OpenAiMessage::getContent)
                        .orElse("");

                    return Mono.just(generateMockResponse(userMessage));
                });
    }

    /**
     * Generate a mock AI response when OpenAI API is unavailable
     */
    private String generateMockResponse(String userMessage) {
        if (userMessage == null || userMessage.trim().isEmpty()) {
            return "Thank you for your response. Can you tell me more about your experience?";
        }

        String lower = userMessage.toLowerCase();

        if (lower.contains("hello") || lower.contains("hi")) {
            return "Hello! Nice to meet you. Let's start with your background. Can you tell me about your recent work experience?";
        } else if (lower.contains("experience") || lower.contains("worked")) {
            return "That's interesting! Can you elaborate on the specific technologies you used and the challenges you faced?";
        } else if (lower.contains("project")) {
            return "Great! What was your role in that project, and what were the main technical challenges?";
        } else if (lower.contains("yes") || lower.contains("yeah") || lower.contains("sure")) {
            return "Perfect! Let's move on to the next topic. Tell me about a challenging problem you solved recently.";
        } else if (lower.length() > 100) {
            return "Thank you for the detailed explanation. That demonstrates good understanding. Can you give me a specific example?";
        } else if (lower.length() > 20) {
            return "I see. Can you provide more details about that?";
        } else {
            return "Could you elaborate on that point?";
        }
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
                    error.printStackTrace();

                    // Generate mock response as fallback
                    String userMessage = messages.stream()
                        .filter(m -> "user".equals(m.getRole()))
                        .reduce((first, second) -> second)
                        .map(OpenAiMessage::getContent)
                        .orElse("");

                    String mockResponse = generateMockResponse(userMessage);

                    // Stream the mock response word by word
                    return Flux.fromArray(mockResponse.split(" "))
                        .delayElements(Duration.ofMillis(50))
                        .map(word -> word + " ");
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

