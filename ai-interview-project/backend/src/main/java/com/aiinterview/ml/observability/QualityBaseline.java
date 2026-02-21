package com.aiinterview.ml.observability;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Quality baseline for an endpoint
 * Used for drift detection
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QualityBaseline {
    
    private String endpoint;
    
    private Double meanQuality;
    
    private Double stdDeviation;
    
    private Integer sampleSize;
    
    private LocalDateTime computedAt;
    
    private LocalDateTime periodStart;
    
    private LocalDateTime periodEnd;
    
    /**
     * Lower bound (mean - 2*std)
     */
    public double getLowerBound() {
        return meanQuality - (2 * stdDeviation);
    }
    
    /**
     * Upper bound (mean + 2*std)
     */
    public double getUpperBound() {
        return meanQuality + (2 * stdDeviation);
    }
}
