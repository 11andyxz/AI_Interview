package com.aiinterview.ml.observability;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Quality alert triggered by drift detection or threshold violation
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QualityAlert {
    
    private String id;
    
    private String endpoint;
    
    private String alertType;  // drift, threshold, cost_spike, latency_spike
    
    private String severity;   // critical, warning, info
    
    private String message;
    
    private Double currentValue;
    
    private Double threshold;
    
    private Double pValue;
    
    private LocalDateTime triggeredAt;
    
    private boolean acknowledged;
    
    private LocalDateTime acknowledgedAt;
    
    private String acknowledgedBy;
    
    /**
     * Check if alert is critical
     */
    public boolean isCritical() {
        return "critical".equalsIgnoreCase(severity);
    }
    
    /**
     * Check if alert is recent (within last hour)
     */
    public boolean isRecent() {
        return triggeredAt != null && 
               triggeredAt.isAfter(LocalDateTime.now().minusHours(1));
    }
}
