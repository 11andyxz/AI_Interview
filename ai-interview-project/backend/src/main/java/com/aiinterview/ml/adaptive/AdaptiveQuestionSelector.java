package com.aiinterview.ml.adaptive;

import com.aiinterview.knowledge.KnowledgeBaseService;
import com.aiinterview.knowledge.model.QuestionItem;
import com.aiinterview.ml.adaptive.model.QuestionDifficultyCalibration;
import com.aiinterview.ml.adaptive.repository.QuestionDifficultyCalibrationRepository;
import com.aiinterview.ml.embedding.entity.QuestionEmbedding;
import com.aiinterview.ml.embedding.repository.QuestionEmbeddingRepository;
import com.aiinterview.ml.embedding.service.TopicCoverageTracker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Selects the next interview question using combined IRT Fisher Information and topic diversity.
 * Score = α * Fisher_Information + (1-α) * Topic_Diversity_Reward
 * where α=0.6 (configurable) balances ability estimation precision with topic coverage.
 */
@Service
public class AdaptiveQuestionSelector {

    private static final Logger logger = LoggerFactory.getLogger(AdaptiveQuestionSelector.class);
    private static final int MIN_CALIBRATION_RESPONSES = 10;

    private final KnowledgeBaseService knowledgeBaseService;
    private final QuestionDifficultyCalibrationRepository calibrationRepository;
    private final CandidateAbilityEstimator abilityEstimator;
    private final TopicCoverageTracker coverageTracker;
    private final QuestionEmbeddingRepository embeddingRepository;

    @Value("${ml.adaptive.se-threshold:0.3}")
    private double seThreshold;

    @Value("${ml.adaptive.min-questions:8}")
    private int minQuestions;

    @Value("${ml.adaptive.max-questions:12}")
    private int maxQuestions;

    @Value("${ml.adaptive.topic-diversity-weight:0.4}")
    private double topicDiversityWeight; // (1-α), default 0.4 means α=0.6

    public AdaptiveQuestionSelector(
            @Qualifier("questionKnowledgeBaseService") KnowledgeBaseService knowledgeBaseService,
            QuestionDifficultyCalibrationRepository calibrationRepository,
            CandidateAbilityEstimator abilityEstimator,
            TopicCoverageTracker coverageTracker,
            QuestionEmbeddingRepository embeddingRepository) {
        this.knowledgeBaseService = knowledgeBaseService;
        this.calibrationRepository = calibrationRepository;
        this.abilityEstimator = abilityEstimator;
        this.coverageTracker = coverageTracker;
        this.embeddingRepository = embeddingRepository;
    }

    /**
     * Select the next question using Maximum Fisher Information at current theta.
     * Legacy method without topic diversity - calls enhanced version with null sessionId.
     */
    public Optional<QuestionItem> selectNextQuestion(String interviewId, String roleId,
                                                      AbilityEstimate currentAbility,
                                                      Set<String> askedQuestionIds) {
        return selectNextQuestion(null, interviewId, roleId, currentAbility, askedQuestionIds);
    }

    /**
     * Select next question using combined IRT Fisher Information and topic diversity.
     * combinedScore = α * Fisher_Information + (1-α) * Topic_Diversity_Reward
     * 
     * @param sessionId Interview session ID (for topic coverage tracking, null disables diversity)
     * @param interviewId Interview ID
     * @param roleId Role ID
     * @param currentAbility Current ability estimate
     * @param askedQuestionIds Set of already asked question IDs
     * @return Best question to ask next
     */
    public Optional<QuestionItem> selectNextQuestion(String sessionId, String interviewId, String roleId,
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

        // Get embeddings for cluster information (if available)
        Map<String, QuestionEmbedding> embeddings = new HashMap<>();
        boolean useTopicDiversity = (sessionId != null);
        Long roleIdNumeric = parseRoleId(roleId);
        
        if (useTopicDiversity && roleIdNumeric != null) {
            List<String> remainingIds = remaining.stream().map(QuestionItem::getId).collect(Collectors.toList());
            embeddings = embeddingRepository.findByQuestionIdInAndRoleId(remainingIds, roleIdNumeric)
                    .stream()
                    .collect(Collectors.toMap(QuestionEmbedding::getQuestionId, e -> e));
        }

        double theta = currentAbility.getTheta();
        QuestionItem best = null;
        double bestScore = -1;
        double maxFisherInfo = 0.0;

        // First pass: find max Fisher Information for normalization
        for (QuestionItem q : remaining) {
            QuestionDifficultyCalibration cal = calibrations.get(q.getId());
            double a = cal != null ? cal.getDiscriminationA() : 1.0;
            double b = cal != null ? cal.getDifficultyB() : mapStaticDifficulty(q.getDifficulty());
            
            double p = abilityEstimator.irtProbability(theta, a, b);
            double info = a * a * p * (1 - p);
            maxFisherInfo = Math.max(maxFisherInfo, info);
        }

        // Second pass: compute combined scores
        for (QuestionItem q : remaining) {
            QuestionDifficultyCalibration cal = calibrations.get(q.getId());
            double a = cal != null ? cal.getDiscriminationA() : 1.0;
            double b = cal != null ? cal.getDifficultyB() : mapStaticDifficulty(q.getDifficulty());

            double p = abilityEstimator.irtProbability(theta, a, b);
            double fisherInfo = a * a * p * (1 - p);
            
            // Normalize Fisher Information to [0, 1]
            double normalizedFisher = maxFisherInfo > 0 ? fisherInfo / maxFisherInfo : 0.0;

            double combinedScore;
            if (useTopicDiversity) {
                // Get topic diversity reward
                QuestionEmbedding embedding = embeddings.get(q.getId());
                double diversityReward = 0.0;
                
                if (embedding != null && embedding.getClusterId() != null) {
                    diversityReward = coverageTracker.getClusterDiversityReward(
                            sessionId, roleIdNumeric, embedding.getClusterId());
                }
                
                // Combined score: α * Fisher + (1-α) * Diversity
                double alpha = 1.0 - topicDiversityWeight;
                combinedScore = alpha * normalizedFisher + topicDiversityWeight * diversityReward;
                
                logger.trace("Question {} - Fisher={:.4f}, Diversity={:.4f}, Combined={:.4f}",
                        q.getId(), normalizedFisher, diversityReward, combinedScore);
            } else {
                // No topic diversity, use Fisher Information only
                combinedScore = normalizedFisher;
            }

            if (combinedScore > bestScore) {
                bestScore = combinedScore;
                best = q;
            }
        }

        if (best != null) {
            if (useTopicDiversity) {
                logger.debug("Selected question {} (combined_score={:.4f}) at theta={:.2f} with topic diversity",
                        best.getId(), bestScore, theta);
            } else {
                logger.debug("Selected question {} (fisher_info={:.4f}) at theta={:.2f}",
                        best.getId(), bestScore * maxFisherInfo, theta);
            }
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

    /**
     * Parse role ID string to Long (for embedding repository queries).
     * Returns null if the role ID is not numeric.
     */
    private Long parseRoleId(String roleId) {
        if (roleId == null) return null;
        try {
            return Long.parseLong(roleId);
        } catch (NumberFormatException e) {
            logger.warn("Role ID '{}' is not numeric, topic diversity disabled", roleId);
            return null;
        }
    }
}
