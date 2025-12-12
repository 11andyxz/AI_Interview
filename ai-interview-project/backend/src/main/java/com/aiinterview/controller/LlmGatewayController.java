package com.aiinterview.controller;

import com.aiinterview.model.openai.OpenAiMessage;
import com.aiinterview.service.LlmEvaluationService;
import com.aiinterview.service.OpenAiService;
import com.aiinterview.service.PromptService;
import com.aiinterview.session.SessionService;
import com.aiinterview.session.model.InterviewSession;
import com.aiinterview.session.model.QAHistory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/llm")
@CrossOrigin(origins = "http://localhost:3000")
public class LlmGatewayController {

    @Autowired
    private OpenAiService openAiService;

    @Autowired
    private PromptService promptService;

    @Autowired
    private LlmEvaluationService evaluationService;

    @Autowired
    private SessionService sessionService;

    @Value("${openai.max-history-messages:10}")
    private int maxHistoryMessages;

    /**
     * Generate next interview question based on session history
     */
    @PostMapping("/question-generate")
    public Mono<ResponseEntity<Object>> questionGenerate(@RequestBody Map<String, Object> body) {
        String sessionId = (String) body.get("sessionId");
        String roleId = (String) body.getOrDefault("roleId", "backend_java");
        String level = (String) body.getOrDefault("level", "mid");
        @SuppressWarnings("unchecked")
        Map<String, Object> candidateInfo = (Map<String, Object>) body.get("candidateInfo");

        // Get session and history
        Optional<InterviewSession> sessionOpt = sessionService.getSession(sessionId);
        List<QAHistory> history = sessionOpt.map(InterviewSession::getHistory).orElse(List.of());

        // Build system prompt
        String systemPrompt = promptService.buildSystemPrompt(roleId, level, candidateInfo);
        
        // Build user prompt with conversation history
        String userPrompt = promptService.buildConversationHistoryPrompt(history, maxHistoryMessages);

        // Call OpenAI
        List<OpenAiMessage> messages = List.of(
            new OpenAiMessage("system", systemPrompt),
            new OpenAiMessage("user", userPrompt)
        );

        return openAiService.chat(messages)
            .map(question -> {
                Map<String, Object> response = Map.of(
                    "question", question,
                    "sessionId", sessionId,
                    "questionNumber", history.size() + 1
                );
                return ResponseEntity.ok((Object) response);
            })
            .onErrorResume(error -> {
                System.err.println("Question generation error: " + error.getMessage());
                return Mono.just(ResponseEntity.status(500).body(Map.of(
                    "error", "Failed to generate question",
                    "message", error.getMessage()
                )));
            });
    }

    /**
     * Evaluate candidate's answer
     */
    @PostMapping("/eval")
    public Mono<ResponseEntity<Object>> eval(@RequestBody Map<String, Object> body) {
        String question = (String) body.get("question");
        String answer = (String) body.get("answer");
        String roleId = (String) body.getOrDefault("roleId", "backend_java");
        String level = (String) body.getOrDefault("level", "mid");

        return evaluationService.evaluateAnswer(question, answer, roleId, level)
            .map(result -> {
                Map<String, Object> response = Map.of(
                    "score", result.getScore(),
                    "rubricLevel", result.getRubricLevel(),
                    "detailedScores", result.getDetailedScores(),
                    "strengths", result.getStrengths(),
                    "improvements", result.getImprovements(),
                    "followUpQuestions", result.getFollowUpQuestions()
                );
                return ResponseEntity.ok((Object) response);
            })
            .onErrorResume(error -> {
                System.err.println("Evaluation error: " + error.getMessage());
                return Mono.just(ResponseEntity.status(500).body(Map.of(
                    "error", "Failed to evaluate answer",
                    "message", error.getMessage()
                )));
            });
    }

    /**
     * General chat endpoint (for follow-up conversations)
     */
    @PostMapping("/chat")
    public Mono<ResponseEntity<Object>> chat(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Map<String, String>> messagesList = (List<Map<String, String>>) body.get("messages");
        
        if (messagesList == null || messagesList.isEmpty()) {
            return Mono.just(ResponseEntity.badRequest().body(Map.of(
                "error", "Messages are required"
            )));
        }

        List<OpenAiMessage> messages = new ArrayList<>();
        for (Map<String, String> msg : messagesList) {
            messages.add(new OpenAiMessage(msg.get("role"), msg.get("content")));
        }

        return openAiService.chat(messages)
            .map(content -> {
                Map<String, String> response = Map.of(
                    "content", content,
                    "role", "assistant"
                );
                return ResponseEntity.ok((Object) response);
            })
            .onErrorResume(error -> {
                System.err.println("Chat error: " + error.getMessage());
                return Mono.just(ResponseEntity.status(500).body(Map.of(
                    "error", "Chat failed",
                    "message", error.getMessage()
                )));
            });
    }

    /**
     * Streaming question generation (SSE)
     */
    @GetMapping(path = "/question-generate/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> questionGenerateStream(
            @RequestParam String sessionId,
            @RequestParam(defaultValue = "backend_java") String roleId,
            @RequestParam(defaultValue = "mid") String level) {

        // Get session and history
        Optional<InterviewSession> sessionOpt = sessionService.getSession(sessionId);
        List<QAHistory> history = sessionOpt.map(InterviewSession::getHistory).orElse(List.of());
        Map<String, Object> candidateInfo = sessionOpt.map(InterviewSession::getCandidateInfo).orElse(null);

        // Build prompts
        String systemPrompt = promptService.buildSystemPrompt(roleId, level, candidateInfo);
        String userPrompt = promptService.buildConversationHistoryPrompt(history, maxHistoryMessages);

        List<OpenAiMessage> messages = List.of(
            new OpenAiMessage("system", systemPrompt),
            new OpenAiMessage("user", userPrompt)
        );

        // Stream response
        return openAiService.chatStream(messages)
            .map(chunk -> ServerSentEvent.<String>builder()
                .data(chunk)
                .build())
            .concatWith(Flux.just(ServerSentEvent.<String>builder()
                .event("end")
                .data("[DONE]")
                .build()))
            .onErrorResume(error -> {
                System.err.println("Streaming error: " + error.getMessage());
                return Flux.just(ServerSentEvent.<String>builder()
                    .event("error")
                    .data("Streaming failed: " + error.getMessage())
                    .build());
            });
    }

    /**
     * Health check for OpenAI integration
     */
    @GetMapping("/health")
    public ResponseEntity<?> health() {
        boolean configured = openAiService.isConfigured();
        return ResponseEntity.ok(Map.of(
            "configured", configured,
            "status", configured ? "ready" : "not_configured",
            "message", configured ? "OpenAI service is ready" : "OpenAI API key not configured"
        ));
    }
}

