package com.aiinterview.ml.observability;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Records metrics for every LLM API call
 * Enables cost tracking, quality monitoring, and drift detection
 */
@Entity
@Table(name = "llm_call_metrics", indexes = {
    @Index(name = "idx_endpoint_created", columnList = "endpoint,created_at"),
    @Index(name = "idx_model_created", columnList = "model,created_at"),
    @Index(name = "idx_created_at", columnList = "created_at")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LlmCallMetric {
    
    @Id
    @Column(length = 64)
    private String id;
    
    /**
     * API endpoint: resume_analysis, question_generation, answer_evaluation, etc.
     */
    @Column(nullable = false, length = 100)
    private String endpoint;
    
    /**
     * Model used: gpt-3.5-turbo-1106, gpt-4, etc.
     */
    @Column(nullable = false, length = 50)
    private String model;
    
    /**
     * Input tokens consumed
     */
    @Column(name = "input_tokens")
    private Integer inputTokens;
    
    /**
     * Output tokens generated
     */
    @Column(name = "output_tokens")
    private Integer outputTokens;
    
    /**
     * Cost in USD for this call
     */
    @Column(name = "cost_usd")
    private Double costUsd;
    
    /**
     * End-to-end latency in milliseconds
     */
    @Column(name = "latency_ms")
    private Long latencyMs;
    
    /**
     * Quality score from validator (0-100)
     */
    @Column(name = "quality_score")
    private Double qualityScore;
    
    /**
     * Whether output passed validation
     */
    @Column(name = "validation_passed")
    private Boolean validationPassed;
    
    /**
     * Error type if call failed: timeout, rate_limit, invalid_response, etc.
     */
    @Column(name = "error_type", length = 50)
    private String errorType;
    
    /**
     * Additional context: user_id, session_id, experiment_id, etc.
     */
    @Column(columnDefinition = "TEXT")
    private String metadata;
    
    /**
     * Temperature parameter used
     */
    private Double temperature;
    
    /**
     * Max tokens parameter
     */
    @Column(name = "max_tokens")
    private Integer maxTokens;
    
    /**
     * Prompt version if using version control
     */
    @Column(name = "prompt_version", length = 20)
    private String promptVersion;
    
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    
    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
    
    /**
     * Calculate total tokens
     */
    public int getTotalTokens() {
        return (inputTokens != null ? inputTokens : 0) + 
               (outputTokens != null ? outputTokens : 0);
    }
    
    /**
     * Check if call was successful
     */
    public boolean isSuccessful() {
        return errorType == null || errorType.isEmpty();
    }
    
    /**
     * Calculate cost per 1K tokens
     */
    public double getCostPer1KTokens() {
        int total = getTotalTokens();
        if (total == 0 || costUsd == null) {
            return 0.0;
        }
        return (costUsd / total) * 1000;
    }
}
