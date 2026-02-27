package com.aiinterview.ml.gateway;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * LLM routing decision including experiment assignment
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LlmRouteDecision {
    
    private String experimentId;
    private String variant;
    private String model;
    private String promptTemplate;
    private boolean useRag;
    private int ragTopK;
    private double temperature;
    private int maxTokens;
    private String promptVersion;
    private boolean isExperiment;
}
