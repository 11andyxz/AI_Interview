package com.aiinterview.controller;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/llm")
@CrossOrigin(origins = "http://localhost:3000")
public class LlmGatewayController {

    // TODO: Replace mock logic with real LLM invocation, prompt construction, history summarization, caching, and streaming support.

    @PostMapping("/chat")
    public ResponseEntity<?> chat(@RequestBody Map<String, Object> body) {
        // Mock echo with timestamp
        return ResponseEntity.ok(Map.of(
                "content", "Mock LLM reply at " + Instant.now() + ": " + body.getOrDefault("messages", List.of()),
                "usage", Map.of("promptTokens", 10, "completionTokens", 5)
        ));
    }

    @PostMapping("/question-generate")
    public ResponseEntity<?> questionGenerate(@RequestBody Map<String, Object> body) {
        String roleId = (String) body.getOrDefault("roleId", "backend_java");
        String level = (String) body.getOrDefault("level", "mid");
        return ResponseEntity.ok(Map.of(
                "questions", List.of(
                        "Mock question for " + roleId + " (" + level + "): Describe your most challenging project.",
                        "Mock question for " + roleId + " (" + level + "): How do you debug complex production issues?"
                )
        ));
    }

    @PostMapping("/eval")
    public ResponseEntity<?> eval(@RequestBody Map<String, Object> body) {
        String answer = (String) body.getOrDefault("answer", "");
        String level = answer.length() > 40 ? "excellent" : answer.length() > 15 ? "average" : "poor";
        return ResponseEntity.ok(Map.of(
                "score", switch (level) {
                    case "excellent" -> 0.9;
                    case "average" -> 0.6;
                    default -> 0.3;
                },
                "rubricLevel", level,
                "comments", "Mock evaluation based on length; replace with LLM scoring."
        ));
    }

    // TODO: Add streaming endpoint (e.g., SSE) when real LLM integration is ready.
    @GetMapping(path = "/chat/stream/mock", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<String> mockStream() {
        return ResponseEntity.ok("data: mock streaming not implemented yet\n\n");
    }
}

