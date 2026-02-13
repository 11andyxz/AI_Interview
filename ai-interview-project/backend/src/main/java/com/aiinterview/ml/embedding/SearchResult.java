package com.aiinterview.ml.embedding;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Result from vector similarity search
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SearchResult {
    
    /** Unique document ID */
    private String id;
    
    /** Cosine similarity score (0.0 to 1.0) */
    private double score;
    
    /** Document content/text */
    private String content;
    
    /** Metadata associated with the document */
    private Map<String, String> metadata;
    
    /** Embedding vector (optional, usually not returned) */
    private float[] embedding;
}
