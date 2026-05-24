package com.aiinterview.controller;

import com.aiinterview.ml.experiment.ExperimentTracker;
import com.aiinterview.ml.gateway.ExperimentAwareLlmRouter;
import com.aiinterview.ml.gateway.LlmRequest;
import com.aiinterview.ml.gateway.LlmRouteDecision;
import com.aiinterview.model.openai.OpenAiMessage;
import com.aiinterview.service.LlmEvaluationService;
import com.aiinterview.service.OpenAiService;
import com.aiinterview.service.PromptService;
import com.aiinterview.session.SessionService;
import com.aiinterview.session.model.InterviewSession;
import com.aiinterview.session.model.QAHistory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger logger = LoggerFactory.getLogger(LlmGatewayController.class);

    @Autowired
    private OpenAiService openAiService;

    @Autowired
    private PromptService promptService;

    @Autowired
    private LlmEvaluationService evaluationService;

    @Autowired
    private SessionService sessionService;

    @Autowired
    private ExperimentAwareLlmRouter experimentRouter;

    @Autowired
    private ExperimentTracker experimentTracker;

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

        Optional<InterviewSession> sessionOpt = sessionService.getSession(sessionId);
        List<QAHistory> history = sessionOpt.map(InterviewSession::getHistory).orElse(List.of());

        LlmRequest llmRequest = new LlmRequest("question-generate", sessionId);
        llmRequest.setRoleId(roleId);
        llmRequest.setLevel(level);
        LlmRouteDecision route = experimentRouter.route(llmRequest);

        String systemPrompt = route.getPromptTemplate() != null
                ? route.getPromptTemplate()
                : promptService.buildSystemPrompt(roleId, level, candidateInfo);
        String userPrompt = promptService.buildConversationHistoryPrompt(history, maxHistoryMessages);

        List<OpenAiMessage> messages = List.of(
            new OpenAiMessage("system", systemPrompt),
            new OpenAiMessage("user", userPrompt)
        );

        long startTime = System.currentTimeMillis();

        return openAiService.chatWithConfig(messages, route.getModel(), route.getTemperature())
            .map(question -> {
                long latency = System.currentTimeMillis() - startTime;

                if (route.isInExperiment()) {
                    experimentTracker.recordMetric(
                            Long.parseLong(route.getExperimentId()), route.getVariant(),
                            sessionId, estimateQuality(question), latency, estimateTokens(question));
                }

                Map<String, Object> response = new java.util.HashMap<>(Map.of(
                    "question", question,
                    "sessionId", sessionId,
                    "questionNumber", history.size() + 1
                ));
                if (route.isInExperiment()) {
                    response.put("experimentId", route.getExperimentId());
                    response.put("variant", route.getVariant());
                }
                return ResponseEntity.ok((Object) response);
            })
            .onErrorResume(error -> {
                logger.error("Question generation error: {}", error.getMessage());
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
        String sessionId = (String) body.getOrDefault("sessionId", "anonymous");

        LlmRequest llmRequest = new LlmRequest("eval", sessionId);
        llmRequest.setRoleId(roleId);
        LlmRouteDecision route = experimentRouter.route(llmRequest);
        long startTime = System.currentTimeMillis();

        return evaluationService.evaluateAnswer(question, answer, roleId, level)
            .map(result -> {
                long latency = System.currentTimeMillis() - startTime;

                if (route.isInExperiment()) {
                    experimentTracker.recordMetric(
                            Long.parseLong(route.getExperimentId()), route.getVariant(),
                            sessionId, result.getScore() != null ? result.getScore() : 0,
                            latency, 0);
                }

                Map<String, Object> response = new java.util.HashMap<>(Map.of(
                    "score", result.getScore(),
                    "rubricLevel", result.getRubricLevel(),
                    "detailedScores", result.getDetailedScores(),
                    "strengths", result.getStrengths(),
                    "improvements", result.getImprovements(),
                    "followUpQuestions", result.getFollowUpQuestions()
                ));
                return ResponseEntity.ok((Object) response);
            })
            .onErrorResume(error -> {
                logger.error("Evaluation error: {}", error.getMessage());
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
        String sessionId = (String) body.getOrDefault("sessionId", "anonymous");
        
        if (messagesList == null || messagesList.isEmpty()) {
            return Mono.just(ResponseEntity.badRequest().body(Map.of(
                "error", "Messages are required"
            )));
        }

        List<OpenAiMessage> messages = new ArrayList<>();
        for (Map<String, String> msg : messagesList) {
            messages.add(new OpenAiMessage(msg.get("role"), msg.get("content")));
        }

        LlmRequest llmRequest = new LlmRequest("chat", sessionId);
        LlmRouteDecision route = experimentRouter.route(llmRequest);
        long startTime = System.currentTimeMillis();

        return openAiService.chatWithConfig(messages, route.getModel(), route.getTemperature())
            .map(content -> {
                long latency = System.currentTimeMillis() - startTime;

                if (route.isInExperiment()) {
                    experimentTracker.recordMetric(
                            Long.parseLong(route.getExperimentId()), route.getVariant(),
                            sessionId, estimateQuality(content), latency, estimateTokens(content));
                }

                Map<String, String> response = Map.of(
                    "content", content,
                    "role", "assistant"
                );
                return ResponseEntity.ok((Object) response);
            })
            .onErrorResume(error -> {
                logger.error("Chat error: {}", error.getMessage());
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

        // Experiment routing — must mirror POST /question-generate so streaming sessions
        // are assigned to a variant and written to experiment_metric.
        LlmRequest llmRequest = new LlmRequest("question-generate", sessionId);
        llmRequest.setRoleId(roleId);
        llmRequest.setLevel(level);
        LlmRouteDecision route = experimentRouter.route(llmRequest);

        // Get session and history
        Optional<InterviewSession> sessionOpt = sessionService.getSession(sessionId);
        List<QAHistory> history = sessionOpt.map(InterviewSession::getHistory).orElse(List.of());
        Map<String, Object> candidateInfo = sessionOpt.map(InterviewSession::getCandidateInfo).orElse(null);

        // Build prompts — use experiment prompt template if provided
        String systemPrompt = route.getPromptTemplate() != null
                ? route.getPromptTemplate()
                : promptService.buildSystemPrompt(roleId, level, candidateInfo);
        String userPrompt = promptService.buildConversationHistoryPrompt(history, maxHistoryMessages);

        List<OpenAiMessage> messages = List.of(
            new OpenAiMessage("system", systemPrompt),
            new OpenAiMessage("user", userPrompt)
        );

        long startTime = System.currentTimeMillis();
        StringBuilder accumulated = new StringBuilder();

        // Stream response; accumulate chunks to record metric on completion
        return openAiService.chatStream(messages)
            .map(chunk -> {
                accumulated.append(chunk);
                return ServerSentEvent.<String>builder()
                    .data(chunk)
                    .build();
            })
            .concatWith(Flux.just(ServerSentEvent.<String>builder()
                .event("end")
                .data("[DONE]")
                .build()))
            .doOnComplete(() -> {
                if (route.isInExperiment()) {
                    long latency = System.currentTimeMillis() - startTime;
                    String fullResponse = accumulated.toString();
                    experimentTracker.recordMetric(
                            Long.parseLong(route.getExperimentId()), route.getVariant(),
                            sessionId, estimateQuality(fullResponse), latency,
                            estimateTokens(fullResponse));
                }
            })
            .onErrorResume(error -> {
                logger.error("Streaming error: {}", error.getMessage());
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

    private double estimateQuality(String response) {
        if (response == null || response.length() < 20) return 30.0;
        if (response.length() > 200) return 80.0;
        return 60.0;
    }

    private int estimateTokens(String response) {
        return response != null ? response.length() / 4 : 0;
    }
}

