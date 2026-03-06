package com.aiinterview.ml.prediction;

import com.aiinterview.ml.prediction.entity.CandidateSkillProfile;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Predicts interview outcomes mid-interview using exponential moving average
 * and sliding-window regression.
 */
@Component
public class InterviewOutcomePredictor {
    
    private static final Logger logger = LoggerFactory.getLogger(InterviewOutcomePredictor.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    
    // Configuration
    private static final double EMA_ALPHA = 0.3;           // Exponential moving average smoothing
    private static final int WINDOW_SIZE = 5;              // Sliding window for trend analysis
    private static final double PASS_THRESHOLD = 60.0;     // Minimum score to pass
    private static final double CONFIDENCE_SCALE = 0.95;   // Confidence interval scale
    
    /**
     * Predict interview outcome based on current performance.
     * 
     * @param profile Current candidate skill profile
     * @param recentScores List of recent question scores
     * @return Updated prediction result
     */
    public PredictionResult predictOutcome(CandidateSkillProfile profile, List<Double> recentScores) {
        if (recentScores == null || recentScores.isEmpty()) {
            return createDefaultPrediction();
        }
        
        PredictionResult result = new PredictionResult();
        
        // 1. Calculate basic statistics
        double mean = calculateMean(recentScores);
        double std = calculateStandardDeviation(recentScores, mean);
        
        result.setScoreMean(mean);
        result.setScoreStd(std);
        
        // 2. Predict final score using EMA and trend
        double predictedScore = predictFinalScore(recentScores, mean);
        result.setPredictedFinalScore(predictedScore);
        
        // 3. Calculate confidence interval
        double[] confidenceInterval = calculateConfidenceInterval(mean, std, recentScores.size());
        result.setConfidenceIntervalLower(confidenceInterval[0]);
        result.setConfidenceIntervalUpper(confidenceInterval[1]);
        
        // 4. Calculate pass probability
        double passProbability = calculatePassProbability(predictedScore, std);
        result.setPassProbability(passProbability);
        
        // 5. Assess score stability
        double stability = assessStability(recentScores);
        result.setScoreStability(stability);
        
        // 6. Calculate confidence in prediction
        double confidence = calculatePredictionConfidence(recentScores.size(), std, stability);
        result.setConfidence(confidence);
        
        // 7. Generate recommendation
        String recommendation = generateRecommendation(passProbability, stability, recentScores.size());
        result.setRecommendation(recommendation);
        
        logger.debug("Prediction for {} questions: score={:.2f}, pass_prob={:.2f}, stability={:.2f}",
                    recentScores.size(), predictedScore, passProbability, stability);
        
        return result;
    }
    
    /**
     * Predict final score using exponential moving average and linear trend
     */
    private double predictFinalScore(List<Double> scores, double mean) {
        if (scores.size() < 2) {
            return mean;
        }
        
        // Calculate EMA (exponential moving average)
        double ema = scores.get(0);
        for (int i = 1; i < scores.size(); i++) {
            ema = EMA_ALPHA * scores.get(i) + (1 - EMA_ALPHA) * ema;
        }
        
        // Calculate linear trend using sliding window
        double trend = 0.0;
        if (scores.size() >= WINDOW_SIZE) {
            List<Double> window = scores.subList(scores.size() - WINDOW_SIZE, scores.size());
            trend = calculateLinearTrend(window);
        } else {
            trend = calculateLinearTrend(scores);
        }
        
        // Combine EMA and trend for prediction
        // Predict 10 questions ahead (typical interview length)
        int remainingQuestions = Math.max(10 - scores.size(), 0);
        double prediction = ema + trend * remainingQuestions;
        
        // Clip to valid range [0, 100]
        return Math.max(0.0, Math.min(100.0, prediction));
    }
    
    /**
     * Calculate linear trend (slope) using least squares regression
     */
    private double calculateLinearTrend(List<Double> scores) {
        int n = scores.size();
        if (n < 2) return 0.0;
        
        double sumX = 0.0, sumY = 0.0, sumXY = 0.0, sumX2 = 0.0;
        
        for (int i = 0; i < n; i++) {
            double x = i;
            double y = scores.get(i);
            sumX += x;
            sumY += y;
            sumXY += x * y;
            sumX2 += x * x;
        }
        
        // Slope = (n*sumXY - sumX*sumY) / (n*sumX2 - sumX*sumX)
        double denominator = n * sumX2 - sumX * sumX;
        if (Math.abs(denominator) < 1e-10) return 0.0;
        
        return (n * sumXY - sumX * sumY) / denominator;
    }
    
    /**
     * Calculate confidence interval using t-distribution
     */
    private double[] calculateConfidenceInterval(double mean, double std, int sampleSize) {
        // For 95% confidence, use approximately 2 standard errors
        double marginOfError = CONFIDENCE_SCALE * 2 * std / Math.sqrt(sampleSize);
        
        double lower = Math.max(0.0, mean - marginOfError);
        double upper = Math.min(100.0, mean + marginOfError);
        
        return new double[]{lower, upper};
    }
    
    /**
     * Calculate probability of passing using normal distribution
     */
    private double calculatePassProbability(double predictedScore, double std) {
        if (std < 1e-6) {
            // No variance, deterministic
            return predictedScore >= PASS_THRESHOLD ? 1.0 : 0.0;
        }
        
        // Z-score: how many standard deviations is pass threshold from predicted score
        double zScore = (predictedScore - PASS_THRESHOLD) / std;
        
        // Convert to probability using cumulative distribution function (CDF)
        return normalCDF(zScore);
    }
    
    /**
     * Normal distribution cumulative distribution function (approximate)
     */
    private double normalCDF(double z) {
        // Approximation using error function
        double t = 1.0 / (1.0 + 0.2316419 * Math.abs(z));
        double d = 0.3989423 * Math.exp(-z * z / 2.0);
        double prob = d * t * (0.3193815 + t * (-0.3565638 + t * (1.781478 + t * (-1.821256 + t * 1.330274))));
        
        return z > 0 ? 1.0 - prob : prob;
    }
    
    /**
     * Assess score stability (coefficient of variation)
     */
    private double assessStability(List<Double> scores) {
        if (scores.size() < 2) {
            return 1.0; // Highly unstable with insufficient data
        }
        
        double mean = calculateMean(scores);
        double std = calculateStandardDeviation(scores, mean);
        
        if (mean < 1e-6) {
            return 1.0;
        }
        
        // Coefficient of variation: std / mean
        // Lower values indicate more stability
        double cv = std / mean;
        
        // Normalize to 0-1 range (0 = stable, 1 = unstable)
        return Math.min(1.0, cv / 0.5);
    }
    
    /**
     * Calculate prediction confidence based on data quantity and quality
     */
    private double calculatePredictionConfidence(int sampleSize, double std, double stability) {
        // More samples = higher confidence
        double sampleConfidence = Math.min(1.0, sampleSize / 10.0);
        
        // Lower variance = higher confidence
        double varianceConfidence = Math.max(0.0, 1.0 - std / 50.0);
        
        // Higher stability = higher confidence
        double stabilityConfidence = 1.0 - stability;
        
        // Weighted combination
        return 0.4 * sampleConfidence + 0.3 * varianceConfidence + 0.3 * stabilityConfidence;
    }
    
    /**
     * Generate recommendation based on prediction
     */
    private String generateRecommendation(double passProbability, double stability, int questionCount) {
        if (questionCount < 3) {
            return "continue_collecting_data";
        }
        
        if (passProbability > 0.95 && stability < 0.3) {
            return "early_pass_suggested";
        }
        
        if (passProbability < 0.05 && stability < 0.3) {
            return "early_fail_suggested";
        }
        
        if (passProbability > 0.7 && questionCount >= 5) {
            return "strong_candidate";
        }
        
        if (passProbability < 0.3 && questionCount >= 5) {
            return "weak_candidate";
        }
        
        if (stability > 0.6) {
            return "unstable_performance";
        }
        
        return "continue_interview";
    }
    
    /**
     * Calculate mean
     */
    private double calculateMean(List<Double> values) {
        return values.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
    }
    
    /**
     * Calculate standard deviation
     */
    private double calculateStandardDeviation(List<Double> values, double mean) {
        if (values.size() < 2) return 0.0;
        
        double sumSquaredDiff = 0.0;
        for (double value : values) {
            double diff = value - mean;
            sumSquaredDiff += diff * diff;
        }
        
        return Math.sqrt(sumSquaredDiff / (values.size() - 1));
    }
    
    /**
     * Create default prediction for empty data
     */
    private PredictionResult createDefaultPrediction() {
        PredictionResult result = new PredictionResult();
        result.setPredictedFinalScore(50.0);
        result.setConfidenceIntervalLower(0.0);
        result.setConfidenceIntervalUpper(100.0);
        result.setPassProbability(0.5);
        result.setScoreStability(1.0);
        result.setConfidence(0.0);
        result.setRecommendation("no_data");
        return result;
    }
    
    /**
     * Parse score trend from JSON
     */
    public List<Double> parseScoreTrend(String scoreTrendJson) {
        if (scoreTrendJson == null || scoreTrendJson.isEmpty()) {
            return new ArrayList<>();
        }
        
        try {
            return objectMapper.readValue(scoreTrendJson, new TypeReference<List<Double>>() {});
        } catch (Exception e) {
            logger.warn("Failed to parse score trend JSON: {}", e.getMessage());
            return new ArrayList<>();
        }
    }
    
    /**
     * Serialize score trend to JSON
     */
    public String serializeScoreTrend(List<Double> scores) {
        try {
            return objectMapper.writeValueAsString(scores);
        } catch (Exception e) {
            logger.error("Failed to serialize score trend: {}", e.getMessage());
            return "[]";
        }
    }
    
    /**
     * Prediction result container
     */
    public static class PredictionResult {
        private Double predictedFinalScore;
        private Double confidenceIntervalLower;
        private Double confidenceIntervalUpper;
        private Double passProbability;
        private Double scoreStability;
        private Double confidence;
        private Double scoreMean;
        private Double scoreStd;
        private String recommendation;
        
        // Getters and setters
        
        public Double getPredictedFinalScore() {
            return predictedFinalScore;
        }
        
        public void setPredictedFinalScore(Double predictedFinalScore) {
            this.predictedFinalScore = predictedFinalScore;
        }
        
        public Double getConfidenceIntervalLower() {
            return confidenceIntervalLower;
        }
        
        public void setConfidenceIntervalLower(Double confidenceIntervalLower) {
            this.confidenceIntervalLower = confidenceIntervalLower;
        }
        
        public Double getConfidenceIntervalUpper() {
            return confidenceIntervalUpper;
        }
        
        public void setConfidenceIntervalUpper(Double confidenceIntervalUpper) {
            this.confidenceIntervalUpper = confidenceIntervalUpper;
        }
        
        public Double getPassProbability() {
            return passProbability;
        }
        
        public void setPassProbability(Double passProbability) {
            this.passProbability = passProbability;
        }
        
        public Double getScoreStability() {
            return scoreStability;
        }
        
        public void setScoreStability(Double scoreStability) {
            this.scoreStability = scoreStability;
        }
        
        public Double getConfidence() {
            return confidence;
        }
        
        public void setConfidence(Double confidence) {
            this.confidence = confidence;
        }
        
        public Double getScoreMean() {
            return scoreMean;
        }
        
        public void setScoreMean(Double scoreMean) {
            this.scoreMean = scoreMean;
        }
        
        public Double getScoreStd() {
            return scoreStd;
        }
        
        public void setScoreStd(Double scoreStd) {
            this.scoreStd = scoreStd;
        }
        
        public String getRecommendation() {
            return recommendation;
        }
        
        public void setRecommendation(String recommendation) {
            this.recommendation = recommendation;
        }
    }
}
