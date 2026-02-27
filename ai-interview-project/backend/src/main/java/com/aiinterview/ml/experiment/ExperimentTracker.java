package com.aiinterview.ml.experiment;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Experiment tracking service for A/B testing
 * 
 * Features:
 * - Consistent traffic assignment (hash-based)
 * - Metrics collection
 * - Statistical evaluation (t-test)
 * - Auto-rollback on regression
 */
@Slf4j
@Service
public class ExperimentTracker {
    
    @Autowired
    private ExperimentRepository experimentRepository;
    
    @Autowired
    private ExperimentMetricRepository metricRepository;
    
    /**
     * Assign a request to an experiment variant  
     * Uses consistent hashing to ensure same requestId always gets same variant
     * 
     * @param experimentId Experiment ID
     * @param requestId Request/session ID (should be consistent for same user)
     * @return Assignment with variant and config
     */
    public ExperimentAssignment assignExperiment(String experimentId, String requestId) {
        Optional<Experiment> experimentOpt = experimentRepository.findById(experimentId);
        
        if (experimentOpt.isEmpty()) {
            throw new IllegalArgumentException("Experiment not found: " + experimentId);
        }
        
        Experiment experiment = experimentOpt.get();
        
        if (!"active".equals(experiment.getStatus())) {
            // Not active - return baseline
            return ExperimentAssignment.builder()
                .experimentId(experimentId)
                .requestId(requestId)
                .variant("baseline")
                .config(experiment.getBaselineConfig())
                .build();
        }
        
        // Consistent hashing to determine variant
        String variant = hashToVariant(experimentId, requestId, experiment.getTrafficSplit());
        String config = "variant".equals(variant) ? 
            experiment.getVariantConfig() : 
            experiment.getBaselineConfig();
        
        log.debug("Assigned experiment {} to variant {} for request {}", 
            experimentId, variant, requestId);
        
        return ExperimentAssignment.builder()
            .experimentId(experimentId)
            .requestId(requestId)
            .variant(variant)
            .config(config)
            .build();
    }
    
    /**
     * Log experiment result metrics
     * 
     * @param experimentId Experiment ID
     * @param requestId Request ID
     * @param metrics Map of metric name to value
     */
    public void logResult(String experimentId, String requestId, Map<String, Double> metrics) {
        // Get assignment to know which variant
        ExperimentAssignment assignment = assignExperiment(experimentId, requestId);
        
        ExperimentMetric metric = ExperimentMetric.builder()
            .experimentId(experimentId)
            .requestId(requestId)
            .variant(assignment.getVariant())
            .qualityScore(metrics.get("quality_score"))
            .latencyMs(metrics.get("latency_ms"))
            .tokensUsed(metrics.get("tokens_used") != null ? metrics.get("tokens_used").intValue() : null)
            .costUsd(metrics.get("cost_usd"))
            .validationPass(metrics.get("validation_pass") != null && metrics.get("validation_pass") > 0.5)
            .build();
        
        metricRepository.save(metric);
        
        log.debug("Logged metrics for experiment {} variant {} request {}", 
            experimentId, assignment.getVariant(), requestId);
    }
    
