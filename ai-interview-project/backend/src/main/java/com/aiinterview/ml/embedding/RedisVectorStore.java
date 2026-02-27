package com.aiinterview.ml.embedding;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Redis-based vector store implementation
 * 
 * Note: This is a simplified implementation using Redis hashes.
 * For production with Redis Stack, use RediSearch vector similarity features.
 * 
 * Redis Stack setup:
 * - FT.CREATE idx:interview_knowledge ON HASH PREFIX 1 knowledge:
 *   SCHEMA content TEXT embedding VECTOR FLAT 6 DIM 1536 DISTANCE_METRIC COSINE
 * 
 * Only activated when redis.enabled=true in application.properties
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "redis.enabled", havingValue = "true", matchIfMissing = false)
public class RedisVectorStore implements VectorStore {
    
    private static final String KEY_PREFIX = "knowledge:";
    private static final int EMBEDDING_DIMENSION = 1536;
    
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    
    @Autowired
    private EmbeddingService embeddingService;
    
    @Override
    public void upsert(String id, float[] embedding, Map<String, Object> metadata) {
        if (id == null || embedding == null) {
            log.warn("Cannot upsert: id or embedding is null");
            return;
        }
        
        try {
            String key = KEY_PREFIX + id;
            
            Map<String, Object> data = new HashMap<>(metadata != null ? metadata : Map.of());
            data.put("embedding", serializeEmbedding(embedding));
            data.put("updated_at", System.currentTimeMillis());
            
            redisTemplate.opsForHash().putAll(key, data);
            
            log.debug("Upserted vector: {}", id);
        } catch (Exception e) {
            log.error("Failed to upsert vector: {}", id, e);
        }
    }
    
    @Override
    public void batchUpsert(List<VectorEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            return;
        }
        
        for (VectorEntry entry : entries) {
            upsert(entry.id(), entry.embedding(), entry.metadata());
        }
        
        log.info("Batch upserted {} vectors", entries.size());
    }
    
    @Override
    public List<SearchResult> search(float[] queryEmbedding, int topK, Map<String, String> filters) {
        if (queryEmbedding == null) {
            return List.of();
        }
        
        try {
            // In production with Redis Stack, use: FT.SEARCH idx:interview_knowledge
            // "*=>[KNN $K @embedding $BLOB]" PARAMS 4 K topK BLOB <embedding>
            
            // Simplified implementation: scan all keys and compute similarity
            Set<String> keys = redisTemplate.keys(KEY_PREFIX + "*");
            if (keys == null || keys.isEmpty()) {
                return List.of();
            }
            
            List<SearchResult> results = new ArrayList<>();
            
            for (String key : keys) {
                Map<Object, Object> data = redisTemplate.opsForHash().entries(key);
                
                // Apply filters
                if (filters != null && !filters.isEmpty()) {
                    boolean matches = filters.entrySet().stream()
                        .allMatch(filter -> {
                            Object value = data.get(filter.getKey());
                            return value != null && value.toString().equals(filter.getValue());
                        });
                    
                    if (!matches) {
                        continue;
                    }
                }
                
                // Compute similarity
                String embeddingStr = (String) data.get("embedding");
                if (embeddingStr != null) {
                    float[] embedding = deserializeEmbedding(embeddingStr);
                    double similarity = embeddingService.cosineSimilarity(queryEmbedding, embedding);
                    
                    Map<String, String> metadata = data.entrySet().stream()
                        .filter(e -> !e.getKey().equals("embedding"))
                        .collect(Collectors.toMap(
                            e -> e.getKey().toString(),
                            e -> e.getValue() != null ? e.getValue().toString() : ""
                        ));
                    
                    SearchResult result = SearchResult.builder()
                        .id(key.substring(KEY_PREFIX.length()))
                        .score(similarity)
                        .content((String) data.get("content"))
                        .metadata(metadata)
                        .build();
                    
                    results.add(result);
                }
            }
            
            // Sort by similarity (highest first) and return top K
            return results.stream()
                .sorted(Comparator.comparingDouble(SearchResult::getScore).reversed())
                .limit(topK)
                .collect(Collectors.toList());
            
        } catch (Exception e) {
            log.error("Failed to search vectors", e);
            return List.of();
        }
    }
    
    @Override
    public void deleteByFilter(Map<String, String> filters) {
        if (filters == null || filters.isEmpty()) {
            log.warn("deleteByFilter called with empty filters, ignoring");
            return;
        }
        
        try {
            Set<String> keys = redisTemplate.keys(KEY_PREFIX + "*");
            if (keys == null) {
                return;
            }
            
            int deleted = 0;
            for (String key : keys) {
                Map<Object, Object> data = redisTemplate.opsForHash().entries(key);
                
                boolean matches = filters.entrySet().stream()
                    .allMatch(filter -> {
                        Object value = data.get(filter.getKey());
                        return value != null && value.toString().equals(filter.getValue());
                    });
                
                if (matches) {
                    redisTemplate.delete(key);
                    deleted++;
                }
            }
            
            log.info("Deleted {} vectors matching filters", deleted);
        } catch (Exception e) {
            log.error("Failed to delete by filter", e);
        }
    }
    
    @Override
    public void deleteById(String id) {
        try {
            String key = KEY_PREFIX + id;
            redisTemplate.delete(key);
            log.debug("Deleted vector: {}", id);
        } catch (Exception e) {
            log.error("Failed to delete vector: {}", id, e);
        }
    }
    
    @Override
    public long count() {
        try {
            Set<String> keys = redisTemplate.keys(KEY_PREFIX + "*");
            return keys != null ? keys.size() : 0;
        } catch (Exception e) {
            log.error("Failed to count vectors", e);
            return 0;
        }
    }
    
    @Override
    public boolean isHealthy() {
        try {
            // Simple ping test
            String testKey = KEY_PREFIX + "health_check";
            redisTemplate.opsForValue().set(testKey, "ok");
            String value = (String) redisTemplate.opsForValue().get(testKey);
            redisTemplate.delete(testKey);
            return "ok".equals(value);
        } catch (Exception e) {
            log.error("Vector store health check failed", e);
            return false;
        }
    }
    
    /**
     * Serialize embedding to string for Redis storage
     */
    private String serializeEmbedding(float[] embedding) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < embedding.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(embedding[i]);
        }
        return sb.toString();
    }
    
    /**
     * Deserialize embedding from Redis string
     */
    private float[] deserializeEmbedding(String embeddingStr) {
        String[] parts = embeddingStr.split(",");
        float[] embedding = new float[parts.length];
        for (int i = 0; i < parts.length; i++) {
            embedding[i] = Float.parseFloat(parts[i]);
        }
        return embedding;
    }
}
