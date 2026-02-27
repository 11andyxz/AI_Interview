package com.aiinterview.controller;

import com.aiinterview.ml.experiment.Experiment;
import com.aiinterview.ml.gateway.*;
import com.aiinterview.model.openai.OpenAiMessage;
import com.aiinterview.service.LlmEvaluationService;
import com.aiinterview.service.OpenAiService;
import com.aiinterview.service.PromptService;
import com.aiinterview.session.SessionService;
import com.aiinterview.session.model.InterviewSession;
import com.aiinterview.session.model.QAHistory;
import lombok.extern.slf4j.Slf4j;
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

@Slf4j
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
    
    @Autowired(required = false)
    private ExperimentAwareLlmRouter router;
    
    @Autowired(required = false)
    private ExperimentLifecycleManager lifecycleManager;
    
    @Autowired(required = false)
    private ExperimentTemplates templates;
    
    @Autowired(required = false)
    private PromptVersionRepository promptVersionRepository;

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
     * Route LLM request through experiment gateway
     */
    @PostMapping("/gateway/route")
    public ResponseEntity<LlmRouteDecision> route(@RequestBody LlmRequest request) {
        if (router == null) {
            return ResponseEntity.status(503).build();
        }
        log.info("Routing request: type={}, requestId={}", 
                 request.getRequestType(), request.getRequestId());
        
        LlmRouteDecision decision = router.route(request);
        return ResponseEntity.ok(decision);
    }
    
    /**
     * Launch experiment from template
     */
    @PostMapping("/gateway/experiments")
    public ResponseEntity<Experiment> launchExperiment(@RequestBody ExperimentTemplate template) {
        if (lifecycleManager == null) {
            return ResponseEntity.status(503).build();
        }
        log.info("Launching experiment: {}", template.getName());
        
        Experiment experiment = lifecycleManager.launchExperiment(template);
        return ResponseEntity.ok(experiment);
    }
    
    /**
     * Conclude experiment
     */
    @PostMapping("/gateway/experiments/{experimentId}/conclude")
    public ResponseEntity<Void> concludeExperiment(@PathVariable String experimentId) {
        if (lifecycleManager == null) {
            return ResponseEntity.status(503).build();
        }
        log.info("Concluding experiment: {}", experimentId);
        
        lifecycleManager.concludeExperiment(experimentId);
        return ResponseEntity.ok().build();
    }
    
    /**
     * Generate experiment report
     */
    @GetMapping("/gateway/experiments/{experimentId}/report")
    public ResponseEntity<ExperimentReport> getReport(@PathVariable String experimentId) {
        if (lifecycleManager == null) {
            return ResponseEntity.status(503).build();
        }
        log.info("Generating report for experiment: {}", experimentId);
        
        ExperimentReport report = lifecycleManager.generateReport(experimentId);
        return ResponseEntity.ok(report);
    }
    
    /**
     * Get prompt variant template
     */
    @GetMapping("/gateway/templates/prompt-variant")
    public ResponseEntity<ExperimentTemplate> getPromptVariantTemplate(
            @RequestParam String promptKey,
            @RequestParam String baselineVersion,
            @RequestParam String variantVersion) {
        if (templates == null) {
            return ResponseEntity.status(503).build();
        }
        
        ExperimentTemplate template = templates.promptVariantTemplate(
            promptKey, baselineVersion, variantVersion);
        return ResponseEntity.ok(template);
    }
    
    /**
     * Get model comparison template
     */
    @GetMapping("/gateway/templates/model-comparison")
    public ResponseEntity<ExperimentTemplate> getModelComparisonTemplate(
            @RequestParam String endpoint,
            @RequestParam String baselineModel,
            @RequestParam String variantModel) {
        if (templates == null) {
            return ResponseEntity.status(503).build();
        }
        
        ExperimentTemplate template = templates.modelComparisonTemplate(
            endpoint, baselineModel, variantModel);
        return ResponseEntity.ok(template);
    }
    
    /**
     * Get temperature tuning template
     */
    @GetMapping("/gateway/templates/temperature-tuning")
    public ResponseEntity<ExperimentTemplate> getTemperatureTuningTemplate(
            @RequestParam String endpoint,
            @RequestParam double baselineTemp,
            @RequestParam double variantTemp) {
        if (templates == null) {
            return ResponseEntity.status(503).build();
        }
        
        ExperimentTemplate template = templates.temperatureTuningTemplate(
            endpoint, baselineTemp, variantTemp);
        return ResponseEntity.ok(template);
    }
    
    /**
     * Get RAG optimization template
     */
    @GetMapping("/gateway/templates/rag-optimization")
    public ResponseEntity<ExperimentTemplate> getRagOptimizationTemplate(
            @RequestParam String endpoint,
            @RequestParam int baselineTopK,
            @RequestParam int variantTopK) {
        if (templates == null) {
            return ResponseEntity.status(503).build();
        }
        
        ExperimentTemplate template = templates.ragOptimizationTemplate(
            endpoint, baselineTopK, variantTopK);
        return ResponseEntity.ok(template);
    }
    
    /**
     * Create or update prompt version
     */
    @PostMapping("/gateway/prompts")
    public ResponseEntity<PromptVersion> createPromptVersion(@RequestBody PromptVersion promptVersion) {
        if (promptVersionRepository == null) {
            return ResponseEntity.status(503).build();
        }
        log.info("Creating prompt version: key={}, version={}", 
                 promptVersion.getPromptKey(), promptVersion.getVersion());
        
        PromptVersion saved = promptVersionRepository.save(promptVersion);
        return ResponseEntity.ok(saved);
    }
    
    /**
     * Get prompt versions by key
     */
    @GetMapping("/gateway/prompts/{promptKey}")
    public ResponseEntity<List<PromptVersion>> getPromptVersions(@PathVariable String promptKey) {
        if (promptVersionRepository == null) {
            return ResponseEntity.status(503).build();
        }
        log.info("Getting prompt versions for key: {}", promptKey);
        
        List<PromptVersion> versions = promptVersionRepository.findByPromptKey(promptKey);
        return ResponseEntity.ok(versions);
    }
    
    /**
     * Get specific prompt version
     */
    @GetMapping("/gateway/prompts/{promptKey}/{version}")
    public ResponseEntity<PromptVersion> getPromptVersion(
            @PathVariable String promptKey,
            @PathVariable String version) {
        if (promptVersionRepository == null) {
            return ResponseEntity.status(503).build();
        }
        
        log.info("Getting prompt version: key={}, version={}", promptKey, version);
        
        return promptVersionRepository.findByPromptKeyAndVersion(promptKey, version)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
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