    /**
     * Evaluate experiment and return statistical results
     * 
     * @param experimentId Experiment ID
     * @return Evaluation result with p-value and recommendation
     */
    public ExperimentResult evaluateExperiment(String experimentId) {
        Optional<Experiment> experimentOpt = experimentRepository.findById(experimentId);
        
        if (experimentOpt.isEmpty()) {
            throw new IllegalArgumentException("Experiment not found: " + experimentId);
        }
        
        Experiment experiment = experimentOpt.get();
        
        // Get metrics for both variants
        List<ExperimentMetric> baselineMetrics = metricRepository
            .findByExperimentIdAndVariant(experimentId, "baseline");
        List<ExperimentMetric> variantMetrics = metricRepository
            .findByExperimentIdAndVariant(experimentId, "variant");
        
        int baselineSamples = baselineMetrics.size();
        int variantSamples = variantMetrics.size();
        
        // Check if we have enough samples
        if (baselineSamples < experiment.getMinSampleSize() || 
            variantSamples < experiment.getMinSampleSize()) {
            
            return ExperimentResult.builder()
                .experimentId(experimentId)
                .experimentName(experiment.getName())
                .baselineSamples(baselineSamples)
                .variantSamples(variantSamples)
                .isSignificant(false)
                .winner("inconclusive")
                .recommendation("continue")
                .explanation(String.format(
                    "Need more samples. Current: baseline=%d, variant=%d. Required: %d per variant.",
                    baselineSamples, variantSamples, experiment.getMinSampleSize()
                ))
                .build();
        }
        
        // Calculate average metrics
        Map<String, Double> baselineAvg = calculateAverageMetrics(baselineMetrics);
        Map<String, Double> variantAvg = calculateAverageMetrics(variantMetrics);
        
        // Perform t-test on primary metric
        String primaryMetric = experiment.getPrimaryMetric();
        List<Double> baselineValues = extractMetricValues(baselineMetrics, primaryMetric);
        List<Double> variantValues = extractMetricValues(variantMetrics, primaryMetric);
        
        double pValue = tTest(baselineValues, variantValues);
        boolean isSignificant = pValue < experiment.getSignificanceThreshold();
        
        // Determine winner
        double baselineValue = baselineAvg.getOrDefault(primaryMetric, 0.0);
        double variantValue = variantAvg.getOrDefault(primaryMetric, 0.0);
        
        boolean variantBetter = isMetricBetter(primaryMetric, variantValue, baselineValue);
        String winner = isSignificant ? (variantBetter ? "variant" : "baseline") : "inconclusive";
        
        // Calculate improvement percentage
        double improvement = 0.0;
        if (baselineValue != 0) {
            improvement = ((variantValue - baselineValue) / baselineValue) * 100;
        }
        
        // Make recommendation
        String recommendation;
        String explanation;
        
        if (!isSignificant) {
            recommendation = "continue";
            explanation = String.format(
                "No statistically significant difference (p=%.4f). Need more data or larger effect size.",
                pValue
            );
        } else if (variantBetter) {
            recommendation = "rollout";
            explanation = String.format(
                "Variant performs %.2f%% better on %s (p=%.4f). Recommend full rollout.",
                Math.abs(improvement), primaryMetric, pValue
            );
        } else {
            recommendation = "rollback";
            explanation = String.format(
                "Baseline performs %.2f%% better on %s (p=%.4f). Recommend rollback.",
                Math.abs(improvement), primaryMetric, pValue
            );
        }
        
        return ExperimentResult.builder()
            .experimentId(experimentId)
            .experimentName(experiment.getName())
            .baselineSamples(baselineSamples)
            .variantSamples(variantSamples)
            .baselineMetrics(baselineAvg)
            .variantMetrics(variantAvg)
            .pValue(pValue)
            .isSignificant(isSignificant)
            .winner(winner)
            .improvement(improvement)
            .recommendation(recommendation)
            .explanation(explanation)
            .build();
    }
    
