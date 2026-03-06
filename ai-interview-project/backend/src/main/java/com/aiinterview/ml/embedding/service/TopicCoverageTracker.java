package com.aiinterview.ml.embedding.service;

import com.aiinterview.ml.embedding.entity.QuestionEmbedding;
import com.aiinterview.ml.embedding.entity.TopicCoverage;
import com.aiinterview.ml.embedding.repository.QuestionEmbeddingRepository;
import com.aiinterview.ml.embedding.repository.TopicCoverageRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Tracks topic coverage and diversity metrics for interview sessions.
 * Computes Shannon entropy to measure topic diversity and identifies coverage gaps.
 */
@Service
public class TopicCoverageTracker {
    
    private static final Logger logger = LoggerFactory.getLogger(TopicCoverageTracker.class);
    private static final double COVERAGE_GAP_THRESHOLD = 0.3; // Cluster with <30% of avg is a gap
    
    private final TopicCoverageRepository coverageRepository;
    private final QuestionEmbeddingRepository embeddingRepository;
    private final ObjectMapper objectMapper;
    
    public TopicCoverageTracker(
            TopicCoverageRepository coverageRepository,
            QuestionEmbeddingRepository embeddingRepository,
            ObjectMapper objectMapper) {
        this.coverageRepository = coverageRepository;
        this.embeddingRepository = embeddingRepository;
        this.objectMapper = objectMapper;
    }
    
    /**
     * Update coverage metrics after a question is asked
     * 
     * @param sessionId Interview session ID
     * @param roleId Role ID
     * @param questionId Question that was just asked
     */
    @Transactional
    public void recordQuestionAsked(String sessionId, Long roleId, Long questionId) {
        logger.debug("Recording question {} for session {} role {}", questionId, sessionId, roleId);
        
        // Get question's cluster
        String questionIdStr = String.valueOf(questionId);
        Optional<QuestionEmbedding> embeddingOpt = embeddingRepository.findByQuestionIdAndRoleId(questionIdStr, roleId);
        if (embeddingOpt.isEmpty() || embeddingOpt.get().getClusterId() == null) {
            logger.warn("Question {} has no cluster assignment, skipping coverage update", questionId);
            return;
        }
        
        Integer clusterId = embeddingOpt.get().getClusterId();
        
        // Get or create coverage record
        TopicCoverage coverage = coverageRepository.findBySessionIdAndRoleId(sessionId, roleId)
                .orElseGet(() -> initializeCoverage(sessionId, roleId));
        
        // Update cluster distribution
        Map<String, Integer> distribution = parseClusterDistribution(coverage.getClusterDistribution());
        distribution.merge(String.valueOf(clusterId), 1, Integer::sum);
        coverage.setClusterDistribution(serializeClusterDistribution(distribution));
        
        // Recompute metrics
        updateCoverageMetrics(coverage, distribution);
        
        coverageRepository.save(coverage);
        
        logger.debug("Updated coverage for session {}: {}/{} clusters, entropy={}", 
                sessionId, coverage.getClustersCovered(), coverage.getTotalClusters(), 
                coverage.getShannonEntropy());
    }
    
    /**
     * Get current coverage metrics for a session
     */
    public TopicCoverage getCoverageMetrics(String sessionId, Long roleId) {
        return coverageRepository.findBySessionIdAndRoleId(sessionId, roleId)
                .orElseGet(() -> initializeCoverage(sessionId, roleId));
    }
    
    /**
     * Get topic diversity score [0, 1] for a session.
     * Based on normalized Shannon entropy.
     */
    public double getTopicDiversityScore(String sessionId, Long roleId) {
        TopicCoverage coverage = getCoverageMetrics(sessionId, roleId);
        return coverage.getNormalizedEntropy() != null ? coverage.getNormalizedEntropy() : 0.0;
    }
    
