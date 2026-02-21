package com.aiinterview.ml.observability;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * Quality trend data point
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QualityTrend {
    
    private LocalDate date;
    
    private String endpoint;
    
    private Double avgQuality;
    
    private Double minQuality;
    
    private Double maxQuality;
    
    private Double stdDeviation;
    
    private Integer sampleSize;
    
    private Long avgLatencyMs;
    
    private Double successRate;
    
    private Double dailyCost;
    
    /**
     * Check if quality is stable (low variance)
     */
    public boolean isStable() {
        return stdDeviation != null && stdDeviation < 5.0;
    }
    
    /**
     * Check if quality is good
     */
    public boolean isGoodQuality() {
        return avgQuality != null && avgQuality >= 80.0;
    }
}
