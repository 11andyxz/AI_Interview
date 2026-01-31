package com.aiinterview.monitoring;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.util.*;

/**
 * Monitoring controller for ML metrics dashboard
 * Returns real-time metrics from database
 */
@RestController
@RequestMapping("/api/monitoring")
public class MonitoringController {
    
    @Autowired
    private MLMetricsCollector metricsCollector;
    
    @Autowired
    private DriftDetectionService driftDetectionService;
    
    @Autowired
    private AlertService alertService;
    
    @Autowired(required = false)
    private SemanticDriftDetector semanticDriftDetector;
    
    /**
     * Get aggregated metrics for dashboard (last 60 minutes)
     */
    @GetMapping("/metrics")
    public Map<String, Object> getMetrics(@RequestParam(defaultValue = "60") int minutes) {
        return metricsCollector.getAggregatedMetrics(minutes);
    }
    
    /**
     * Get drift detection status
     */
    @GetMapping("/drift")
    public Map<String, Object> getDriftStatus() {
        return driftDetectionService.runDriftCheck();
    }
    
    /**
     * Get recent alerts
     */
    @GetMapping("/alerts")
    public List<Map<String, Object>> getAlerts(@RequestParam(defaultValue = "24") int hours) {
        return alertService.getRecentAlerts(hours);
    }
    
    /**
     * Health check endpoint
     */
    @GetMapping("/health")
    public Map<String, String> getHealth() {
        Map<String, String> health = new HashMap<>();
        health.put("status", "UP");
        health.put("service", "ml-observability");
        return health;
    }
    
    /**
     * Manual test endpoint to record sample metrics
     */
    @PostMapping("/test-record")
    public Map<String, String> testRecordMetrics() {
        metricsCollector.recordRequest("/api/ai/test", "gpt-4o-mini", 1500, 500, true);
        metricsCollector.recordValidation("/api/ai/test", "gpt-4o-mini", true);
        metricsCollector.recordQualityScore("/api/ai/test", "gpt-4o-mini", 0.92);
        
        Map<String, String> response = new HashMap<>();
        response.put("status", "success");
        response.put("message", "Test metrics recorded");
        return response;
    }
    
    /**
     * Get semantic drift analysis
     */
    @GetMapping("/semantic-drift")
    public Map<String, Object> getSemanticDrift() {
        if (semanticDriftDetector == null) {
            Map<String, Object> error = new HashMap<>();
            error.put("status", "unavailable");
            error.put("message", "Semantic drift detector not initialized");
            return error;
        }
        return semanticDriftDetector.runSemanticDriftCheck();
    }
}
