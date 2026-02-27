package com.aiinterview.ml.guardrails;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Output conformance monitor
 * Tracks metrics and triggers alerts for degradation
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OutputConformanceMonitor {
    
    private final LlmOutputConformanceRepository conformanceRepository;
    
    // Alert thresholds
    private static final double MIN_FIRST_PASS_RATE = 0.50; // 50% minimum
    private static final double MIN_REPAIR_SUCCESS_RATE = 0.80; // 80% minimum
    private static final double MAX_FALLBACK_RATE = 0.05; // 5% maximum
    private static final long MAX_REPAIR_OVERHEAD_MS = 3000; // 3 seconds
    
    private static final int MONITOR_INTERVAL_MINUTES = 15;
    
    // Track last alert time per endpoint to avoid spam
    private final Map<String, LocalDateTime> lastAlertTime = new HashMap<>();
    
    /**
     * Monitor conformance metrics every 15 minutes
     */
    @Scheduled(fixedRate = 900000) // 15 minutes
    public void monitorConformance() {
        log.info("Running conformance monitoring check");
        
        LocalDateTime since = LocalDateTime.now().minusMinutes(MONITOR_INTERVAL_MINUTES);
        
        // Get distinct endpoints
        List<String> endpoints = conformanceRepository.findRecentByEndpoint("", since)
            .stream()
            .map(LlmOutputConformance::getEndpoint)
            .distinct()
            .toList();
        
        for (String endpoint : endpoints) {
            checkEndpointConformance(endpoint, since);
        }
    }
    
    /**
     * Check conformance metrics for specific endpoint
     */
    public void checkEndpointConformance(String endpoint, LocalDateTime since) {
        try {
            ConformanceMetrics metrics = calculateMetrics(endpoint, since);
            
            log.debug("Conformance metrics for {}: firstPass={}, repairSuccess={}, fallback={}, repairLatency={}ms",
                      endpoint, metrics.firstPassRate, metrics.repairSuccessRate, 
                      metrics.fallbackRate, metrics.averageRepairLatency);
            
            // Check for degradation
            checkFirstPassRate(endpoint, metrics);
            checkRepairSuccessRate(endpoint, metrics);
            checkFallbackRate(endpoint, metrics);
            checkRepairLatency(endpoint, metrics);
            
        } catch (Exception e) {
            log.error("Error checking conformance for endpoint: {}", endpoint, e);
        }
    }
    
    /**
     * Calculate conformance metrics
     */
    public ConformanceMetrics calculateMetrics(String endpoint, LocalDateTime since) {
        Double firstPassRate = conformanceRepository.calculateFirstPassRate(endpoint, since);
        Double repairSuccessRate = conformanceRepository.calculateRepairSuccessRate(endpoint, since);
        Double fallbackRate = conformanceRepository.calculateFallbackRate(endpoint, since);
        Double avgRepairLatency = conformanceRepository.calculateAverageRepairLatency(endpoint, since);
        
        List<LlmOutputConformance> recent = conformanceRepository.findRecentByEndpoint(endpoint, since);
        
        return ConformanceMetrics.builder()
            .endpoint(endpoint)
            .firstPassRate(firstPassRate != null ? firstPassRate : 0.0)
            .repairSuccessRate(repairSuccessRate != null ? repairSuccessRate : 0.0)
            .fallbackRate(fallbackRate != null ? fallbackRate : 0.0)
            .averageRepairLatency(avgRepairLatency != null ? avgRepairLatency.longValue() : 0L)
            .sampleSize(recent.size())
            .timeWindowMinutes(MONITOR_INTERVAL_MINUTES)
            .build();
    }
    
    /**
     * Check first-pass validation rate
     */
    private void checkFirstPassRate(String endpoint, ConformanceMetrics metrics) {
        if (metrics.sampleSize < 10) {
            return; // Not enough samples
        }
        
        if (metrics.firstPassRate < MIN_FIRST_PASS_RATE) {
            triggerAlert(endpoint, 
                        "FIRST_PASS_RATE_LOW",
                        String.format("First-pass validation rate %.1f%% below threshold %.1f%%",
                                    metrics.firstPassRate * 100, MIN_FIRST_PASS_RATE * 100));
        }
    }
    
    /**
     * Check repair success rate
     */
    private void checkRepairSuccessRate(String endpoint, ConformanceMetrics metrics) {
        if (metrics.sampleSize < 10) {
            return;
        }
        
        if (metrics.repairSuccessRate < MIN_REPAIR_SUCCESS_RATE && metrics.repairSuccessRate > 0) {
            triggerAlert(endpoint,
                        "REPAIR_SUCCESS_RATE_LOW",
                        String.format("Repair success rate %.1f%% below threshold %.1f%%",
                                    metrics.repairSuccessRate * 100, MIN_REPAIR_SUCCESS_RATE * 100));
        }
    }
    
    /**
     * Check fallback rate
     */
    private void checkFallbackRate(String endpoint, ConformanceMetrics metrics) {
        if (metrics.sampleSize < 10) {
            return;
        }
        
        if (metrics.fallbackRate > MAX_FALLBACK_RATE) {
            triggerAlert(endpoint,
                        "FALLBACK_RATE_HIGH",
                        String.format("Fallback rate %.1f%% exceeds threshold %.1f%%",
                                    metrics.fallbackRate * 100, MAX_FALLBACK_RATE * 100));
        }
    }
    
    /**
     * Check repair latency
     */
    private void checkRepairLatency(String endpoint, ConformanceMetrics metrics) {
        if (metrics.sampleSize < 10) {
            return;
        }
        
        if (metrics.averageRepairLatency > MAX_REPAIR_OVERHEAD_MS) {
            triggerAlert(endpoint,
                        "REPAIR_LATENCY_HIGH",
                        String.format("Average repair latency %dms exceeds threshold %dms",
                                    metrics.averageRepairLatency, MAX_REPAIR_OVERHEAD_MS));
        }
    }
    
    /**
     * Trigger alert (with rate limiting)
     */
    private void triggerAlert(String endpoint, String alertType, String message) {
        String alertKey = endpoint + ":" + alertType;
        LocalDateTime lastAlert = lastAlertTime.get(alertKey);
        
        // Rate limit: only alert once per hour for same issue
        if (lastAlert != null && lastAlert.plusHours(1).isAfter(LocalDateTime.now())) {
            return;
        }
        
        log.error("CONFORMANCE ALERT [{}] {}: {}", alertType, endpoint, message);
        
        // Update last alert time
        lastAlertTime.put(alertKey, LocalDateTime.now());
        
        // In production, integrate with alerting system (PagerDuty, Slack, etc.)
    }
    
    /**
     * Conformance metrics DTO
     */
    @lombok.Data
    @lombok.Builder
    public static class ConformanceMetrics {
        private String endpoint;
        private double firstPassRate;
        private double repairSuccessRate;
        private double fallbackRate;
        private long averageRepairLatency;
        private int sampleSize;
        private int timeWindowMinutes;
    }
}
