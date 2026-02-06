package com.aiinterview.ml.experiments;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Represents an A/B test experiment for prompt versions
 */
@Data
@Builder
public class Experiment {
    private String name;
    private String scenario; // resume_analysis, question_generation, answer_evaluation
    private Map<String, Double> variants; // variant_name -> traffic_percentage
    private String primaryMetric; // quality_score, pass_rate, cost_per_request
    private int minimumSamples;
    private LocalDateTime startDate;
    private LocalDateTime endDate;
    private ExperimentStatus status;
    
    @Data
    @Builder
    public static class ExperimentResult {
        private String variant;
        private int sampleSize;
        private double metricValue;
        private double confidenceInterval;
        private boolean isSignificant;
    }
    
    public enum ExperimentStatus {
        DRAFT,
        ACTIVE,
        PAUSED,
        COMPLETED,
        CANCELLED
    }
    
    /**
     * Validate experiment configuration
     */
    public boolean isValid() {
        if (variants == null || variants.isEmpty()) {
            return false;
        }
        
        // Check traffic percentages sum to ~1.0
        double totalTraffic = variants.values().stream()
            .mapToDouble(Double::doubleValue)
            .sum();
        
        return Math.abs(totalTraffic - 1.0) < 0.001;
    }
    
    /**
     * Check if experiment is currently active
     */
    public boolean isActive() {
        if (status != ExperimentStatus.ACTIVE) {
            return false;
        }
        
        LocalDateTime now = LocalDateTime.now();
        return (startDate == null || now.isAfter(startDate)) &&
               (endDate == null || now.isBefore(endDate));
    }
}
