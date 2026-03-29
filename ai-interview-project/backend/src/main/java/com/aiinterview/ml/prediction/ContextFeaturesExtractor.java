package com.aiinterview.ml.prediction;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Context-Aware Feature Extractor for enhanced prediction.
 * 
 * Extracts additional features beyond raw scores to improve prediction accuracy:
 * 1. Job role embeddings (vectorized representation of target role requirements)
 * 2. Question difficulty matching (alignment between question difficulty and candidate level)
 * 3. Trajectory features (rate of improvement, consistency, momentum)
 * 
 * Week 17 P1 Task 3: Address weak slices (junior profiles, short sessions)
 */
@Component
public class ContextFeaturesExtractor {
    
    private static final Logger logger = LoggerFactory.getLogger(ContextFeaturesExtractor.class);
    
    // Job role embeddings (simplified 4-dimensional vectors)
    // Dimensions: [technical_depth, breadth_required, system_design_weight, coding_weight]
    private static final Map<String, double[]> ROLE_EMBEDDINGS = Map.of(
        "junior", new double[]{0.3, 0.7, 0.2, 0.8},
        "mid", new double[]{0.6, 0.6, 0.5, 0.6},
        "senior", new double[]{0.8, 0.5, 0.8, 0.4},
        "staff", new double[]{0.9, 0.4, 0.9, 0.2},
        "principal", new double[]{1.0, 0.3, 1.0, 0.1}
    );
    
    // Question difficulty levels (normalized 0-1 scale)
    private static final Map<String, Double> DIFFICULTY_LEVELS = Map.of(
        "easy", 0.2,
        "medium", 0.5,
        "hard", 0.8,
        "expert", 1.0
    );
    
    /**
     * Extract all context-aware features for a candidate.
     * 
     * @param targetRole Target job role (e.g., "junior", "mid", "senior")
     * @param questionDifficulties List of difficulty levels for each question
     * @param scoreTrend List of scores over time
     * @return Feature vector combining all context features
     */
    public ContextFeatures extractFeatures(
            String targetRole,
            List<String> questionDifficulties,
            List<Double> scoreTrend) {
        
        ContextFeatures features = new ContextFeatures();
        
        // 1. Job role embedding features
        double[] roleEmbedding = getRoleEmbedding(targetRole);
        features.setRoleEmbedding(roleEmbedding);
        features.setTechnicalDepthWeight(roleEmbedding[0]);
        features.setBreadthWeight(roleEmbedding[1]);
        features.setSystemDesignWeight(roleEmbedding[2]);
        features.setCodingWeight(roleEmbedding[3]);
        
        // 2. Question difficulty matching
        if (questionDifficulties != null && !questionDifficulties.isEmpty()) {
            DifficultyMatchingResult difficultyMatch = analyzeDifficultyMatching(
                targetRole, questionDifficulties, scoreTrend
            );
            features.setAvgDifficultyAlignment(difficultyMatch.avgAlignment);
            features.setDifficultyVariance(difficultyMatch.variance);
            features.setOverchallengingRatio(difficultyMatch.overchallengingRatio);
        }
        
        // 3. Trajectory features
        if (scoreTrend != null && scoreTrend.size() >= 2) {
            TrajectoryFeatures trajectory = analyzeTrajectory(scoreTrend);
            features.setImprovementRate(trajectory.improvementRate);
            features.setConsistency(trajectory.consistency);
            features.setMomentum(trajectory.momentum);
            features.setRecentTrend(trajectory.recentTrend);
        }
        
        logger.debug("Extracted context features for role={}: alignment={:.3f}, improvement_rate={:.3f}",
                    targetRole, features.getAvgDifficultyAlignment(), features.getImprovementRate());
        
        return features;
    }
    
    /**
     * Get role embedding vector.
     */
    private double[] getRoleEmbedding(String targetRole) {
        String normalizedRole = targetRole != null ? targetRole.toLowerCase() : "mid";
        return ROLE_EMBEDDINGS.getOrDefault(normalizedRole, ROLE_EMBEDDINGS.get("mid"));
    }
    
