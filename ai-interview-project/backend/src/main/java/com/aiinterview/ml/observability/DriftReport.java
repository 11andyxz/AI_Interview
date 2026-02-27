package com.aiinterview.ml.observability;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Report of quality drift detection
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DriftReport {
    
    private String endpoint;
    
    private QualityBaseline baseline;
    
    private Double currentMean;
    
    private Double currentStdDev;
    
    private Integer currentSampleSize;
    
    private Double pValue;
    
    private Double tStatistic;
    
    private boolean driftDetected;
    
    private LocalDateTime detectedAt;
    
    private LocalDateTime windowStart;
    
    private LocalDateTime windowEnd;
    
    /**
     * Quality change percentage
     */
    public double getQualityChange() {
        if (baseline == null || baseline.getMeanQuality() == null) {
            return 0.0;
        }
        return ((currentMean - baseline.getMeanQuality()) / baseline.getMeanQuality()) * 100;
    }
    
    /**
     * Get drift severity: critical, warning, normal
     */
    public String getSeverity() {
        if (!driftDetected) {
            return "normal";
        }
        if (pValue < 0.01 || Math.abs(getQualityChange()) > 20) {
            return "critical";
        }
        return "warning";
    }
}