    /**
     * Check and perform auto-rollback if variant is significantly worse
     * 
     * @param experimentId Experiment ID
     * @return true if rollback was performed
     */
    public boolean checkAndRollback(String experimentId) {
        ExperimentResult result = evaluateExperiment(experimentId);
        
        if (result.getIsSignificant() && "rollback".equals(result.getRecommendation())) {
            Optional<Experiment> experimentOpt = experimentRepository.findById(experimentId);
            
            if (experimentOpt.isPresent()) {
                Experiment experiment = experimentOpt.get();
                experiment.setStatus("rolled_back");
                experiment.setCompletedAt(LocalDateTime.now());
                experiment.setWinner("baseline");
                experiment.setResultSummary(result.getExplanation());
                experimentRepository.save(experiment);
                
                log.warn("Auto-rollback triggered for experiment {}: {}", 
                    experimentId, result.getExplanation());
                
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Create a new experiment
     */
    public Experiment createExperiment(Experiment experiment) {
        if (experiment.getId() == null || experiment.getId().isEmpty()) {
            experiment.setId(UUID.randomUUID().toString());
        }
        
        if (experiment.getStatus() == null) {
            experiment.setStatus("draft");
        }
        
        if (experiment.getTrafficSplit() == null) {
            experiment.setTrafficSplit(0.5); // Default 50/50 split
        }
        
        if (experiment.getMinSampleSize() == null) {
            experiment.setMinSampleSize(100);
        }
        
        if (experiment.getSignificanceThreshold() == null) {
            experiment.setSignificanceThreshold(0.05); // 5% significance level
        }
        
        return experimentRepository.save(experiment);
    }
    
    /**
     * Start an experiment
     */
    public void startExperiment(String experimentId) {
        Experiment experiment = experimentRepository.findById(experimentId)
            .orElseThrow(() -> new IllegalArgumentException("Experiment not found"));
        
        experiment.setStatus("active");
        experiment.setStartedAt(LocalDateTime.now());
        experimentRepository.save(experiment);
        
        log.info("Started experiment: {} ({})", experiment.getId(), experiment.getName());
    }
    
    /**
     * Complete an experiment
     */
    public void completeExperiment(String experimentId, String winner) {
        Experiment experiment = experimentRepository.findById(experimentId)
            .orElseThrow(() -> new IllegalArgumentException("Experiment not found"));
        
        experiment.setStatus("completed");
        experiment.setCompletedAt(LocalDateTime.now());
        experiment.setWinner(winner);
        experimentRepository.save(experiment);
        
        log.info("Completed experiment: {} (winner: {})", experimentId, winner);
    }
    
    /**
     * Get all active experiments
     */
    public List<Experiment> getActiveExperiments() {
        return experimentRepository.findByStatus("active");
    }
    
    // ========== Private Helper Methods ==========
    
    /**
     * Hash requestId to determine variant assignment
     */
    private String hashToVariant(String experimentId, String requestId, double trafficSplit) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String combined = experimentId + ":" + requestId;
            byte[] hash = digest.digest(combined.getBytes(StandardCharsets.UTF_8));
            
            // Convert first 8 bytes to long
            long hashValue = 0;
            for (int i = 0; i < min(8, hash.length); i++) {
                hashValue = (hashValue << 8) | (hash[i] & 0xFF);
            }
            
            // Normalize to 0.0-1.0
            double normalized = (hashValue & 0x7FFFFFFFFFFFFFFFL) / (double) Long.MAX_VALUE;
            
            // Assign based on traffic split
            return normalized < trafficSplit ? "variant" : "baseline";
            
        } catch (Exception e) {
            log.error("Hash failed, defaulting to baseline", e);
            return "baseline"; 
        }
    }
    
    private int min(int a, int b) {
        return a < b ? a : b;
    }
    
    /**
     * Calculate average metrics for a list of metric entries
     */
    private Map<String, Double> calculateAverageMetrics(List<ExperimentMetric> metrics) {
        if (metrics.isEmpty()) {
            return Map.of();
        }
        
        Map<String, Double> averages = new HashMap<>();
        
        // Quality score
        double avgQuality = metrics.stream()
            .filter(m -> m.getQualityScore() != null)
            .mapToDouble(ExperimentMetric::getQualityScore)
            .average()
            .orElse(0.0);
        averages.put("quality_score", avgQuality);
        
        // Latency
        double avgLatency = metrics.stream()
            .filter(m -> m.getLatencyMs() != null)
            .mapToDouble(ExperimentMetric::getLatencyMs)
            .average()
            .orElse(0.0);
        averages.put("latency_ms", avgLatency);
        
        // Cost
        double avgCost = metrics.stream()
            .filter(m -> m.getCostUsd() != null)
            .mapToDouble(ExperimentMetric::getCostUsd)
            .average()
            .orElse(0.0);
        averages.put("cost_usd", avgCost);
        
        // Pass rate
        long passCount = metrics.stream()
            .filter(m -> m.getValidationPass() != null && m.getValidationPass())
            .count();
        double passRate = (double) passCount / metrics.size();
        averages.put("pass_rate", passRate);
        
        return averages;
    }
    
    /**
     * Extract metric values for statistical testing
     */
    private List<Double> extractMetricValues(List<ExperimentMetric> metrics, String metricName) {
        return metrics.stream()
            .map(m -> {
                switch (metricName) {
                    case "quality_score": return m.getQualityScore();
                    case "latency_ms": return m.getLatencyMs();
                    case "cost_usd": return m.getCostUsd();
                    case "pass_rate": return m.getValidationPass() != null && m.getValidationPass() ? 1.0 : 0.0;
                    default: return null;
                }
            })
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
    }
    
    /**
     * Perform Welch's t-test (unequal variances)
     * Returns p-value
     */
    private double tTest(List<Double> sample1, List<Double> sample2) {
        if (sample1.size() < 2 || sample2.size() < 2) {
            return 1.0; // Not enough data
        }
        
        double mean1 = sample1.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double mean2 = sample2.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        
        double var1 = variance(sample1, mean1);
        double var2 = variance(sample2, mean2);
        
        int n1 = sample1.size();
        int n2 = sample2.size();
        
        // Welch's t-statistic
        double t = (mean1 - mean2) / Math.sqrt(var1/n1 + var2/n2);
        
        // Degrees of freedom (Welch-Satterthwaite)
        double df = Math.pow(var1/n1 + var2/n2, 2) / 
            (Math.pow(var1/n1, 2)/(n1-1) + Math.pow(var2/n2, 2)/(n2-1));
        
        // Simplified p-value approximation (two-tailed)
        // For production, use Apache Commons Math or similar library
        double pValue = 2 * (1 - approximateCDF(Math.abs(t), df));
        
        return Math.max(0.0, Math.min(1.0, pValue));
    }
    
    /**
     * Calculate variance
     */
    private double variance(List<Double> values, double mean) {
        if (values.size() < 2) {
            return 0.0;
        }
        
        double sumSquares = values.stream()
            .mapToDouble(v -> Math.pow(v - mean, 2))
            .sum();
        
        return sumSquares / (values.size() - 1);
    }
    
    /**
     * Approximate CDF of t-distribution (simplified)
     * For production, use proper statistical library
     */
    private double approximateCDF(double t, double df) {
        // Very rough approximation using normal distribution
        // For small t and large df, t-distribution ≈ normal
        if (df > 30) {
            return 0.5 * (1 + erf(t / Math.sqrt(2)));
        }
        
        // For smaller df, use conservative estimate
        return 0.5 * (1 + erf(t / Math.sqrt(2 + df/10)));
    }
    
    /**
     * Error function approximation
     */
    private double erf(double x) {
        // Abramowitz and Stegun approximation
        double a1 =  0.254829592;
        double a2 = -0.284496736;
        double a3 =  1.421413741;
        double a4 = -1.453152027;
        double a5 =  1.061405429;
        double p  =  0.3275911;
        
        int sign = x < 0 ? -1 : 1;
        x = Math.abs(x);
        
        double t = 1.0 / (1.0 + p * x);
        double y = 1.0 - (((((a5 * t + a4) * t) + a3) * t + a2) * t + a1) * t * Math.exp(-x * x);
        
        return sign * y;
    }
    
    /**
     * Check if variant metric is better than baseline
     * For quality_score and pass_rate: higher is better
     * For latency_ms and cost_usd: lower is better
     */
    private boolean isMetricBetter(String metricName, double variantValue, double baselineValue) {
        switch (metricName) {
            case "quality_score":
            case "pass_rate":
                return variantValue > baselineValue;
            case "latency_ms":
            case "cost_usd":
                return variantValue < baselineValue;
            default:
                return variantValue > baselineValue;
        }
    }
}
