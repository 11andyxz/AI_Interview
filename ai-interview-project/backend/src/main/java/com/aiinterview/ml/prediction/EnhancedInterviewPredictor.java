package com.aiinterview.ml.prediction;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Enhanced Interview Outcome Predictor with calibration and context features.
 * 
 * Improvements over baseline InterviewOutcomePredictor:
 * 1. Platt Scaling: Calibrates predicted probabilities to reduce Brier score
 * 2. Context-Aware Features: Uses job role, question difficulty, and trajectory features
 * 3. Weak Slice Improvement: Specifically targets low-performance slices (junior profiles, short sessions)
 * 
 * Week 17 P1 Task 3: Address weak slice RMSE improvement (junior RMSE 12.4 → ≤11.5)
 */
@Component
@ConditionalOnProperty(name = "ml.prediction.enhanced.enabled", havingValue = "true", matchIfMissing = false)
public class EnhancedInterviewPredictor {
    
    private static final Logger logger = LoggerFactory.getLogger(EnhancedInterviewPredictor.class);
    
    @Autowired
    private InterviewOutcomePredictor basePredictor;
    
    @Autowired
    private PlattCalibrator calibrator;
    
    @Autowired
    private ContextFeaturesExtractor featureExtractor;
    
    // Feature weights for enhanced prediction (tuned on validation data)
    private static final double BASE_PREDICTION_WEIGHT = 0.6;
    private static final double CONTEXT_FEATURE_WEIGHT = 0.4;
    
    /**
     * Predict interview outcome with enhanced features and calibration.
     * 
     * @param profile Candidate skill profile
     * @param recentScores Recent question scores
     * @param targetRole Target job role (e.g., "junior", "mid", "senior")
     * @param questionDifficulties List of question difficulty levels
     * @return Enhanced prediction result with calibrated probabilities
     */
    public EnhancedPredictionResult predictOutcome(
            com.aiinterview.ml.prediction.entity.CandidateSkillProfile profile,
            List<Double> recentScores,
            String targetRole,
            List<String> questionDifficulties) {
        
        // Week 17 P1 Task 3: Short session conservative fallback
        // For sessions with <6 questions, use conservative prediction with wider confidence intervals
        if (recentScores == null || recentScores.size() < 6) {
            logger.info("Short session detected ({} questions < 6), applying conservative fallback",
                       recentScores != null ? recentScores.size() : 0);
            return createConservativeFallbackPrediction(profile, recentScores, targetRole);
        }
        
        // 1. Get base prediction
        InterviewOutcomePredictor.PredictionResult basePrediction = 
            basePredictor.predictOutcome(profile, recentScores);
        
        // 2. Extract context-aware features
        ContextFeaturesExtractor.ContextFeatures contextFeatures = 
            featureExtractor.extractFeatures(targetRole, questionDifficulties, recentScores);
        
        // 3. Enhance prediction with context features
        double enhancedScore = enhancePredictionWithContext(
            basePrediction.getPredictedFinalScore(),
            contextFeatures
        );
        
        // 4. Calibrate probability using Platt scaling (if fitted)
        double rawPassProb = basePrediction.getPassProbability();
        double calibratedPassProb = calibrator.isFitted() ? 
            calibrator.calibrate(rawPassProb * 100) : rawPassProb;
        
        // 5. Adjust confidence based on context features
        double enhancedConfidence = enhanceConfidence(
            basePrediction.getConfidence(),
            contextFeatures
        );
        
        // Build enhanced result
        EnhancedPredictionResult result = new EnhancedPredictionResult();
        result.setBasePrediction(basePrediction);
        result.setContextFeatures(contextFeatures);
        result.setEnhancedScore(enhancedScore);
        result.setCalibratedPassProbability(calibratedPassProb);
        result.setEnhancedConfidence(enhancedConfidence);
        
        // Generate slice-specific recommendation
        String sliceRecommendation = generateSliceAwareRecommendation(
            enhancedScore,
            calibratedPassProb,
            targetRole,
            contextFeatures
        );
        result.setRecommendation(sliceRecommendation);
        
        logger.debug("Enhanced prediction: base={:.2f}, enhanced={:.2f}, calib_prob={:.3f}, role={}",
                    basePrediction.getPredictedFinalScore(), 
                    enhancedScore,
                    calibratedPassProb,
                    targetRole);
        
        return result;
    }
    
