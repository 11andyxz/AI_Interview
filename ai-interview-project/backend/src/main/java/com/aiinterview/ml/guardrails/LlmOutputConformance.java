package com.aiinterview.ml.guardrails;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * LLM output conformance tracking entity
 * Records validation results, repair attempts, and latency
 */
@Entity
@Table(name = "llm_output_conformance")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LlmOutputConformance {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false, length = 100)
    private String endpoint;
    
    @Column(nullable = false, length = 50)
    private String model;
    
    @Column(length = 20)
    private String promptVersion;
    
    @Column(nullable = false)
    private Boolean initialValid;
    
    @Column(nullable = false)
    @Builder.Default
    private Integer repairAttempts = 0;
    
    @Column(nullable = false)
    private Boolean finalValid;
    
    @Column(nullable = false)
    @Builder.Default
    private Boolean usedFallback = false;
    
    @Column(columnDefinition = "TEXT")
    private String validationErrors;
    
    @Column
    private Long totalLatencyMs;
    
    @Column(nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
    
    /**
     * Create from StructuredOutput
     */
    public static LlmOutputConformance fromStructuredOutput(
            StructuredOutput<?> output, 
            String endpoint) {
        
        return LlmOutputConformance.builder()
            .endpoint(endpoint)
            .model(output.getModel())
            .promptVersion(output.getPromptVersion())
            .initialValid(output.isInitialValid())
            .repairAttempts(output.getRepairAttempts())
            .finalValid(output.isFinalValid())
            .usedFallback(output.isUsedFallback())
            .validationErrors(output.getValidationErrors() != null ? 
                             String.join("; ", output.getValidationErrors()) : null)
            .totalLatencyMs(output.getTotalLatencyMs())
            .build();
    }
}
