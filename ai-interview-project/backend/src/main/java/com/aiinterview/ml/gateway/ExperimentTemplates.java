package com.aiinterview.ml.gateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Pre-defined experiment templates
 * Provides common experiment type templates
 */
@Component
@RequiredArgsConstructor
public class ExperimentTemplates {
    
    private final ObjectMapper objectMapper;
    
    /**
     * Prompt variant comparison template
     * Compare two different prompt versions
     */
    public ExperimentTemplate promptVariantTemplate(
            String promptKey, 
            String baselineVersion, 
            String variantVersion) {
        
        Map<String, Object> baselineConfig = new HashMap<>();
        baselineConfig.put("model", "gpt-4o-mini");
        baselineConfig.put("promptKey", promptKey);
        baselineConfig.put("promptVersion", baselineVersion);
        baselineConfig.put("temperature", 0.7);
        
        Map<String, Object> variantConfig = new HashMap<>();
        variantConfig.put("model", "gpt-4o-mini");
        variantConfig.put("promptKey", promptKey);
        variantConfig.put("promptVersion", variantVersion);
        variantConfig.put("temperature", 0.7);
        
        return ExperimentTemplate.builder()
            .templateType("prompt_variant")
            .name(String.format("Prompt A/B Test: %s vs %s", baselineVersion, variantVersion))
            .targetEndpoint(promptKey)
            .baselineConfig(baselineConfig)
            .variantConfig(variantConfig)
            .trafficSplit(0.5)
            .minSampleSize(100)
            .rollbackPValue(0.05)
            .build();
    }
    
    /**
     * Model comparison template
     * Compare the effectiveness of different models
     */
    public ExperimentTemplate modelComparisonTemplate(
            String endpoint,
            String baselineModel, 
            String variantModel) {
        
        Map<String, Object> baselineConfig = new HashMap<>();
        baselineConfig.put("model", baselineModel);
        baselineConfig.put("temperature", 0.7);
        
        Map<String, Object> variantConfig = new HashMap<>();
        variantConfig.put("model", variantModel);
        variantConfig.put("temperature", 0.7);
        
        return ExperimentTemplate.builder()
            .templateType("model_comparison")
            .name(String.format("Model Comparison: %s vs %s", baselineModel, variantModel))
            .targetEndpoint(endpoint)
            .baselineConfig(baselineConfig)
            .variantConfig(variantConfig)
            .trafficSplit(0.5)
            .minSampleSize(100)
            .rollbackPValue(0.05)
            .build();
    }
    
    /**
     * Temperature parameter tuning template
     * Test the effect of different temperature parameters
     */
    public ExperimentTemplate temperatureTuningTemplate(
            String endpoint,
            double baselineTemp, 
            double variantTemp) {
        
        Map<String, Object> baselineConfig = new HashMap<>();
        baselineConfig.put("model", "gpt-4o-mini");
        baselineConfig.put("temperature", baselineTemp);
        
        Map<String, Object> variantConfig = new HashMap<>();
        variantConfig.put("model", "gpt-4o-mini");
        variantConfig.put("temperature", variantTemp);
        
        return ExperimentTemplate.builder()
            .templateType("parameter_tuning")
            .name(String.format("Temperature Tuning: %.1f vs %.1f", baselineTemp, variantTemp))
            .targetEndpoint(endpoint)
            .baselineConfig(baselineConfig)
            .variantConfig(variantConfig)
            .trafficSplit(0.5)
            .minSampleSize(100)
            .rollbackPValue(0.05)
            .build();
    }
    
    /**
     * RAG optimization template
     * Test the effect of RAG parameters
     */
    public ExperimentTemplate ragOptimizationTemplate(
            String endpoint,
            int baselineTopK,
            int variantTopK) {
        
        Map<String, Object> baselineConfig = new HashMap<>();
        baselineConfig.put("model", "gpt-4o-mini");
        baselineConfig.put("useRag", true);
        baselineConfig.put("ragTopK", baselineTopK);
        baselineConfig.put("temperature", 0.7);
        
        Map<String, Object> variantConfig = new HashMap<>();
        variantConfig.put("model", "gpt-4o-mini");
        variantConfig.put("useRag", true);
        variantConfig.put("ragTopK", variantTopK);
        variantConfig.put("temperature", 0.7);
        
        return ExperimentTemplate.builder()
            .templateType("rag_optimization")
            .name(String.format("RAG TopK Test: %d vs %d", baselineTopK, variantTopK))
            .targetEndpoint(endpoint)
            .baselineConfig(baselineConfig)
            .variantConfig(variantConfig)
            .trafficSplit(0.5)
            .minSampleSize(100)
            .rollbackPValue(0.05)
            .build();
    }
}
