package com.aiinterview.ml.cache;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Semantic caching service using embedding similarity
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SemanticCacheService {
    
    private final EmbeddingService embeddingService;
    private final RedisTemplate<String, Object> redisTemplate;
    
    // In-memory index of embeddings (in production, use vector database like Pinecone or Redis Vector Search)
    private final Map<String, List<CachedAIResponse>> scenarioCache = new ConcurrentHashMap<>();
    
    private static final double SIMILARITY_THRESHOLD = 0.95; // High threshold for semantic matching
    private static final int MAX_CACHE_SIZE_PER_SCENARIO = 1000;
    
    /**
     * Try to find cached response for a prompt using semantic similarity
     * 
     * @param scenario Use case (resume_analysis, question_generation, etc.)
     * @param prompt The input prompt
     * @return Cached response if similarity > threshold, empty otherwise
     */
    public Optional<CachedAIResponse> getCachedResponse(String scenario, String prompt) {
        try {
            // Generate embedding for input prompt
            float[] promptEmbedding = embeddingService.embed(prompt);
            
            // Search for similar cached prompts
            List<CachedAIResponse> candidateCache = scenarioCache.getOrDefault(scenario, new ArrayList<>());
            
            CachedAIResponse bestMatch = null;
            double bestSimilarity = SIMILARITY_THRESHOLD;
            
            for (CachedAIResponse cached : candidateCache) {
                if (!cached.isValid()) {
                    continue; // Skip expired entries
                }
                
                double similarity = embeddingService.cosineSimilarity(
                    promptEmbedding, 
                    cached.getPromptEmbedding()
                );
                
                if (similarity > bestSimilarity) {
                    bestMatch = cached;
                    bestSimilarity = similarity;
                }
            }
            
            if (bestMatch != null) {
                bestMatch.incrementHitCount();
                log.info("Semantic cache HIT for scenario={}, similarity={:.3f}", 
                        scenario, bestSimilarity);
                return Optional.of(bestMatch);
            }
            
            log.debug("Semantic cache MISS for scenario={}", scenario);
            return Optional.empty();
            
        } catch (Exception e) {
            log.error("Error in semantic cache lookup", e);
            return Optional.empty();
        }
    }
    
    /**
     * Cache a new AI response
     * 
     * @param scenario Use case
     * @param prompt Input prompt
     * @param response AI response
     * @param metadata Additional metadata
     * @param ttlHours Time to live in hours
     */
    public void cacheResponse(String scenario, String prompt, String response, 
                             Map<String, Object> metadata, int ttlHours) {
        try {
            float[] embedding = embeddingService.embed(prompt);
            String promptHash = generateHash(prompt);
            
            CachedAIResponse cached = CachedAIResponse.builder()
                .id(UUID.randomUUID().toString())
                .scenario(scenario)
                .promptHash(promptHash)
                .promptEmbedding(embedding)
                .prompt(prompt)
                .response(response)
                .metadata(metadata)
                .cachedAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusHours(ttlHours))
                .hitCount(0)
                .build();
            
            // Add to in-memory cache
            scenarioCache.computeIfAbsent(scenario, k -> new ArrayList<>()).add(cached);
            
            // Enforce cache size limit (simple LRU eviction)
            List<CachedAIResponse> cache = scenarioCache.get(scenario);
            if (cache.size() > MAX_CACHE_SIZE_PER_SCENARIO) {
                cache.sort(Comparator.comparing(CachedAIResponse::getCachedAt));
                cache.remove(0); // Remove oldest
            }
            
            // Also store in Redis for persistence
            String redisKey = "semantic_cache:" + scenario + ":" + promptHash;
            redisTemplate.opsForValue().set(redisKey, cached, Duration.ofHours(ttlHours));
            
            log.debug("Cached response for scenario={}, ttl={}h", scenario, ttlHours);
            
        } catch (Exception e) {
            log.error("Failed to cache response", e);
        }
    }
    
    /**
     * Get cache statistics for a scenario
     */
    public CacheStats getStats(String scenario) {
        List<CachedAIResponse> cache = scenarioCache.getOrDefault(scenario, new ArrayList<>());
        
        int totalEntries = cache.size();
        long validEntries = cache.stream().filter(CachedAIResponse::isValid).count();
        int totalHits = cache.stream().mapToInt(CachedAIResponse::getHitCount).sum();
        
        double avgAge = cache.stream()
            .mapToLong(CachedAIResponse::getAgeInHours)
            .average()
            .orElse(0.0);
        
        return CacheStats.builder()
            .scenario(scenario)
            .totalEntries(totalEntries)
            .validEntries((int) validEntries)
            .totalHits(totalHits)
            .avgAgeHours(avgAge)
            .hitRate(calculateHitRate(scenario))
            .build();
    }
    
    private double calculateHitRate(String scenario) {
        // This would be tracked separately in production
        // For now, return estimated hit rate based on reuse
        List<CachedAIResponse> cache = scenarioCache.getOrDefault(scenario, new ArrayList<>());
        if (cache.isEmpty()) {
            return 0.0;
        }
        
        long itemsWithHits = cache.stream().filter(c -> c.getHitCount() > 0).count();
        return (double) itemsWithHits / cache.size();
    }
    
    /**
     * Clear expired entries from cache
     */
    public int evictExpired(String scenario) {
        List<CachedAIResponse> cache = scenarioCache.get(scenario);
        if (cache == null) {
            return 0;
        }
        
        int initialSize = cache.size();
        cache.removeIf(c -> !c.isValid());
        int removed = initialSize - cache.size();
        
        if (removed > 0) {
            log.info("Evicted {} expired entries from scenario={}", removed, scenario);
        }
        
        return removed;
    }
    
    private String generateHash(String text) {
        return String.valueOf(text.hashCode());
    }
    
    @lombok.Data
    @lombok.Builder
    public static class CacheStats {
        private String scenario;
        private int totalEntries;
        private int validEntries;
        private int totalHits;
        private double avgAgeHours;
        private double hitRate;
    }
}
