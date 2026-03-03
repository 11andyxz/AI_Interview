package com.aiinterview.ml.adaptive;

import com.aiinterview.knowledge.KnowledgeBaseService;
import com.aiinterview.knowledge.model.QuestionItem;
import com.aiinterview.ml.adaptive.model.QuestionDifficultyCalibration;
import com.aiinterview.ml.adaptive.repository.QuestionDifficultyCalibrationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Selects the next interview question using Maximum Fisher Information criterion.
 * At the current ability estimate theta, the question whose Fisher Information
 * I(theta) = a^2 * P(theta) * (1 - P(theta)) is maximized will be selected.
 */
@Service
public class AdaptiveQuestionSelector {

    private static final Logger logger = LoggerFactory.getLogger(AdaptiveQuestionSelector.class);
    private static final int MIN_CALIBRATION_RESPONSES = 10;

    private final KnowledgeBaseService knowledgeBaseService;
    private final QuestionDifficultyCalibrationRepository calibrationRepository;
    private final CandidateAbilityEstimator abilityEstimator;

    @Value("${ml.adaptive.se-threshold:0.3}")
    private double seThreshold;

    @Value("${ml.adaptive.min-questions:8}")
    private int minQuestions;

    @Value("${ml.adaptive.max-questions:12}")
    private int maxQuestions;

    public AdaptiveQuestionSelector(
            @Qualifier("questionKnowledgeBaseService") KnowledgeBaseService knowledgeBaseService,
            QuestionDifficultyCalibrationRepository calibrationRepository,
            CandidateAbilityEstimator abilityEstimator) {
        this.knowledgeBaseService = knowledgeBaseService;
        this.calibrationRepository = calibrationRepository;
        this.abilityEstimator = abilityEstimator;
    }

    /**
     * Select the next question using Maximum Fisher Information at current theta.
     */
    public Optional<QuestionItem> selectNextQuestion(String interviewId, String roleId,
                                                      AbilityEstimate currentAbility,
                                                      Set<String> askedQuestionIds) {
        List<QuestionItem> allQuestions = knowledgeBaseService.getQuestions(roleId);
        List<QuestionItem> remaining = allQuestions.stream()
                .filter(q -> !askedQuestionIds.contains(q.getId()))
                .collect(Collectors.toList());

        if (remaining.isEmpty()) return Optional.empty();

        Map<String, QuestionDifficultyCalibration> calibrations =
                calibrationRepository.findByRoleIdAndResponseCountGreaterThanEqual(roleId, MIN_CALIBRATION_RESPONSES)
                        .stream()
                        .collect(Collectors.toMap(QuestionDifficultyCalibration::getQuestionId, c -> c));

        double theta = currentAbility.getTheta();
        QuestionItem best = null;
        double bestInfo = -1;

        for (QuestionItem q : remaining) {
            QuestionDifficultyCalibration cal = calibrations.get(q.getId());
            double a, b;

            if (cal != null) {
                a = cal.getDiscriminationA();
                b = cal.getDifficultyB();
            } else {
                a = 1.0;
                b = mapStaticDifficulty(q.getDifficulty());
            }

            double p = abilityEstimator.irtProbability(theta, a, b);
            double info = a * a * p * (1 - p);

            if (info > bestInfo) {
                bestInfo = info;
                best = q;
            }
        }

        if (best != null) {
            logger.debug("Selected question {} (info={:.4f}) at theta={:.2f}", best.getId(), bestInfo, theta);
        }

        return Optional.ofNullable(best);
    }

    /**
     * Determine if the interview should terminate based on convergence criteria.
     */
    public boolean shouldTerminate(AbilityEstimate estimate, int questionsAsked, int maxQuestionsOverride) {
        int effectiveMax = maxQuestionsOverride > 0 ? maxQuestionsOverride : maxQuestions;

        if (questionsAsked >= effectiveMax) return true;
        if (questionsAsked < minQuestions) return false;
        return estimate.getStandardError() < seThreshold;
    }

    /**
     * Map static difficulty strings from knowledge base JSON to IRT b-parameters.
     */
    private double mapStaticDifficulty(String difficulty) {
        if (difficulty == null) return 0.0;
        return switch (difficulty.toLowerCase()) {
            case "easy", "1" -> -1.5;
            case "medium", "2" -> 0.0;
            case "hard", "3" -> 1.5;
            case "expert", "4" -> 2.5;
            default -> 0.0;
        };
    }
}
