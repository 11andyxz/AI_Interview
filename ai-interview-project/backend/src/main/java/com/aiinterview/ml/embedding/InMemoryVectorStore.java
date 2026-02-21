package com.aiinterview.ml.embedding;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * In-memory vector store implementation for testing/evaluation
 * Used when Redis is not available or redis.enabled=false
 * 
 * This is the default implementation for development and testing.
 * Activated when redis.enabled is not set to true (default).
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "redis.enabled", havingValue = "false", matchIfMissing = true)
public class InMemoryVectorStore implements VectorStore {
    
    private final Map<String, VectorEntry> storage = new ConcurrentHashMap<>();
    
    @Autowired
    private EmbeddingService embeddingService;
    
    public static class VectorEntry {
        public float[] embedding;
        public Map<String, Object> metadata;
        public long updatedAt;
        
        public VectorEntry(float[] embedding, Map<String, Object> metadata) {
            this.embedding = embedding;
            this.metadata = metadata != null ? new HashMap<>(metadata) : new HashMap<>();
            this.updatedAt = System.currentTimeMillis();
        }
    }
    
    @Override
    public void upsert(String id, float[] embedding, Map<String, Object> metadata) {
        if (id == null || embedding == null) {
            log.warn("Cannot upsert: id or embedding is null");
            return;
        }
        
        storage.put(id, new VectorEntry(embedding, metadata));
        log.debug("Upserted vector to memory: {} (total: {})", id, storage.size());
    }
    
    @Override
    public void batchUpsert(List<com.aiinterview.ml.embedding.VectorStore.VectorEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            return;
        }
        
        for (var entry : entries) {
            upsert(entry.id(), entry.embedding(), entry.metadata());
        }
        
        log.info("Batch upserted {} vectors to memory", entries.size());
    }
    
    @Override
    public List<SearchResult> search(float[] queryEmbedding, int topK, Map<String, String> filters) {
        if (queryEmbedding == null) {
            return List.of();
        }
        
        if (storage.isEmpty()) {
            log.warn("Vector store is empty, returning no results");
            return List.of();
        }
        
        try {
            List<SearchResult> results = new ArrayList<>();
            
            for (Map.Entry<String, VectorEntry> entry : storage.entrySet()) {
                VectorEntry vectorEntry = entry.getValue();
                
                // Apply filters
                if (filters != null && !filters.isEmpty()) {
                    boolean matches = filters.entrySet().stream()
                        .allMatch(filter -> {
                            Object value = vectorEntry.metadata.get(filter.getKey());
                            return value != null && value.toString().equals(filter.getValue());
                        });
                    
                    if (!matches) {
                        continue;
                    }
                }
                
                // Compute similarity
                double similarity = embeddingService.cosineSimilarity(queryEmbedding, vectorEntry.embedding);
                
                Map<String, String> metadata = vectorEntry.metadata.entrySet().stream()
                    .collect(Collectors.toMap(
                        e -> e.getKey(),
                        e -> e.getValue() != null ? e.getValue().toString() : ""
                    ));
                
                SearchResult result = SearchResult.builder()
                    .id(entry.getKey())
                    .score(similarity)
                    .content((String) vectorEntry.metadata.get("content"))
                    .metadata(metadata)
                    .build();
                
                results.add(result);
            }
            
            // Sort by similarity and return top K
            return results.stream()
                .sorted(Comparator.comparingDouble(SearchResult::getScore).reversed())
                .limit(topK)
                .collect(Collectors.toList());
            
        } catch (Exception e) {
            log.error("Failed to search vectors in memory", e);
            return List.of();
        }
    }
    
    @Override
    public void deleteByFilter(Map<String, String> filters) {
        if (filters == null || filters.isEmpty()) {
            log.warn("deleteByFilter called with empty filters, ignoring");
            return;
        }
        
        int deleted = 0;
        Iterator<Map.Entry<String, VectorEntry>> iterator = storage.entrySet().iterator();
        
        while (iterator.hasNext()) {
            Map.Entry<String, VectorEntry> entry = iterator.next();
            VectorEntry vectorEntry = entry.getValue();
            
            boolean matches = filters.entrySet().stream()
                .allMatch(filter -> {
                    Object value = vectorEntry.metadata.get(filter.getKey());
                    return value != null && value.toString().equals(filter.getValue());
                });
            
            if (matches) {
                iterator.remove();
                deleted++;
            }
        }
        
        log.info("Deleted {} vectors from memory matching filters", deleted);
    }
    
    @Override
    public void deleteById(String id) {
        storage.remove(id);
        log.debug("Deleted vector from memory: {}", id);
    }
    
    @Override
    public long count() {
        return storage.size();
    }
    
    @Override
    public boolean isHealthy() {
        return true; // In-memory is always healthy
    }
    
    /**
     * Clear all data (for testing)
     */
    public void clear() {
        storage.clear();
        log.info("Cleared in-memory vector store");
    }
}
