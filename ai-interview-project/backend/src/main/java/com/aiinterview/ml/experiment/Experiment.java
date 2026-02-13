package com.aiinterview.ml.experiment;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * A/B experiment entity for testing prompts, models, and parameters
 */
@Entity
@Table(name = "ml_experiments")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Experiment {
    
    @Id
    @Column(length = 64)
    private String id;
    
    @Column(nullable = false)
    private String name;
    
    /**
     * Experiment type: prompt_variant, model_comparison, parameter_tuning
     */
    @Column(nullable = false, length = 50)
    private String type;
    
    /**
     * Status: draft, active, completed, rolled_back
     */
    @Column(nullable = false, length = 20)
    private String status;
    
    /**
     * Baseline configuration (JSON)
     */
    @Column(columnDefinition = "TEXT")
    private String baselineConfig;
    
    /**
     * Variant configuration (JSON)
     */
    @Column(columnDefinition = "TEXT")
    private String variantConfig;
    
    /**
     * Traffic split (0.0 to 1.0 for variant, rest goes to baseline)
     * Example: 0.5 means 50% variant, 50% baseline
     */
    @Column(nullable = false)
    private Double trafficSplit;
    
    /**
     * Minimum sample size per variant before evaluation
     */
    @Column(nullable = false)
    private Integer minSampleSize;
    
    /**
     * Statistical significance threshold (p-value)
     */
    @Column(nullable = false)
    private Double significanceThreshold;
    
    /**
     * Primary metric to optimize (quality_score, latency_ms, cost_usd)
     */
    @Column(length = 50)
    private String primaryMetric;
    
    /**
     * Description/hypothesis
     */
    @Column(columnDefinition = "TEXT")
    private String description;
    
    /**
     * Experiment start time
     */
    private LocalDateTime startedAt;
    
    /**
     * Experiment completion time
     */
    private LocalDateTime completedAt;
    
    /**
     * Winning variant (baseline or variant)
     */
    @Column(length = 20)
    private String winner;
    
    /**
     * Result summary (JSON)
     */
    @Column(columnDefinition = "TEXT")
    private String resultSummary;
    
    /**
     * Created timestamp
     */
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    /**
     * Updated timestamp
     */
    private LocalDateTime updatedAt;
    
    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        updatedAt = LocalDateTime.now();
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
