package com.aiinterview.ml.quality;

import com.aiinterview.model.openai.OpenAiMessage;
import com.aiinterview.service.OpenAiService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Scores generated interview questions using an LLM evaluation call.
 *
 * Replaces the 3-bucket length heuristic (metric version: heuristic-v1.0) with a 0-100
 * GPT-scored metric (metric version: llm-v1.0). The LLM prompt evaluates specificity,
 * technical depth, role/level relevance, clarity, and non-repetitiveness.
 *
 * If the LLM call fails or returns an unparseable response, the method falls back to
 * {@link #heuristicFallback(String)} so experiment_metric writes are never blocked.
 *
 * Week 25 rows were collected with heuristic-v1.0 (quality scores 30/60/80 only).
 * Week 26 rows collected after this scorer is wired will use llm-v1.0 and carry
 * the metric_version label in readout artifacts for version separation.
 */
@Service
public class QuestionQualityScorer {

    private static final Logger logger = LoggerFactory.getLogger(QuestionQualityScorer.class);

    /** Metric version identifier written to experiment readout artifacts. */
    public static final String METRIC_VERSION = "llm-v1.0";

    @Autowired
    private OpenAiService openAiService;

    /**
     * Score a generated interview question on a 0-100 scale via a GPT evaluation call.
     * Falls back to the length heuristic if the LLM call fails.
     *
     * @param question the generated interview question text
     * @param roleId   the role identifier (e.g., "backend_java")
     * @param level    the seniority level (e.g., "mid", "senior")
     * @return Mono emitting a score in [0, 100]
     */
    public Mono<Double> score(String question, String roleId, String level) {
        if (question == null || question.isBlank()) {
            return Mono.just(0.0);
        }

        String prompt = buildScoringPrompt(question, roleId, level);
        List<OpenAiMessage> messages = List.of(new OpenAiMessage("user", prompt));

        return openAiService.chatWithConfig(messages, "gpt-3.5-turbo", 0.0)
                .map(response -> parseScore(response, question))
                .onErrorResume(e -> {
                    logger.warn("LLM quality scoring failed (heuristic fallback) for question='{}...': {}",
                            question.substring(0, Math.min(60, question.length())), e.getMessage());
                    return Mono.just(heuristicFallback(question));
                });
    }

    private String buildScoringPrompt(String question, String roleId, String level) {
        return "You are evaluating the quality of a technical interview question.\n"
                + "Role: " + roleId + ", Level: " + level + "\n\n"
                + "Score the following question on a scale of 0 to 100.\n"
                + "Consider: specificity, technical depth, role relevance, clarity, "
                + "and non-repetitiveness.\n\n"
                + "Question: \"" + question + "\"\n\n"
                + "Respond with only a single integer between 0 and 100.";
    }

    private double parseScore(String response, String question) {
        if (response == null) return heuristicFallback(question);
        // Strip all non-digit characters and parse the first number found.
        String cleaned = response.trim().replaceAll("[^0-9]", "");
        if (!cleaned.isEmpty()) {
            try {
                double score = Double.parseDouble(cleaned);
                return Math.min(100.0, Math.max(0.0, score));
            } catch (NumberFormatException e) {
                logger.warn("Failed to parse LLM quality score from response: '{}'", response);
            }
        }
        return heuristicFallback(question);
    }

    /**
     * Length-based heuristic fallback (metric version: heuristic-v1.0).
     * Used when LLM scoring is unavailable or fails.
     */
    public static double heuristicFallback(String question) {
        if (question == null || question.length() < 20) return 30.0;
        if (question.length() > 200) return 80.0;
        return 60.0;
    }
}