    /**
     * Enhance prediction score using context features.
     * 
     * Context features help correct for:
     * - Role mismatch (candidate at wrong difficulty level)
     * - Poor trajectory (declining performance)
     * - Overchallenging questions (unfairly low scores)
     */
    private double enhancePredictionWithContext(
            double baseScore,
            ContextFeaturesExtractor.ContextFeatures contextFeatures) {
        
        double adjustment = 0.0;
        
        // 1. Difficulty alignment adjustment
        // If questions are poorly aligned (too hard/easy), adjust score
        double alignmentAdjustment = (contextFeatures.getAvgDifficultyAlignment() - 0.5) * 10.0;
        
        // 2. Trajectory adjustment
        // Positive trend → increase score; negative trend → decrease score
        double trajectoryAdjustment = contextFeatures.getImprovementRate() * 2.0;
        
        // 3. Overchallenging penalty reduction
        // If many questions are overchallenging, boost score slightly
        double overchallengingBoost = contextFeatures.getOverchallengingRatio() * 5.0;
        
        adjustment = alignmentAdjustment + trajectoryAdjustment + overchallengingBoost;
        
        // Weighted combination of base and adjusted score
        double enhancedScore = BASE_PREDICTION_WEIGHT * baseScore + 
                               CONTEXT_FEATURE_WEIGHT * (baseScore + adjustment);
        
        // Clip to valid range
        return Math.max(0.0, Math.min(100.0, enhancedScore));
    }
    
    /**
     * Enhance confidence using context features.
     * 
     * Higher consistency and good difficulty alignment → higher confidence
     */
    private double enhanceConfidence(
            double baseConfidence,
            ContextFeaturesExtractor.ContextFeatures contextFeatures) {
        
        // Consistency bonus: stable performance → higher confidence
        double consistencyBonus = contextFeatures.getConsistency() * 0.2;
        
        // Alignment bonus: well-matched questions → higher confidence
        double alignmentBonus = (contextFeatures.getAvgDifficultyAlignment() - 0.5) * 0.2;
        
        double enhancedConfidence = baseConfidence + consistencyBonus + alignmentBonus;
        
        return Math.max(0.0, Math.min(1.0, enhancedConfidence));
    }
    
    /**
     * Create conservative fallback prediction for short sessions (<6 questions).
     * 
     * Week 17 P1 Task 3: Address short session weakness
     * 
     * Strategy:
     * - Lower confidence (uncertainty due to insufficient data)
     * - Neutral/conservative pass probability (avoid false positives/negatives)
     * - Recommendation to continue interview
     * 
     * @param profile Candidate skill profile
     * @param recentScores Recent question scores (may be empty/null)
     * @param targetRole Target job role
     * @return Conservative prediction result
     */
    private EnhancedPredictionResult createConservativeFallbackPrediction(
            com.aiinterview.ml.prediction.entity.CandidateSkillProfile profile,
            List<Double> recentScores,
            String targetRole) {
        
        // Get base prediction (even with limited data)
        InterviewOutcomePredictor.PredictionResult basePrediction = 
            basePredictor.predictOutcome(profile, recentScores);
        
        // Apply conservative adjustments
        double conservativeScore = basePrediction.getPredictedFinalScore();
        
        // Regress toward mean (reduce extreme predictions)
        double MEAN_SCORE = 70.0;  // Historical average
        double REGRESSION_FACTOR = 0.3;  // 30% regression toward mean
        conservativeScore = conservativeScore * (1 - REGRESSION_FACTOR) + MEAN_SCORE * REGRESSION_FACTOR;
        
        // Conservative pass probability (neutral)
        double conservativePassProb = 0.5;  // Maximum uncertainty
        
        // Low confidence (insufficient data)
        double conservativeConfidence = 0.3;  // Indicate high uncertainty
        
        // Build result
        EnhancedPredictionResult result = new EnhancedPredictionResult();
        result.setBasePrediction(basePrediction);
        result.setContextFeatures(null);  // No context features for short sessions
        result.setEnhancedScore(conservativeScore);
        result.setCalibratedPassProbability(conservativePassProb);
        result.setEnhancedConfidence(conservativeConfidence);
        result.setRecommendation("short_session_continue");  // Always continue
        
        logger.info("Conservative fallback: score={:.2f}, prob={:.2f}, confidence={:.2f}",
                   conservativeScore, conservativePassProb, conservativeConfidence);
        
        return result;
    }
    
