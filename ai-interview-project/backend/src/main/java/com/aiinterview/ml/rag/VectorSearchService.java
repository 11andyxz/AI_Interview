package com.aiinterview.ml.rag;

import com.aiinterview.ml.embedding.EmbeddingService;
import com.aiinterview.ml.embedding.VectorStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Vector-based semantic search service
 * Wraps VectorStore (Redis or InMemory) for RAG use cases
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VectorSearchService {
    
    private final VectorStore vectorStore;
    private final EmbeddingService embeddingService;
    
    /**
     * Perform semantic search using vector embeddings
     */
    public List<SearchResult> semanticSearch(String query, Map<String, String> filters, int topK) {
        // Generate embedding for query
        float[] queryEmbedding = embeddingService.generateEmbedding(query);
        
        // Search using vector store
        List<com.aiinterview.ml.embedding.SearchResult> rawResults = 
            vectorStore.search(queryEmbedding, topK, filters);
        
        // Convert to RAG SearchResult
        return rawResults.stream()
            .map(r -> SearchResult.builder()
                .id(r.getId())
                .content(r.getContent())
                .score(r.getScore())
                .semanticScore(r.getScore())
                .metadata(r.getMetadata() != null ? r.getMetadata() : new HashMap<>())
                .build())
            .collect(Collectors.toList());
    }
}
