package com.aiinterview.ml.gateway;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * LLM configuration resolved from experiment or defaults
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LlmConfig {
    
    private String model;
    private String promptKey;
    private String promptVersion;
    private Boolean useRag;
    private Integer ragTopK;
    private Double temperature;
    private Integer maxTokens;
    
    /**
     * Create default configuration
     */
    public static LlmConfig defaultConfig() {
        return LlmConfig.builder()
            .model("gpt-4o-mini")
            .useRag(false)
            .ragTopK(5)
            .temperature(0.7)
            .maxTokens(1000)
            .build();
    }
}