    /**
     * Analyze question difficulty matching for the candidate.
     * 
     * This measures how well the candidate performs relative to question difficulty.
     * Good candidates perform well on hard questions; struggling candidates fail easy ones.
     */
    private DifficultyMatchingResult analyzeDifficultyMatching(
            String targetRole,
            List<String> questionDifficulties,
            List<Double> scoreTrend) {
        
        if (questionDifficulties.size() != scoreTrend.size()) {
            logger.warn("Difficulty list size mismatch: {} difficulties vs {} scores",
                       questionDifficulties.size(), scoreTrend.size());
            return new DifficultyMatchingResult(0.5, 0.0, 0.0);
        }
        
        double[] roleEmbedding = getRoleEmbedding(targetRole);
        double expectedDifficulty = roleEmbedding[0];  // Technical depth as difficulty expectation
        
        List<Double> alignmentScores = new ArrayList<>();
        int overchallengingCount = 0;
        
        for (int i = 0; i < questionDifficulties.size(); i++) {
            String difficulty = questionDifficulties.get(i);
            double difficultyLevel = DIFFICULTY_LEVELS.getOrDefault(difficulty.toLowerCase(), 0.5);
            double score = scoreTrend.get(i);
            
            // Alignment: score should be inversely proportional to difficulty gap
            // If question is too hard (difficulty > expected), low score is acceptable
            // If question is easy (difficulty < expected), high score is expected
            double difficultyGap = difficultyLevel - expectedDifficulty;
            double expectedScore = 75.0 - (difficultyGap * 50.0);  // Heuristic mapping
            
            double alignment = 1.0 - Math.abs(score - expectedScore) / 100.0;
            alignmentScores.add(alignment);
            
            // Overchallenging: hard question + low score
            if (difficultyLevel > expectedDifficulty + 0.2 && score < 60.0) {
                overchallengingCount++;
            }
        }
        
        double avgAlignment = alignmentScores.stream()
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0.5);
        
        double variance = calculateVariance(alignmentScores);
        double overchallengingRatio = (double) overchallengingCount / questionDifficulties.size();
        
