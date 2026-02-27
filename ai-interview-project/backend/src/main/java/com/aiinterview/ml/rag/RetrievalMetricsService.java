package com.aiinterview.ml.rag;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Retrieval evaluation metrics for RAG systems
 * Implements MRR, NDCG@K, Recall@K, Precision@K
 */
@Slf4j
@Service
public class RetrievalMetricsService {
    
    /**
     * Calculate Mean Reciprocal Rank (MRR)
     * Measures where the first relevant result appears
     * 
     * @param retrievedIds Retrieved document IDs
     * @param relevantIds Relevant document IDs
     * @return MRR score (0-1)
     */
    public double calculateMRR(List<String> retrievedIds, Set<String> relevantIds) {
        if (retrievedIds.isEmpty() || relevantIds.isEmpty()) {
            return 0.0;
        }
        
        for (int i = 0; i < retrievedIds.size(); i++) {
            if (relevantIds.contains(retrievedIds.get(i))) {
                return 1.0 / (i + 1);
            }
        }
        
        return 0.0;
    }
    
    /**
     * Calculate Normalized Discounted Cumulative Gain at K
     * Measures ranking quality with position discount
     * 
     * @param retrievedIds Retrieved document IDs
     * @param relevanceScores Relevance scores for each document (0-N scale)
     * @param k Top-K cutoff
     * @return NDCG@K score (0-1)
     */
    public double calculateNDCG(List<String> retrievedIds, Map<String, Double> relevanceScores, int k) {
        if (retrievedIds.isEmpty() || relevanceScores.isEmpty()) {
            return 0.0;
        }
        
        // Calculate DCG@K for retrieved results
        double dcg = 0.0;
        for (int i = 0; i < Math.min(k, retrievedIds.size()); i++) {
            String docId = retrievedIds.get(i);
            double relevance = relevanceScores.getOrDefault(docId, 0.0);
            dcg += (Math.pow(2, relevance) - 1) / (Math.log(i + 2) / Math.log(2));
        }
        
        // Calculate ideal DCG@K
        List<Double> sortedRelevances = relevanceScores.values().stream()
            .sorted(Comparator.reverseOrder())
            .collect(Collectors.toList());
        
        double idcg = 0.0;
        for (int i = 0; i < Math.min(k, sortedRelevances.size()); i++) {
            double relevance = sortedRelevances.get(i);
            idcg += (Math.pow(2, relevance) - 1) / (Math.log(i + 2) / Math.log(2));
        }
        
        return idcg > 0 ? dcg / idcg : 0.0;
    }
    
    /**
     * Calculate Recall@K
     * Measures proportion of relevant documents retrieved
     * 
     * @param retrievedIds Retrieved document IDs (top K)
     * @param relevantIds All relevant document IDs
     * @return Recall@K score (0-1)
     */
    public double calculateRecall(List<String> retrievedIds, Set<String> relevantIds) {
        if (relevantIds.isEmpty()) {
            return 0.0;
        }
        
        Set<String> retrievedSet = new HashSet<>(retrievedIds);
        long relevantRetrieved = relevantIds.stream()
            .filter(retrievedSet::contains)
            .count();
        
        return (double) relevantRetrieved / relevantIds.size();
    }
    
    /**
     * Calculate Precision@K
     * Measures proportion of retrieved documents that are relevant
     * 
     * @param retrievedIds Retrieved document IDs (top K)
     * @param relevantIds All relevant document IDs
     * @return Precision@K score (0-1)
     */
    public double calculatePrecision(List<String> retrievedIds, Set<String> relevantIds) {
        if (retrievedIds.isEmpty()) {
            return 0.0;
        }
        
        long relevantCount = retrievedIds.stream()
            .filter(relevantIds::contains)
            .count();
        
        return (double) relevantCount / retrievedIds.size();
    }
    
    /**
     * Calculate F1 Score (harmonic mean of precision and recall)
     * 
     * @param retrievedIds Retrieved document IDs
     * @param relevantIds Relevant document IDs
     * @return F1 score (0-1)
     */
    public double calculateF1(List<String> retrievedIds, Set<String> relevantIds) {
        double precision = calculatePrecision(retrievedIds, relevantIds);
        double recall = calculateRecall(retrievedIds, relevantIds);
        
        if (precision + recall == 0) {
            return 0.0;
        }
        
        return 2 * (precision * recall) / (precision + recall);
    }
    
    /**
     * Calculate all metrics for a single query
     * 
     * @param retrievedIds Retrieved document IDs
     * @param relevantIds Relevant document IDs (binary relevance)
     * @param relevanceScores Optional graded relevance scores
     * @param k Top-K cutoff for metrics
     * @return Comprehensive metrics result
     */
    public MetricsResult calculateAllMetrics(
        List<String> retrievedIds,
        Set<String> relevantIds,
        Map<String, Double> relevanceScores,
        int k
    ) {
        List<String> topK = retrievedIds.stream()
            .limit(k)
            .collect(Collectors.toList());
        
        double mrr = calculateMRR(retrievedIds, relevantIds);
        double ndcg = relevanceScores != null ? 
            calculateNDCG(retrievedIds, relevanceScores, k) : 0.0;
        double recall = calculateRecall(topK, relevantIds);
        double precision = calculatePrecision(topK, relevantIds);
        double f1 = calculateF1(topK, relevantIds);
        
        return new MetricsResult(mrr, ndcg, recall, precision, f1, k);
    }
    
    /**
     * Calculate average metrics across multiple queries
     * 
     * @param results List of individual query metrics
     * @return Averaged metrics
     */
    public MetricsResult calculateAverageMetrics(List<MetricsResult> results) {
        if (results.isEmpty()) {
            return new MetricsResult(0.0, 0.0, 0.0, 0.0, 0.0, 0);
        }
        
        double avgMRR = results.stream()
            .mapToDouble(MetricsResult::getMrr)
            .average()
            .orElse(0.0);
        
        double avgNDCG = results.stream()
            .mapToDouble(MetricsResult::getNdcg)
            .average()
            .orElse(0.0);
        
        double avgRecall = results.stream()
            .mapToDouble(MetricsResult::getRecall)
            .average()
            .orElse(0.0);
        
        double avgPrecision = results.stream()
            .mapToDouble(MetricsResult::getPrecision)
            .average()
            .orElse(0.0);
        
        double avgF1 = results.stream()
            .mapToDouble(MetricsResult::getF1)
            .average()
            .orElse(0.0);
        
        int k = results.get(0).getK();
        
        return new MetricsResult(avgMRR, avgNDCG, avgRecall, avgPrecision, avgF1, k);
    }
    
    /**
     * Container for retrieval metrics
     */
    @Data
    public static class MetricsResult {
        private final double mrr;
        private final double ndcg;
        private final double recall;
        private final double precision;
        private final double f1;
        private final int k;
        
        @Override
        public String toString() {
            return String.format(
                "Metrics@%d: MRR=%.4f, NDCG=%.4f, Recall=%.4f, Precision=%.4f, F1=%.4f",
                k, mrr, ndcg, recall, precision, f1
            );
        }
    }
}
