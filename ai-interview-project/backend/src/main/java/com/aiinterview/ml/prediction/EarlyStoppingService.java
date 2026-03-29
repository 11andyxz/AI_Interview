package com.aiinterview.ml.prediction;

import com.aiinterview.ml.prediction.config.EarlyStoppingConfig;
import com.aiinterview.ml.prediction.entity.CandidateSkillProfile;
import com.aiinterview.ml.prediction.repository.CandidateSkillProfileRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Early stopping service for interviews based on outcome predictions.
 * Week 17 P0 Task 2: Unified dual-threshold policy (0.90/0.10, min 6 questions).
 */
@Service
@ConditionalOnProperty(name = "ml.prediction.enabled", havingValue = "true", matchIfMissing = false)
public class EarlyStoppingService {
    
    private static final Logger logger = LoggerFactory.getLogger(EarlyStoppingService.class);
    
    @Autowired
    private InterviewOutcomePredictor outcomePredictor;
    
    @Autowired
    private CandidateSkillProfileRepository profileRepository;
    
    @Autowired
    private EarlyStoppingConfig config;
    
    /**
     * Evaluate whether interview should stop early.
     * Week 17 P0 Task 2: Uses new policy (0.90/0.10) if enabled, otherwise baseline.
     * 
     * @param profile Candidate skill profile
     * @param recentScores Recent question scores
     * @return Early stopping decision
     */
    public EarlyStoppingDecision evaluateEarlyStopping(CandidateSkillProfile profile, 
                                                       List<Double> recentScores) {
        EarlyStoppingDecision decision = new EarlyStoppingDecision();
        decision.setShouldStop(false);
        decision.setReason("continue");
        
        // Determine which policy to use
        double passThreshold;
        double failThreshold;
        int minQuestions;
        String policyName;
        
        if (config.getNewPolicy().isEnabled()) {
            passThreshold = config.getNewPolicy().getPassThreshold();
            failThreshold = config.getNewPolicy().getFailThreshold();
            minQuestions = config.getNewPolicy().getMinQuestions();
            policyName = "new_policy";
        } else {
            passThreshold = config.getPassThreshold();
            failThreshold = config.getFailThreshold();
            minQuestions = config.getMinQuestions();
            policyName = "baseline";
        }
        
        // Check minimum question requirement
        if (recentScores.size() < minQuestions) {
            decision.setConfidence(0.0);
            decision.setMessage("Insufficient questions answered (" + recentScores.size() + "/" + 
                              minQuestions + ")");
            return decision;
        }
        
        // Get outcome prediction
        InterviewOutcomePredictor.PredictionResult prediction = 
            outcomePredictor.predictOutcome(profile, recentScores);
        
        double passProbability = prediction.getPassProbability();
        double stability = prediction.getScoreStability();
        double confidence = prediction.getConfidence();
        
        decision.setPassProbability(passProbability);
        decision.setStability(stability);
        decision.setConfidence(confidence);
        
        // Check early pass condition
        if (passProbability > passThreshold && 
            stability < config.getStabilityThreshold()) {
            decision.setShouldStop(true);
            decision.setReason("early_pass");
            decision.setMessage(String.format(
                "High pass probability (%.2f%%) with stable performance - candidate qualified",
                passProbability * 100
            ));
            
            logger.info("Early pass triggered for session {}: pass_prob={}, stability={}, policy={}, threshold={}",
                       profile.getSessionId(), passProbability, stability, policyName, passThreshold);
            
            return decision;
        }
        
        // Check early fail condition
        if (passProbability < failThreshold && 
            stability < config.getStabilityThreshold()) {
            decision.setShouldStop(true);
            decision.setReason("early_fail");
            decision.setMessage(String.format(
                "Low pass probability (%.2f%%) with stable performance - unlikely to qualify",
                passProbability * 100
            ));
            
            logger.info("Early fail triggered for session {}: pass_prob={}, stability={}, policy={}, threshold={}",
                       profile.getSessionId(), passProbability, stability, policyName, failThreshold);
            
            return decision;
        }
        
        // Check if performance is too unstable for early stopping
        if (stability > config.getStabilityThreshold()) {
            decision.setMessage(String.format(
                "Performance too unstable (%.2f) - continue collecting data",
                stability
            ));
        } else {
            // In borderline range - continue interview
            decision.setMessage(String.format(
                "Pass probability %.2f%% - continue interview",
                passProbability * 100
            ));
        }
        
        return decision;
    }
    
    /**
     * Apply early stopping decision to profile
     */
    public void applyEarlyStoppingDecision(CandidateSkillProfile profile, 
                                          EarlyStoppingDecision decision) {
        if (decision.isShouldStop()) {
            profile.setEarlyStoppingTriggered(true);
            profile.setEarlyStoppingReason(decision.getReason());
            profileRepository.save(profile);
            
            logger.info("Applied early stopping to session {}: reason={}",
                       profile.getSessionId(), decision.getReason());
        }
    }
    
