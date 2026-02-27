package com.aiinterview.ml.observability;

import lombok.*;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Evaluation metric for offline quality assessments
 * Stores results from eval harness and manual reviews
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "evaluation_metrics", indexes = {
    @Index(name = "idx_eval_endpoint_created", columnList = "endpoint,created_at"),
    @Index(name = "idx_eval_created_at", columnList = "created_at"),
    @Index(name = "idx_eval_type", columnList = "evaluation_type")
})
public class EvaluationMetric {
    
    @Id
    private String id;
    
    @Column(nullable = false, length = 100)
    private String endpoint;
    
    @Column(nullable = false, length = 50)
    private String evaluationType;  // harness, manual, golden_set
    
    @Column(nullable = false, length = 100)
    private String model;
    
    @Column(columnDefinition = "TEXT")
    private String inputData;
    
    @Column(columnDefinition = "TEXT")
    private String outputData;
    
    @Column(columnDefinition = "TEXT")
    private String expectedOutput;
    
    private Double qualityScore;
    
    private Boolean passed;
    
    @Column(length = 50)
    private String validatorName;
    
    @Column(columnDefinition = "TEXT")
    private String errorMessage;
    
    @Column(columnDefinition = "JSON")
    private String metadata;
    
    private String promptVersion;
    
    private Double temperature;
    
    private Integer maxTokens;
    
    @Column(nullable = false)
    private LocalDateTime createdAt;
    
    private String createdBy;
    
    // Derived fields for analysis
    public boolean isSuccessful() {
        return passed != null && passed;
    }
    
    public String getEvaluationKey() {
        return String.format("%s:%s:%s", endpoint, evaluationType, model);
    }
}
