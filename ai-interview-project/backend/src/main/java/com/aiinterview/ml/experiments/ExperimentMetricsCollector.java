package com.aiinterview.ml.experiments;

import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Collects and aggregates metrics for A/B test experiments
 */
@Slf4j
@Service
public class ExperimentMetricsCollector {
    
    private final Map<String, List<ExperimentDataPoint>> experimentData = new ConcurrentHashMap<>();
    
    @Data
    @Builder
    public static class ExperimentDataPoint {
        private String scenario;
        private String variant;
        private String userId;
        private LocalDateTime timestamp;
        private double latencyMs;
        private int tokenCount;
        private double qualityScore;
        private boolean passed;
        private double costEstimate;
        private Map<String, Object> metadata;
    }
    
    /**
     * Record a single data point for an experiment
     */
    public void recordDataPoint(ExperimentDataPoint dataPoint) {
        String key = dataPoint.getScenario() + "/" + dataPoint.getVariant();
        experimentData.computeIfAbsent(key, k -> new ArrayList<>()).add(dataPoint);
        
        log.debug("Recorded experiment data: scenario={}, variant={}, quality={}", 
                 dataPoint.getScenario(), dataPoint.getVariant(), dataPoint.getQualityScore());
    }
    
    /**
     * Get aggregated metrics for a variant
     */
    public VariantMetrics getVariantMetrics(String scenario, String variant) {
        String key = scenario + "/" + variant;
        List<ExperimentDataPoint> data = experimentData.getOrDefault(key, new ArrayList<>());
        
        if (data.isEmpty()) {
            return VariantMetrics.builder()
                .variant(variant)
                .sampleSize(0)
                .build();
        }
        
        return VariantMetrics.builder()
            .variant(variant)
            .sampleSize(data.size())
            .avgLatencyMs(calculateAverage(data, ExperimentDataPoint::getLatencyMs))
            .p50LatencyMs(calculatePercentile(data, ExperimentDataPoint::getLatencyMs, 0.5))
            .p95LatencyMs(calculatePercentile(data, ExperimentDataPoint::getLatencyMs, 0.95))
            .avgTokens(calculateAverage(data, dp -> (double) dp.getTokenCount()))
            .avgQualityScore(calculateAverage(data, ExperimentDataPoint::getQualityScore))
            .passRate(calculatePassRate(data))
            .avgCost(calculateAverage(data, ExperimentDataPoint::getCostEstimate))
            .build();
    }
    
    /**
     * Compare two variants and determine if difference is significant
     */
    public ComparisonResult compareVariants(String scenario, String controlVariant, String treatmentVariant) {
        VariantMetrics control = getVariantMetrics(scenario, controlVariant);
        VariantMetrics treatment = getVariantMetrics(scenario, treatmentVariant);
        
        // Simple significance test (t-test approximation)
        double qualityImprovement = treatment.getAvgQualityScore() - control.getAvgQualityScore();
        double costReduction = ((control.getAvgCost() - treatment.getAvgCost()) / control.getAvgCost()) * 100;
        double latencyReduction = ((control.getAvgLatencyMs() - treatment.getAvgLatencyMs()) / control.getAvgLatencyMs()) * 100;
        
        // Simple significance check (would use proper statistical test in production)
        boolean isSignificant = Math.abs(qualityImprovement) > 0.3 && 
                               control.getSampleSize() > 30 && 
                               treatment.getSampleSize() > 30;
        
        return ComparisonResult.builder()
            .scenario(scenario)
            .controlVariant(controlVariant)
            .treatmentVariant(treatmentVariant)
            .controlMetrics(control)
            .treatmentMetrics(treatment)
            .qualityImprovement(qualityImprovement)
            .costReductionPercent(costReduction)
            .latencyReductionPercent(latencyReduction)
            .isSignificant(isSignificant)
            .recommendation(generateRecommendation(qualityImprovement, costReduction, isSignificant))
            .build();
    }
    
    private String generateRecommendation(double qualityImprovement, double costReduction, boolean isSignificant) {
        if (!isSignificant) {
            return "CONTINUE_TESTING - Need more samples for statistical significance";
        }
        
        if (qualityImprovement > 0.5 && costReduction > 0) {
            return "ROLLOUT_TREATMENT - Significant quality improvement with cost savings";
        } else if (qualityImprovement > 0.3) {
            return "CONSIDER_ROLLOUT - Notable quality improvement";
        } else if (qualityImprovement < -0.3) {
            return "KEEP_CONTROL - Treatment variant shows worse performance";
        } else {
            return "INCONCLUSIVE - Minimal difference between variants";
        }
    }
    
    private double calculateAverage(List<ExperimentDataPoint> data, 
                                    java.util.function.Function<ExperimentDataPoint, Double> extractor) {
        return data.stream()
            .mapToDouble(extractor::apply)
            .average()
            .orElse(0.0);
    }
    
    private double calculatePercentile(List<ExperimentDataPoint> data,
                                      java.util.function.Function<ExperimentDataPoint, Double> extractor,
                                      double percentile) {
        List<Double> values = data.stream()
            .map(extractor)
            .sorted()
            .toList();
        
        if (values.isEmpty()) {
            return 0.0;
        }
        
        int index = (int) Math.ceil(percentile * values.size()) - 1;
        return values.get(Math.max(0, Math.min(index, values.size() - 1)));
    }
    
    private double calculatePassRate(List<ExperimentDataPoint> data) {
        long passedCount = data.stream().filter(ExperimentDataPoint::isPassed).count();
        return data.isEmpty() ? 0.0 : (double) passedCount / data.size();
    }
    
    /**
     * Clear all data for a scenario (useful after experiment completion)
     */
    public void clearScenarioData(String scenario) {
        experimentData.keySet().removeIf(key -> key.startsWith(scenario + "/"));
        log.info("Cleared experiment data for scenario: {}", scenario);
    }
    
    @Data
    @Builder
    public static class VariantMetrics {
        private String variant;
        private int sampleSize;
        private double avgLatencyMs;
        private double p50LatencyMs;
        private double p95LatencyMs;
        private double avgTokens;
        private double avgQualityScore;
        private double passRate;
        private double avgCost;
    }
    
    @Data
    @Builder
    public static class ComparisonResult {
        private String scenario;
        private String controlVariant;
        private String treatmentVariant;
        private VariantMetrics controlMetrics;
        private VariantMetrics treatmentMetrics;
        private double qualityImprovement;
        private double costReductionPercent;
        private double latencyReductionPercent;
        private boolean isSignificant;
        private String recommendation;
    }
}
