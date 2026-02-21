package com.aiinterview.controller;

import com.aiinterview.ml.embedding.EmbeddingService;
import com.aiinterview.ml.embedding.VectorStore;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Admin API for data management
 * Import interview questions to vector store (Redis or InMemory)
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/knowledge")
@RequiredArgsConstructor
public class KnowledgeImportController {

    private final EmbeddingService embeddingService;
    private final VectorStore vectorStore;

    @Data
    public static class ImportRequest {
        private String id;
        private String content;
        private Map<String, String> metadata;
    }

    /**
     * Import a single question with automatic embedding generation
     * POST /api/admin/knowledge/import
     * Body: {id, content, metadata}
     */
    @PostMapping("/import")
    public Map<String, Object> importQuestion(@RequestBody ImportRequest request) {
        long startTime = System.currentTimeMillis();

        try {
            // Validate input
            if (request.getId() == null || request.getId().isEmpty()) {
                return Map.of("success", false, "error", "Missing 'id' field");
            }
            if (request.getContent() == null || request.getContent().isEmpty()) {
                return Map.of("success", false, "error", "Missing 'content' field");
            }

            // Generate embedding
            float[] embedding = embeddingService.generateEmbedding(request.getContent());
            
            // Prepare metadata
            Map<String, Object> metadata = new HashMap<>();
            if (request.getMetadata() != null) {
                metadata.putAll(request.getMetadata());
            }
            metadata.put("content", request.getContent());
            
            // Store in Redis
            vectorStore.upsert(request.getId(), embedding, metadata);
            
            long latency = System.currentTimeMillis() - startTime;
            
            log.info("Imported question: {} ({}ms)", request.getId(), latency);
            
            return Map.of(
                "success", true,
                "id", request.getId(),
                "latency_ms", latency
            );

        } catch (Exception e) {
            long latency = System.currentTimeMillis() - startTime;
            log.error("Failed to import question: {}", request.getId(), e);
            
            return Map.of(
                "success", false,
                "error", e.getMessage(),
                "latency_ms", latency
            );
        }
    }

    /**
     * Health check for import service
     */
    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of(
            "status", "UP",
            "embedding_service", embeddingService != null ? "available" : "unavailable",
            "vector_store", vectorStore != null ? "available" : "unavailable"
        );
    }

    /**
     * Get statistics about vector store
     */
    @GetMapping("/stats")
    public Map<String, Object> stats() {
        try {
            long count = vectorStore.count();
            Map<String, Object> cacheStats = embeddingService.getCacheStats();
            
            return Map.of(
                "success", true,
                "vector_count", count,
                "cache_stats", cacheStats
            );
        } catch (Exception e) {
            log.error("Failed to get stats", e);
            return Map.of(
                "success", false,
                "error", e.getMessage()
            );
        }
    }
}
