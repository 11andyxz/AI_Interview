package com.aiinterview.ml.cache;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Random;

/**
 * Service for generating embeddings for semantic caching
 * 
 * Note: This is a simplified implementation using hash-based pseudo-embeddings.
 * In production, this would call OpenAI Embeddings API or use a local embedding model.
 */
@Slf4j
@Service("cacheEmbeddingService")
public class EmbeddingService {
    
    private static final int EMBEDDING_DIMENSION = 384; // Typical small embedding size
    private final Random random = new Random(42); // Fixed seed for consistency
    
    /**
     * Generate embedding vector for text
     * 
     * @param text Input text to embed
     * @return Embedding vector (float array)
     */
    public float[] embed(String text) {
        try {
            // In production, this would call OpenAI API:
            // POST https://api.openai.com/v1/embeddings
            // { "model": "text-embedding-3-small", "input": text }
            
            // For now, use deterministic hash-based pseudo-embeddings
            return generatePseudoEmbedding(text);
        } catch (Exception e) {
            log.error("Failed to generate embedding", e);
            return new float[EMBEDDING_DIMENSION];
        }
    }
    
    /**
     * Calculate cosine similarity between two embedding vectors
     * 
     * @param embedding1 First embedding
     * @param embedding2 Second embedding
     * @return Similarity score (0.0 to 1.0)
     */
    public double cosineSimilarity(float[] embedding1, float[] embedding2) {
        if (embedding1.length != embedding2.length) {
            throw new IllegalArgumentException("Embeddings must have same dimension");
        }
        
        double dotProduct = 0.0;
        double norm1 = 0.0;
        double norm2 = 0.0;
        
        for (int i = 0; i < embedding1.length; i++) {
            dotProduct += embedding1[i] * embedding2[i];
            norm1 += embedding1[i] * embedding1[i];
            norm2 += embedding2[i] * embedding2[i];
        }
        
        if (norm1 == 0.0 || norm2 == 0.0) {
            return 0.0;
        }
        
        return dotProduct / (Math.sqrt(norm1) * Math.sqrt(norm2));
    }
    
    /**
     * Generate pseudo-embedding from text hash (for testing/development)
     * 
     * This creates a deterministic embedding-like vector from text.
     * In production, replace with real embedding API call.
     */
    private float[] generatePseudoEmbedding(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            
            float[] embedding = new float[EMBEDDING_DIMENSION];
            
            // Use hash bytes to seed random number generation
            long seed = 0;
            for (int i = 0; i < Math.min(8, hash.length); i++) {
                seed = (seed << 8) | (hash[i] & 0xFF);
            }
            
            Random rng = new Random(seed);
            
            // Generate pseudo-random embedding with some structure
            for (int i = 0; i < EMBEDDING_DIMENSION; i++) {
                embedding[i] = (float) (rng.nextGaussian() * 0.1);
            }
            
            // Normalize to unit length
            return normalizeEmbedding(embedding);
        } catch (NoSuchAlgorithmException e) {
            log.error("SHA-256 not available", e);
            return new float[EMBEDDING_DIMENSION];
        }
    }
    
    /**
     * Normalize embedding to unit length
     */
    private float[] normalizeEmbedding(float[] embedding) {
        double norm = 0.0;
        for (float v : embedding) {
            norm += v * v;
        }
        norm = Math.sqrt(norm);
        
        if (norm > 0) {
            for (int i = 0; i < embedding.length; i++) {
                embedding[i] /= norm;
            }
        }
        
        return embedding;
    }
    
    /**
     * Batch embed multiple texts (more efficient for multiple calls)
     */
    public float[][] embedBatch(String[] texts) {
        float[][] embeddings = new float[texts.length][];
        for (int i = 0; i < texts.length; i++) {
            embeddings[i] = embed(texts[i]);
        }
        return embeddings;
    }
}
