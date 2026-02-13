package com.aiinterview.ml.embedding;

import java.util.List;
import java.util.Map;

/**
 * Interface for vector storage and similarity search
 * Implementations: Redis Stack, Pinecone, Weaviate, etc.
 */
public interface VectorStore {
    
    /**
     * Insert or update a vector with metadata
     * 
     * @param id Unique document ID
     * @param embedding Vector embedding
     * @param metadata Associated metadata (category, role, difficulty, etc.)
     */
    void upsert(String id, float[] embedding, Map<String, Object> metadata);
    
    /**
     * Batch upsert for efficiency
     * 
     * @param entries List of entries to upsert
     */
    void batchUpsert(List<VectorEntry> entries);
    
    /**
     * Search for similar vectors
     * 
     * @param queryEmbedding Query vector
     * @param topK Number of results to return
     * @param filters Metadata filters (e.g., category=question, role=backend_java)
     * @return List of search results sorted by similarity (highest first)
     */
    List<SearchResult> search(float[] queryEmbedding, int topK, Map<String, String> filters);
    
    /**
     * Delete vectors matching filters
     * 
     * @param filters Metadata filters
     */
    void deleteByFilter(Map<String, String> filters);
    
    /**
     * Delete a specific vector by ID
     * 
     * @param id Document ID
     */
    void deleteById(String id);
    
    /**
     * Get total number of vectors stored
     * 
     * @return Count of vectors
     */
    long count();
    
    /**
     * Check if vector store is healthy
     * 
     * @return true if operational
     */
    boolean isHealthy();
    
    /**
     * Entry for batch upsert
     */
    record VectorEntry(
        String id,
        float[] embedding,
        Map<String, Object> metadata
    ) {}
}
