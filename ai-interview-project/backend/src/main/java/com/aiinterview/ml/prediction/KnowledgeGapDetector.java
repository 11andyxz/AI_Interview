package com.aiinterview.ml.prediction;

import com.aiinterview.ml.embedding.service.TopicCoverageTracker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Detects knowledge gaps and strengths based on performance patterns.
 * Uses topic clustering and score analysis to provide targeted recommendations.
 */
@Component
public class KnowledgeGapDetector {
    
    private static final Logger logger = LoggerFactory.getLogger(KnowledgeGapDetector.class);
    
    @Autowired
    private TopicCoverageTracker coverageTracker;
    
    // Thresholds
    private static final double STRENGTH_THRESHOLD = 75.0;     // Score >= 75 = strength
    private static final double WEAKNESS_THRESHOLD = 50.0;     // Score < 50 = weakness
    private static final double GAP_THRESHOLD = 60.0;          // Score < 60 = potential gap
    private static final int MIN_SAMPLES_PER_TOPIC = 2;        // Min questions per topic for assessment
    
    /**
     * Detect knowledge gaps and generate comprehensive analysis.
     * 
     * @param sessionId Interview session ID
     * @param roleId Role/position ID
     * @param topicScores Map of topic (cluster) ID to list of scores
     * @return Gap analysis result
     */
    public GapAnalysisResult detectGaps(String sessionId, Long roleId, Map<Integer, List<Double>> topicScores) {
        GapAnalysisResult result = new GapAnalysisResult();
        
        if (topicScores == null || topicScores.isEmpty()) {
            logger.warn("No topic scores provided for gap detection");
            return result;
        }
        
        // 1. Assess each topic
        List<TopicAssessment> assessments = new ArrayList<>();
        for (Map.Entry<Integer, List<Double>> entry : topicScores.entrySet()) {
            Integer topicId = entry.getKey();
            List<Double> scores = entry.getValue();
            
            if (scores.size() >= MIN_SAMPLES_PER_TOPIC) {
                TopicAssessment assessment = assessTopic(topicId, scores);
                assessments.add(assessment);
            }
        }
        
        result.setTopicAssessments(assessments);
        
        // 2. Identify strengths (high-performing topics)
        List<TopicAssessment> strengths = assessments.stream()
            .filter(a -> a.getAverageScore() >= STRENGTH_THRESHOLD)
            .sorted(Comparator.comparingDouble(TopicAssessment::getAverageScore).reversed())
            .collect(Collectors.toList());
        result.setStrengths(strengths);
        
        // 3. Identify weaknesses (low-performing topics)
        List<TopicAssessment> weaknesses = assessments.stream()
            .filter(a -> a.getAverageScore() < WEAKNESS_THRESHOLD)
            .sorted(Comparator.comparingDouble(TopicAssessment::getAverageScore))
            .collect(Collectors.toList());
        result.setWeaknesses(weaknesses);
        
        // 4. Rank knowledge gaps by severity
        List<KnowledgeGap> gaps = identifyGaps(assessments);
        result.setRankedGaps(gaps);
        
        // 5. Generate learning recommendations
        List<String> recommendations = generateRecommendations(strengths, weaknesses, gaps);
        result.setLearningRecommendations(recommendations);
        
        // 6. Calculate overall metrics
        double overallScore = calculateOverallScore(assessments);
        double consistencyScore = calculateConsistencyScore(assessments);
        result.setOverallScore(overallScore);
        result.setConsistencyScore(consistencyScore);
        
        logger.info("Gap analysis for session {}: {} strengths, {} weaknesses, {} gaps",
                   sessionId, strengths.size(), weaknesses.size(), gaps.size());
        
        return result;
    }
    
    /**
     * Assess a single topic's performance
     */
    private TopicAssessment assessTopic(Integer topicId, List<Double> scores) {
        TopicAssessment assessment = new TopicAssessment();
        assessment.setTopicId(topicId);
        assessment.setQuestionCount(scores.size());
        
        // Calculate statistics
        double avg = scores.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double min = scores.stream().mapToDouble(Double::doubleValue).min().orElse(0.0);
        double max = scores.stream().mapToDouble(Double::doubleValue).max().orElse(0.0);
        
        assessment.setAverageScore(avg);
        assessment.setMinScore(min);
        assessment.setMaxScore(max);
        
        // Calculate standard deviation
        double variance = scores.stream()
            .mapToDouble(s -> Math.pow(s - avg, 2))
            .average()
            .orElse(0.0);
        double std = Math.sqrt(variance);
        assessment.setScoreStd(std);
        
        // Determine performance level
        String level;
        if (avg >= STRENGTH_THRESHOLD) {
            level = "strong";
        } else if (avg >= GAP_THRESHOLD) {
            level = "adequate";
        } else if (avg >= WEAKNESS_THRESHOLD) {
            level = "needs_improvement";
        } else {
            level = "weak";
        }
        assessment.setPerformanceLevel(level);
        
        // Calculate trend (improving/declining)
        if (scores.size() >= 3) {
            double trend = calculateTrend(scores);
            assessment.setTrend(trend > 0.5 ? "improving" :
                              trend < -0.5 ? "declining" : "stable");
        } else {
            assessment.setTrend("insufficient_data");
        }
        
        return assessment;
    }
    
