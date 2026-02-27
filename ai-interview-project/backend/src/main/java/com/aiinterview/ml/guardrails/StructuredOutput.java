package com.aiinterview.ml.guardrails;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Structured output result with repair and validation metadata
 * Tracks conformance, repair attempts, and fallback usage
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StructuredOutput<T> {
    
    /**
     * Parsed result object (null if validation failed)
     */
    private T result;
    
    /**
     * Whether repair loop was invoked
     */
    private boolean usedRepair;
    
    /**
     * Number of repair attempts made
     */
    private int repairAttempts;
    
    /**
     * Whether fallback mechanism was used
     */
    private boolean usedFallback;
    
    /**
     * List of validation errors encountered
     */
    @Builder.Default
    private List<String> validationErrors = new ArrayList<>();
    
    /**
     * Total latency including all repair attempts (milliseconds)
     */
    private long totalLatencyMs;
    
    /**
     * Whether the initial output was valid (before repair)
     */
    private boolean initialValid;
    
    /**
     * Whether the final output is valid
     */
    private boolean finalValid;
    
    /**
     * Raw LLM output (for debugging)
     */
    private String rawOutput;
    
    /**
     * Model used for generation
     */
    private String model;
    
    /**
     * Prompt version used
     */
    private String promptVersion;
    
    /**
     * Create a successful result
     */
    public static <T> StructuredOutput<T> success(T result, long latencyMs, String model, String promptVersion) {
        return StructuredOutput.<T>builder()
            .result(result)
            .usedRepair(false)
            .repairAttempts(0)
            .usedFallback(false)
            .initialValid(true)
            .finalValid(true)
            .totalLatencyMs(latencyMs)
            .model(model)
            .promptVersion(promptVersion)
            .build();
    }
    
    /**
     * Create a failed result (used fallback)
     */
    public static <T> StructuredOutput<T> fallback(T fallbackResult, List<String> errors, 
                                                     long latencyMs, String model, String promptVersion) {
        return StructuredOutput.<T>builder()
            .result(fallbackResult)
            .usedRepair(false)
            .repairAttempts(0)
            .usedFallback(true)
            .initialValid(false)
            .finalValid(false)
            .validationErrors(errors)
            .totalLatencyMs(latencyMs)
            .model(model)
            .promptVersion(promptVersion)
            .build();
    }
}
