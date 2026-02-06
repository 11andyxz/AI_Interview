package com.aiinterview.ml.cache;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Represents a cached AI response
 */
@Data
@Builder
public class CachedAIResponse {
    private String id;
    private String scenario;  // resume_analysis, question_generation, answer_evaluation
    private String promptHash;
    private float[] promptEmbedding;
    private String prompt;
    private String response;
    private Map<String, Object> metadata;
    private LocalDateTime cachedAt;
    private LocalDateTime expiresAt;
    private int hitCount;
    private String model;
    private int tokenCount;
    private double latencyMs;
    
    /**
     * Check if this cached response is still valid
     */
    public boolean isValid() {
        return expiresAt == null || LocalDateTime.now().isBefore(expiresAt);
    }
    
    /**
     * Increment hit count
     */
    public void incrementHitCount() {
        this.hitCount++;
    }
    
    /**
     * Calculate cache age in hours
     */
    public long getAgeInHours() {
        return java.time.Duration.between(cachedAt, LocalDateTime.now()).toHours();
    }
}