    /**
     * Calculate performance trend
     */
    private double calculateTrend(List<Double> scores) {
        int n = scores.size();
        if (n < 2) return 0.0;
        
        // Simple linear regression slope
        double sumX = 0.0, sumY = 0.0, sumXY = 0.0, sumX2 = 0.0;
        
        for (int i = 0; i < n; i++) {
            double x = i;
            double y = scores.get(i);
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
     * Identify knowledge gaps ranked by severity
     */
    private List<KnowledgeGap> identifyGaps(List<TopicAssessment> assessments) {
        List<KnowledgeGap> gaps = new ArrayList<>();
        
        for (TopicAssessment assessment : assessments) {
            if (assessment.getAverageScore() < GAP_THRESHOLD) {
                KnowledgeGap gap = new KnowledgeGap();
                gap.setTopicId(assessment.getTopicId());
                gap.setAverageScore(assessment.getAverageScore());
                gap.setQuestionCount(assessment.getQuestionCount());
                
                // Calculate gap severity (0-1, higher = more severe)
                double severity = 1.0 - (assessment.getAverageScore() / 100.0);
                
                // Adjust for sample size (more samples = more confident in gap)
                double sampleConfidence = Math.min(1.0, assessment.getQuestionCount() / 5.0);
                severity *= sampleConfidence;
                
                gap.setSeverity(severity);
                
                // Determine gap type
                if (assessment.getTrend().equals("declining")) {
                    gap.setGapType("declining_performance");
                } else if (assessment.getScoreStd() > 20.0) {
                    gap.setGapType("inconsistent_understanding");
                } else {
                    gap.setGapType("fundamental_weakness");
                }
                
                // Priority: high severity + many samples = high priority
                double priority = severity * Math.log(assessment.getQuestionCount() + 1);
                gap.setPriority(priority);
                
                gaps.add(gap);
            }
        }
        
        // Sort by priority (highest first)
        gaps.sort(Comparator.comparingDouble(KnowledgeGap::getPriority).reversed());
        
        return gaps;
    }
    
    /**
     * Generate learning recommendations based on analysis
     */
    private List<String> generateRecommendations(List<TopicAssessment> strengths,
                                                 List<TopicAssessment> weaknesses,
                                                 List<KnowledgeGap> gaps) {
        List<String> recommendations = new ArrayList<>();
        
        // Overall performance assessment
        if (strengths.isEmpty() && !weaknesses.isEmpty()) {
            recommendations.add("Focus on fundamental concepts across all topics");
        } else if (strengths.size() > weaknesses.size()) {
            recommendations.add("Strong overall performance; focus on targeted gap closure");
        }
        
        // Address top gaps
        if (!gaps.isEmpty()) {
            int topGapsToAddress = Math.min(3, gaps.size());
            recommendations.add(String.format("Priority: Address %d critical knowledge gaps", topGapsToAddress));
            
            for (int i = 0; i < topGapsToAddress; i++) {
                KnowledgeGap gap = gaps.get(i);
                recommendations.add(String.format("Topic %d: %s (severity: %.2f)",
                    gap.getTopicId(), gap.getGapType(), gap.getSeverity()));
            }
        }
        
        // Leverage strengths
        if (!strengths.isEmpty()) {
            recommendations.add(String.format("Leverage %d strong areas as foundation", strengths.size()));
        }
        
        // Address weaknesses
        if (!weaknesses.isEmpty()) {
            recommendations.add(String.format("Reinforce %d weak areas with practice", weaknesses.size()));
        }
        
        // Specific recommendations based on trends
        long decliningTopics = gaps.stream()
            .filter(g -> "declining_performance".equals(g.getGapType()))
            .count();
        
        if (decliningTopics > 0) {
            recommendations.add("Review fundamentals - performance declining in " + decliningTopics + " topics");
        }
        
        long inconsistentTopics = gaps.stream()
            .filter(g -> "inconsistent_understanding".equals(g.getGapType()))
            .count();
        
        if (inconsistentTopics > 0) {
            recommendations.add("Practice consistency - unstable performance in " + inconsistentTopics + " topics");
        }
        
        return recommendations;
    }
    
    /**
     * Calculate overall performance score
     */
    private double calculateOverallScore(List<TopicAssessment> assessments) {
        if (assessments.isEmpty()) return 0.0;
        
        return assessments.stream()
            .mapToDouble(TopicAssessment::getAverageScore)
            .average()
            .orElse(0.0);
    }
    
    /**
     * Calculate consistency score (0-1, higher = more consistent)
     */
    private double calculateConsistencyScore(List<TopicAssessment> assessments) {
        if (assessments.isEmpty()) return 0.0;
        
        // Calculate coefficient of variation across topics
        double meanScore = calculateOverallScore(assessments);
        
        double variance = assessments.stream()
            .mapToDouble(a -> Math.pow(a.getAverageScore() - meanScore, 2))
            .average()
            .orElse(0.0);
        
        double std = Math.sqrt(variance);
        
        if (meanScore < 1e-6) return 0.0;
        
        double cv = std / meanScore;
        
        // Convert to consistency score (lower CV = higher consistency)
        return Math.max(0.0, 1.0 - cv);
    }
    
    /**
     * Topic assessment result
     */
    public static class TopicAssessment {
        private Integer topicId;
        private Integer questionCount;
        private Double averageScore;
        private Double minScore;
        private Double maxScore;
        private Double scoreStd;
        private String performanceLevel;
        private String trend;
        
        // Getters and setters
        
        public Integer getTopicId() {
            return topicId;
        }
        
        public void setTopicId(Integer topicId) {
            this.topicId = topicId;
        }
        
        public Integer getQuestionCount() {
            return questionCount;
        }
        
        public void setQuestionCount(Integer questionCount) {
            this.questionCount = questionCount;
        }
        
        public Double getAverageScore() {
            return averageScore;
        }
        
        public void setAverageScore(Double averageScore) {
            this.averageScore = averageScore;
        }
        
        public Double getMinScore() {
            return minScore;
        }
        
        public void setMinScore(Double minScore) {
            this.minScore = minScore;
        }
        
        public Double getMaxScore() {
            return maxScore;
        }
        
        public void setMaxScore(Double maxScore) {
            this.maxScore = maxScore;
        }
        
        public Double getScoreStd() {
            return scoreStd;
        }
        
        public void setScoreStd(Double scoreStd) {
            this.scoreStd = scoreStd;
        }
        
        public String getPerformanceLevel() {
            return performanceLevel;
        }
        
        public void setPerformanceLevel(String performanceLevel) {
            this.performanceLevel = performanceLevel;
        }
        
        public String getTrend() {
            return trend;
        }
        
        public void setTrend(String trend) {
            this.trend = trend;
        }
    }
    
    /**
     * Knowledge gap identification
     */
    public static class KnowledgeGap {
        private Integer topicId;
        private Double averageScore;
        private Integer questionCount;
        private Double severity;
        private String gapType;
        private Double priority;
        
        // Getters and setters
        
        public Integer getTopicId() {
            return topicId;
        }
        
        public void setTopicId(Integer topicId) {
            this.topicId = topicId;
        }
        
        public Double getAverageScore() {
            return averageScore;
        }
        
        public void setAverageScore(Double averageScore) {
            this.averageScore = averageScore;
        }
        
        public Integer getQuestionCount() {
            return questionCount;
        }
        
        public void setQuestionCount(Integer questionCount) {
            this.questionCount = questionCount;
        }
        
        public Double getSeverity() {
            return severity;
        }
        
        public void setSeverity(Double severity) {
            this.severity = severity;
        }
        
        public String getGapType() {
            return gapType;
        }
        
        public void setGapType(String gapType) {
            this.gapType = gapType;
        }
        
        public Double getPriority() {
            return priority;
        }
        
        public void setPriority(Double priority) {
            this.priority = priority;
        }
    }
    
    /**
     * Complete gap analysis result
     */
    public static class GapAnalysisResult {
        private List<TopicAssessment> topicAssessments = new ArrayList<>();
        private List<TopicAssessment> strengths = new ArrayList<>();
        private List<TopicAssessment> weaknesses = new ArrayList<>();
        private List<KnowledgeGap> rankedGaps = new ArrayList<>();
        private List<String> learningRecommendations = new ArrayList<>();
        private Double overallScore;
        private Double consistencyScore;
        
        // Getters and setters
        
        public List<TopicAssessment> getTopicAssessments() {
            return topicAssessments;
        }
        
        public void setTopicAssessments(List<TopicAssessment> topicAssessments) {
            this.topicAssessments = topicAssessments;
        }
        
        public List<TopicAssessment> getStrengths() {
            return strengths;
        }
        
        public void setStrengths(List<TopicAssessment> strengths) {
            this.strengths = strengths;
        }
        
        public List<TopicAssessment> getWeaknesses() {
            return weaknesses;
        }
        
        public void setWeaknesses(List<TopicAssessment> weaknesses) {
            this.weaknesses = weaknesses;
        }
        
        public List<KnowledgeGap> getRankedGaps() {
            return rankedGaps;
        }
        
        public void setRankedGaps(List<KnowledgeGap> rankedGaps) {
            this.rankedGaps = rankedGaps;
        }
        
        public List<String> getLearningRecommendations() {
            return learningRecommendations;
        }
        
        public void setLearningRecommendations(List<String> learningRecommendations) {
            this.learningRecommendations = learningRecommendations;
        }
        
        public Double getOverallScore() {
            return overallScore;
        }
        
        public void setOverallScore(Double overallScore) {
            this.overallScore = overallScore;
        }
        
        public Double getConsistencyScore() {
            return consistencyScore;
        }
        
        public void setConsistencyScore(Double consistencyScore) {
            this.consistencyScore = consistencyScore;
        }
    }
}
