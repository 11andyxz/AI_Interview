package com.aiinterview.ml.rag;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Hybrid search combining semantic and keyword-based retrieval
 * Improves retrieval precision by leveraging both approaches
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HybridSearchEngine {
    
    private final KeywordSearchService keywordSearchService;
    private final VectorSearchService vectorSearchService;
    
    /**
     * Perform hybrid search combining semantic and keyword retrieval
     * 
     * @param query User query
     * @param filters Metadata filters (category, tags, etc.)
     * @param topK Number of results to return
     * @param semanticWeight Weight for semantic similarity (0.0-1.0)
     * @param keywordWeight Weight for keyword matching (0.0-1.0)
     * @return Ranked search results
     */
    public List<SearchResult> hybridSearch(
            String query,
            Map<String, String> filters,
            int topK,
            double semanticWeight,
            double keywordWeight) {
        
        log.debug("Hybrid search: query='{}', topK={}, weights=[semantic={}, keyword={}]",
                 query, topK, semanticWeight, keywordWeight);
        
        // Validate weights
        if (semanticWeight < 0 || keywordWeight < 0) {
            throw new IllegalArgumentException("Weights must be non-negative");
        }
        
        double totalWeight = semanticWeight + keywordWeight;
        if (totalWeight == 0) {
            throw new IllegalArgumentException("At least one weight must be positive");
        }
        
        // Normalize weights
        double normSemanticWeight = semanticWeight / totalWeight;
        double normKeywordWeight = keywordWeight / totalWeight;
        
        // Retrieve from both search methods
        int fetchSize = Math.min(topK * 3, 100); // Fetch more for better fusion
        
        List<SearchResult> semanticResults = vectorSearchService.semanticSearch(query, filters, fetchSize);
        List<SearchResult> keywordResults = keywordSearchService.keywordSearch(query, fetchSize);
        
        // Merge and score results
        Map<String, SearchResult> mergedResults = new HashMap<>();
        
        // Add semantic results
        for (SearchResult result : semanticResults) {
            result.setSemanticScore(result.getScore());
            result.setScore(result.getScore() * normSemanticWeight);
            mergedResults.put(result.getId(), result);
        }
        
        // Add/merge keyword results
        for (SearchResult result : keywordResults) {
            if (mergedResults.containsKey(result.getId())) {
                SearchResult existing = mergedResults.get(result.getId());
                existing.setKeywordScore(result.getScore());
                existing.setScore(existing.getScore() + result.getScore() * normKeywordWeight);
            } else {
                result.setKeywordScore(result.getScore());
                result.setScore(result.getScore() * normKeywordWeight);
                mergedResults.put(result.getId(), result);
            }
        }
        
        // Sort by combined score and return topK
        List<SearchResult> rankedResults = mergedResults.values().stream()
            .sorted(Comparator.comparingDouble(SearchResult::getScore).reversed())
            .limit(topK)
            .collect(Collectors.toList());
        
        log.info("Hybrid search completed: {} results, semantic={}, keyword={}, merged={}",
                rankedResults.size(), semanticResults.size(), keywordResults.size(), mergedResults.size());
        
        return rankedResults;
    }
    
    /**
     * Hybrid search with default weights (0.7 semantic, 0.3 keyword)
     */
    public List<SearchResult> hybridSearch(String query, Map<String, String> filters, int topK) {
        return hybridSearch(query, filters, topK, 0.7, 0.3);
    }
}
