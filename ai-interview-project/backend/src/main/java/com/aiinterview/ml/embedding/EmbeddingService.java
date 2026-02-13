package com.aiinterview.ml.embedding;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for generating embeddings using OpenAI Embeddings API
 * Uses text-embedding-3-small model (1536 dimensions)
 */
@Slf4j
@Service
public class EmbeddingService {
    
    private static final int EMBEDDING_DIMENSION = 1536;
    private static final String EMBEDDING_MODEL = "text-embedding-3-small";
    private static final String OPENAI_EMBEDDING_URL = "https://api.openai.com/v1/embeddings";
    
    @Value("${openai.api.key}")
    private String apiKey;
    
    @Value("${openai.embedding.enabled:true}")
    private boolean embeddingEnabled;
    
    private final RestTemplate restTemplate;
    
    public EmbeddingService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }
    
    /**
     * Generate embedding for a single text
     * 
     * @param text Input text
     * @return Embedding vector (1536 dimensions)
     */
    public float[] generateEmbedding(String text) {
        if (!embeddingEnabled) {
            log.warn("Embedding service disabled, returning zero vector");
            return new float[EMBEDDING_DIMENSION];
        }
        
        if (text == null || text.trim().isEmpty()) {
            return new float[EMBEDDING_DIMENSION];
        }
        
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);
            
            Map<String, Object> requestBody = Map.of(
                "model", EMBEDDING_MODEL,
                "input", text.trim()
            );
            
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);
            
            ResponseEntity<Map> response = restTemplate.postForEntity(
                OPENAI_EMBEDDING_URL,
                request,
                Map.class
            );
            
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                List<Map<String, Object>> data = (List<Map<String, Object>>) response.getBody().get("data");
                if (data != null && !data.isEmpty()) {
                    List<Double> embedding = (List<Double>) data.get(0).get("embedding");
                    return embedding.stream()
                        .map(Double::floatValue)
                        .collect(Collectors.toList())
                        .stream()
                        .mapToDouble(Float::doubleValue)
                        .toArray();
                }
            }
            
            log.error("Failed to get embedding from OpenAI API");
            return new float[EMBEDDING_DIMENSION];
            
        } catch (Exception e) {
            log.error("Error generating embedding", e);
            return new float[EMBEDDING_DIMENSION];
        }
    }
    
    /**
     * Generate embeddings for multiple texts in batch
     * More efficient than calling generateEmbedding() multiple times
     * 
     * @param texts List of input texts
     * @return List of embedding vectors
     */
    public List<float[]> generateBatchEmbeddings(List<String> texts) {
        if (!embeddingEnabled || texts == null || texts.isEmpty()) {
            return texts.stream()
                .map(t -> new float[EMBEDDING_DIMENSION])
                .collect(Collectors.toList());
        }
        
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);
            
            // OpenAI batch API accepts array of strings
            List<String> cleanTexts = texts.stream()
                .map(t -> t == null ? "" : t.trim())
                .collect(Collectors.toList());
            
            Map<String, Object> requestBody = Map.of(
                "model", EMBEDDING_MODEL,
                "input", cleanTexts
            );
            
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);
            
            ResponseEntity<Map> response = restTemplate.postForEntity(
                OPENAI_EMBEDDING_URL,
                request,
                Map.class
            );
            
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                List<Map<String, Object>> data = (List<Map<String, Object>>) response.getBody().get("data");
                if (data != null) {
                    return data.stream()
                        .sorted(Comparator.comparingInt(item -> (Integer) item.get("index")))
                        .map(item -> {
                            List<Double> embedding = (List<Double>) item.get("embedding");
                            return embedding.stream()
                                .map(Double::floatValue)
                                .collect(Collectors.toList())
                                .stream()
                                .mapToDouble(Float::doubleValue)
                                .toArray();
                        })
                        .map(doubles -> {
                            float[] floats = new float[doubles.length];
                            for (int i = 0; i < doubles.length; i++) {
                                floats[i] = (float) doubles[i];
                            }
                            return floats;
                        })
                        .collect(Collectors.toList());
                }
            }
            
            log.error("Failed to get batch embeddings from OpenAI API");
            return texts.stream()
                .map(t -> new float[EMBEDDING_DIMENSION])
                .collect(Collectors.toList());
            
        } catch (Exception e) {
            log.error("Error generating batch embeddings", e);
            return texts.stream()
                .map(t -> new float[EMBEDDING_DIMENSION])
                .collect(Collectors.toList());
        }
    }
    
    /**
     * Calculate cosine similarity between two embedding vectors
     * 
     * @param embedding1 First embedding
     * @param embedding2 Second embedding
     * @return Similarity score (0.0 to 1.0, or -1.0 to 1.0 for non-normalized)
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
}
