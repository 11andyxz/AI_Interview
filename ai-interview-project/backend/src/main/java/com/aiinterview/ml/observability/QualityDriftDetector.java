package com.aiinterview.ml.observability;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Detects quality drift using statistical tests
 * Uses Welch's t-test to compare current window vs baseline
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QualityDriftDetector {
    
    private final LlmCallMetricRepository metricRepository;
    private final MlMetricsCollector metricsCollector;
    
    // Store baselines per endpoint
    private final Map<String, QualityBaseline> baselines = new ConcurrentHashMap<>();
    
    // Alert thresholds
    private static final double DRIFT_P_VALUE_THRESHOLD = 0.05;
    private static final double QUALITY_DROP_THRESHOLD = 10.0;  // 10% drop
    private static final int MIN_SAMPLE_SIZE = 30;
    
    /**
     * Detect quality drift for an endpoint
     * Compares recent window against baseline using Welch's t-test
     * 
     * @param endpoint Endpoint to check
     * @param window Recent time window to analyze
     * @return Drift report with p-value and recommendation
     */
    public DriftReport detectDrift(String endpoint, Duration window) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime windowStart = now.minus(window);
        
        // Get current window metrics
        List<LlmCallMetric> currentMetrics = metricRepository
            .findByEndpointAndCreatedAtBetween(endpoint, windowStart, now);
        
        if (currentMetrics.size() < MIN_SAMPLE_SIZE) {
            log.warn("Insufficient samples for drift detection: endpoint={}, count={}", 
                     endpoint, currentMetrics.size());
            return buildInsufficientDataReport(endpoint, windowStart, now);
        }
        
        // Extract quality scores
        double[] currentScores = currentMetrics.stream()
            .filter(m -> m.getQualityScore() != null)
            .mapToDouble(LlmCallMetric::getQualityScore)
            .toArray();
        
        if (currentScores.length < MIN_SAMPLE_SIZE) {
            return buildInsufficientDataReport(endpoint, windowStart, now);
        }
        
        // Get or compute baseline
        QualityBaseline baseline = baselines.get(endpoint);
        if (baseline == null) {
            log.info("No baseline found for endpoint={}, computing from historical data", endpoint);
            baseline = computeBaselineFromHistory(endpoint);
            if (baseline != null) {
                baselines.put(endpoint, baseline);
            } else {
                // Use current window as baseline
                baseline = computeBaseline(endpoint, currentScores, windowStart, now);
                baselines.put(endpoint, baseline);
                return buildNoBaselineReport(endpoint, baseline, windowStart, now);
            }
        }
        
        // Perform Welch's t-test
        double currentMean = Arrays.stream(currentScores).average().orElse(0.0);
        double currentStdDev = calculateStdDev(currentScores, currentMean);
        
        TTestResult tTest = welchTTest(
            baseline.getMeanQuality(), baseline.getStdDeviation(), baseline.getSampleSize(),
            currentMean, currentStdDev, currentScores.length
        );
        
        boolean driftDetected = tTest.pValue < DRIFT_P_VALUE_THRESHOLD && 
                               (baseline.getMeanQuality() - currentMean) > QUALITY_DROP_THRESHOLD;
        
        DriftReport report = DriftReport.builder()
            .endpoint(endpoint)
            .baseline(baseline)
            .currentMean(currentMean)
            .currentStdDev(currentStdDev)
            .currentSampleSize(currentScores.length)
            .pValue(tTest.pValue)
            .tStatistic(tTest.tStatistic)
            .driftDetected(driftDetected)
            .detectedAt(now)
            .windowStart(windowStart)
            .windowEnd(now)
            .build();
        
        if (driftDetected) {
            log.warn("Quality drift detected! endpoint={}, baseline={}, current={}, p-value={}, change={}%",
                     endpoint, baseline.getMeanQuality(), currentMean, tTest.pValue, report.getQualityChange());
        }
        
        return report;
    }
    
    /**
     * Check all active alerts
     * Returns alerts that need attention
     */
    public List<QualityAlert> checkAlerts() {
        List<QualityAlert> alerts = new ArrayList<>();
        
        // Get all endpoints from recent metrics
        List<String> endpoints = metricRepository.findAllEndpoints();
        
        for (String endpoint : endpoints) {
            // Check for drift
            DriftReport driftReport = detectDrift(endpoint, Duration.ofHours(1));
            
            if (driftReport.isDriftDetected()) {
                QualityAlert alert = QualityAlert.builder()
                    .id(UUID.randomUUID().toString())
                    .endpoint(endpoint)
                    .alertType("drift")
                    .severity(driftReport.getSeverity())
                    .message(String.format("Quality drift detected: %.1f%% drop (p-value=%.4f)",
                                          Math.abs(driftReport.getQualityChange()), 
                                          driftReport.getPValue()))
                    .currentValue(driftReport.getCurrentMean())
                    .threshold(driftReport.getBaseline().getMeanQuality())
                    .pValue(driftReport.getPValue())
                    .triggeredAt(LocalDateTime.now())
                    .acknowledged(false)
                    .build();
                
                alerts.add(alert);
            }
            
            // Check for latency spikes
            Double avgLatency = metricRepository.getAverageLatency(
                endpoint, 
                LocalDateTime.now().minusHours(1), 
                LocalDateTime.now()
            );
            
            if (avgLatency != null && avgLatency > 3000) {  // >3s
                QualityAlert alert = QualityAlert.builder()
                    .id(UUID.randomUUID().toString())
                    .endpoint(endpoint)
                    .alertType("latency_spike")
                    .severity(avgLatency > 5000 ? "critical" : "warning")
                    .message(String.format("High latency detected: %.0fms average", avgLatency))
                    .currentValue(avgLatency)
                    .threshold(2000.0)
                    .triggeredAt(LocalDateTime.now())
                    .acknowledged(false)
                    .build();
                
                alerts.add(alert);
            }
            
            // Check for high failure rate
            Long failureCount = metricRepository.countFailures(
                endpoint,
                LocalDateTime.now().minusHours(1),
                LocalDateTime.now()
            );
            
            if (failureCount != null && failureCount > 10) {
                QualityAlert alert = QualityAlert.builder()
                    .id(UUID.randomUUID().toString())
                    .endpoint(endpoint)
                    .alertType("failure_rate")
                    .severity("critical")
                    .message(String.format("High failure rate: %d failures in last hour", failureCount))
                    .currentValue((double) failureCount)
                    .threshold(10.0)
                    .triggeredAt(LocalDateTime.now())
                    .acknowledged(false)
                    .build();
                
                alerts.add(alert);
            }
        }
        
        return alerts;
    }
    
    /**
     * Update baseline for an endpoint
     * Call this after model improvements or when resetting baseline
     */
    public void updateBaseline(String endpoint, QualityBaseline baseline) {
        baselines.put(endpoint, baseline);
        log.info("Updated baseline for endpoint={}: mean={}, stdDev={}, samples={}",
                 endpoint, baseline.getMeanQuality(), baseline.getStdDeviation(), baseline.getSampleSize());
    }
    
    /**
     * Compute baseline from historical data (last 7 days)
     */
    private QualityBaseline computeBaselineFromHistory(String endpoint) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime sevenDaysAgo = now.minusDays(7);
        LocalDateTime oneDayAgo = now.minusDays(1);  // Exclude last 24h
        
        List<LlmCallMetric> historicalMetrics = metricRepository
            .findByEndpointAndCreatedAtBetween(endpoint, sevenDaysAgo, oneDayAgo);
        
        double[] scores = historicalMetrics.stream()
            .filter(m -> m.getQualityScore() != null)
            .mapToDouble(LlmCallMetric::getQualityScore)
            .toArray();
        
        if (scores.length < MIN_SAMPLE_SIZE) {
            return null;
        }
        
        return computeBaseline(endpoint, scores, sevenDaysAgo, oneDayAgo);
    }
    
    /**
     * Compute baseline from quality scores
     */
    private QualityBaseline computeBaseline(String endpoint, double[] scores, 
                                           LocalDateTime periodStart, LocalDateTime periodEnd) {
        double mean = Arrays.stream(scores).average().orElse(0.0);
        double stdDev = calculateStdDev(scores, mean);
        
        return QualityBaseline.builder()
            .endpoint(endpoint)
            .meanQuality(mean)
            .stdDeviation(stdDev)
            .sampleSize(scores.length)
            .computedAt(LocalDateTime.now())
            .periodStart(periodStart)
            .periodEnd(periodEnd)
            .build();
    }
    
    /**
     * Perform Welch's t-test
     * Tests if two samples have significantly different means
     */
    private TTestResult welchTTest(double mean1, double stdDev1, int n1,
                                   double mean2, double stdDev2, int n2) {
        // Welch's t-statistic
        double variance1 = stdDev1 * stdDev1;
        double variance2 = stdDev2 * stdDev2;
        
        double tStatistic = (mean1 - mean2) / Math.sqrt((variance1 / n1) + (variance2 / n2));
        
        // Welch-Satterthwaite degrees of freedom
        double numerator = Math.pow((variance1 / n1) + (variance2 / n2), 2);
        double denominator = Math.pow(variance1 / n1, 2) / (n1 - 1) + 
                            Math.pow(variance2 / n2, 2) / (n2 - 1);
        double df = numerator / denominator;
        
        // Approximate p-value using t-distribution
        // For simplicity, use normal approximation for large samples
        double pValue = 2 * (1 - normalCDF(Math.abs(tStatistic)));
        
        return new TTestResult(tStatistic, pValue, (int) Math.round(df));
    }
    
    /**
     * Calculate standard deviation
     */
    private double calculateStdDev(double[] values, double mean) {
        if (values.length <= 1) {
            return 0.0;
        }
        
        double sumSquaredDiff = Arrays.stream(values)
            .map(v -> Math.pow(v - mean, 2))
            .sum();
        
        return Math.sqrt(sumSquaredDiff / (values.length - 1));
    }
    
    /**
     * Normal cumulative distribution function
     * Approximation for p-value calculation
     */
    private double normalCDF(double z) {
        // Abramowitz and Stegun approximation
        double t = 1.0 / (1.0 + 0.2316419 * Math.abs(z));
        double d = 0.3989423 * Math.exp(-z * z / 2);
        double p = d * t * (0.3193815 + t * (-0.3565638 + t * (1.781478 + t * (-1.821256 + t * 1.330274))));
        
        return z > 0 ? 1 - p : p;
    }
    
    private DriftReport buildInsufficientDataReport(String endpoint, LocalDateTime start, LocalDateTime end) {
        return DriftReport.builder()
            .endpoint(endpoint)
            .driftDetected(false)
            .detectedAt(LocalDateTime.now())
            .windowStart(start)
            .windowEnd(end)
            .build();
    }
    
    private DriftReport buildNoBaselineReport(String endpoint, QualityBaseline baseline, 
                                             LocalDateTime start, LocalDateTime end) {
        return DriftReport.builder()
            .endpoint(endpoint)
            .baseline(baseline)
            .currentMean(baseline.getMeanQuality())
            .currentStdDev(baseline.getStdDeviation())
            .currentSampleSize(baseline.getSampleSize())
            .driftDetected(false)
            .detectedAt(LocalDateTime.now())
            .windowStart(start)
            .windowEnd(end)
            .build();
    }
    
    /**
     * T-test result
     */
    private static class TTestResult {
        double tStatistic;
        double pValue;
        int degreesOfFreedom;
        
        TTestResult(double tStatistic, double pValue, int df) {
            this.tStatistic = tStatistic;
            this.pValue = pValue;
            this.degreesOfFreedom = df;
        }
    }
}