    /**
     * Check if early stopping is appropriate (validation).
     */
    public boolean isEarlyStoppingAppropriate(double passProbability, 
                                             double stability, 
                                             int questionCount) {
        // Use new policy if enabled
        int minQuestions = config.getNewPolicy().isEnabled() ? 
            config.getNewPolicy().getMinQuestions() : config.getMinQuestions();
        double passThreshold = config.getNewPolicy().isEnabled() ? 
            config.getNewPolicy().getPassThreshold() : config.getPassThreshold();
        double failThreshold = config.getNewPolicy().isEnabled() ? 
            config.getNewPolicy().getFailThreshold() : config.getFailThreshold();
        
        // Must have minimum questions
        if (questionCount < minQuestions) {
            return false;
        }
        
        // Performance must be stable
        if (stability > config.getStabilityThreshold()) {
            return false;
        }
        
        // Pass probability must be extreme (very high or very low)
        return passProbability > passThreshold || 
               passProbability < failThreshold;
    }
    
    /**
     * Calculate early stopping rate (for monitoring)
     */
    public EarlyStoppingStatistics calculateStatistics() {
        EarlyStoppingStatistics stats = new EarlyStoppingStatistics();
        
        long totalProfiles = profileRepository.count();
        long earlyStoppedCount = profileRepository.countEarlyStopped();
        
        List<CandidateSkillProfile> earlyPasses = 
            profileRepository.findByEarlyStoppingReason("early_pass");
        List<CandidateSkillProfile> earlyFails = 
            profileRepository.findByEarlyStoppingReason("early_fail");
        
        stats.setTotalInterviews(totalProfiles);
        stats.setEarlyStoppedCount(earlyStoppedCount);
        stats.setEarlyPassCount((long) earlyPasses.size());
        stats.setEarlyFailCount((long) earlyFails.size());
        
        if (totalProfiles > 0) {
            stats.setEarlyStoppingRate((double) earlyStoppedCount / totalProfiles);
        }
        
        return stats;
    }
    
    /**
     * Early stopping decision result
     */
    public static class EarlyStoppingDecision {
        private boolean shouldStop;
        private String reason;
        private String message;
        private Double passProbability;
        private Double stability;
        private Double confidence;
        
        // Getters and setters
        
        public boolean isShouldStop() {
            return shouldStop;
        }
        
        public void setShouldStop(boolean shouldStop) {
            this.shouldStop = shouldStop;
        }
        
        public String getReason() {
            return reason;
        }
        
        public void setReason(String reason) {
            this.reason = reason;
        }
        
        public String getMessage() {
            return message;
        }
        
        public void setMessage(String message) {
            this.message = message;
        }
        
        public Double getPassProbability() {
            return passProbability;
        }
        
        public void setPassProbability(Double passProbability) {
            this.passProbability = passProbability;
        }
        
        public Double getStability() {
            return stability;
        }
        
        public void setStability(Double stability) {
            this.stability = stability;
        }
        
        public Double getConfidence() {
            return confidence;
        }
        
        public void setConfidence(Double confidence) {
            this.confidence = confidence;
        }
    }
    
    /**
     * Early stopping statistics
     */
    public static class EarlyStoppingStatistics {
        private Long totalInterviews;
        private Long earlyStoppedCount;
        private Long earlyPassCount;
        private Long earlyFailCount;
        private Double earlyStoppingRate;
        
        // Getters and setters
        
        public Long getTotalInterviews() {
            return totalInterviews;
        }
        
        public void setTotalInterviews(Long totalInterviews) {
            this.totalInterviews = totalInterviews;
        }
        
        public Long getEarlyStoppedCount() {
            return earlyStoppedCount;
        }
        
        public void setEarlyStoppedCount(Long earlyStoppedCount) {
            this.earlyStoppedCount = earlyStoppedCount;
        }
        
        public Long getEarlyPassCount() {
            return earlyPassCount;
        }
        
        public void setEarlyPassCount(Long earlyPassCount) {
            this.earlyPassCount = earlyPassCount;
        }
        
        public Long getEarlyFailCount() {
            return earlyFailCount;
        }
        
        public void setEarlyFailCount(Long earlyFailCount) {
            this.earlyFailCount = earlyFailCount;
        }
        
        public Double getEarlyStoppingRate() {
            return earlyStoppingRate;
        }
        
        public void setEarlyStoppingRate(Double earlyStoppingRate) {
            this.earlyStoppingRate = earlyStoppingRate;
        }
    }
}
