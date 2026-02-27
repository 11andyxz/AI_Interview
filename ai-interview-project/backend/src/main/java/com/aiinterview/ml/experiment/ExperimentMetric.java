package com.aiinterview.ml.experiment;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Metric data point for experiment evaluation
 */
@Entity
@Table(name = "experiment_metrics", indexes = {
    @Index(name = "idx_experiment_variant", columnList = "experimentId,variant"),
    @Index(name = "idx_created_at", columnList = "createdAt")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExperimentMetric {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    /**
     * Experiment ID
     */
    @Column(nullable = false, length = 64)
    private String experimentId;
    
    /**
     * Request/session ID
     */
    @Column(nullable = false, length = 64)
    private String requestId;
    
    /**
     * Variant: baseline or variant
     */
    @Column(nullable = false, length = 20)
    private String variant;
    
    /**
     * Quality score (0.0 to 10.0)
     */
    private Double qualityScore;
    
    /**
     * Latency in milliseconds
     */
    private Double latencyMs;
    
    /**
     * Total tokens used
     */
    private Integer tokensUsed;
    
    /**
     * Cost in USD
     */
    private Double costUsd;
    
    /**
     * Validation passed (e.g., passed quality checks)
     */
    private Boolean validationPass;
    
    /**
     * Additional metadata (JSON)
     */
    @Column(columnDefinition = "TEXT")
    private String metadata;
    
    /**
     * Timestamp
     */
    @Column(nullable = false)
    private LocalDateTime createdAt;
    
    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
