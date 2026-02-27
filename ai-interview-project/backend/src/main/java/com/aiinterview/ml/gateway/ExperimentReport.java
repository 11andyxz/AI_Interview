package com.aiinterview.ml.gateway;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Experiment report with results, statistical significance, and recommendations
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExperimentReport {
    
    /**
     * Experiment ID
     */
    private String experimentId;
    
    /**
     * Experiment name
     */
    private String experimentName;
    
    /**
     * Experiment status
     */
    private String status;
    
    /**
     * Start time
     */
    private LocalDateTime startTime;
    
    /**
     * End time
     */
    private LocalDateTime endTime;
    
    /**
     * Baseline metrics
     */
    private MetricsSummary baselineMetrics;
    
    /**
     * Variant metrics
     */
    private MetricsSummary variantMetrics;
    
    /**
     * Statistical significance
     */
    private StatisticalSignificance significance;
    
    /**
     * Recommendation: continue, rollback, promote, inconclusive
     */
    private String recommendation;
    
    /**
     * Recommendation reason
     */
    private String recommendationReason;
    
    /**
     * Additional analysis data
     */
    private Map<String, Object> additionalData;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MetricsSummary {
        private int sampleSize;
        private double meanQuality;
        private double meanLatency;
        private double meanCost;
        private double stdDevQuality;
        private double stdDevLatency;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StatisticalSignificance {
        private double pValue;
        private double effectSize;
        private double confidenceInterval95Lower;
        private double confidenceInterval95Upper;
        private boolean isSignificant;
    }
}
