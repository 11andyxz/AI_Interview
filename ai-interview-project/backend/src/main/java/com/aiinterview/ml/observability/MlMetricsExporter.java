package com.aiinterview.ml.observability;

import io.micrometer.core.instrument.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Exports ML metrics to Prometheus
 * Bridges database metrics to Micrometer/Prometheus format
 * 
 * Exposes 4 custom metrics:
 * - llm_calls_total: Total LLM API calls (Counter)
 * - llm_quality_score: Average quality score (Gauge)
 * - llm_cost_total: Total cost in USD (Counter)
 * - llm_latency_seconds: Request latency distribution (Histogram)
 */
@Slf4j
@Component
public class MlMetricsExporter {
    
    private final MeterRegistry meterRegistry;
    private final LlmCallMetricRepository metricRepository;
    
    // Counters (cumulative)
    private final Map<String, Counter> callCounters = new ConcurrentHashMap<>();
    private final Map<String, Counter> costCounters = new ConcurrentHashMap<>();
    
    // Gauges (current snapshot)
    private final Map<String, AtomicReference<Double>> qualityGauges = new ConcurrentHashMap<>();
    
    // Histograms (distribution)
    private final Map<String, Timer> latencyTimers = new ConcurrentHashMap<>();
    
    @Autowired
    public MlMetricsExporter(
            MeterRegistry meterRegistry,
            LlmCallMetricRepository metricRepository) {
        this.meterRegistry = meterRegistry;
        this.metricRepository = metricRepository;
        
        log.info("MlMetricsExporter initialized - will export ML metrics to Prometheus");
    }
    
