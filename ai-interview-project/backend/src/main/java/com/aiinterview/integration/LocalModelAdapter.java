package com.aiinterview.integration;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URI;
import java.time.Duration;

/**
 * Skeleton adapter to route requests to a local OpenAI-compatible model endpoint.
 * This is a minimal, synchronous example; integrate with your existing async/DI patterns.
 */
public class LocalModelAdapter {
    private final String url;
    private final String modelName;
    private final HttpClient client;

    public LocalModelAdapter(String url, String modelName) {
        this.url = url;
        this.modelName = modelName;
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    /**
     * Send a chat-style payload to the local model and return raw JSON string.
     * Caller should validate/parse the response using existing validator pipeline.
     */
    public String callLocalModel(String messagesJson, int maxTokens, double temperature) throws Exception {
        String payload = String.format("{\"model\": \"%s\", \"messages\": %s, \"max_tokens\": %d, \"temperature\": %s}",
                modelName, messagesJson, maxTokens, Double.toString(temperature));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(60))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build();

        HttpResponse<String> resp = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() >= 200 && resp.statusCode() < 300) {
            return resp.body();
        }
        throw new RuntimeException("Local model call failed: " + resp.statusCode() + " " + resp.body());
    }

    // Example helper to build a simple messages JSON from a single prompt (naive)
    public static String singlePromptMessagesJson(String prompt) {
        return String.format("[{\"role\": \"system\", \"content\": \"You are an interview assistant.\"}, {\"role\": \"user\", \"content\": \"%s\"} ]", escapeForJson(prompt));
    }

    private static String escapeForJson(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }
}
