package com.aiinterview.ml.cache;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for caching system
 * Note: RedisTemplate bean is defined in com.aiinterview.config.RedisConfig
 */
@Configuration
public class CacheConfig {
    
    @Value("${ml.cache.enabled:true}")
    private boolean cacheEnabled;
    
    @Value("${ml.cache.semantic.enabled:true}")
    private boolean semanticCacheEnabled;
    
    @Value("${ml.cache.semantic.similarity-threshold:0.95}")
    private double semanticSimilarityThreshold;
    
    @Value("${ml.cache.max-size:1000}")
    private int maxCacheSize;
    
    /**
     * Check if caching is enabled
     */
    public boolean isCacheEnabled() {
        return cacheEnabled;
    }
    
    /**
     * Check if semantic caching is enabled
     */
    public boolean isSemanticCacheEnabled() {
        return semanticCacheEnabled;
    }
    
    /**
     * Get semantic similarity threshold
     */
    public double getSemanticSimilarityThreshold() {
        return semanticSimilarityThreshold;
    }
    
    /**
     * Get maximum cache size
     */
    public int getMaxCacheSize() {
        return maxCacheSize;
    }
}