    /**
     * Refresh metrics from database every 30 seconds
     * Pulls latest data and updates Prometheus gauges/counters
     */
    @Scheduled(fixedRate = 30000) // 30 seconds
    public void refreshMetrics() {
        try {
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime windowStart = now.minus(Duration.ofHours(24));
            
            // Get recent metrics from database
            List<LlmCallMetric> recentMetrics = metricRepository.findByCreatedAtBetween(windowStart, now);
            
            if (recentMetrics.isEmpty()) {
                log.debug("No metrics to export in last 24 hours");
                return;
            }
            
            // Group by endpoint
            Map<String, List<LlmCallMetric>> byEndpoint = recentMetrics.stream()
                .collect(java.util.stream.Collectors.groupingBy(LlmCallMetric::getEndpoint));
            
            // Update metrics for each endpoint
            byEndpoint.forEach(this::updateEndpointMetrics);
            
            log.debug("Refreshed ML metrics for {} endpoints", byEndpoint.size());
            
        } catch (Exception e) {
            log.error("Failed to refresh ML metrics: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Update all metrics for a specific endpoint
     */
    private void updateEndpointMetrics(String endpoint, List<LlmCallMetric> metrics) {
        if (metrics.isEmpty()) return;
        
        // 1. Call count (Counter)
        Counter callCounter = callCounters.computeIfAbsent(endpoint, ep -> 
            Counter.builder("llm_calls_total")
                .description("Total number of LLM API calls")
                .tag("endpoint", ep)
                .register(meterRegistry)
        );
        
        // Note: Counters in Micrometer can't be set to absolute values
        // We track the increment since last refresh
        // For accurate totals, use database queries
        long currentDbCount = metrics.size();
        log.trace("Endpoint {} has {} calls in window", endpoint, currentDbCount);
        
        // 2. Average quality score (Gauge)
        double avgQuality = metrics.stream()
            .filter(m -> m.getQualityScore() != null)
            .mapToDouble(LlmCallMetric::getQualityScore)
            .average()
            .orElse(0.0);
        
        AtomicReference<Double> qualityRef = qualityGauges.computeIfAbsent(endpoint, ep -> {
            AtomicReference<Double> ref = new AtomicReference<>(0.0);
            Gauge.builder("llm_quality_score", ref, AtomicReference::get)
                .description("Average quality score (0-1)")
                .tag("endpoint", ep)
                .register(meterRegistry);
            return ref;
        });
        qualityRef.set(avgQuality);
        
        // 3. Total cost (Counter via Gauge since we need absolute values)
        double totalCost = metrics.stream()
            .filter(m -> m.getCostUsd() != null)
            .mapToDouble(LlmCallMetric::getCostUsd)
            .sum();
        
        // Use Gauge for cost to show absolute cumulative value
        meterRegistry.gauge("llm_cost_total_usd", 
            Tags.of("endpoint", endpoint), 
            totalCost);
        
        // 4. Latency distribution (Timer/Histogram)
        // Record individual latencies for histogram
        Timer latencyTimer = latencyTimers.computeIfAbsent(endpoint, ep ->
            Timer.builder("llm_latency_seconds")
                .description("LLM request latency distribution")
                .tag("endpoint", ep)
                .publishPercentiles(0.5, 0.95, 0.99) // p50, p95, p99
                .register(meterRegistry)
        );
        
        // Record recent latencies (sample to avoid overload)
        metrics.stream()
            .filter(m -> m.getLatencyMs() != null)
            .limit(100) // Sample recent 100
            .forEach(m -> latencyTimer.record(Duration.ofMillis(m.getLatencyMs())));
    }
    
    /**
     * Export metrics for a single LLM call (called after each call)
     * This provides real-time metric updates
     */
    public void recordLlmCall(LlmCallMetric metric) {
        if (metric == null || metric.getEndpoint() == null) {
            return;
        }
        
        String endpoint = metric.getEndpoint();
        
        try {
            // Increment call counter
            Counter callCounter = callCounters.computeIfAbsent(endpoint, ep -> 
                Counter.builder("llm_calls_total")
                    .description("Total number of LLM API calls")
                    .tag("endpoint", ep)
                    .register(meterRegistry)
            );
            callCounter.increment();
            
            // Record latency
            if (metric.getLatencyMs() != null) {
                Timer latencyTimer = latencyTimers.computeIfAbsent(endpoint, ep ->
                    Timer.builder("llm_latency_seconds")
                        .description("LLM request latency distribution")
                        .tag("endpoint", ep)
                        .publishPercentiles(0.5, 0.95, 0.99)
                        .register(meterRegistry)
                );
                latencyTimer.record(Duration.ofMillis(metric.getLatencyMs()));
            }
            
            // Update quality gauge (with exponential moving average)
            if (metric.getQualityScore() != null) {
                AtomicReference<Double> qualityRef = qualityGauges.computeIfAbsent(endpoint, ep -> {
                    AtomicReference<Double> ref = new AtomicReference<>(0.0);
                    Gauge.builder("llm_quality_score", ref, AtomicReference::get)
                        .description("Average quality score (0-1)")
                        .tag("endpoint", ep)
                        .register(meterRegistry);
                    return ref;
                });
                
                // Exponential moving average: new_avg = 0.9 * old_avg + 0.1 * new_value
                double oldAvg = qualityRef.get();
                double newAvg = 0.9 * oldAvg + 0.1 * metric.getQualityScore();
                qualityRef.set(newAvg);
            }
            
            log.trace("Recorded ML metrics for endpoint: {}", endpoint);
            
        } catch (Exception e) {
            log.error("Failed to record ML metrics for call: {}", e.getMessage());
        }
    }
    
    /**
     * Get current metric values (for debugging/monitoring)
     */
    public Map<String, Object> getCurrentMetrics() {
        Map<String, Object> metrics = new ConcurrentHashMap<>();
        
        // Call counts
        Map<String, Double> callCounts = new ConcurrentHashMap<>();
        callCounters.forEach((endpoint, counter) -> 
            callCounts.put(endpoint, counter.count()));
        metrics.put("call_counts", callCounts);
        
        // Quality scores
        Map<String, Double> qualityScores = new ConcurrentHashMap<>();
        qualityGauges.forEach((endpoint, ref) ->
            qualityScores.put(endpoint, ref.get()));
        metrics.put("quality_scores", qualityScores);
        
        // Latency stats
        Map<String, Map<String, Double>> latencyStats = new ConcurrentHashMap<>();
        latencyTimers.forEach((endpoint, timer) -> {
            Map<String, Double> stats = new ConcurrentHashMap<>();
            stats.put("count", (double) timer.count());
            stats.put("mean", timer.mean(java.util.concurrent.TimeUnit.MILLISECONDS));
            stats.put("max", timer.max(java.util.concurrent.TimeUnit.MILLISECONDS));
            latencyStats.put(endpoint, stats);
        });
        metrics.put("latency_stats", latencyStats);
        
        return metrics;
    }
}
