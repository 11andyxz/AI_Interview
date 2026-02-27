package com.aiinterview.ml.adaptive;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Question calibration service
 * Handles incremental calibration updates and bootstrapping from historical data
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QuestionCalibrationService {
    
    private final QuestionCalibrationRepository calibrationRepository;
    
    private static final int MIN_RESPONSES_FOR_CALIBRATION = 10;
    private static final double LEARNING_RATE = 0.1;
    
    /**
     * Update calibration incrementally with new response
     * Uses simple moving average and variance update
     */
    @Transactional
    public void updateCalibration(
            String questionId,
            String roleId,
            double score,
            double candidateAbility) {
        
        log.debug("Updating calibration: questionId={}, roleId={}, score={}, ability={}",
                  questionId, roleId, score, candidateAbility);
        
        Optional<QuestionCalibration> existing = 
            calibrationRepository.findByQuestionIdAndRoleId(questionId, roleId);
        
        QuestionCalibration calibration;
        
        if (existing.isPresent()) {
            calibration = existing.get();
            
            // Incremental update
            int n = calibration.getResponseCount();
            double oldMean = calibration.getMeanScore();
            double oldVariance = calibration.getScoreVariance();
            
            // Update mean
            double newMean = (oldMean * n + score) / (n + 1);
            
            // Update variance (Welford's online algorithm)
            double newVariance;
            if (n > 0) {
                newVariance = ((n - 1) * oldVariance + (score - oldMean) * (score - newMean)) / n;
            } else {
                newVariance = 0.25;
            }
            
            calibration.setMeanScore(newMean);
            calibration.setScoreVariance(Math.max(0.01, newVariance));
            calibration.setResponseCount(n + 1);
            
            // Update IRT parameters if enough data
            if (n + 1 >= MIN_RESPONSES_FOR_CALIBRATION) {
                updateIRTParameters(calibration, score, candidateAbility);
                calibration.setLastCalibratedAt(LocalDateTime.now());
            }
            
        } else {
            // Create new calibration with defaults
            calibration = QuestionCalibration.builder()
                .questionId(questionId)
                .roleId(roleId)
                .difficultyB(0.0)
                .discriminationA(1.0)
                .responseCount(1)
                .meanScore(score)
                .scoreVariance(0.25)
                .lastCalibratedAt(LocalDateTime.now())
                .build();
        }
        
        calibrationRepository.save(calibration);
        
        log.debug("Calibration updated: difficulty={}, discrimination={}, responses={}",
                  calibration.getDifficultyB(), calibration.getDiscriminationA(),
                  calibration.getResponseCount());
    }
    
    /**
     * Update IRT parameters using simple gradient descent approximation
     * This is a simplified version for incremental updates
     */
    private void updateIRTParameters(
            QuestionCalibration calibration,
            double observed,
            double candidateAbility) {
        
        double a = calibration.getDiscriminationA();
        double b = calibration.getDifficultyB();
        
        // Compute predicted probability
        double z = a * (candidateAbility - b);
        double predicted = 1.0 / (1.0 + Math.exp(-z));
        
        // Error
        double error = observed - predicted;
        
        // Gradient descent update (simplified)
        double dbGradient = -a * predicted * (1.0 - predicted) * error;
        double daGradient = (candidateAbility - b) * predicted * (1.0 - predicted) * error;
        
        // Update with learning rate
        double newB = b - LEARNING_RATE * dbGradient;
        double newA = a + LEARNING_RATE * daGradient;
        
        // Constrain parameters to reasonable ranges
        newB = Math.max(-3.0, Math.min(3.0, newB));
        newA = Math.max(0.1, Math.min(3.0, newA));
        
        calibration.setDifficultyB(newB);
        calibration.setDiscriminationA(newA);
    }
    
    /**
     * Bootstrap calibration from historical data
     * Computes initial parameters from batch of responses
     */
    @Transactional
    public void bootstrapFromHistory(
            String questionId,
            String roleId,
            List<HistoricalResponse> responses) {
        
        if (responses == null || responses.isEmpty()) {
            log.warn("No historical data for bootstrapping: questionId={}, roleId={}",
                     questionId, roleId);
            return;
        }
        
        log.info("Bootstrapping calibration from {} historical responses", responses.size());
        
        // Compute mean and variance
        double sumScore = 0.0;
        double sumAbility = 0.0;
        
        for (HistoricalResponse response : responses) {
            sumScore += response.getScore();
            sumAbility += response.getCandidateAbility();
        }
        
        double meanScore = sumScore / responses.size();
        double meanAbility = sumAbility / responses.size();
        
        // Compute variance
        double sumSquaredDiff = 0.0;
        for (HistoricalResponse response : responses) {
            double diff = response.getScore() - meanScore;
            sumSquaredDiff += diff * diff;
        }
        double variance = sumSquaredDiff / responses.size();
        
        // Estimate initial IRT parameters
        // Simple heuristic: difficulty ≈ mean ability, discrimination ≈ 1/variance
        double initialB = meanAbility;
        double initialA = variance > 0.01 ? Math.min(2.0, 1.0 / Math.sqrt(variance)) : 1.0;
        
        // Create or update calibration
        Optional<QuestionCalibration> existing = 
            calibrationRepository.findByQuestionIdAndRoleId(questionId, roleId);
        
        QuestionCalibration calibration;
        if (existing.isPresent()) {
            calibration = existing.get();
        } else {
            calibration = new QuestionCalibration();
            calibration.setQuestionId(questionId);
            calibration.setRoleId(roleId);
        }
        
        calibration.setDifficultyB(initialB);
        calibration.setDiscriminationA(initialA);
        calibration.setMeanScore(meanScore);
        calibration.setScoreVariance(Math.max(0.01, variance));
        calibration.setResponseCount(responses.size());
        calibration.setLastCalibratedAt(LocalDateTime.now());
        
        calibrationRepository.save(calibration);
        
        log.info("Bootstrapped calibration: difficulty={}, discrimination={}, n={}",
                 initialB, initialA, responses.size());
    }
    
    /**
     * Get or create calibration with safe defaults
     */
    @Transactional
    public QuestionCalibration getOrCreateCalibration(String questionId, String roleId) {
        return calibrationRepository
            .findByQuestionIdAndRoleId(questionId, roleId)
            .orElseGet(() -> {
                log.info("Creating default calibration for question: {} role: {}",
                         questionId, roleId);
                
                QuestionCalibration calibration = QuestionCalibration.builder()
                    .questionId(questionId)
                    .roleId(roleId)
                    .difficultyB(0.0)
                    .discriminationA(1.0)
                    .responseCount(0)
                    .meanScore(0.0)
                    .scoreVariance(0.25)
                    .lastCalibratedAt(LocalDateTime.now())
                    .build();
                
                return calibrationRepository.save(calibration);
            });
    }
    
    /**
     * Historical response record for bootstrapping
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class HistoricalResponse {
        private double score;
        private double candidateAbility;
    }
}