    /**
     * Get clusters that are under-represented in the interview
     * 
     * @return List of cluster IDs that have been covered less than threshold
     */
    public List<Integer> detectCoverageGaps(String sessionId, Long roleId) {
        TopicCoverage coverage = coverageRepository.findBySessionIdAndRoleId(sessionId, roleId)
                .orElse(null);
        
        if (coverage == null || coverage.getTotalQuestions() == 0) {
            // No questions asked yet, all clusters are gaps
            return embeddingRepository.findDistinctClusterIdsByRoleId(roleId);
        }
        
        Map<String, Integer> distribution = parseClusterDistribution(coverage.getClusterDistribution());
        
        // Calculate average questions per cluster (if uniform)
        double avgQuestionsPerCluster = (double) coverage.getTotalQuestions() / coverage.getTotalClusters();
        double gapThreshold = avgQuestionsPerCluster * COVERAGE_GAP_THRESHOLD;
        
        // Find all clusters for this role
        List<Integer> allClusters = embeddingRepository.findDistinctClusterIdsByRoleId(roleId);
        
        // Identify gaps: clusters with 0 questions or below threshold
        List<Integer> gaps = new ArrayList<>();
        for (Integer clusterId : allClusters) {
            int count = distribution.getOrDefault(String.valueOf(clusterId), 0);
            if (count < gapThreshold) {
                gaps.add(clusterId);
            }
        }
        
        logger.debug("Detected {} coverage gaps for session {} (threshold={}): {}", 
                gaps.size(), sessionId, gapThreshold, gaps);
        
        return gaps;
    }
    
    /**
     * Get reward for asking a question from a specific cluster.
     * Higher reward for under-covered clusters.
     * 
     * @return Diversity reward in [0, 1]
     */
    public double getClusterDiversityReward(String sessionId, Long roleId, Integer clusterId) {
        if (clusterId == null) {
            return 0.0;
        }
        
        TopicCoverage coverage = coverageRepository.findBySessionIdAndRoleId(sessionId, roleId)
                .orElse(null);
        
        if (coverage == null || coverage.getTotalQuestions() == 0) {
            // First question, all clusters equally rewarded
            return 1.0;
        }
        
        Map<String, Integer> distribution = parseClusterDistribution(coverage.getClusterDistribution());
        int clusterCount = distribution.getOrDefault(String.valueOf(clusterId), 0);
        
        // Inverse frequency: reward = 1 / (1 + count)
        // Questions from uncovered clusters get highest reward (1.0)
        // Questions from frequently covered clusters get lower reward
        return 1.0 / (1.0 + clusterCount);
    }
    
    /**
     * Initialize a new coverage record for a session
     */
    private TopicCoverage initializeCoverage(String sessionId, Long roleId) {
        TopicCoverage coverage = new TopicCoverage();
        coverage.setSessionId(sessionId);
        coverage.setRoleId(roleId);
        coverage.setClusterDistribution("{}");
        coverage.setTotalQuestions(0);
        coverage.setClustersCovered(0);
        
        // Get total available clusters for this role
        List<Integer> allClusters = embeddingRepository.findDistinctClusterIdsByRoleId(roleId);
        int totalClusters = allClusters.size();
        coverage.setTotalClusters(totalClusters);
        
        coverage.setCoverageRatio(0.0);
        coverage.setShannonEntropy(0.0);
        coverage.setMaxEntropy(totalClusters > 0 ? Math.log(totalClusters) : 0.0);
        coverage.setNormalizedEntropy(0.0);
        
        return coverage;
    }
    
    /**
     * Update all coverage metrics based on current distribution
     */
    private void updateCoverageMetrics(TopicCoverage coverage, Map<String, Integer> distribution) {
        int totalQuestions = distribution.values().stream().mapToInt(Integer::intValue).sum();
        int clustersCovered = distribution.size();
        int totalClusters = coverage.getTotalClusters();
        
        coverage.setTotalQuestions(totalQuestions);
        coverage.setClustersCovered(clustersCovered);
        coverage.setCoverageRatio(totalClusters > 0 ? (double) clustersCovered / totalClusters : 0.0);
        
        // Compute Shannon entropy: H = -Σ(p_i * log(p_i))
        double shannonEntropy = computeShannonEntropy(distribution, totalQuestions);
        coverage.setShannonEntropy(shannonEntropy);
        
        // Normalized entropy
        double maxEntropy = totalClusters > 0 ? Math.log(totalClusters) : 0.0;
        coverage.setMaxEntropy(maxEntropy);
        coverage.setNormalizedEntropy(maxEntropy > 0 ? shannonEntropy / maxEntropy : 0.0);
    }
    
