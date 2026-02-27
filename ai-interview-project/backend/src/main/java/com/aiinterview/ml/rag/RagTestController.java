package com.aiinterview.ml.rag;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Test endpoint for RAG performance testing (Week 12 P1-AC3)
 * Exposes hybrid search + reranking for load testing
 */
@Slf4j
@RestController
@RequestMapping("/api/test/rag")
@RequiredArgsConstructor
public class RagTestController {

    private final HybridSearchEngine hybridSearchEngine;
    private final RerankerService rerankerService;

    /**
     * Test endpoint: Hybrid search + reranking
     * Used for performance testing (p95 latency measurement)
     */
    @GetMapping("/search")
    public Map<String, Object> testHybridSearch(
            @RequestParam(defaultValue = "Java Spring Boot dependency injection") String query,
            @RequestParam(required = false) String role,
            @RequestParam(defaultValue = "5") int topK,
            @RequestParam(defaultValue = "true") boolean useReranker) {

        long startTime = System.currentTimeMillis();

        try {
            // Step 1: Hybrid search (vector + keyword)
            Map<String, String> filters = new HashMap<>();
            if (role != null && !role.isEmpty()) {
                filters.put("category", role);
            }

            int candidateCount = useReranker ? (topK * 2) : topK; // Fetch more for reranking
            List<SearchResult> searchResults = hybridSearchEngine.hybridSearch(
                    query, filters, candidateCount, 0.7, 0.3);

            // Step 2: Reranking (optional)
            List<SearchResult> finalResults = useReranker ?
                rerankerService.rerank(query, searchResults, topK) :
                searchResults.stream().limit(topK).collect(java.util.stream.Collectors.toList());

            long latencyMs = System.currentTimeMillis() - startTime;

            return Map.of(
                    "success", true,
                    "query", query,
                    "results", finalResults,
                    "count", finalResults.size(),
                    "latency_ms", latencyMs
            );

        } catch (Exception e) {
            long latencyMs = System.currentTimeMillis() - startTime;
            log.error("Test search failed: {}", e.getMessage(), e);

            return Map.of(
                    "success", false,
                    "error", e.getMessage(),
                    "latency_ms", latencyMs
            );
        }
    }

    /**
     * Health check for RAG components
     */
    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of(
                "status", "UP",
                "hybridSearch", hybridSearchEngine != null ? "available" : "unavailable",
                "reranker", rerankerService != null ? "available" : "unavailable"
        );
    }
}
