package com.aiinterview.ml.observability;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Collects and stores ML metrics for monitoring and analysis
 * Core service for ML observability system
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MlMetricsCollector {
    
    private final LlmCallMetricRepository metricRepository;
    private final EvaluationMetricRepository evaluationRepository;
    private final MlMetricsExporter metricsExporter;
    
    /**
     * Record a single LLM call metric
     * Called after every OpenAI API request
     */
    @Transactional
    public void recordLlmCall(LlmCallMetric metric) {
        try {
            if (metric.getId() == null) {
                metric.setId(UUID.randomUUID().toString());
            }
            
            metricRepository.save(metric);
            
            // Export to Prometheus
            metricsExporter.recordLlmCall(metric);
            
            log.debug("Recorded LLM call metric: endpoint={}, model={}, latency={}ms, cost=${}, quality={}",
                metric.getEndpoint(), metric.getModel(), metric.getLatencyMs(), 
                metric.getCostUsd(), metric.getQualityScore());
                
        } catch (Exception e) {
            log.error("Failed to record LLM metric: {}", e.getMessage(), e);
            // Don't fail the main request if metric recording fails
        }
    }
    
    /**
     * Record an evaluation metric from offline eval harness
     * Called after running quality validators on test sets
     */
    @Transactional
    public void recordEvaluation(EvaluationMetric metric) {
        try {
            if (metric.getId() == null) {
                metric.setId(UUID.randomUUID().toString());
            }
            
            evaluationRepository.save(metric);
            
            log.debug("Recorded evaluation metric: endpoint={}, type={}, model={}, quality={}, passed={}",
                metric.getEndpoint(), metric.getEvaluationType(), metric.getModel(), 
                metric.getQualityScore(), metric.getPassed());
                
        } catch (Exception e) {
            log.error("Failed to record evaluation metric: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Get overall health summary for recent time window
     */
    public MlHealthSummary getHealthSummary(Duration window) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime windowStart = now.minus(window);
        
        List<LlmCallMetric> metrics = metricRepository.findByCreatedAtBetween(windowStart, now);
        
        if (metrics.isEmpty()) {
            return MlHealthSummary.builder()
                .status("unknown")
                .timestamp(now)
                .endpointHealth(Map.of())
                .activeAlerts(0)
                .criticalAlerts(0)
                .build();
        }
        
        // Calculate overall statistics
        double avgQuality = metrics.stream()
            .filter(m -> m.getQualityScore() != null)
            .mapToDouble(LlmCallMetric::getQualityScore)
            .average()
            .orElse(0.0);
        
        long avgLatency = (long) metrics.stream()
            .filter(m -> m.getLatencyMs() != null)
            .mapToLong(LlmCallMetric::getLatencyMs)
            .average()
            .orElse(0.0);
        
        double totalCost = metrics.stream()
            .filter(m -> m.getCostUsd() != null)
            .mapToDouble(LlmCallMetric::getCostUsd)
            .sum();
        
        long successCount = metrics.stream()
            .filter(LlmCallMetric::isSuccessful)
            .count();
        
        double successRate = (double) successCount / metrics.size();
        
        // Calculate per-endpoint health
        Map<String, List<LlmCallMetric>> byEndpoint = metrics.stream()
            .collect(Collectors.groupingBy(LlmCallMetric::getEndpoint));
        
        Map<String, MlHealthSummary.EndpointHealth> endpointHealth = new HashMap<>();
        
        for (Map.Entry<String, List<LlmCallMetric>> entry : byEndpoint.entrySet()) {
            String endpoint = entry.getKey();
            List<LlmCallMetric> endpointMetrics = entry.getValue();
            
            double epQuality = endpointMetrics.stream()
                .filter(m -> m.getQualityScore() != null)
                .mapToDouble(LlmCallMetric::getQualityScore)
                .average()
                .orElse(0.0);
            
            long epLatency = (long) endpointMetrics.stream()
                .filter(m -> m.getLatencyMs() != null)
                .mapToLong(LlmCallMetric::getLatencyMs)
                .average()
                .orElse(0.0);
            
            long epSuccess =endpointMetrics.stream()
                .filter(LlmCallMetric::isSuccessful)
                .count();
            
            double epSuccessRate = (double) epSuccess / endpointMetrics.size();
            
            String epStatus = determineEndpointStatus(epQuality, epLatency, epSuccessRate);
            
            endpointHealth.put(endpoint, MlHealthSummary.EndpointHealth.builder()
                .endpoint(endpoint)
                .status(epStatus)
                .qualityScore(epQuality)
                .avgLatency(epLatency)
                .successRate(epSuccessRate)
                .callCount(endpointMetrics.size())
                .driftDetected(false)  // Will be updated by drift detector
                .build());
        }
        
        // Determine overall status
        String overallStatus = determineOverallStatus(avgQuality, successRate, endpointHealth);
        
        return MlHealthSummary.builder()
            .status(overallStatus)
            .timestamp(now)
            .endpointHealth(endpointHealth)
            .activeAlerts(0)  // Will be updated by alert system
            .criticalAlerts(0)
            .overallQualityScore(avgQuality)
            .avgLatencyMs(avgLatency)
            .totalCostToday(totalCost)
            .totalCallsToday(metrics.size())
            .successRate(successRate)
            .recentAlerts(List.of())
            .build();
    }
    
    /**
     * Get quality trends over time
     */
    public List<QualityTrend> getQualityTrends(LocalDate from, LocalDate to) {
        LocalDateTime start = from.atStartOfDay();
        LocalDateTime end = to.plusDays(1).atStartOfDay();
        
        List<LlmCallMetric> metrics = metricRepository.findByCreatedAtBetween(start, end);
        
        // Group by date and endpoint
        Map<String, Map<LocalDate, List<LlmCallMetric>>> byEndpointAndDate = metrics.stream()
            .collect(Collectors.groupingBy(
                LlmCallMetric::getEndpoint,
                Collectors.groupingBy(m -> m.getCreatedAt().toLocalDate())
            ));
        
        List<QualityTrend> trends = new ArrayList<>();
        
        for (Map.Entry<String, Map<LocalDate, List<LlmCallMetric>>> epEntry : byEndpointAndDate.entrySet()) {
            String endpoint = epEntry.getKey();
            
            for (Map.Entry<LocalDate, List<LlmCallMetric>> dateEntry : epEntry.getValue().entrySet()) {
                LocalDate date = dateEntry.getKey();
                List<LlmCallMetric> dailyMetrics = dateEntry.getValue();
                
                double[] qualities = dailyMetrics.stream()
                    .filter(m -> m.getQualityScore() != null)
                    .mapToDouble(LlmCallMetric::getQualityScore)
                    .toArray();
                
                if (qualities.length == 0) continue;
                
                double avg = Arrays.stream(qualities).average().orElse(0.0);
                double min = Arrays.stream(qualities).min().orElse(0.0);
                double max = Arrays.stream(qualities).max().orElse(0.0);
                double stdDev = calculateStdDev(qualities, avg);
                
                long avgLatency = (long) dailyMetrics.stream()
                    .filter(m -> m.getLatencyMs() != null)
                    .mapToLong(LlmCallMetric::getLatencyMs)
                    .average()
                    .orElse(0.0);
                
                long successCount = dailyMetrics.stream()
                    .filter(LlmCallMetric::isSuccessful)
                    .count();
                
                double successRate = (double) successCount / dailyMetrics.size();
                
                double cost = dailyMetrics.stream()
                    .filter(m -> m.getCostUsd() != null)
                    .mapToDouble(LlmCallMetric::getCostUsd)
                    .sum();
                
                trends.add(QualityTrend.builder()
                    .date(date)
                    .endpoint(endpoint)
                    .avgQuality(avg)
                    .minQuality(min)
                    .maxQuality(max)
                    .stdDeviation(stdDev)
                    .sampleSize(qualities.length)
                    .avgLatencyMs(avgLatency)
                    .successRate(successRate)
                    .dailyCost(cost)
                    .build());
            }
        }
        
        trends.sort(Comparator.comparing(QualityTrend::getDate)
            .thenComparing(QualityTrend::getEndpoint));
        
        return trends;
    }
    
    /**
     * Get metrics for specific endpoint and time range
     */
    public List<LlmCallMetric> getMetrics(String endpoint, Duration window) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime start = now.minus(window);
        
        if (endpoint != null) {
            return metricRepository.findByEndpointAndCreatedAtBetween(endpoint, start, now);
        } else {
            return metricRepository.findByCreatedAtBetween(start, now);
        }
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
     * Determine endpoint status based on metrics
     */
    private String determineEndpointStatus(double quality, long latency, double successRate) {
        if (quality < 70 || successRate < 0.90 || latency > 3000) {
            return "critical";
        } else if (quality < 80 || successRate < 0.95 || latency > 2000) {
            return "degraded";
        } else {
            return "healthy";
        }
    }
    
    /**
     * Determine overall system status
     */
    private String determineOverallStatus(double avgQuality, double successRate, 
                                         Map<String, MlHealthSummary.EndpointHealth> endpointHealth) {
        long criticalCount = endpointHealth.values().stream()
            .filter(ep -> "critical".equals(ep.getStatus()))
            .count();
        
        if (criticalCount > 0 || avgQuality < 70 || successRate < 0.90) {
            return "critical";
        }
        
        long degradedCount = endpointHealth.values().stream()
            .filter(ep -> "degraded".equals(ep.getStatus()))
            .count();
        
        if (degradedCount > 0 || avgQuality < 80 || successRate < 0.95) {
            return "degraded";
        }
        
        return "healthy";
    }
}