    /**
     * Compute Shannon entropy: H = -Σ(p_i * log(p_i))
     * Measures diversity of topic distribution.
     * 
     * @param distribution Map of cluster_id -> count
     * @param totalQuestions Total number of questions
     * @return Shannon entropy (nats)
     */
    private double computeShannonEntropy(Map<String, Integer> distribution, int totalQuestions) {
        if (totalQuestions == 0) {
            return 0.0;
        }
        
        double entropy = 0.0;
        for (int count : distribution.values()) {
            double probability = (double) count / totalQuestions;
            if (probability > 0) {
                entropy -= probability * Math.log(probability);
            }
        }
        
        return entropy;
    }
    
    /**
     * Parse JSON cluster distribution
     */
    private Map<String, Integer> parseClusterDistribution(String json) {
        if (json == null || json.trim().isEmpty() || json.equals("{}")) {
            return new HashMap<>();
        }
        
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Integer>>() {});
        } catch (JsonProcessingException e) {
            logger.error("Failed to parse cluster distribution JSON: {}", json, e);
            return new HashMap<>();
        }
    }
    
    /**
     * Serialize cluster distribution to JSON
     */
    private String serializeClusterDistribution(Map<String, Integer> distribution) {
        try {
            return objectMapper.writeValueAsString(distribution);
        } catch (JsonProcessingException e) {
            logger.error("Failed to serialize cluster distribution", e);
            return "{}";
        }
    }
    
    /**
     * Result class for coverage analysis
     */
    public static class CoverageAnalysis {
        private final double coverageRatio;
        private final double shannonEntropy;
        private final double normalizedEntropy;
        private final List<Integer> coverageGaps;
        private final Map<Integer, Integer> clusterDistribution;
        
        public CoverageAnalysis(double coverageRatio, double shannonEntropy, double normalizedEntropy,
                                List<Integer> coverageGaps, Map<Integer, Integer> clusterDistribution) {
            this.coverageRatio = coverageRatio;
            this.shannonEntropy = shannonEntropy;
            this.normalizedEntropy = normalizedEntropy;
            this.coverageGaps = coverageGaps;
            this.clusterDistribution = clusterDistribution;
        }
        
        public double getCoverageRatio() {
            return coverageRatio;
        }
        
        public double getShannonEntropy() {
            return shannonEntropy;
        }
        
        public double getNormalizedEntropy() {
            return normalizedEntropy;
        }
        
        public List<Integer> getCoverageGaps() {
            return coverageGaps;
        }
        
        public Map<Integer, Integer> getClusterDistribution() {
            return clusterDistribution;
        }
    }
    
    /**
     * Get comprehensive coverage analysis for a session
     */
    public CoverageAnalysis analyzeCoverage(String sessionId, Long roleId) {
        TopicCoverage coverage = getCoverageMetrics(sessionId, roleId);
        List<Integer> gaps = detectCoverageGaps(sessionId, roleId);
        
        Map<String, Integer> distMap = parseClusterDistribution(coverage.getClusterDistribution());
        Map<Integer, Integer> clusterDist = distMap.entrySet().stream()
                .collect(Collectors.toMap(
                        e -> Integer.parseInt(e.getKey()),
                        Map.Entry::getValue
                ));
        
        return new CoverageAnalysis(
                coverage.getCoverageRatio(),
                coverage.getShannonEntropy(),
                coverage.getNormalizedEntropy(),
                gaps,
                clusterDist
        );
    }
}
