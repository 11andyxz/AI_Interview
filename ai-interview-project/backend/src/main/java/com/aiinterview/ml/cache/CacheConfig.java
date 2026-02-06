package com.aiinterview.ml.cache;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Configuration for caching system
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
    
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        
        // Use String serializer for keys
        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        
        // Use JSON serializer for values
        GenericJackson2JsonRedisSerializer serializer = new GenericJackson2JsonRedisSerializer();
        template.setValueSerializer(serializer);
        template.setHashValueSerializer(serializer);
        
        template.afterPropertiesSet();
        return template;
    }
    
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
