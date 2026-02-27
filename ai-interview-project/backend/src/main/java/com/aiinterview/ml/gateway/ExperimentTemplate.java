package com.aiinterview.ml.gateway;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Experiment template for quick experiment creation
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExperimentTemplate {
    
    /**
     * Template type: prompt_variant, model_comparison, parameter_tuning, rag_optimization
     */
    private String templateType;
    
    /**
     * Experiment name
     */
    private String name;
    
    /**
     * Target endpoint: question_generation, answer_evaluation, etc.
     */
    private String targetEndpoint;
    
    /**
     * Baseline configuration
     */
    private Map<String, Object> baselineConfig;
    
    /**
     * Variant configuration
     */
    private Map<String, Object> variantConfig;
    
    /**
     * Traffic split ratio (variant proportion, 0.0-1.0)
     */
    private Double trafficSplit;
    
    /**
     * Minimum sample size
     */
    private Integer minSampleSize;
    
    /**
     * Auto rollback threshold (p-value)
     */
    private Double rollbackPValue;
    
    /**
     * Additional metadata
     */
    private Map<String, Object> metadata;
}
