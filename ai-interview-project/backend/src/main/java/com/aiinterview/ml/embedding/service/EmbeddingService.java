package com.aiinterview.ml.embedding.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Embedding service using OpenAI text-embedding-3-small
 * Provides semantic vector generation with Redis caching
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "ml.embedding.enabled", havingValue = "true", matchIfMissing = false)
public class EmbeddingService {
    
    private final WebClient openAiWebClient;
    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;
    
    @Autowired
    public EmbeddingService(
            WebClient openAiWebClient, 
            @Autowired(required = false) RedisTemplate<String, Object> redisTemplate,
            ObjectMapper objectMapper) {
        this.openAiWebClient = openAiWebClient;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }
    
    @Value("${openai.embedding.model:text-embedding-3-small}")
    private String embeddingModel;
    
    @Value("${openai.embedding.dimensions:1536}")
    private int embeddingDimensions;
    
    @Value("${openai.embedding.batch-size:100}")
    private int batchSize;
    
    @Value("${redis.embedding.ttl-days:7}")
    private int cacheTtlDays;
    
    private static final String CACHE_PREFIX = "embedding:";
    
    /**
     * Generate embedding for single text with caching
     */
    public Mono<double[]> embed(String text) {
        if (text == null || text.trim().isEmpty()) {
            return Mono.just(new double[embeddingDimensions]);
        }
        
        String cacheKey = CACHE_PREFIX + hash(text);
        
        // Try cache first
        double[] cached = getFromCache(cacheKey);
        if (cached != null) {
            log.debug("Cache hit for text: {}...", text.substring(0, Math.min(50, text.length())));
            return Mono.just(cached);
        }
        
        // Generate embedding via API
        return callEmbeddingApi(Collections.singletonList(text))
            .map(embeddings -> {
                double[] embedding = embeddings.get(0);
                saveToCache(cacheKey, embedding);
                return embedding;
            });
    }
    
    /**
     * Generate embeddings for batch of texts
     */
    public Mono<List<double[]>> embedBatch(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return Mono.just(Collections.emptyList());
        }
        
        log.info("Embedding batch of {} texts", texts.size());
        
        // Filter empty texts
        List<String> validTexts = texts.stream()
            .filter(t -> t != null && !t.trim().isEmpty())
            .collect(Collectors.toList());
        
        if (validTexts.isEmpty()) {
            return Mono.just(new ArrayList<>());
        }
        
        // Process in batches
        List<Mono<List<double[]>>> batchMonos = new ArrayList<>();
        for (int i = 0; i < validTexts.size(); i += batchSize) {
            int end = Math.min(i + batchSize, validTexts.size());
            List<String> batch = validTexts.subList(i, end);
            batchMonos.add(processBatch(batch));
        }
        
        // Combine all batches
        return Mono.zip(batchMonos, results -> {
            List<double[]> allEmbeddings = new ArrayList<>();
            for (Object result : results) {
                allEmbeddings.addAll((List<double[]>) result);
            }
            return allEmbeddings;
        });
    }
    
    /**
     * Process single batch with cache lookup
     */
    private Mono<List<double[]>> processBatch(List<String> batch) {
        Map<Integer, double[]> cachedResults = new HashMap<>();
        List<String> uncachedTexts = new ArrayList<>();
        List<Integer> uncachedIndices = new ArrayList<>();
        
        // Check cache for each text
        for (int i = 0; i < batch.size(); i++) {
            String text = batch.get(i);
            String cacheKey = CACHE_PREFIX + hash(text);
            double[] cached = getFromCache(cacheKey);
            
            if (cached != null) {
                cachedResults.put(i, cached);
            } else {
                uncachedTexts.add(text);
                uncachedIndices.add(i);
            }
        }
        
        if (uncachedTexts.isEmpty()) {
            // All cached
            return Mono.just(reconstructList(cachedResults, batch.size()));
        }
        
        // Call API for uncached texts
        return callEmbeddingApi(uncachedTexts)
            .map(embeddings -> {
                // Save to cache
                for (int i = 0; i < uncachedTexts.size(); i++) {
                    String text = uncachedTexts.get(i);
                    double[] embedding = embeddings.get(i);
                    String cacheKey = CACHE_PREFIX + hash(text);
                    saveToCache(cacheKey, embedding);
                    cachedResults.put(uncachedIndices.get(i), embedding);
                }
                
                return reconstructList(cachedResults, batch.size());
            });
    }
    
    /**
     * Call OpenAI embedding API
     */
    private Mono<List<double[]>> callEmbeddingApi(List<String> texts) {
        Map<String, Object> request = new HashMap<>();
        request.put("model", embeddingModel);
        request.put("input", texts);
        request.put("dimensions", embeddingDimensions);
        
        return openAiWebClient.post()
            .uri("/embeddings")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .retrieve()
            .bodyToMono(String.class)
            .timeout(Duration.ofSeconds(30))
            .map(this::parseEmbeddingResponse)
            .doOnError(error -> log.error("Embedding API call failed: {}", error.getMessage()));
    }
    
    /**
     * Parse OpenAI embedding response
     */
    private List<double[]> parseEmbeddingResponse(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode dataArray = root.get("data");
            
            List<double[]> embeddings = new ArrayList<>();
            for (JsonNode item : dataArray) {
                JsonNode embeddingNode = item.get("embedding");
                double[] embedding = new double[embeddingNode.size()];
                for (int i = 0; i < embeddingNode.size(); i++) {
                    embedding[i] = embeddingNode.get(i).asDouble();
                }
                embeddings.add(embedding);
            }
            
            return embeddings;
            
        } catch (Exception e) {
            log.error("Failed to parse embedding response", e);
            throw new RuntimeException("Failed to parse embedding response", e);
        }
    }
    
    /**
     * Compute cosine similarity between two vectors
     */
    public double cosineSimilarity(double[] a, double[] b) {
        if (a == null || b == null || a.length != b.length) {
            throw new IllegalArgumentException("Invalid vectors for cosine similarity");
        }
        
        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;
        
        for (int i = 0; i < a.length; i++) {
            dotProduct += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        
        if (normA == 0.0 || normB == 0.0) {
            return 0.0;
        }
        
        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }
    
    /**
     * Get embedding from Redis cache
     */
    private double[] getFromCache(String key) {
        if (redisTemplate == null) {
            return null; // Redis not configured
        }
        try {
            Object cached = redisTemplate.opsForValue().get(key);
            if (cached instanceof double[]) {
                return (double[]) cached;
            }
            return null;
        } catch (Exception e) {
            log.warn("Redis cache get failed: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * Save embedding to Redis cache
     */
    private void saveToCache(String key, double[] embedding) {
        if (redisTemplate == null) {
            return; // Redis not configured
        }
        try {
            redisTemplate.opsForValue().set(key, embedding, cacheTtlDays, TimeUnit.DAYS);
        } catch (Exception e) {
            log.warn("Redis cache set failed: {}", e.getMessage());
        }
    }
    
    /**
     * Reconstruct ordered list from cached results
     */
    private List<double[]> reconstructList(Map<Integer, double[]> results, int size) {
        List<double[]> list = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            list.add(results.get(i));
        }
        return list;
    }
    
    /**
     * Simple hash function for cache keys
     */
    private String hash(String text) {
        return String.valueOf(text.hashCode());
    }
}