    /**
     * Generate slice-aware recommendation.
     * 
     * Tailors recommendations based on job role and context:
     * - Junior: Focus on growth trajectory and potential
     * - Senior: Focus on consistency and alignment
     */
    private String generateSliceAwareRecommendation(
            double enhancedScore,
            double calibratedPassProb,
            String targetRole,
            ContextFeaturesExtractor.ContextFeatures contextFeatures) {
        
        boolean isJunior = targetRole != null && targetRole.equalsIgnoreCase("junior");
        boolean isSenior = targetRole != null && 
                          (targetRole.equalsIgnoreCase("senior") || 
                           targetRole.equalsIgnoreCase("staff") ||
                           targetRole.equalsIgnoreCase("principal"));
        
        // Week 17 P1 Task 3: Emphasize junior slice improvement
        // For junior roles: emphasize improvement trajectory and potential
        if (isJunior) {
            // Strong growth trajectory → high confidence even if current score is moderate
            if (contextFeatures.getImprovementRate() > 2.0 && calibratedPassProb > 0.6) {
                return "junior_strong_growth_potential";
            }
            // Good consistency → reliable baseline
            if (contextFeatures.getConsistency() > 0.7 && calibratedPassProb > 0.7) {
                return "junior_reliable_performance";
            }
            // Positive momentum → room for growth
            if (contextFeatures.getMomentum() > 0.5) {
                return "junior_positive_momentum";
            }
        }
        
        // For senior roles: consistency and alignment are critical
        if (isSenior) {
            if (contextFeatures.getConsistency() > 0.8 && calibratedPassProb > 0.8) {
                return "senior_high_confidence";
            }
            if (contextFeatures.getConsistency() < 0.5) {
                return "senior_inconsistent_performance";
            }
        }
        
        // General recommendations
        if (calibratedPassProb > 0.9) {
            return "strong_pass_recommended";
        }
        if (calibratedPassProb < 0.1) {
            return "fail_recommended";
        }
        if (contextFeatures.getOverchallengingRatio() > 0.5) {
            return "questions_too_difficult";
        }
        
        return "continue_interview";
    }
    
    /**
     * Enhanced prediction result containing base prediction, context features, and calibrated outputs.
     */
    public static class EnhancedPredictionResult {
        private InterviewOutcomePredictor.PredictionResult basePrediction;
        private ContextFeaturesExtractor.ContextFeatures contextFeatures;
        private double enhancedScore;
        private double calibratedPassProbability;
        private double enhancedConfidence;
        private String recommendation;
        
        // Getters and setters
        public InterviewOutcomePredictor.PredictionResult getBasePrediction() { 
            return basePrediction; 
        }
        public void setBasePrediction(InterviewOutcomePredictor.PredictionResult basePrediction) { 
            this.basePrediction = basePrediction; 
        }
        
        public ContextFeaturesExtractor.ContextFeatures getContextFeatures() { 
            return contextFeatures; 
        }
        public void setContextFeatures(ContextFeaturesExtractor.ContextFeatures contextFeatures) { 
            this.contextFeatures = contextFeatures; 
        }
        
        public double getEnhancedScore() { 
            return enhancedScore; 
        }
        public void setEnhancedScore(double enhancedScore) { 
            this.enhancedScore = enhancedScore; 
        }
        
        public double getCalibratedPassProbability() { 
            return calibratedPassProbability; 
        }
        public void setCalibratedPassProbability(double calibratedPassProbability) { 
            this.calibratedPassProbability = calibratedPassProbability; 
        }
        
        public double getEnhancedConfidence() { 
            return enhancedConfidence; 
        }
        public void setEnhancedConfidence(double enhancedConfidence) { 
            this.enhancedConfidence = enhancedConfidence; 
        }
        
        public String getRecommendation() { 
            return recommendation; 
        }
        public void setRecommendation(String recommendation) { 
            this.recommendation = recommendation; 
        }
    }
}
