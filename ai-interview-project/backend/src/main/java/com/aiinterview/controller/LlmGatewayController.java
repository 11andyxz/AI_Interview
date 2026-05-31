package com.aiinterview.controller;

import com.aiinterview.ml.experiment.ExperimentTracker;
import com.aiinterview.ml.gateway.ExperimentAwareLlmRouter;
import com.aiinterview.ml.gateway.LlmRequest;
import com.aiinterview.ml.gateway.LlmRouteDecision;
import com.aiinterview.ml.embedding.service.TopicCoverageTracker;
import com.aiinterview.ml.nlp.ResponseFeatureExtractor;
import com.aiinterview.ml.prediction.entity.CandidateSkillProfile;
import com.aiinterview.ml.prediction.repository.CandidateSkillProfileRepository;
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

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
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

    // Optional beans — only active when ml.nlp.enabled / ml.embedding.enabled are true.
    // Use required=false so the controller starts even when these feature flags are off.
    @Autowired(required = false)
    private ResponseFeatureExtractor featureExtractor;

    @Autowired(required = false)
    private TopicCoverageTracker coverageTracker;

    @Autowired(required = false)
    private CandidateSkillProfileRepository skillProfileRepository;

    // Stable mapping from roleId string to numeric role_id used in ML tables
    private static final Map<String, Long> ROLE_ID_MAP = Map.of(
            "backend_java", 1L,
            "frontend_react", 2L,
            "fullstack", 3L,
            "devops", 4L,
            "data_engineer", 5L,
            "ml_engineer", 6L
    );

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

                // Record topic coverage for this question if the tracker is active.
                // Uses question number as a proxy question ID; the tracker returns early if
                // no embedding is found for that ID (graceful no-op for LLM-generated questions).
                if (coverageTracker != null && sessionId != null) {
                    try {
                        Long numericRoleId = ROLE_ID_MAP.getOrDefault(roleId, 1L);
                        long questionNumber = (long) (history.size() + 1);
                        coverageTracker.recordQuestionAsked(sessionId, numericRoleId, questionNumber);
                    } catch (Exception e) {
                        logger.warn("TopicCoverageTracker failed for session={}: {}", sessionId, e.getMessage());
                    }
                }
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

                // Extract and cache NLP features for this response (async-safe: runs in map()).
                // Derives a stable question key from the question text hash to avoid storing
                // the full question string in the cache key column.
                if (featureExtractor != null && answer != null && !answer.isBlank()) {
                    try {
                        String questionId = "q-" + Integer.toHexString(question != null ? question.hashCode() : 0);
                        featureExtractor.extractAndCache(sessionId, questionId, answer, result.getScore());
                    } catch (Exception e) {
                        logger.warn("ResponseFeatureExtractor failed for session={}: {}", sessionId, e.getMessage());
                    }
                }

                // Update candidate skill profile with the latest score.
                if (skillProfileRepository != null && result.getScore() != null) {
                    try {
                        updateSkillProfile(sessionId, roleId, result.getScore());
                    } catch (Exception e) {
                        logger.warn("CandidateSkillProfile update failed for session={}: {}", sessionId, e.getMessage());
                    }
                }

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

    /**
     * Upsert the candidate skill profile for a session after each answer evaluation.
     * Maintains a rolling score trend (last 20 scores) and recomputes mean/std.
     */
    private void updateSkillProfile(String sessionId, String roleId, double score) {
        CandidateSkillProfile profile = skillProfileRepository.findBySessionId(sessionId)
                .orElseGet(() -> {
                    CandidateSkillProfile p = new CandidateSkillProfile();
                    p.setSessionId(sessionId);
                    p.setRoleId(ROLE_ID_MAP.getOrDefault(roleId, 1L));
                    p.setCumulativeScore(0.0);
                    p.setQuestionCount(0);
                    p.setScoreTrend("[]");
                    p.setCreatedAt(LocalDateTime.now());
                    return p;
                });

        // Update running totals
        profile.setCumulativeScore(profile.getCumulativeScore() + score);
        profile.setQuestionCount(profile.getQuestionCount() + 1);

        // Maintain score trend as a JSON array (last 20 values)
        List<Double> trend = parseTrend(profile.getScoreTrend());
        trend.add(score);
        if (trend.size() > 20) {
            trend = trend.subList(trend.size() - 20, trend.size());
        }
        profile.setScoreTrend(serializeTrend(trend));

        // Recompute mean and std
        double mean = trend.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        profile.setScoreMean(mean);
        if (trend.size() > 1) {
            double variance = trend.stream().mapToDouble(v -> (v - mean) * (v - mean)).average().orElse(0.0);
            profile.setScoreStd(Math.sqrt(variance));
        }

        profile.setUpdatedAt(LocalDateTime.now());
        skillProfileRepository.save(profile);
    }

    private List<Double> parseTrend(String json) {
        List<Double> result = new ArrayList<>();
        if (json == null || json.isBlank() || json.equals("[]")) return result;
        String inner = json.trim().replaceAll("[\\[\\]]", "");
        for (String token : inner.split(",")) {
            try { result.add(Double.parseDouble(token.trim())); } catch (NumberFormatException ignored) {}
        }
        return result;
    }

    private String serializeTrend(List<Double> trend) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < trend.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append(trend.get(i));
        }
        sb.append("]");
        return sb.toString();
    }
}

