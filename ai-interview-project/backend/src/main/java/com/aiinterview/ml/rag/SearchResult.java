package com.aiinterview.ml.rag;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Search result with relevance score and metadata
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class SearchResult {
    
    /**
     * Document ID
     */
    private String id;
    
    /**
     * Document content
     */
    private String content;
    
    /**
     * Relevance score (0.0 - 1.0)
     */
    private double score;
    
    /**
     * Semantic similarity score
     */
    private Double semanticScore;
    
    /**
     * Keyword match score
     */
    private Double keywordScore;
    
    /**
     * Reranker score (if reranked)
     */
    private Double rerankScore;
    
    /**
     * Document metadata (tags, category, etc.)
     */
    private Map<String, String> metadata;
    
    /**
     * Token count for context budget management
     */
    private Integer tokenCount;
    
    /**
     * Get final relevance score for ranking
     */
    public double getFinalScore() {
        if (rerankScore != null) {
            return rerankScore;
        }
        return score;
    }
}
