package com.aiinterview.service;

import com.aiinterview.model.EvaluationResult;
import com.aiinterview.model.openai.OpenAiMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;

@Service
public class LlmEvaluationService {

    @Autowired
    private OpenAiService openAiService;

    @Autowired
    private PromptService promptService;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final Logger logger = LoggerFactory.getLogger(LlmEvaluationService.class);

    /**
     * Evaluate candidate's answer using LLM
     */
    public Mono<EvaluationResult> evaluateAnswer(String question, String answer,
                                                  String roleId, String level) {
        return evaluateAnswer(question, answer, roleId, level, null, null);
    }

    public Mono<EvaluationResult> evaluateAnswer(String question, String answer,
                                                  String roleId, String level,
                                                  String requestId, String promptHash) {
        // Build evaluation prompt
        String systemPrompt = promptService.buildEvaluationSystemPrompt();
        String userPrompt = promptService.buildEvaluationPrompt(question, answer, roleId, level);

        List<OpenAiMessage> messages = List.of(
            new OpenAiMessage("system", systemPrompt),
            new OpenAiMessage("user", userPrompt)
        );

        final String effectiveRequestId = requestId == null ? java.util.UUID.randomUUID().toString() : requestId;
        logger.info("evaluateAnswer start - requestId={} promptHash={} roleId={}", effectiveRequestId, promptHash, roleId);

        return openAiService.chat(messages)
            .map(r -> parseEvaluationResult(r, effectiveRequestId, promptHash, 1))
            .onErrorResume(error -> {
                logger.warn("evaluateAnswer first attempt failed - requestId={} promptHash={} error={}", effectiveRequestId, promptHash, error.toString());
                // Only retry for explicit retryable errors (schema mismatch or token truncation)
                if (!isRetryableError(error)) {
                    logger.warn("Not retrying - non-retryable error - requestId={} promptHash={}", effectiveRequestId, promptHash);
                    return Mono.just(createFallbackEvaluation(answer));
                }

                // retry once
                return openAiService.chat(messages)
                    .map(r2 -> {
                        EvaluationResult res = parseEvaluationResult(r2, effectiveRequestId, promptHash, 2);
                        logger.info("evaluateAnswer retry succeeded - requestId={} promptHash={}", effectiveRequestId, promptHash);
                        return res;
                    })
                    .onErrorResume(e2 -> {
                        logger.error("evaluateAnswer retry failed - requestId={} promptHash={} error={}", effectiveRequestId, promptHash, e2.toString());
                        return Mono.just(createFallbackEvaluation(answer));
                    });
            });
    }

    /**
     * Decide whether an error should trigger a retry. Keep this conservative.
     */
    private boolean isRetryableError(Throwable t) {
        if (t == null) return false;
        String m = t.toString().toLowerCase();
        // Retry only for messages that clearly indicate schema or truncation problems
        if (m.contains("schema_mismatch") || m.contains("schema error") || m.contains("schema_error")) return true;
        if (m.contains("token_truncation") || m.contains("truncat") || m.contains("truncated")) return true;
        return false;
    }

    /**
     * Parse evaluation result from JSON string
     */
    private EvaluationResult parseEvaluationResult(String jsonResponse, String requestId, String promptHash, int attempt) {
        try {
            String jsonContent = extractJson(jsonResponse);
            EvaluationResult result = objectMapper.readValue(jsonContent, EvaluationResult.class);
            logger.info("evaluateAnswer parse success - requestId={} promptHash={} attempt={} score={}", requestId, promptHash, attempt, result.getScore());
            return result;
        } catch (com.fasterxml.jackson.core.JsonParseException jpe) {
            logger.error("evaluateAnswer parse error - MALFORMED_JSON - requestId={} promptHash={} attempt={} error={}", requestId, promptHash, attempt, jpe.toString());
            logger.debug("Evaluation raw response: {}", jsonResponse);
            return createFallbackEvaluation(null);
        } catch (Exception e) {
            logger.error("evaluateAnswer parse error - {} - requestId={} promptHash={} attempt={} error={}", e.getClass().getSimpleName(), requestId, promptHash, attempt, e.toString());
            logger.debug("Evaluation raw response: {}", jsonResponse);
            return createFallbackEvaluation(null);
        }
    }

    /**
     * Extract JSON from response that might contain additional text
     */
    private String extractJson(String response) {
        if (response == null) return "";
        int first = response.indexOf('{');
        if (first < 0) return response;

        int depth = 0;
        for (int i = first; i < response.length(); i++) {
            char c = response.charAt(i);
            if (c == '{') depth++;
            else if (c == '}') depth--;

            if (depth == 0) {
                // balanced JSON found
                return response.substring(first, i + 1);
            }
        }

        // No balanced closing brace found -> partial/truncated JSON
        // Try to salvage by taking substring from first to last '}' if exists
        int last = response.lastIndexOf('}');
        if (last > first) {
            return response.substring(first, last + 1);
        }

        // Give up: return original to allow parser to throw
        return response;
    }

    /**
     * Create fallback evaluation when AI service fails
     */
    private EvaluationResult createFallbackEvaluation(String answer) {
        EvaluationResult result = new EvaluationResult();
        
        // Simple length-based scoring as fallback
        int length = answer != null ? answer.length() : 0;
        
        if (length > 200) {
            result.setScore(70.0);
            result.setRubricLevel("good");
            result.setTechnicalAccuracy(7);
            result.setDepth(7);
            result.setExperience(7);
            result.setCommunication(7);
        } else if (length > 100) {
            result.setScore(60.0);
            result.setRubricLevel("average");
            result.setTechnicalAccuracy(6);
            result.setDepth(6);
            result.setExperience(6);
            result.setCommunication(6);
        } else {
            result.setScore(50.0);
            result.setRubricLevel("poor");
            result.setTechnicalAccuracy(5);
            result.setDepth(5);
            result.setExperience(5);
            result.setCommunication(5);
        }
        
        result.setStrengths(List.of("回答了问题"));
        result.setImprovements(List.of("可以提供更多细节", "建议结合实际项目经验"));
        result.setFollowUpQuestions(List.of("能否详细解释一下？", "在实际项目中如何应用？"));
        
        return result;
    }

    /**
     * Batch evaluate multiple answers (for final feedback)
     */
    public String generateOverallFeedback(List<String> questions, List<String> answers, 
                                         String roleId, String level) {
        StringBuilder feedback = new StringBuilder();
        feedback.append("面试总结：\n\n");
        
        // This could be enhanced to use LLM for comprehensive feedback
        feedback.append("候选人在本次面试中回答了 ").append(questions.size()).append(" 个问题。\n");
        feedback.append("建议进一步评估候选人的技术深度和实践经验。\n");
        
        return feedback.toString();
    }
}

