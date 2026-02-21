package com.aiinterview.ml.observability;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Overall ML system health summary
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MlHealthSummary {
    
    private String status;  // healthy, degraded, critical
    
    private LocalDateTime timestamp;
    
    private Map<String, EndpointHealth> endpointHealth;
    
    private Integer activeAlerts;
    
    private Integer criticalAlerts;
    
    private Double overallQualityScore;
    
    private Long avgLatencyMs;
    
    private Double totalCostToday;
    
    private Integer totalCallsToday;
    
    private Double successRate;
    
    private List<QualityAlert> recentAlerts;
    
    /**
     * Endpoint-specific health
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EndpointHealth {
        private String endpoint;
        private String status;
        private Double qualityScore;
        private Long avgLatency;
        private Double successRate;
        private Integer callCount;
        private boolean driftDetected;
    }
    
    /**
     * Check if system is healthy
     */
    public boolean isHealthy() {
        return "healthy".equalsIgnoreCase(status);
    }
    
    /**
     * Check if immediate action needed
     */
    public boolean needsAttention() {
        return criticalAlerts != null && criticalAlerts > 0;
    }
}
