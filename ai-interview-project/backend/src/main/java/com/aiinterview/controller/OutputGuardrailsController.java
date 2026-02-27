package com.aiinterview.controller;

import com.aiinterview.ml.guardrails.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * REST API for structured output guardrails
 * Provides conformance metrics and monitoring endpoints
 */
@Slf4j
@RestController
@RequestMapping("/api/guardrails")
@RequiredArgsConstructor
public class OutputGuardrailsController {
    
    private final LlmOutputConformanceRepository conformanceRepository;
    private final OutputConformanceMonitor conformanceMonitor;
    
    /**
     * Track conformance record
     */
    @PostMapping("/conformance")
    public ResponseEntity<LlmOutputConformance> trackConformance(
            @RequestBody TrackConformanceRequest request) {
        
        log.info("Tracking conformance: endpoint={}, initialValid={}", 
                 request.getEndpoint(), request.getInitialValid());
        
        LlmOutputConformance record = LlmOutputConformance.builder()
            .endpoint(request.getEndpoint())
            .model(request.getModel())
            .promptVersion(request.getPromptVersion())
            .initialValid(request.getInitialValid())
            .repairAttempts(request.getRepairAttempts())
            .finalValid(request.getFinalValid())
            .usedFallback(request.getUsedFallback())
            .validationErrors(request.getValidationErrors())
            .totalLatencyMs(request.getTotalLatencyMs())
            .build();
        
        LlmOutputConformance saved = conformanceRepository.save(record);
        
        return ResponseEntity.ok(saved);
    }
    
    /**
     * Track from StructuredOutput
     */
    @PostMapping("/conformance/from-output")
    public ResponseEntity<LlmOutputConformance> trackFromOutput(
            @RequestParam String endpoint,
            @RequestBody StructuredOutput<?> output) {
        
        LlmOutputConformance record = LlmOutputConformance.fromStructuredOutput(output, endpoint);
        LlmOutputConformance saved = conformanceRepository.save(record);
        
        return ResponseEntity.ok(saved);
    }
    
    /**
     * Get conformance metrics for endpoint
     */
    @GetMapping("/metrics/{endpoint}")
    public ResponseEntity<OutputConformanceMonitor.ConformanceMetrics> getMetrics(
            @PathVariable String endpoint,
            @RequestParam(defaultValue = "60") int minutes) {
        
        LocalDateTime since = LocalDateTime.now().minusMinutes(minutes);
        OutputConformanceMonitor.ConformanceMetrics metrics = 
            conformanceMonitor.calculateMetrics(endpoint, since);
        
        return ResponseEntity.ok(metrics);
    }
    
    /**
     * Get recent conformance records
     */
    @GetMapping("/conformance/{endpoint}")
    public ResponseEntity<List<LlmOutputConformance>> getRecentConformance(
            @PathVariable String endpoint,
            @RequestParam(defaultValue = "60") int minutes) {
        
        LocalDateTime since = LocalDateTime.now().minusMinutes(minutes);
        List<LlmOutputConformance> records = 
            conformanceRepository.findRecentByEndpoint(endpoint, since);
        
        return ResponseEntity.ok(records);
    }
    
    /**
     * Get conformance summary for all endpoints
     */
    @GetMapping("/summary")
    public ResponseEntity<Map<String, OutputConformanceMonitor.ConformanceMetrics>> getSummary(
            @RequestParam(defaultValue = "60") int minutes) {
        
        LocalDateTime since = LocalDateTime.now().minusMinutes(minutes);
        
        List<String> endpoints = conformanceRepository.findRecentByEndpoint("", since)
            .stream()
            .map(LlmOutputConformance::getEndpoint)
            .distinct()
            .toList();
        
        Map<String, OutputConformanceMonitor.ConformanceMetrics> summary = new HashMap<>();
        
        for (String endpoint : endpoints) {
            OutputConformanceMonitor.ConformanceMetrics metrics = 
                conformanceMonitor.calculateMetrics(endpoint, since);
            summary.put(endpoint, metrics);
        }
        
        return ResponseEntity.ok(summary);
    }
    
    /**
     * Get conformance statistics
     */
    @GetMapping("/stats/{endpoint}")
    public ResponseEntity<ConformanceStats> getStats(
            @PathVariable String endpoint,
            @RequestParam(defaultValue = "1440") int minutes) { // Default 24 hours
        
        LocalDateTime since = LocalDateTime.now().minusMinutes(minutes);
        
        Double firstPassRate = conformanceRepository.calculateFirstPassRate(endpoint, since);
        Double repairSuccessRate = conformanceRepository.calculateRepairSuccessRate(endpoint, since);
        Double fallbackRate = conformanceRepository.calculateFallbackRate(endpoint, since);
        Double avgRepairLatency = conformanceRepository.calculateAverageRepairLatency(endpoint, since);
        
        List<LlmOutputConformance> records = conformanceRepository.findRecentByEndpoint(endpoint, since);
        
        ConformanceStats stats = ConformanceStats.builder()
            .endpoint(endpoint)
            .timeWindowMinutes(minutes)
            .totalRecords(records.size())
            .firstPassRate(firstPassRate != null ? firstPassRate : 0.0)
            .repairSuccessRate(repairSuccessRate != null ? repairSuccessRate : 0.0)
            .fallbackRate(fallbackRate != null ? fallbackRate : 0.0)
            .averageRepairLatencyMs(avgRepairLatency != null ? avgRepairLatency.longValue() : 0L)
            .meetsThresholds(checkThresholds(firstPassRate, repairSuccessRate, fallbackRate, avgRepairLatency))
            .build();
        
        return ResponseEntity.ok(stats);
    }
    
    /**
     * Manual conformance check
     */
    @PostMapping("/check/{endpoint}")
    public ResponseEntity<String> checkConformance(@PathVariable String endpoint) {
        LocalDateTime since = LocalDateTime.now().minusMinutes(15);
        conformanceMonitor.checkEndpointConformance(endpoint, since);
        return ResponseEntity.ok("Conformance check completed for " + endpoint);
    }
    
    /**
     * Check if metrics meet thresholds
     */
    private boolean checkThresholds(Double firstPassRate, Double repairSuccessRate, 
                                    Double fallbackRate, Double avgRepairLatency) {
        if (firstPassRate == null || repairSuccessRate == null || 
            fallbackRate == null || avgRepairLatency == null) {
            return false;
        }
        
        return repairSuccessRate >= 0.80 &&
               fallbackRate < 0.05 &&
               avgRepairLatency < 3000;
    }
    
    /**
     * Request DTO for tracking conformance
     */
    @lombok.Data
    public static class TrackConformanceRequest {
        private String endpoint;
        private String model;
        private String promptVersion;
        private Boolean initialValid;
        private Integer repairAttempts;
        private Boolean finalValid;
        private Boolean usedFallback;
        private String validationErrors;
        private Long totalLatencyMs;
    }
    
    /**
     * Conformance statistics response
     */
    @lombok.Data
    @lombok.Builder
    public static class ConformanceStats {
        private String endpoint;
        private int timeWindowMinutes;
        private int totalRecords;
        private double firstPassRate;
        private double repairSuccessRate;
        private double fallbackRate;
        private long averageRepairLatencyMs;
        private boolean meetsThresholds;
    }
}