        return new DifficultyMatchingResult(avgAlignment, variance, overchallengingRatio);
    }
    
    /**
     * Analyze performance trajectory over time.
     * 
     * Trajectory features capture learning/adaptation during the interview:
     * - Improvement rate: How quickly scores increase
     * - Consistency: How stable performance is
     * - Momentum: Recent trend direction and strength
     */
    private TrajectoryFeatures analyzeTrajectory(List<Double> scoreTrend) {
        int n = scoreTrend.size();
        if (n < 2) {
            return new TrajectoryFeatures(0.0, 0.0, 0.0, 0.0);
        }
        
        // 1. Improvement rate: linear regression slope
        double improvementRate = calculateLinearSlope(scoreTrend);
        
        // 2. Consistency: coefficient of variation (lower is more consistent)
        double mean = scoreTrend.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double std = Math.sqrt(calculateVariance(scoreTrend));
        double consistency = mean > 0 ? 1.0 - Math.min(1.0, std / mean) : 0.5;
        
        // 3. Momentum: difference between recent average and overall average
        int recentWindow = Math.min(3, n / 2);  // Last 3 questions or half
        List<Double> recentScores = scoreTrend.subList(n - recentWindow, n);
        double recentMean = recentScores.stream().mapToDouble(Double::doubleValue).average().orElse(mean);
        double momentum = (recentMean - mean) / 100.0;  // Normalize to [-1, 1]
        
        // 4. Recent trend: slope of last few questions
        double recentTrend = calculateLinearSlope(recentScores);
        
        return new TrajectoryFeatures(improvementRate, consistency, momentum, recentTrend);
    }
    
    /**
     * Calculate linear regression slope using least squares.
     */
    private double calculateLinearSlope(List<Double> values) {
        int n = values.size();
        if (n < 2) return 0.0;
        
        double sumX = 0.0, sumY = 0.0, sumXY = 0.0, sumX2 = 0.0;
        
        for (int i = 0; i < n; i++) {
            double x = i;
            double y = values.get(i);
            sumX += x;
            sumY += y;
            sumXY += x * y;
            sumX2 += x * x;
        }
        
        double denominator = n * sumX2 - sumX * sumX;
        if (Math.abs(denominator) < 1e-10) return 0.0;
        
        return (n * sumXY - sumX * sumY) / denominator;
    }
    
    /**
     * Calculate variance.
     */
    private double calculateVariance(List<Double> values) {
        if (values.size() < 2) return 0.0;
        
        double mean = values.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double sumSquaredDiff = values.stream()
                .mapToDouble(v -> Math.pow(v - mean, 2))
                .sum();
        
        return sumSquaredDiff / (values.size() - 1);
    }
    
    /**
     * Container for difficulty matching analysis results.
     */
    private static class DifficultyMatchingResult {
        final double avgAlignment;
        final double variance;
        final double overchallengingRatio;
        
        DifficultyMatchingResult(double avgAlignment, double variance, double overchallengingRatio) {
            this.avgAlignment = avgAlignment;
            this.variance = variance;
            this.overchallengingRatio = overchallengingRatio;
        }
    }
    
    /**
     * Container for trajectory analysis results.
     */
    private static class TrajectoryFeatures {
        final double improvementRate;
        final double consistency;
        final double momentum;
        final double recentTrend;
        
        TrajectoryFeatures(double improvementRate, double consistency, double momentum, double recentTrend) {
            this.improvementRate = improvementRate;
            this.consistency = consistency;
            this.momentum = momentum;
            this.recentTrend = recentTrend;
        }
    }
    
    /**
     * Data class for extracted context features.
     */
    public static class ContextFeatures {
        // Role embedding features
        private double[] roleEmbedding;
        private double technicalDepthWeight;
        private double breadthWeight;
        private double systemDesignWeight;
        private double codingWeight;
        
        // Difficulty matching features
        private double avgDifficultyAlignment;
        private double difficultyVariance;
        private double overchallengingRatio;
        
        // Trajectory features
        private double improvementRate;
        private double consistency;
        private double momentum;
        private double recentTrend;
        
        // Getters and setters
        public double[] getRoleEmbedding() { return roleEmbedding; }
        public void setRoleEmbedding(double[] roleEmbedding) { this.roleEmbedding = roleEmbedding; }
        
        public double getTechnicalDepthWeight() { return technicalDepthWeight; }
        public void setTechnicalDepthWeight(double technicalDepthWeight) { this.technicalDepthWeight = technicalDepthWeight; }
        
        public double getBreadthWeight() { return breadthWeight; }
        public void setBreadthWeight(double breadthWeight) { this.breadthWeight = breadthWeight; }
        
        public double getSystemDesignWeight() { return systemDesignWeight; }
        public void setSystemDesignWeight(double systemDesignWeight) { this.systemDesignWeight = systemDesignWeight; }
        
        public double getCodingWeight() { return codingWeight; }
        public void setCodingWeight(double codingWeight) { this.codingWeight = codingWeight; }
        
        public double getAvgDifficultyAlignment() { return avgDifficultyAlignment; }
        public void setAvgDifficultyAlignment(double avgDifficultyAlignment) { this.avgDifficultyAlignment = avgDifficultyAlignment; }
        
        public double getDifficultyVariance() { return difficultyVariance; }
        public void setDifficultyVariance(double difficultyVariance) { this.difficultyVariance = difficultyVariance; }
        
        public double getOverchallengingRatio() { return overchallengingRatio; }
        public void setOverchallengingRatio(double overchallengingRatio) { this.overchallengingRatio = overchallengingRatio; }
        
        public double getImprovementRate() { return improvementRate; }
        public void setImprovementRate(double improvementRate) { this.improvementRate = improvementRate; }
        
        public double getConsistency() { return consistency; }
        public void setConsistency(double consistency) { this.consistency = consistency; }
        
        public double getMomentum() { return momentum; }
        public void setMomentum(double momentum) { this.momentum = momentum; }
        
        public double getRecentTrend() { return recentTrend; }
        public void setRecentTrend(double recentTrend) { this.recentTrend = recentTrend; }
        
        /**
         * Convert to feature vector for ML models.
         * 
         * @return 15-dimensional feature vector
         */
        public double[] toFeatureVector() {
            return new double[]{
                // Role embedding (4 features)
                technicalDepthWeight,
                breadthWeight,
                systemDesignWeight,
                codingWeight,
                
                // Difficulty matching (3 features)
                avgDifficultyAlignment,
                difficultyVariance,
                overchallengingRatio,
                
                // Trajectory (4 features)
                improvementRate,
                consistency,
                momentum,
                recentTrend
            };
        }
        
        /**
         * Get feature names (for debugging/logging).
         */
        public static String[] getFeatureNames() {
            return new String[]{
                "technical_depth_weight",
                "breadth_weight",
                "system_design_weight",
                "coding_weight",
                "avg_difficulty_alignment",
                "difficulty_variance",
                "overchallenging_ratio",
                "improvement_rate",
                "consistency",
                "momentum",
                "recent_trend"
            };
        }
    }
}
