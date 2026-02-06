package com.aiinterview.ml.cache;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

/**
 * Main caching service that combines exact match and semantic caching
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ResponseCacheService {
    
    private final RedisTemplate<String, Object> redisTemplate;
    private final SemanticCacheService semanticCacheService;
    
    // Cache TTL configuration by scenario
    private static final Map<String, Integer> TTL_HOURS = Map.of(
        "resume_analysis", 24,        // Resume analysis cache for 24 hours
        "question_generation", 168,   // Question cache for 7 days
        "answer_evaluation", 24,      // Answer evaluation cache for 24 hours
        "resume_parsing", 720         // Resume PDF parsing cache for 30 days
    );
    
    private long totalRequests = 0;
    private long cacheHits = 0;
    private long exactMatchHits = 0;
    private long semanticMatchHits = 0;
    
    /**
     * Get cached response using multi-level caching strategy:
     * 1. Try exact match cache (fastest)
     * 2. Try semantic cache (slower but more flexible)
     * 
     * @param scenario Use case identifier
     * @param prompt Input prompt
     * @return Cached response if found
     */
    public Optional<CachedAIResponse> getCached(String scenario, String prompt) {
        totalRequests++;
        
        // Level 1: Try exact match cache (Redis)
        Optional<CachedAIResponse> exactMatch = getExactMatch(scenario, prompt);
        if (exactMatch.isPresent()) {
            exactMatchHits++;
            cacheHits++;
            log.info("Cache HIT (exact) for scenario={}, hit_rate={:.2f}%", 
                    scenario, getHitRate() * 100);
            return exactMatch;
        }
        
        // Level 2: Try semantic cache (embedding similarity)
        Optional<CachedAIResponse> semanticMatch = semanticCacheService.getCachedResponse(scenario, prompt);
        if (semanticMatch.isPresent()) {
            semanticMatchHits++;
            cacheHits++;
            log.info("Cache HIT (semantic) for scenario={}, hit_rate={:.2f}%", 
                    scenario, getHitRate() * 100);
            return semanticMatch;
        }
        
        log.debug("Cache MISS for scenario={}", scenario);
        return Optional.empty();
    }
    
    /**
     * Cache a new response
     * 
     * @param scenario Use case
     * @param prompt Input prompt
     * @param response AI response
     * @param metadata Additional metadata
     */
    public void cache(String scenario, String prompt, String response, Map<String, Object> metadata) {
        int ttlHours = TTL_HOURS.getOrDefault(scenario, 24);
        
        // Store in exact match cache (Redis)
        cacheExactMatch(scenario, prompt, response, metadata, ttlHours);
        
        // Store in semantic cache (for similarity matching)
        semanticCacheService.cacheResponse(scenario, prompt, response, metadata, ttlHours);
        
        log.debug("Cached response for scenario={}, ttl={}h", scenario, ttlHours);
    }
    
    /**
     * Get exact match from Redis cache
     */
    private Optional<CachedAIResponse> getExactMatch(String scenario, String prompt) {
        try {
            String cacheKey = generateExactMatchKey(scenario, prompt);
            Object cached = redisTemplate.opsForValue().get(cacheKey);
            
            if (cached instanceof CachedAIResponse response) {
                if (response.isValid()) {
                    response.incrementHitCount();
                    return Optional.of(response);
                }
            }
            
            return Optional.empty();
        } catch (Exception e) {
            log.error("Redis cache lookup failed", e);
            return Optional.empty();
        }
    }
    
    /**
     * Store in exact match cache (Redis)
     */
    private void cacheExactMatch(String scenario, String prompt, String response, 
                                 Map<String, Object> metadata, int ttlHours) {
        try {
            String cacheKey = generateExactMatchKey(scenario, prompt);
            
            CachedAIResponse cached = CachedAIResponse.builder()
                .id(cacheKey)
                .scenario(scenario)
                .promptHash(String.valueOf(prompt.hashCode()))
                .prompt(prompt)
                .response(response)
                .metadata(metadata)
                .cachedAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusHours(ttlHours))
                .hitCount(0)
                .build();
            
            redisTemplate.opsForValue().set(cacheKey, cached, Duration.ofHours(ttlHours));
            
        } catch (Exception e) {
            log.error("Failed to cache in Redis", e);
        }
    }
    
    /**
     * Invalidate cached response
     */
    public void invalidate(String scenario, String prompt) {
        try {
            String cacheKey = generateExactMatchKey(scenario, prompt);
            redisTemplate.delete(cacheKey);
            log.debug("Invalidated cache for scenario={}", scenario);
        } catch (Exception e) {
            log.error("Failed to invalidate cache", e);
        }
    }
    
    /**
     * Invalidate all cache entries for a scenario
     */
    public void invalidateScenario(String scenario) {
        try {
            String pattern = "cache:" + scenario + ":*";
            var keys = redisTemplate.keys(pattern);
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
                log.info("Invalidated {} cache entries for scenario={}", keys.size(), scenario);
            }
        } catch (Exception e) {
            log.error("Failed to invalidate scenario cache", e);
        }
    }
    
    /**
     * Get cache statistics
     */
    public CacheStatistics getStatistics() {
        return CacheStatistics.builder()
            .totalRequests(totalRequests)
            .cacheHits(cacheHits)
            .exactMatchHits(exactMatchHits)
            .semanticMatchHits(semanticMatchHits)
            .hitRate(getHitRate())
            .exactMatchRate(totalRequests > 0 ? (double) exactMatchHits / totalRequests : 0.0)
            .semanticMatchRate(totalRequests > 0 ? (double) semanticMatchHits / totalRequests : 0.0)
            .build();
    }
    
    /**
     * Reset statistics (useful for testing)
     */
    public void resetStatistics() {
        totalRequests = 0;
        cacheHits = 0;
        exactMatchHits = 0;
        semanticMatchHits = 0;
        log.info("Cache statistics reset");
    }
    
    private double getHitRate() {
        return totalRequests > 0 ? (double) cacheHits / totalRequests : 0.0;
    }
    
    private String generateExactMatchKey(String scenario, String prompt) {
        return "cache:" + scenario + ":" + Math.abs(prompt.hashCode());
    }
    
    @lombok.Data
    @lombok.Builder
    public static class CacheStatistics {
        private long totalRequests;
        private long cacheHits;
        private long exactMatchHits;
        private long semanticMatchHits;
        private double hitRate;
        private double exactMatchRate;
        private double semanticMatchRate;
    }
}
